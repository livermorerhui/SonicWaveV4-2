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

// 音乐分类管理 (受保护)
router.get('/categories', authenticateToken, musicController.getMusicCategories);
router.post('/categories', authenticateToken, musicController.createMusicCategory);
router.patch('/categories/:id', authenticateToken, musicController.updateMusicCategory);
router.delete('/categories/:id', authenticateToken, musicController.deleteMusicCategory);

// 单曲分类更新 (受保护)
router.patch('/:id/category', authenticateToken, musicController.updateMusicTrackCategory);

// 单曲元数据更新 (受保护)
router.patch('/:id', authenticateToken, musicController.updateMusicTrackMetadata);

// 删除曲目 (受保护)
router.delete('/:id', authenticateToken, musicController.deleteMusicTrack);

module.exports = router;
