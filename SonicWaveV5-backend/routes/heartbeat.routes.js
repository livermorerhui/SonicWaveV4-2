// heartbeat.routes.js
const express = require('express');
const router = express.Router();
const heartbeatController = require('../controllers/heartbeat.controller');
const authMiddleware = require('../middleware/auth');

// 所有心跳请求都必须经过认证
router.post('/', authMiddleware, heartbeatController.handleHeartbeat);

module.exports = router;