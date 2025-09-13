const { dbPool } = require('../config/db');
const logger = require('../logger');

// Create client logs
const createClientLogs = async (req, res) => {
  try {
    const logs = req.body;

    if (!Array.isArray(logs) || logs.length === 0) {
      return res.status(400).json({ message: 'Log data must be a non-empty array.' });
    }

    const sql = 'INSERT INTO client_logs (log_level, request_url, request_method, response_code, is_successful, duration_ms, error_message, device_info) VALUES ?';
    
    const values = logs.map(log => [
      log.log_level || 'INFO',
      log.request_url,
      log.request_method,
      log.response_code,
      log.is_successful,
      log.duration_ms,
      log.error_message,
      JSON.stringify(log.device_info)
    ]);

    await dbPool.query(sql, [values]);

    res.status(201).json({ message: 'Logs created successfully!' });

  } catch (error) {
    logger.error('Error during client log creation:', { error: error.message });
    res.status(500).json({ message: 'Internal server error.' });
  }
};

module.exports = {
    createClientLogs,
};
