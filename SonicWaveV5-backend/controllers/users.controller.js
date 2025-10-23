const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const SHA256 = require('crypto-js/sha256');
const { dbPool } = require('../config/db');
const logger = require('../logger');
const { hasColumn } = require('../utils/schema');

const buildError = (code, message) => ({
  error: {
    code,
    message,
    traceId: crypto.randomUUID()
  }
});

// 用户注册
const registerUser = async (req, res) => {
  try {
    const { username, email, password } = req.body;
    if (!username || !email || !password) {
      return res.status(400).json(buildError('INVALID_INPUT', '用户名、邮箱和密码均为必填项'));
    }

    const saltRounds = 10;
    const hashedPassword = await bcrypt.hash(password, saltRounds);

    const hasRoleColumn = await hasColumn('users', 'role');
    const hasAccountTypeColumn = await hasColumn('users', 'account_type');
    const columns = ['username', 'email', 'password'];
    const placeholders = ['?', '?', '?'];
    const values = [username, email, hashedPassword];

    if (hasRoleColumn) {
      columns.push('role');
      placeholders.push('?');
      values.push('user');
    }

    if (hasAccountTypeColumn) {
      columns.push('account_type');
      placeholders.push('?');
      values.push('normal');
    }

    const sql = `INSERT INTO users (${columns.join(', ')}) VALUES (${placeholders.join(', ')})`;
    const [result] = await dbPool.execute(sql, values);

    res.status(201).json({
      message: '用户创建成功',
      userId: result.insertId,
      username
    });
  } catch (error) {
    if (error.code === 'ER_DUP_ENTRY') {
      return res.status(409).json(buildError('USER_EXISTS', '用户名或邮箱已存在'));
    }
    logger.error('Error during user registration:', { error: error.message });
    res.status(500).json(buildError('INTERNAL_ERROR', '服务器内部错误'));
  }
};

// Helper function to generate and store a new refresh token
const generateAndStoreRefreshToken = async (userId, dbPool) => {
    const refreshToken = crypto.randomBytes(64).toString('hex');
    const hashedToken = SHA256(refreshToken).toString();
    const expiresInDays = 7;
    const expiresAt = new Date();
    expiresAt.setDate(expiresAt.getDate() + expiresInDays);

    const sql = 'INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES (?, ?, ?)';
    await dbPool.execute(sql, [userId, hashedToken, expiresAt]);

    return refreshToken;
};

// 用户登录 (Refactored)
const loginUser = async (req, res) => {
  try {
    const { email, password } = req.body;
    if (!email || !password) {
      return res.status(400).json(buildError('INVALID_INPUT', '邮箱和密码为必填项'));
    }

    const sql = 'SELECT * FROM users WHERE email = ?';
    const [users] = await dbPool.execute(sql, [email]);

    if (users.length === 0) {
      return res.status(401).json(buildError('INVALID_CREDENTIALS', '邮箱或密码错误'));
    }

    const user = users[0];
    const isPasswordMatch = await bcrypt.compare(password, user.password);

    if (!isPasswordMatch) {
      return res.status(401).json(buildError('INVALID_CREDENTIALS', '邮箱或密码错误'));
    }

    // 1. Generate Access Token (short-lived)
    const hasRoleColumn = await hasColumn('users', 'role');
    const hasAccountTypeColumn = await hasColumn('users', 'account_type');
    const accessTokenPayload = {
      id: user.id,
      email: user.email,
      username: user.username,
      role: hasRoleColumn ? user.role : 'user',
      accountType: hasAccountTypeColumn ? user.account_type : 'normal'
    };
    const secret = process.env.JWT_SECRET;
    const accessToken = jwt.sign(accessTokenPayload, secret, { expiresIn: '15m' });

    // 2. Generate and Store Refresh Token (long-lived)
    const refreshToken = await generateAndStoreRefreshToken(user.id, dbPool);

    // 3. Return both tokens to the client
    res.json({
      message: '登录成功',
      accessToken,
      refreshToken,
      username: user.username,
      userId: user.id,
      role: accessTokenPayload.role,
      accountType: accessTokenPayload.accountType
    });
  } catch (error) {
    logger.error('Error during user login:', { error: error.message });
    res.status(500).json(buildError('INTERNAL_ERROR', '服务器内部错误'));
  }
};

// 获取当前用户信息
const getUserProfile = async (req, res) => {
  try {
    const userId = req.user?.id || req.user?.userId;
    if (!userId) {
      return res.status(401).json(buildError('UNAUTHENTICATED', '需要登录才能访问'));
    }

    const hasRoleColumn = await hasColumn('users', 'role');
    const hasAccountTypeColumn = await hasColumn('users', 'account_type');
    const hasDeletedAtColumn = await hasColumn('users', 'deleted_at');
    const roleProjection = hasRoleColumn ? 'role' : "'user' AS role";
    const accountTypeProjection = hasAccountTypeColumn ? 'account_type' : "'normal' AS account_type";
    const deletedAtProjection = hasDeletedAtColumn ? 'deleted_at' : 'NULL AS deleted_at';
    const sql = `SELECT id, username, email, ${roleProjection}, ${accountTypeProjection}, created_at, ${deletedAtProjection} FROM users WHERE id = ?`;
    const [users] = await dbPool.execute(sql, [userId]);

    if (users.length === 0) {
      return res.status(404).json(buildError('USER_NOT_FOUND', '未找到用户'));
    }

    const user = users[0];
    res.json({
      user: {
        id: user.id,
        email: user.email,
        role: user.role,
        username: user.username,
        accountType: user.account_type,
        createdAt: user.created_at,
        deletedAt: user.deleted_at
      }
    });
  } catch (error) {
    logger.error('Error fetching user profile:', { error: error.message });
    res.status(500).json(buildError('INTERNAL_ERROR', '服务器内部错误'));
  }
};

// 更新当前用户信息
const updateUserProfile = async (req, res) => {
  try {
    const userId = req.user?.id || req.user?.userId;
    const { username, email } = req.body;

    if (!username && !email) {
      return res.status(400).json(buildError('INVALID_INPUT', '请至少提供新的用户名或邮箱'));
    }

    const fieldsToUpdate = [];
    const values = [];
    if (username) {
      fieldsToUpdate.push('username = ?');
      values.push(username);
    }
    if (email) {
      fieldsToUpdate.push('email = ?');
      values.push(email);
    }
    values.push(userId);

    fieldsToUpdate.push('updated_at = CURRENT_TIMESTAMP');

    const sql = `UPDATE users SET ${fieldsToUpdate.join(', ')} WHERE id = ?`;
    await dbPool.execute(sql, values);

    res.json({
      message: '用户信息更新成功'
    });
  } catch (error) {
    if (error.code === 'ER_DUP_ENTRY') {
      return res.status(409).json(buildError('USER_EXISTS', '用户名或邮箱已被其他账号使用'));
    }
    logger.error('Error updating user profile:', { error: error.message });
    res.status(500).json(buildError('INTERNAL_ERROR', '服务器内部错误'));
  }
};

module.exports = {
  registerUser,
  loginUser,
  getUserProfile,
  updateUserProfile
};
