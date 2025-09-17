const logger = require('../logger');
const { dbPool } = require('../config/db');

const recordAppUsage = async (req, res) => {
  try {
    const { launchTime, userId } = req.body; // userId can be undefined

    // Log the data to console
    logger.info('App usage recorded:', { launchTime, userId });

    // Save the data to the database. Use 'guest' if userId is undefined.
    const query = 'INSERT INTO app_usage_logs (user_id, launch_time) VALUES (?, ?)';
    await dbPool.execute(query, [userId || 'guest', launchTime]);

    res.status(200).json({ message: 'App usage recorded and saved successfully.' });
  } catch (error) {
    logger.error('Error recording app usage:', { error: error.message });
    res.status(500).json({ message: 'Internal server error.' });
  }
};

module.exports = {
  recordAppUsage
};