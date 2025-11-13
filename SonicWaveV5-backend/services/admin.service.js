const bcrypt = require('bcrypt');
const logger = require('../logger');
const { dbPool } = require('../config/db');
const {
  listUsers,
  listCustomers,
  findUserById,
  findUserDetailedById,
  updateUserRole,
  updateUserAccountType,
  softDeleteUser,
  hardDeleteUser,
  updateUserPasswordInternal,
  findCustomerById,
  updateCustomerById
} = require('../repositories/admin.repository');
const { hasColumn } = require('../utils/schema');
const auditService = require('./audit.service');
const featureFlagsService = require('./featureFlags.service');
const offlineControlChannel = require('../realtime/offlineControlChannel');

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

async function getUserDetail(userId) {
  return findUserDetailedById(userId);
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

async function resetUserPassword({ actorId, userId, password, ip, userAgent }) {
  const existing = await findUserById(userId);
  if (!existing) {
    throw ServiceError('USER_NOT_FOUND', '目标用户不存在');
  }

  const hashed = await bcrypt.hash(password, 10);
  const affected = await updateUserPasswordInternal(userId, hashed);

  if (!affected) {
    throw ServiceError('PASSWORD_UPDATE_FAILED', '更新密码失败');
  }

  await auditService.logAction({
    actorId,
    action: 'USER_PASSWORD_RESET',
    targetType: 'user',
    targetId: userId,
    metadata: {
      passwordLength: password.length
    },
    ip,
    userAgent
  });
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

async function getCustomerDetail(customerId) {
  return findCustomerById(customerId);
}

async function updateCustomer({ actorId, customerId, payload, ip, userAgent }) {
  const existing = await findCustomerById(customerId);
  if (!existing) {
    throw ServiceError('CUSTOMER_NOT_FOUND', '目标客户不存在');
  }

  const normalizedPayload = {};
  if (payload.name !== undefined) normalizedPayload.name = payload.name;
  if (payload.dateOfBirth !== undefined) normalizedPayload.dateOfBirth = payload.dateOfBirth;
  if (payload.gender !== undefined) normalizedPayload.gender = payload.gender;
  if (payload.phone !== undefined) normalizedPayload.phone = payload.phone;
  if (payload.email !== undefined) normalizedPayload.email = payload.email;
  if (payload.height !== undefined) normalizedPayload.height = payload.height;
  if (payload.weight !== undefined) normalizedPayload.weight = payload.weight;

  let affected;
  try {
    affected = await updateCustomerById(customerId, normalizedPayload);
  } catch (error) {
    if (error.code === 'ER_DUP_ENTRY') {
      throw ServiceError('CUSTOMER_EXISTS', '该邮箱已存在于其它客户记录中');
    }
    throw error;
  }
  const updated = affected ? await findCustomerById(customerId) : existing;

  await auditService.logAction({
    actorId,
    action: 'CUSTOMER_UPDATED',
    targetType: 'customer',
    targetId: customerId,
    metadata: {
      changes: normalizedPayload
    },
    ip,
    userAgent
  });

  return updated;
}

async function getFeatureFlagsSnapshot() {
  return featureFlagsService.getFeatureFlagSnapshot();
}

async function updateOfflineModeFlag({ actorId, enabled, notifyOnline = false, ip, userAgent }) {
  const updated = await featureFlagsService.setOfflineModeFlag({ enabled, actorId });
  if (notifyOnline) {
    offlineControlChannel.broadcastOfflineModeUpdate({
      enabled,
      updatedBy: actorId
    });
  }
  await auditService.logAction({
    actorId,
    action: 'FEATURE_FLAG_UPDATED',
    targetType: 'feature_flag',
    targetId: featureFlagsService.FEATURE_KEYS.OFFLINE_MODE,
    metadata: {
      enabled,
      notifyOnline
    },
    ip,
    userAgent
  });
  return updated;
}

async function forceExitOfflineMode({ actorId, countdownSec, ip, userAgent }) {
  offlineControlChannel.broadcastForceExit({
    countdownSec,
    updatedBy: actorId
  });
  setTimeout(async () => {
    try {
      await featureFlagsService.setOfflineModeFlag({ enabled: false, actorId });
      offlineControlChannel.broadcastOfflineModeUpdate({
        enabled: false,
        updatedBy: actorId
      });
      logger.info('[AdminService] Force exit countdown elapsed, offline mode disabled.');
    } catch (error) {
      logger.error('[AdminService] Failed to disable offline mode after force exit countdown', {
        error: error.message
      });
    }
  }, Math.max(1, countdownSec) * 1000);
  await auditService.logAction({
    actorId,
    action: 'OFFLINE_FORCE_EXIT_TRIGGERED',
    targetType: 'feature_flag',
    targetId: featureFlagsService.FEATURE_KEYS.OFFLINE_MODE,
    metadata: {
      countdownSec
    },
    ip,
    userAgent
  });
  return { countdownSec };
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
  getUserDetail,
  changeUserRole,
  changeUserAccountType,
  resetUserPassword,
  deleteUser,
  getCustomerDetail,
  updateCustomer,
  ensureSeedAdmin,
  getFeatureFlagsSnapshot,
  updateOfflineModeFlag,
  forceExitOfflineMode
};
