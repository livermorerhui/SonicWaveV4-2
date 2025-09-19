// onlineStatusManager.js
const logger = require('./logger');
const { dbPool } = require('./config/db');

const onlineUsers = new Map(); // 存储 { userId: { timestamp, sessionId } }
const TIMEOUT_MS = 45 * 1000; // 45秒
const BATCH_UPDATE_INTERVAL_MS = 5 * 60 * 1000; // 5分钟

function updateUser(userId, sessionId) {
  onlineUsers.set(userId, { timestamp: Date.now(), sessionId });
  logger.info(`[Heartbeat] Received from user: ${userId}, session: ${sessionId}`); // ADDED LOG
}

function startManagerIntervals() {
  logger.info('✅ Online user status manager starting intervals...');

  // 1. 清理超时用户的定时器
  setInterval(async () => {
    const now = Date.now();
    const timedOutSessionIds = [];

    for (const [userId, data] of onlineUsers.entries()) {
      if (now - data.timestamp > TIMEOUT_MS) {
        timedOutSessionIds.push(data.sessionId);
        onlineUsers.delete(userId); // 立即从内存中移除
      }
    }

    if (timedOutSessionIds.length > 0) {
      logger.info(`发现 ${timedOutSessionIds.length} 个超时会话，正在数据库中标为非活跃。`);
      try {
        // [最佳实践] 在单次查询中批量更新所有超时的会话。
        const placeholders = timedOutSessionIds.map(() => '?').join(',');
        const sql = `UPDATE user_sessions SET logout_time = NOW(), is_active = FALSE WHERE id IN (${placeholders}) AND logout_time IS NULL`;
        await dbPool.execute(sql, timedOutSessionIds);
      } catch (error) {
        logger.error('批量清理超时会话时出错:', { error: error.message });
      }
    }
  }, TIMEOUT_MS / 2); // 每15秒检查一次超时。

  // 2. 批量更新活跃用户心跳时间的定时器
  setInterval(async () => {
    if (onlineUsers.size === 0) {
      return;
    }

    const activeSessionIds = Array.from(onlineUsers.values()).map(data => data.sessionId);

    if (activeSessionIds.length > 0) {
      try {
        const placeholders = activeSessionIds.map(() => '?').join(',');
        const sql = `UPDATE user_sessions SET last_heartbeat_time = NOW() WHERE id IN (${placeholders})`;
        await dbPool.execute(sql, activeSessionIds);
        logger.info(`为 ${activeSessionIds.length} 个活跃会话批量更新了心跳时间。`);
      } catch (error) {
        logger.error('批量更新心跳时间时出错:', { error: error.message });
      }
    }
  }, BATCH_UPDATE_INTERVAL_MS);
}

module.exports = {
  updateUser,
  startManagerIntervals, // 确保在 index.js 中调用这个新的启动函数
  onlineUsers
};