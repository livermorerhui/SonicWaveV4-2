const express = require('express');
const router = express.Router();
const userController = require('../controllers/users.controller.js');
const authenticateToken = require('../middleware/auth.js');

// 公开路由
router.post('/register', userController.registerUser);
router.post('/login', userController.loginUser);

// 受保护的路由
router.get('/me', authenticateToken, userController.getUserProfile);
router.put('/me', authenticateToken, userController.updateUserProfile);

module.exports = router;
