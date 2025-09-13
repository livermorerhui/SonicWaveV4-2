const logger = require('../logger');

// 这个模块导出一个函数，该函数返回一个控制器对象
// 这样做是为了将 WebSocket 服务器实例 (wss) 注入到控制器中
const createReportController = (wss, wsClients) => {
  const generateReport = (req, res) => {
    const { userId } = req.user;
    logger.info(`Received report generation request from user ${userId}`);

    // 立即返回响应，表示请求已被接受
    res.status(202).json({ message: 'Report generation started. You will be notified upon completion.' });

    // 模拟耗时的异步任务
    setTimeout(() => {
      const reportContent = `This is the report for user ${userId} generated at ${new Date().toISOString()}`;
      logger.info(`Report for user ${userId} has been generated.`);

      // 从映射中查找该用户的 WebSocket 连接
      const userSocket = wsClients.get(userId);

      // 如果找到了并且连接是打开的，就发送消息
      if (userSocket && userSocket.readyState === require('ws').OPEN) {
        userSocket.send(JSON.stringify({
          type: 'REPORT_READY',
          message: 'Your report is ready!',
          report: reportContent,
          userId: userId
        }));
        logger.info(`Sent report notification to user ${userId}`);
      } else {
        logger.warn(`Could not send report notification to user ${userId}. User not connected.`);
      }
    }, 5000); // 模拟5秒处理时间
  };

  return {
    generateReport
  };
};

module.exports = createReportController;
