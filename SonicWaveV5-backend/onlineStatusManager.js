// onlineStatusManager.js
const logger = require('./logger');

const onlineUsers = new Map();
const TIMEOUT_MS = 30 * 1000; // 30秒超时

/**
 * 更新用户的心跳时间戳
 * @param {string} userId 
 */
function updateUser(userId) {
  onlineUsers.set(userId, Date.now());
  // logger.info(`Heartbeat received from user: ${userId}`); // 这条日志会很频繁，默认注释掉
}

/**
 * 启动一个定时器，周期性地清理超时的用户
 */
function startCleanupInterval() {
  setInterval(() => {
    const now = Date.now();
    let cleanedCount = 0;
    for (const [userId, lastSeen] of onlineUsers.entries()) {
      if (now - lastSeen > TIMEOUT_MS) {
        onlineUsers.delete(userId);
        logger.info(`User ${userId} timed out and was marked as offline.`);
        cleanedCount++;
      }
    }
    if (cleanedCount > 0) {
        logger.info(`Cleaned up ${cleanedCount} inactive user(s).`);
    }
  }, TIMEOUT_MS / 2); // 每15秒检查一次

  logger.info('✅ Online user status manager started.');
}

module.exports = {
  updateUser,
  startCleanupInterval,
  onlineUsers // 也可以导出以供其他模块查询
};