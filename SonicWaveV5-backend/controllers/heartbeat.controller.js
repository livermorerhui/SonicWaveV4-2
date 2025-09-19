// heartbeat.controller.js
const { updateUser } = require('../onlineStatusManager');
const logger = require('../logger'); // 引入 logger

const handleHeartbeat = (req, res) => {
  // [新增日志] 确认心跳接口被调用
  logger.info(`[Heartbeat Controller] Received request for userId: ${req.user.userId}`);

  const userId = req.user.userId;
  const { sessionId } = req.body;

  if (!sessionId) {
    // [新增日志] 增加对缺少 sessionId 的情况的日志记录
    logger.warn(`[Heartbeat Controller] Request from userId: ${userId} is missing sessionId.`);
    return res.status(400).json({ message: 'Session ID is required for heartbeat' });
  }

  // 为了高性能，只更新内存中的map。
  updateUser(userId, sessionId);

  res.status(200).json({ message: 'Heartbeat received' });
};

module.exports = {
  handleHeartbeat
};