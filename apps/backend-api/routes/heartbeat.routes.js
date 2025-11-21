// heartbeat.routes.js
const express = require('express');
const router = express.Router();
const heartbeatController = require('../controllers/heartbeat.controller');
const authMiddleware = require('../middleware/auth');

// [修改] 使用 router.use() 将认证中间件应用于此路由文件中的所有路由
router.use(authMiddleware);

// 所有心跳请求都必须经过认证
router.post('/', heartbeatController.handleHeartbeat);

module.exports = router;