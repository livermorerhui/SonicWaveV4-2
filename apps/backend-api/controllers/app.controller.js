const logger = require('../logger');
const { dbPool } = require('../config/db');
const featureFlagsService = require('../services/featureFlags.service');
const deviceService = require('../services/device.service');

const resolveClientIp = req =>
  req.headers['x-forwarded-for']?.split(',')[0]?.trim() || req.connection?.remoteAddress || req.ip || null;

const recordAppUsage = async (req, res) => {
  try {
    const { launchTime, userId, deviceId, ipAddress, deviceModel, osVersion, appVersion } = req.body;

    // Log the data to console
    logger.info('App usage recorded:', { launchTime, userId, deviceId });

    const resolvedIp = ipAddress || resolveClientIp(req);
    const query = `
      INSERT INTO app_usage_logs (
        user_id,
        device_id,
        ip_address,
        device_model,
        os_version,
        app_version,
        launch_time
      ) VALUES (?, ?, ?, ?, ?, ?, ?)
    `;
    await dbPool.execute(query, [
      userId || 'guest',
      deviceId || null,
      resolvedIp,
      deviceModel || null,
      osVersion || null,
      appVersion || null,
      launchTime
    ]);

    await deviceService.upsertFromAppUsage({
      deviceId,
      userId: userId ? String(userId) : null,
      userEmail: null,
      userName: null,
      ipAddress: resolvedIp,
      deviceModel,
      osVersion,
      appVersion,
      metadata: null
    });

    res.status(200).json({ message: 'App usage recorded and saved successfully.' });
  } catch (error) {
    logger.error('Error recording app usage:', { error: error.message });
    res.status(500).json({ message: 'Internal server error.' });
  }
};

const getFeatureFlags = async (req, res) => {
  try {
    const deviceId = typeof req.query.deviceId === 'string' ? req.query.deviceId.trim() : null;
    const device = deviceId ? await deviceService.findDevice(deviceId) : null;
    const snapshot = await featureFlagsService.getFeatureFlagSnapshot();
    const offlineAllowed = snapshot.offlineMode.enabled && (device ? device.offlineAllowed : true);
    res.json({
      offlineModeEnabled: offlineAllowed,
      updatedAt: snapshot.offlineMode.updatedAt,
      deviceOfflineAllowed: device ? device.offlineAllowed : true,
      deviceRegistered: Boolean(device),
      deviceId: device?.deviceId || deviceId || null
    });
  } catch (error) {
    logger.error('Error fetching feature flags snapshot:', { error: error.message });
    res.status(500).json({ message: 'Internal server error.' });
  }
};

module.exports = {
  recordAppUsage,
  getFeatureFlags
};
