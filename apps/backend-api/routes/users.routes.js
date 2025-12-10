const express = require('express');
const router = express.Router();
const userController = require('../controllers/users.controller.js');
const authenticateToken = require('../middleware/auth.js');

// 公开路由
router.post('/register', userController.registerUser);
router.post('/login', userController.loginUser);
// 忘记密码：发送重置验证码
router.post('/password/reset/send_code', userController.sendResetPasswordCode);
// 忘记密码：提交验证码与新密码
router.post('/password/reset/submit', userController.resetPassword);

// 受保护的路由
router.get('/me', authenticateToken, userController.getUserProfile);
router.put('/me', authenticateToken, userController.updateUserProfile);

// 当前登录用户修改密码
router.patch('/me/password', authenticateToken, userController.changePassword);

module.exports = router;
