const logger = require('../logger');
const deviceService = require('../services/device.service');

const recordDeviceHeartbeat = async (req, res) => {
  try {
    const { deviceId, ipAddress, deviceModel, osVersion, appVersion } = req.body;
    logger.info('[DeviceController] Incoming heartbeat', {
      deviceId,
      ipAddress: ipAddress || req.connection?.remoteAddress || req.ip || null
    });
    await deviceService.touchDevice({
      deviceId,
      userId: null,
      userEmail: null,
      userName: null,
      ipAddress: ipAddress || req.connection?.remoteAddress || req.ip || null,
      deviceModel,
      osVersion,
      appVersion
    });
    res.status(200).json({ message: 'Device heartbeat recorded' });
  } catch (error) {
    logger.error('[DeviceController] Failed to record device heartbeat', { error: error.message });
    res.status(500).json({ message: 'Internal server error.' });
  }
};

module.exports = {
  recordDeviceHeartbeat
};
