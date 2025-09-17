const express = require('express');
const router = express.Router();
const operationsController = require('../controllers/operations.controller');
const authMiddleware = require('../middleware/auth'); // 假设有认证中间件

// 路由将受认证中间件保护
router.use(authMiddleware);

// 开始一个新操作
router.post('/start', operationsController.startOperation);

// 停止一个操作
router.put('/stop/:id', operationsController.stopOperation);

module.exports = router;