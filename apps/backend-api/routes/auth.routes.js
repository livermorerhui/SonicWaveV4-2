// auth.routes.js
const express = require('express');
const router = express.Router();
const authController = require('../controllers/auth.controller');
const authMiddleware = require('../middleware/auth');

// 记录登录事件 (在用户成功登录后调用)
router.post('/login-event', authMiddleware, authController.recordLoginEvent);

// 记录登出事件 (在用户主动登出时调用)
router.put('/logout-event', authMiddleware, authController.recordLogoutEvent);

module.exports = router;