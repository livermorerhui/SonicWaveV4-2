const logger = require('../logger');

const recordAppUsage = async (req, res) => {
  try {
    const { launchTime, userId } = req.body;

    // For now, just log the data. In a real application, you would save this to a database.
    logger.info('App usage recorded:', { launchTime, userId });

    res.status(200).json({ message: 'App usage recorded successfully.' });
  } catch (error) {
    logger.error('Error recording app usage:', { error: error.message });
    res.status(500).json({ message: 'Internal server error.' });
  }
};

module.exports = {
  recordAppUsage
};