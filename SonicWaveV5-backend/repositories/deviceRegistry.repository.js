const { dbPool } = require('../config/db');
const logger = require('../logger');

const DEFAULT_PAGE = 1;
const DEFAULT_PAGE_SIZE = 20;
const MAX_PAGE_SIZE = 200;

const parsePagination = ({ page, pageSize }) => {
  const parsedPage = Number.parseInt(page, 10);
  const parsedPageSize = Number.parseInt(pageSize, 10);
  const safePage = Number.isFinite(parsedPage) && parsedPage > 0 ? parsedPage : DEFAULT_PAGE;
  const safePageSize = Number.isFinite(parsedPageSize) && parsedPageSize > 0 ? parsedPageSize : DEFAULT_PAGE_SIZE;
  const finalPageSize = Math.min(MAX_PAGE_SIZE, safePageSize);
  return {
    page: safePage,
    pageSize: finalPageSize,
    offset: (safePage - 1) * finalPageSize
  };
};

const safeParseJson = value => {
  if (value === null || value === undefined) {
    return null;
  }
  if (typeof value === 'object') {
    return value;
  }
  if (typeof value !== 'string' || value.trim() === '' || value === '[object Object]') {
    return null;
  }
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
};

const toIsoString = value => (value ? new Date(value).toISOString() : null);

const mapDeviceRow = row => ({
  deviceId: row.device_id,
  firstSeenAt: toIsoString(row.first_seen_at),
  lastSeenAt: toIsoString(row.last_seen_at),
  lastUserId: row.last_user_id,
  lastUserEmail: row.last_user_email || null,
  lastUserName: row.last_user_name || null,
  lastIp: row.last_ip,
  deviceModel: row.device_model,
  osVersion: row.os_version,
  appVersion: row.app_version,
  offlineAllowed: Boolean(row.offline_allowed),
  metadata: safeParseJson(row.metadata),
  updatedAt: toIsoString(row.updated_at)
});

const sanitizeMetadata = metadata => {
  if (!metadata) return null;
  try {
    return JSON.stringify(metadata);
  } catch {
    return null;
  }
};

async function upsertDeviceRecord({
  deviceId,
  userId,
  userEmail,
  userName,
  ipAddress,
  deviceModel,
  osVersion,
  appVersion,
  metadata
}) {
  if (!deviceId) {
    throw new Error('deviceId is required to upsert device');
  }
  const normalizedUserId =
    typeof userId === 'number'
      ? String(userId)
      : typeof userId === 'string' && userId.trim() !== ''
        ? userId.trim()
        : null;
  const payload = [
    deviceId,
    normalizedUserId,
    userEmail || null,
    userName || null,
    ipAddress || null,
    deviceModel || null,
    osVersion || null,
    appVersion || null,
    sanitizeMetadata(metadata)
  ];
  const sql = `
    INSERT INTO device_registry (
      device_id,
      first_seen_at,
      last_seen_at,
      last_user_id,
      last_user_email,
      last_user_name,
      last_ip,
      device_model,
      os_version,
      app_version,
      offline_allowed,
      metadata,
      updated_at
    ) VALUES (?, NOW(), NOW(), ?, ?, ?, ?, ?, ?, ?, TRUE, ?, NOW())
    ON DUPLICATE KEY UPDATE
      last_seen_at = VALUES(last_seen_at),
      last_user_id = VALUES(last_user_id),
      last_user_email = VALUES(last_user_email),
      last_user_name = VALUES(last_user_name),
      last_ip = VALUES(last_ip),
      device_model = VALUES(device_model),
      os_version = VALUES(os_version),
      app_version = VALUES(app_version),
      metadata = COALESCE(VALUES(metadata), metadata),
      updated_at = VALUES(updated_at)
  `;
  await dbPool.execute(sql, payload);
  return findDeviceById(deviceId);
}

async function findDeviceById(deviceId) {
  const [rows] = await dbPool.execute(
    `
      SELECT device_id, first_seen_at, last_seen_at, last_user_id, last_ip, device_model,
             os_version, app_version, offline_allowed, metadata, updated_at
      FROM device_registry
      WHERE device_id = ?
      LIMIT 1
    `,
    [deviceId]
  );
  if (!rows.length) {
    return null;
  }
  return mapDeviceRow(rows[0]);
}

async function setDeviceOfflineAllowed(deviceId, offlineAllowed) {
  const [result] = await dbPool.execute(
    `
      UPDATE device_registry
      SET offline_allowed = ?, updated_at = NOW()
      WHERE device_id = ?
    `,
    [offlineAllowed ? 1 : 0, deviceId]
  );
  return result.affectedRows;
}

const normalizeBoolean = value => {
  if (value === undefined || value === null) return undefined;
  if (typeof value === 'boolean') return value;
  if (typeof value === 'string') {
    const lowered = value.toLowerCase();
    if (lowered === 'true') return true;
    if (lowered === 'false') return false;
  }
  if (typeof value === 'number') {
    if (value === 1) return true;
    if (value === 0) return false;
  }
  return undefined;
};

const normalizeNumber = (value, fallback) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
};

async function listDevices({
  page,
  pageSize,
  keyword,
  offlineAllowed,
  onlyOnline = false,
  onlineWindowSeconds = 90
}) {
  const { page: safePage, pageSize: safePageSize, offset } = parsePagination({ page, pageSize });
  const filters = [];
  const params = [];

  if (keyword) {
    filters.push('(device_id LIKE ? OR last_user_id LIKE ? OR last_ip LIKE ? OR device_model LIKE ?)');
    const like = `%${keyword}%`;
    params.push(like, like, like, like);
  }

  const normalizedOfflineAllowed = normalizeBoolean(offlineAllowed);
  if (typeof normalizedOfflineAllowed === 'boolean') {
    filters.push('offline_allowed = ?');
    params.push(normalizedOfflineAllowed ? 1 : 0);
  }

  const normalizedOnlyOnline = normalizeBoolean(onlyOnline) === true;
  const normalizedWindowSeconds = Math.max(10, Math.min(normalizeNumber(onlineWindowSeconds, 90), 3600));
  if (normalizedOnlyOnline) {
    const windowMs = normalizedWindowSeconds;
    const boundary = new Date(Date.now() - windowMs * 1000);
    filters.push('last_seen_at >= ?');
    params.push(boundary);
  }

  const whereClause = filters.length ? `WHERE ${filters.join(' AND ')}` : '';
  logger.debug('[DeviceRepository] listDevices whereClause=%s params=%o', whereClause, params);
  const limit = Math.max(1, Math.trunc(safePageSize));
  const offsetValue = Math.max(0, Math.trunc(offset));
  const dataSql = `
    SELECT d.device_id,
           d.first_seen_at,
           d.last_seen_at,
           d.last_user_id,
           u.email AS last_user_email,
           u.username AS last_user_name,
           d.last_ip,
           d.device_model,
           d.os_version,
           d.app_version,
           d.offline_allowed,
           d.metadata,
           d.updated_at
    FROM device_registry d
    LEFT JOIN users u ON u.id = CAST(NULLIF(d.last_user_id, '') AS UNSIGNED)
    ${whereClause}
    ORDER BY last_seen_at DESC
    LIMIT ${limit}
    OFFSET ${offsetValue}
  `;

  const totalSql = `
    SELECT COUNT(*) AS total
    FROM device_registry
    ${whereClause}
  `;

  const [rows] = await dbPool.execute(dataSql, params);
  const [countRows] = await dbPool.execute(totalSql, params);

  const result = {
    items: rows.map(mapDeviceRow),
    page: safePage,
    pageSize: safePageSize,
    total: countRows[0]?.total ?? 0
  };
  logger.debug('[DeviceRepository] listDevices result total=%d items=%d', result.total, result.items.length);
  return result;
}

module.exports = {
  upsertDeviceRecord,
  findDeviceById,
  setDeviceOfflineAllowed,
  listDevices
};
