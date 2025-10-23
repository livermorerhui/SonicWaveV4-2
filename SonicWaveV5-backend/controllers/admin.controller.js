const crypto = require('crypto');
let adminService = require('../services/admin.service');
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

const setAdminService = nextService => {
  adminService = nextService;
};

module.exports = {
  getAllUsers,
  getAllCustomers,
  updateUserRole,
  updateUserAccountType,
  deleteUser,
  setAdminService
};
