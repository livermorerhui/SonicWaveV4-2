const WebSocket = require('ws');
const logger = require('../logger');

const controlClients = new Set();
let userSocketMap = null;

function registerUserSocketMap(map) {
  userSocketMap = map;
}

function registerControlClient(ws) {
  controlClients.add(ws);
  logger.info('[ControlChannel] Control client connected. total=%d', controlClients.size);
  ws.on('close', () => {
    controlClients.delete(ws);
    logger.info('[ControlChannel] Control client disconnected. total=%d', controlClients.size);
  });
  ws.on('error', error => {
    controlClients.delete(ws);
    logger.warn('[ControlChannel] Control client error: %s', error?.message);
  });
}

function broadcast(message) {
  const serialized = JSON.stringify(message);
  let delivered = 0;
  if (userSocketMap) {
    for (const [, socket] of userSocketMap.entries()) {
      if (socket?.readyState === WebSocket.OPEN) {
        socket.send(serialized);
        delivered += 1;
      }
    }
  }
  for (const socket of controlClients) {
    if (socket?.readyState === WebSocket.OPEN) {
      socket.send(serialized);
      delivered += 1;
    }
  }
  return delivered;
}

function broadcastOfflineModeUpdate({ enabled, updatedBy, targetDeviceIds = null }) {
  const payload = {
    enabled,
    updatedBy: updatedBy ?? null,
    timestamp: Date.now(),
    targetDeviceIds: Array.isArray(targetDeviceIds) && targetDeviceIds.length > 0 ? targetDeviceIds : null
  };
  const message = {
    type: 'offline_mode',
    action: enabled ? 'enable' : 'disable',
    payload
  };
  const total = broadcast(message);
  logger.info('[ControlChannel] Broadcast offline %s to %d clients.', payload.enabled ? 'enable' : 'disable', total);
  return total;
}

function broadcastForceExit({ countdownSec, updatedBy, targetDeviceIds = null }) {
  const payload = {
    countdownSec,
    updatedBy: updatedBy ?? null,
    timestamp: Date.now(),
    targetDeviceIds: Array.isArray(targetDeviceIds) && targetDeviceIds.length > 0 ? targetDeviceIds : null
  };
  const message = {
    type: 'offline_mode',
    action: 'force_exit_offline_mode',
    payload
  };
  const total = broadcast(message);
  logger.warn('[ControlChannel] Broadcast offline force exit (%ds) to %d clients.', countdownSec, total);
  return total;
}

module.exports = {
  registerUserSocketMap,
  registerControlClient,
  broadcastOfflineModeUpdate,
  broadcastForceExit
};
