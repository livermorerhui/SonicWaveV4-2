const express = require('express');
const router = express.Router();
const musicController = require('../controllers/music.controller.js');
const authenticateToken = require('../middleware/auth.js');
const upload = require('../middleware/upload.js');

// 获取音乐列表 (受保护)
router.get('/', authenticateToken, musicController.getMusicList);

// 上传音乐文件 (受保护)
// upload.single('musicFile') 是处理文件上传的中间件
router.post('/upload', authenticateToken, upload.single('musicFile'), musicController.uploadMusic);

module.exports = router;
