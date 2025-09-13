const { dbPool } = require('../config/db');
const logger = require('../logger');

// 获取音乐列表
const getMusicList = async (req, res) => {
  try {
    const sql = 'SELECT id, title, artist, file_key, created_at FROM music_tracks';
    const [tracks] = await dbPool.query(sql);

    res.json({
      message: 'Successfully fetched music list.',
      tracks: tracks
    });

  } catch (error) {
    logger.error('Error fetching music list:', { error: error.message });
    res.status(500).json({ message: 'Internal server error.' });
  }
};

// 上传音乐文件
const uploadMusic = async (req, res) => {
  try {
    const { userId } = req.user;
    const { title, artist } = req.body;
    const file = req.file;

    if (!file) {
      return res.status(400).json({ message: 'No music file uploaded.' });
    }
    if (!title) {
      return res.status(400).json({ message: 'Music title is required.' });
    }

    // file.path 是由 multer.diskStorage 生成的临时文件路径
    const file_key = file.path;

    logger.info(`File received and stored at: ${file_key}, size: ${file.size}`);

    const sql = 'INSERT INTO music_tracks (title, artist, file_key, uploader_id) VALUES (?, ?, ?, ?)';
    const [result] = await dbPool.execute(sql, [title, artist || 'Unknown Artist', file_key, userId]);

    res.status(201).json({
      message: 'File uploaded and metadata saved successfully!',
      trackId: result.insertId,
      file_key: file_key
    });

  } catch (error) {
    logger.error('Error during file upload:', { error: error.message });
    res.status(500).json({ message: 'Internal server error.' });
  }
};

module.exports = {
  getMusicList,
  uploadMusic
};
