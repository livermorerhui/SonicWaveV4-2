const deviceRepository = require('../repositories/deviceRegistry.repository');
const auditService = require('./audit.service');
const offlineControlChannel = require('../realtime/offlineControlChannel');
const logger = require('../logger');

const DeviceServiceError = (code, message) => {
  const err = new Error(message);
  err.code = code;
  return err;
};

const sanitizeDeviceId = value => {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed.slice(0, 191) : null;
};

async function upsertFromAppUsage({
  deviceId,
  userId,
  userEmail,
  userName,
  ipAddress,
  deviceModel,
  osVersion,
  appVersion
}) {
  const normalizedId = sanitizeDeviceId(deviceId);
  if (!normalizedId) {
    return null;
  }
  return deviceRepository.upsertDeviceRecord({
    deviceId: normalizedId,
    userId,
    userEmail,
    userName,
    ipAddress,
    deviceModel,
    osVersion,
    appVersion,
    metadata: null
  });
}

async function touchDevice({
  deviceId,
  userId,
  userEmail,
  userName,
  ipAddress,
  deviceModel,
  osVersion,
  appVersion
}) {
  const normalizedId = sanitizeDeviceId(deviceId);
  if (!normalizedId) return null;
  return deviceRepository.upsertDeviceRecord({
    deviceId: normalizedId,
    userId,
    userEmail,
    userName,
    ipAddress,
    deviceModel,
    osVersion,
    appVersion,
    metadata: null
  });
}

async function findDevice(deviceId) {
  const normalizedId = sanitizeDeviceId(deviceId);
  if (!normalizedId) return null;
  return deviceRepository.findDeviceById(normalizedId);
}

async function listDevices(params) {
  return deviceRepository.listDevices(params);
}

async function setAllDevicesOfflinePermission(offlineAllowed) {
  await deviceRepository.setAllDevicesOfflineAllowed(offlineAllowed);
}

async function setOfflinePermission({
  actorId,
  deviceId,
  offlineAllowed,
  notifyOnline = false,
  ip,
  userAgent
}) {
  const device = await findDevice(deviceId);
  if (!device) {
    throw DeviceServiceError('DEVICE_NOT_FOUND', '目标设备不存在');
  }
  const normalizedId = device.deviceId;
  if (device.offlineAllowed !== offlineAllowed) {
    await deviceRepository.setDeviceOfflineAllowed(normalizedId, offlineAllowed);
  }
  const updated = await findDevice(normalizedId);
  if (notifyOnline) {
    offlineControlChannel.broadcastOfflineModeUpdate({
      enabled: offlineAllowed,
      updatedBy: actorId,
      targetDeviceIds: [normalizedId]
    });
  }
  await auditService.logAction({
    actorId,
    action: 'DEVICE_OFFLINE_FLAG_UPDATED',
    targetType: 'device',
    targetId: normalizedId,
    metadata: {
      offlineAllowed,
      notifyOnline
    },
    ip,
    userAgent
  });
  return updated;
}

async function forceExitOffline({
  actorId,
  deviceId,
  countdownSec,
  ip,
  userAgent
}) {
  const device = await findDevice(deviceId);
  if (!device) {
    throw DeviceServiceError('DEVICE_NOT_FOUND', '目标设备不存在');
  }
  const normalizedId = device.deviceId;
  offlineControlChannel.broadcastForceExit({
    countdownSec,
    updatedBy: actorId,
    targetDeviceIds: [normalizedId]
  });
  let updatedDevice = null;
  try {
    await deviceRepository.setDeviceOfflineAllowed(normalizedId, false);
    updatedDevice = await findDevice(normalizedId);
  } catch (error) {
    logger.warn('[DeviceService] Failed to update offline flag after force exit', {
      deviceId: normalizedId,
      error: error.message
    });
  }
  await auditService.logAction({
    actorId,
    action: 'DEVICE_FORCE_EXIT_TRIGGERED',
    targetType: 'device',
    targetId: normalizedId,
    metadata: {
      countdownSec
    },
    ip,
    userAgent
  });
  return { countdownSec, device: updatedDevice };
}

module.exports = {
  DeviceServiceError,
  upsertFromAppUsage,
  touchDevice,
  findDevice,
  listDevices,
  setAllDevicesOfflinePermission,
  setOfflinePermission,
  forceExitOffline
};
