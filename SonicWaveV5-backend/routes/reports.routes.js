const express = require('express');
const router = express.Router();
const createReportController = require('../controllers/reports.controller.js');
const authenticateToken = require('../middleware/auth.js');

// 导出一个函数，用于创建和配置路由
const createReportRouter = (wss, wsClients) => {
  // 从控制器工厂函数中获取控制器实例
  const reportController = createReportController(wss, wsClients);

  // 定义受保护的路由
  router.post('/', authenticateToken, reportController.generateReport);

  return router;
};

module.exports = createReportRouter;
