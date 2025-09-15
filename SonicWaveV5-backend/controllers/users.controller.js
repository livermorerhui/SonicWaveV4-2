const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const { dbPool } = require('../config/db');
const logger = require('../logger');

// 用户注册
const registerUser = async (req, res) => {
  try {
    const { username, email, password } = req.body;
    if (!username || !email || !password) {
      return res.status(400).json({ message: 'Username, email, and password are required.' });
    }

    const saltRounds = 10;
    const hashedPassword = await bcrypt.hash(password, saltRounds);

    const sql = 'INSERT INTO users (username, email, password) VALUES (?, ?, ?)';
    const [result] = await dbPool.execute(sql, [username, email, hashedPassword]);

    res.status(201).json({
      message: 'User created successfully!',
      userId: result.insertId,
      username: username
    });

  } catch (error) {
    if (error.code === 'ER_DUP_ENTRY') {
      return res.status(409).json({ message: 'Username or email already exists.' });
    }
    logger.error('Error during user registration:', { error: error.message });
    res.status(500).json({ message: 'Internal server error.' });
  }
};

// 用户登录
const loginUser = async (req, res) => {
  try {
    const { email, password } = req.body;
    if (!email || !password) {
      return res.status(400).json({ message: 'Email and password are required.' });
    }

    const sql = 'SELECT * FROM users WHERE email = ?';
    const [users] = await dbPool.execute(sql, [email]);

    if (users.length === 0) {
      return res.status(401).json({ message: 'Invalid credentials.' });
    }

    const user = users[0];
    const isPasswordMatch = await bcrypt.compare(password, user.password);

    if (!isPasswordMatch) {
      return res.status(401).json({ message: 'Invalid credentials.' });
    }

    const payload = { userId: user.id, username: user.username };
    const secret = process.env.JWT_SECRET;
    const options = { expiresIn: '1h' };
    const token = jwt.sign(payload, secret, options);

    res.json({
      message: 'Logged in successfully!',
      token: token,
      username: user.username
    });

  } catch (error) {
    logger.error('Error during user login:', { error: error.message });
    res.status(500).json({ message: 'Internal server error.' });
  }
};

// 获取当前用户信息
const getUserProfile = async (req, res) => {
  try {
    const { userId } = req.user;
    const sql = 'SELECT id, username, email, created_at FROM users WHERE id = ?';
    const [users] = await dbPool.execute(sql, [userId]);

    if (users.length === 0) {
      return res.status(404).json({ message: 'User not found.' });
    }

    res.json({
      message: 'Successfully fetched user profile.',
      user: users[0]
    });

  } catch (error) {
    logger.error('Error fetching user profile:', { error: error.message });
    res.status(500).json({ message: 'Internal server error.' });
  }
};

// 更新当前用户信息
const updateUserProfile = async (req, res) => {
  try {
    const { userId } = req.user;
    const { username, email } = req.body;

    if (!username && !email) {
      return res.status(400).json({ message: 'Username or email is required to update.' });
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

    const sql = `UPDATE users SET ${fieldsToUpdate.join(', ')} WHERE id = ?`;
    await dbPool.execute(sql, values);

    res.json({
      message: 'User profile updated successfully.'
    });

  } catch (error) {
    if (error.code === 'ER_DUP_ENTRY') {
      return res.status(409).json({ message: 'Username or email is already taken by another account.' });
    }
    logger.error('Error updating user profile:', { error: error.message });
    res.status(500).json({ message: 'Internal server error.' });
  }
};

module.exports = {
  registerUser,
  loginUser,
  getUserProfile,
  updateUserProfile
};
