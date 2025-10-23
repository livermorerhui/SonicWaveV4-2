const bcrypt = require('bcrypt');
const logger = require('../logger');
const { dbPool } = require('../config/db');
const {
  listUsers,
  listCustomers,
  findUserById,
  updateUserRole,
  updateUserAccountType,
  softDeleteUser,
  hardDeleteUser
} = require('../repositories/admin.repository');
const { hasColumn } = require('../utils/schema');
const auditService = require('./audit.service');

const ServiceError = (code, message) => {
  const err = new Error(message);
  err.code = code;
  return err;
};

async function getAllUsers(params) {
  return listUsers(params);
}

async function getAllCustomers(params) {
  return listCustomers(params);
}

async function changeUserRole({ actorId, userId, role, ip, userAgent }) {
  const existing = await findUserById(userId);
  if (!existing) {
    throw ServiceError('USER_NOT_FOUND', '目标用户不存在');
  }

  if (existing.role === role) {
    return existing;
  }

  const affected = await updateUserRole(userId, role);
  if (!affected) {
    throw ServiceError('ROLE_UPDATE_FAILED', '更新用户角色失败');
  }

  const updated = await findUserById(userId);

  await auditService.logAction({
    actorId,
    action: 'USER_ROLE_UPDATED',
    targetType: 'user',
    targetId: userId,
    metadata: {
      previousRole: existing.role,
      newRole: updated.role
    },
    ip,
    userAgent
  });

  return updated;
}

async function changeUserAccountType({ actorId, userId, accountType, ip, userAgent }) {
  const existing = await findUserById(userId);
  if (!existing) {
    throw ServiceError('USER_NOT_FOUND', '目标用户不存在');
  }

  if (existing.accountType === accountType) {
    return existing;
  }

  const affected = await updateUserAccountType(userId, accountType);
  if (!affected) {
    throw ServiceError('ACCOUNT_TYPE_UPDATE_FAILED', '更新账号类型失败');
  }

  const updated = await findUserById(userId);

  await auditService.logAction({
    actorId,
    action: 'USER_ACCOUNT_TYPE_UPDATED',
    targetType: 'user',
    targetId: userId,
    metadata: {
      previousAccountType: existing.accountType,
      newAccountType: updated.accountType
    },
    ip,
    userAgent
  });

  return updated;
}

async function deleteUser({ actorId, userId, force = false, ip, userAgent }) {
  const existing = await findUserById(userId);
  if (!existing) {
    throw ServiceError('USER_NOT_FOUND', '目标用户不存在');
  }

  const action = force ? hardDeleteUser : softDeleteUser;
  const affected = await action(userId);

  if (!affected) {
    throw ServiceError('DELETE_FAILED', '删除用户失败');
  }

  await auditService.logAction({
    actorId,
    action: force ? 'USER_HARD_DELETED' : 'USER_SOFT_DELETED',
    targetType: 'user',
    targetId: userId,
    metadata: {
      force,
      previousDeletedAt: existing.deletedAt
    },
    ip,
    userAgent
  });
}

async function ensureSeedAdmin() {
  const email = process.env.ADMIN_SEED_EMAIL;
  const password = process.env.ADMIN_SEED_PASSWORD;
  const username = process.env.ADMIN_SEED_USERNAME || 'admin';

  if (!email || !password) {
    logger.info('[AdminService] Skipping admin seeding (missing ADMIN_SEED_EMAIL or ADMIN_SEED_PASSWORD)');
    return;
  }

  const hasRole = await hasColumn('users', 'role');
  const hasAccountType = await hasColumn('users', 'account_type');
  if (!hasRole || !hasAccountType) {
    logger.warn('[AdminService] Cannot seed admin user because users.role or users.account_type column is missing.');
    return;
  }

  const [existingUsers] = await dbPool.execute(
    'SELECT id, role FROM users WHERE email = ? LIMIT 1',
    [email]
  );

  if (existingUsers.length > 0) {
    const existing = existingUsers[0];
    if (existing.role !== 'admin') {
      await dbPool.execute(
        'UPDATE users SET role = ?, account_type = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?',
        ['admin', 'normal', existing.id]
      );
      logger.info(`[AdminService] Ensured existing user ${email} has admin role.`);
    } else {
      logger.info('[AdminService] Admin seed user already exists with admin role.');
    }
    return;
  }

  const hashedPassword = await bcrypt.hash(password, 10);
  await dbPool.execute(
    'INSERT INTO users (username, email, password, role, account_type) VALUES (?, ?, ?, ?, ?)',
    [username, email, hashedPassword, 'admin', 'normal']
  );
  logger.info(`[AdminService] Seeded admin user with email ${email}.`);
}

module.exports = {
  getAllUsers,
  getAllCustomers,
  changeUserRole,
  changeUserAccountType,
  deleteUser,
  ensureSeedAdmin
};
