const crypto = require('crypto');
let adminService = require('../services/admin.service');
const featureFlagsService = require('../services/featureFlags.service');
const logger = require('../logger');

const buildError = (code, message) => ({
  error: {
    code,
    message,
    traceId: crypto.randomUUID()
  }
});

const buildSuccess = (message, data = {}) => ({
  message,
  ...data
});

const toActorId = req => req.user?.id || req.user?.userId || null;
const toIp = req => req.headers['x-forwarded-for']?.split(',')[0]?.trim() || req.ip || null;
const toUserAgent = req => req.headers['user-agent'] || null;

const toNullableTrimmed = value => {
  if (value === undefined) return undefined;
  if (value === null) return null;
  if (typeof value === 'string') {
    const trimmed = value.trim();
    return trimmed === '' ? null : trimmed;
  }
  return value;
};

const normalizeDateInput = value => {
  if (value === undefined) return undefined;
  if (value === null) return null;
  if (value instanceof Date) {
    if (Number.isNaN(value.getTime())) return null;
    const year = value.getUTCFullYear();
    const month = String(value.getUTCMonth() + 1).padStart(2, '0');
    const day = String(value.getUTCDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  const trimmed = typeof value === 'string' ? value.trim() : String(value);
  if (trimmed === '') return null;
  if (/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) {
    return trimmed;
  }
  const parsed = new Date(trimmed);
  if (Number.isNaN(parsed.getTime())) {
    return null;
  }
  const year = parsed.getUTCFullYear();
  const month = String(parsed.getUTCMonth() + 1).padStart(2, '0');
  const day = String(parsed.getUTCDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const getAllUsers = async (req, res) => {
  try {
    const { page, pageSize, keyword, role, accountType, sortBy, sortOrder } = req.query;
    const result = await adminService.getAllUsers({
      page,
      pageSize,
      keyword,
      role,
      accountType,
      sortBy,
      sortOrder
    });
    res.json(result);
  } catch (error) {
    logger.error('[AdminController] Failed to fetch users', { error: error.message });
    res.status(500).json(buildError('INTERNAL_ERROR', '获取用户列表失败'));
  }
};

const getAllCustomers = async (req, res) => {
  try {
    const { page, pageSize, keyword, sortBy, sortOrder } = req.query;
    const result = await adminService.getAllCustomers({ page, pageSize, keyword, sortBy, sortOrder });
    res.json(result);
  } catch (error) {
    logger.error('[AdminController] Failed to fetch customers', { error: error.message });
    res.status(500).json(buildError('INTERNAL_ERROR', '获取客户列表失败'));
  }
};

const getUserDetail = async (req, res) => {
  try {
    const userId = Number.parseInt(req.params.id, 10);
    const detail = await adminService.getUserDetail(userId);
    if (!detail) {
      return res.status(404).json(buildError('USER_NOT_FOUND', '未找到指定用户'));
    }
    res.json(detail);
  } catch (error) {
    logger.error('[AdminController] Failed to fetch user detail', { error: error.message });
    res.status(500).json(buildError('INTERNAL_ERROR', '获取用户详情失败'));
  }
};

const updateUserRole = async (req, res) => {
  try {
    const userId = Number.parseInt(req.params.id, 10);
    const { role } = req.body;

    const updated = await adminService.changeUserRole({
      actorId: toActorId(req),
      userId,
      role,
      ip: toIp(req),
      userAgent: toUserAgent(req)
    });

    res.json(
      buildSuccess('用户角色已更新', {
        user: updated
      })
    );
  } catch (error) {
    if (error.code === 'USER_NOT_FOUND') {
      return res.status(404).json(buildError('USER_NOT_FOUND', '未找到指定用户'));
    }
    if (error.code === 'ROLE_UPDATE_FAILED') {
      return res.status(500).json(buildError('ROLE_UPDATE_FAILED', '更新用户角色失败'));
    }
    logger.error('[AdminController] Failed to update user role', { error: error.message });
    return res.status(500).json(buildError('INTERNAL_ERROR', '更新用户角色时发生未知错误'));
  }
};

const updateUserAccountType = async (req, res) => {
  try {
    const userId = Number.parseInt(req.params.id, 10);
    const { accountType } = req.body;

    const updated = await adminService.changeUserAccountType({
      actorId: toActorId(req),
      userId,
      accountType,
      ip: toIp(req),
      userAgent: toUserAgent(req)
    });

    res.json(
      buildSuccess('账号类型已更新', {
        user: updated
      })
    );
  } catch (error) {
    if (error.code === 'USER_NOT_FOUND') {
      return res.status(404).json(buildError('USER_NOT_FOUND', '未找到指定用户'));
    }
    if (error.code === 'ACCOUNT_TYPE_UPDATE_FAILED') {
      return res.status(500).json(buildError('ACCOUNT_TYPE_UPDATE_FAILED', '更新账号类型失败'));
    }
    logger.error('[AdminController] Failed to update account type', { error: error.message });
    return res.status(500).json(buildError('INTERNAL_ERROR', '更新账号类型时发生未知错误'));
  }
};

const updateUserPassword = async (req, res) => {
  try {
    const userId = Number.parseInt(req.params.id, 10);
    const rawPassword = req.body.password;
    const password = typeof rawPassword === 'string' ? rawPassword.trim() : rawPassword;

    await adminService.resetUserPassword({
      actorId: toActorId(req),
      userId,
      password,
      ip: toIp(req),
      userAgent: toUserAgent(req)
    });

    res.json(buildSuccess('密码已重置'));
  } catch (error) {
    if (error.code === 'USER_NOT_FOUND') {
      return res.status(404).json(buildError('USER_NOT_FOUND', '未找到指定用户'));
    }
    if (error.code === 'PASSWORD_UPDATE_FAILED') {
      return res.status(500).json(buildError('PASSWORD_UPDATE_FAILED', '更新密码失败'));
    }
    logger.error('[AdminController] Failed to reset password', { error: error.message });
    return res.status(500).json(buildError('INTERNAL_ERROR', '重置密码时发生未知错误'));
  }
};

const deleteUser = async (req, res) => {
  try {
    const userId = Number.parseInt(req.params.id, 10);
    const force = Boolean(req.query.force);

    await adminService.deleteUser({
      actorId: toActorId(req),
      userId,
      force,
      ip: toIp(req),
      userAgent: toUserAgent(req)
    });

    res.json(
      buildSuccess(force ? '用户已彻底删除' : '用户已标记为删除', {
        force
      })
    );
  } catch (error) {
    if (error.code === 'USER_NOT_FOUND') {
      return res.status(404).json(buildError('USER_NOT_FOUND', '未找到指定用户'));
    }
    if (error.code === 'DELETE_FAILED') {
      return res.status(500).json(buildError('DELETE_FAILED', '删除用户失败'));
    }
    logger.error('[AdminController] Failed to delete user', { error: error.message });
    return res.status(500).json(buildError('INTERNAL_ERROR', '删除用户时发生未知错误'));
  }
};

const getCustomerDetail = async (req, res) => {
  try {
    const customerId = Number.parseInt(req.params.id, 10);
    const detail = await adminService.getCustomerDetail(customerId);
    if (!detail) {
      return res.status(404).json(buildError('CUSTOMER_NOT_FOUND', '未找到客户信息'));
    }
    res.json(detail);
  } catch (error) {
    logger.error('[AdminController] Failed to fetch customer detail', { error: error.message });
    res.status(500).json(buildError('INTERNAL_ERROR', '获取客户详情失败'));
  }
};

const updateCustomer = async (req, res) => {
  try {
    const customerId = Number.parseInt(req.params.id, 10);
    const normalizedPayload = {
      name: toNullableTrimmed(req.body.name),
      dateOfBirth: normalizeDateInput(req.body.dateOfBirth),
      gender: toNullableTrimmed(req.body.gender),
      phone: toNullableTrimmed(req.body.phone),
      email: toNullableTrimmed(req.body.email),
      height: req.body.height,
      weight: req.body.weight
    };

    if (
      req.body.dateOfBirth !== undefined &&
      req.body.dateOfBirth !== null &&
      typeof req.body.dateOfBirth === 'string' &&
      req.body.dateOfBirth.trim() !== '' &&
      normalizedPayload.dateOfBirth === null
    ) {
      return res.status(400).json(buildError('INVALID_INPUT', '出生日期格式不正确'));
    }

    const updated = await adminService.updateCustomer({
      actorId: toActorId(req),
      customerId,
      payload: normalizedPayload,
      ip: toIp(req),
      userAgent: toUserAgent(req)
    });

    res.json(buildSuccess('客户信息已更新', { customer: updated }));
  } catch (error) {
    if (error.code === 'CUSTOMER_NOT_FOUND') {
      return res.status(404).json(buildError('CUSTOMER_NOT_FOUND', '未找到客户信息'));
    }
    if (error.code === 'CUSTOMER_EXISTS') {
      return res.status(409).json(buildError('CUSTOMER_EXISTS', '该邮箱已存在于其它客户记录中'));
    }
    logger.error('[AdminController] Failed to update customer', { error: error.message });
    res.status(500).json(buildError('INTERNAL_ERROR', '更新客户信息失败'));
  }
};

const getDevices = async (req, res) => {
  try {
    const {
      page,
      pageSize,
      keyword,
      offlineAllowed,
      onlyOnline,
      onlineWindowSeconds
    } = req.query;
    const result = await adminService.listRegisteredDevices({
      page,
      pageSize,
      keyword,
      offlineAllowed,
      onlyOnline,
      onlineWindowSeconds
    });
    res.json(result);
  } catch (error) {
    logger.error('[AdminController] Failed to fetch devices', { error: error.message });
    res.status(500).json(buildError('INTERNAL_ERROR', '获取设备列表失败'));
  }
};

const getDeviceDetail = async (req, res) => {
  try {
    const { deviceId } = req.params;
    const detail = await adminService.getDeviceRegistryEntry(deviceId);
    res.json(detail);
  } catch (error) {
    if (error.code === 'DEVICE_NOT_FOUND') {
      return res.status(404).json(buildError('DEVICE_NOT_FOUND', '未找到目标设备'));
    }
    logger.error('[AdminController] Failed to fetch device detail', { error: error.message });
    res.status(500).json(buildError('INTERNAL_ERROR', '获取设备详情失败'));
  }
};

const updateDeviceOfflinePermission = async (req, res) => {
  try {
    const { deviceId } = req.params;
    const { offlineAllowed } = req.body;
    const notifyOnline = req.body.notifyOnline === true;
    const updated = await adminService.updateDeviceOfflinePermission({
      actorId: toActorId(req),
      deviceId,
      offlineAllowed,
      notifyOnline,
      ip: toIp(req),
      userAgent: toUserAgent(req)
    });
    res.json(
      buildSuccess('设备离线权限已更新', {
        device: updated
      })
    );
  } catch (error) {
    if (error.code === 'DEVICE_NOT_FOUND') {
      return res.status(404).json(buildError('DEVICE_NOT_FOUND', '未找到目标设备'));
    }
    logger.error('[AdminController] Failed to update device offline flag', { error: error.message });
    res.status(500).json(buildError('INTERNAL_ERROR', '更新设备离线权限失败'));
  }
};

const forceExitDeviceOffline = async (req, res) => {
  try {
    const { deviceId } = req.params;
    const countdownSec = Number.parseInt(req.body.countdownSec, 10);
    const result = await adminService.forceExitDeviceOffline({
      actorId: toActorId(req),
      deviceId,
      countdownSec,
      ip: toIp(req),
      userAgent: toUserAgent(req)
    });
    res.json(
      buildSuccess('已下发强制退出指令', {
        deviceId,
        countdownSec: result.countdownSec,
        device: result.device || null
      })
    );
  } catch (error) {
    if (error.code === 'DEVICE_NOT_FOUND') {
      return res.status(404).json(buildError('DEVICE_NOT_FOUND', '未找到目标设备'));
    }
    logger.error('[AdminController] Failed to force exit device', { error: error.message });
    res.status(500).json(buildError('INTERNAL_ERROR', '触发设备强制退出失败'));
  }
};

const getFeatureFlags = async (req, res) => {
  try {
    const snapshot = await adminService.getFeatureFlagsSnapshot();
    res.json(snapshot);
  } catch (error) {
    logger.error('[AdminController] Failed to fetch feature flags', { error: error.message });
    res.status(500).json(buildError('INTERNAL_ERROR', '获取功能开关状态失败'));
  }
};

const updateOfflineModeFlag = async (req, res) => {
  try {
    const { enabled } = req.body;
    const notifyOnline = req.body.notifyOnline === true;
    const updated = await adminService.updateOfflineModeFlag({
      actorId: toActorId(req),
      enabled,
      notifyOnline,
      ip: toIp(req),
      userAgent: toUserAgent(req)
    });
    res.json(
      buildSuccess('离线模式开关已更新', {
        featureFlag: updated,
        notified: notifyOnline
      })
    );
  } catch (error) {
    logger.error('[AdminController] Failed to update offline flag', { error: error.message });
    res.status(500).json(buildError('INTERNAL_ERROR', '更新离线模式开关失败'));
  }
};

const updateRegisterRolloutProfile = async (req, res) => {
  try {
    const { profile } = req.body;
    const result = await featureFlagsService.setRegisterRolloutProfile({
      profile,
      actorId: toActorId(req)
    });
    res.json(
      buildSuccess('注册链路档位已更新', {
        registerRolloutProfile: result
      })
    );
  } catch (error) {
    if (error.code === 'INVALID_PROFILE') {
      return res.status(400).json(buildError('INVALID_PROFILE', '无效的注册链路档位'));
    }
    logger.error('[AdminController] Failed to update register rollout profile', { error: error.message });
    res.status(500).json(buildError('INTERNAL_ERROR', '更新注册链路档位失败'));
  }
};

const forceExitOfflineMode = async (req, res) => {
  try {
    const countdownSec = Number.parseInt(req.body.countdownSec, 10);
    const result = await adminService.forceExitOfflineMode({
      actorId: toActorId(req),
      countdownSec,
      ip: toIp(req),
      userAgent: toUserAgent(req)
    });
    res.json(
      buildSuccess('已发送离线模式强制退出指令', {
        countdownSec: result.countdownSec
      })
    );
  } catch (error) {
    logger.error('[AdminController] Failed to broadcast force exit', { error: error.message });
    res.status(500).json(buildError('INTERNAL_ERROR', '强制关闭离线模式失败'));
  }
};

const setAdminService = nextService => {
  adminService = nextService;
};

module.exports = {
  getAllUsers,
  getAllCustomers,
  getUserDetail,
  updateUserRole,
  updateUserAccountType,
  updateUserPassword,
  deleteUser,
  getCustomerDetail,
  updateCustomer,
  getFeatureFlags,
  updateOfflineModeFlag,
  updateRegisterRolloutProfile,
  forceExitOfflineMode,
  setAdminService,
  getDevices,
  getDeviceDetail,
  updateDeviceOfflinePermission,
  forceExitDeviceOffline
};
