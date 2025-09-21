const { dbPool } = require('../config/db');
const logger = require('../logger');

// 开始一个新操作
const startOperation = async (req, res) => {
  try {
    logger.info('Received request body for startOperation:', req.body);

    const {
      userId, userName = null, email = null, customer = null, frequency, intensity, operationTime
    } = req.body;

    if (!userId || operationTime === undefined) {
      return res.status(400).json({ message: 'userId and operationTime are required.' });
    }

    const startTime = new Date();

    const sql = `INSERT INTO user_operations 
      (user_id, user_name, email, customer, frequency, intensity, operation_time, start_time)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)`;

    const [result] = await dbPool.execute(sql, [
      userId, userName, email, customer, frequency, intensity, operationTime, startTime
    ]);

    res.status(201).json({
      message: 'Operation started successfully.',
      operationId: result.insertId
    });

  } catch (error) {
    logger.error('Error starting operation:', { error: error.message });
    res.status(500).json({ message: 'Internal server error.' });
  }
};

// 停止一个操作
const stopOperation = async (req, res) => {
  try {
    const { id } = req.params;
    // --- MODIFIED: 不再从 req.body 获取时间，而是直接在服务器生成 ---
    const stopTime = new Date();

    const sql = 'UPDATE user_operations SET stop_time = ? WHERE id = ?';
    // --- MODIFIED: 使用服务器生成的 stopTime ---
    const [result] = await dbPool.execute(sql, [stopTime, id]);

    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Operation not found.' });
    }

    res.status(200).json({ message: 'Operation stopped successfully.' });

  } catch (error) {
    logger.error('Error stopping operation:', { error: error.message });
    res.status(500).json({ message: 'Internal server error.' });
  }
};

module.exports = {
  startOperation,
  stopOperation
};