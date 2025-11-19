const { dbPool } = require('../config/db');
const logger = require('../logger');

const STOP_REASONS = new Set(['manual', 'logout', 'countdown_complete', 'hardware_error', 'unknown']);

const validateStopReason = (reason) => {
  if (!reason) {
    return 'unknown';
  }
  const normalized = reason.toLowerCase();
  if (STOP_REASONS.has(normalized)) {
    return normalized;
  }
  return null;
};

// 开始一个新操作
const startOperation = async (req, res) => {
  try {
    logger.info('Received request body for startOperation:', req.body);

    const {
      userId,
      userName = null,
      user_email = null,
      customer_id = null,
      customer_name = null,
      frequency,
      intensity,
      operationTime
    } = req.body;

    if (!userId || operationTime === undefined) {
      return res.status(400).json({ message: 'userId and operationTime are required.' });
    }

    const startTime = new Date();

    const sql = `INSERT INTO user_operations 
      (user_id, user_name, user_email, customer_id, customer_name, frequency, intensity, operation_time, start_time)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`;

    const [result] = await dbPool.execute(sql, [
      userId,
      userName,
      user_email,
      customer_id,
      customer_name,
      frequency,
      intensity,
      operationTime,
      startTime
    ]);

    res.status(201).json({
      message: 'Operation started successfully.',
      operationId: result.insertId
    });
  } catch (error) {
    logger.error('Error starting operation:', { error: error.message });
    res.status(500).json({ error: 'Internal server error.' });
  }
};

// 记录操作事件
const logOperationEvent = async (req, res) => {
  try {
    const { id } = req.params;
    const {
      eventType,
      frequency = null,
      intensity = null,
      timeRemaining = null,
      extraDetail = null
    } = req.body;

    if (!eventType) {
      return res.status(400).json({ message: 'eventType is required.' });
    }

    const insertSql = `INSERT INTO user_operation_events
      (operation_id, event_type, frequency, intensity, time_remaining, extra_detail)
      VALUES (?, ?, ?, ?, ?, ?)`;

    await dbPool.execute(insertSql, [
      id,
      eventType,
      frequency,
      intensity,
      timeRemaining,
      extraDetail
    ]);

    res.status(201).json({ message: 'Event recorded successfully.' });
  } catch (error) {
    logger.error('Error recording operation event:', { error: error.message });
    res.status(500).json({ message: 'Internal server error.' });
  }
};

// 停止一个操作
const stopOperation = async (req, res) => {
  try {
    const { id } = req.params;
    const { reason = 'unknown', detail = null } = req.body || {};

    const normalizedReason = validateStopReason(reason);
    if (!normalizedReason) {
      return res.status(400).json({ message: 'Invalid stop reason.' });
    }

    const stopTime = new Date();

    const sql = 'UPDATE user_operations SET stop_time = ?, stop_reason = ?, stop_detail = ? WHERE id = ?';
    const [result] = await dbPool.execute(sql, [stopTime, normalizedReason, detail, id]);

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
  logOperationEvent,
  stopOperation
};
