// heartbeat.controller.js
const { updateUser } = require('../onlineStatusManager');

const handleHeartbeat = (req, res) => {
  // authMiddleware 确保了 req.user.userId 存在
  const userId = req.user.userId;
  
  // 更新用户最后在线时间
  updateUser(userId);
  
  // 返回成功响应
  res.status(200).json({ message: 'Heartbeat received' });
};

module.exports = {
  handleHeartbeat
};