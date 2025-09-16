const { dbPool } = require('../config/db');
const logger = require('../logger');

// Create client logs
const createClientLogs = async (req, res) => {
  try {
    let logs = req.body;

    // Handle both single object and array of objects
    if (!Array.isArray(logs)) {
      logs = [logs]; // If it's a single object, wrap it in an array
    }

    if (logs.length === 0) {
      return res.status(400).json({ message: 'Log data cannot be empty.' });
    }

    const sql = 'INSERT INTO client_logs (log_level, request_url, request_method, response_code, is_successful, duration_ms, error_message, device_info) VALUES ?';
    
    const values = logs.map(log => {
      // If there's a launchTime, we assume the whole log object is the device_info.
      // Otherwise, we use the existing device_info field.
      const deviceInfo = log.launchTime ? log : log.device_info;

      return [
        log.log_level || 'INFO',
        log.request_url || '',
        log.request_method || '',
        log.response_code || null,
        log.is_successful !== undefined ? log.is_successful : true,
        log.duration_ms || 0,
        log.error_message || null,
        JSON.stringify(deviceInfo)
      ];
    });

    logger.debug('Preparing to insert into DB:', { sql, values });
    await dbPool.query(sql, [values]);

    res.status(201).json({ message: 'Logs created successfully!' });

  } catch (error) {
    logger.error('Error during client log creation:', { error: error.message, requestBody: req.body });
    res.status(500).json({ message: 'Internal server error.' });
  }
};

module.exports = {
    createClientLogs,
};
