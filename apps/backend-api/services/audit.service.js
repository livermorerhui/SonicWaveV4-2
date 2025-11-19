const { dbPool } = require('../config/db');
const logger = require('../logger');

/**
 * 写入审计日志
 * @param {Object} payload
 * @param {number} payload.actorId - 操作者 ID，可为空
 * @param {string} payload.action - 动作名称
 * @param {string} payload.targetType - 目标类型
 * @param {string|number|null} payload.targetId - 目标主键
 * @param {Object|null} payload.metadata - 附加数据
 * @param {string|null} payload.ip - 请求 IP
 * @param {string|null} payload.userAgent - UA
 */
async function logAction({
  actorId = null,
  action,
  targetType,
  targetId = null,
  metadata = null,
  ip = null,
  userAgent = null
}) {
  try {
    const sql = `
      INSERT INTO audit_logs
        (actor_id, action, target_type, target_id, metadata, ip, user_agent)
      VALUES (?, ?, ?, ?, ?, ?, ?)
    `;

    const serializedMetadata = metadata ? JSON.stringify(metadata) : null;

    await dbPool.execute(sql, [
      actorId,
      action,
      targetType,
      targetId !== undefined ? String(targetId) : null,
      serializedMetadata,
      ip,
      userAgent
    ]);
  } catch (error) {
    logger.error('[AuditService] Failed to persist audit log', {
      error: error.message,
      action,
      targetType,
      targetId
    });
  }
}

module.exports = {
  logAction
};
