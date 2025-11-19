const { dbPool } = require('../config/db');

const mapRowToFlag = row => ({
  featureKey: row.feature_key,
  enabled: Boolean(row.enabled),
  metadata: parseMetadata(row.metadata),
  updatedAt: row.updated_at,
  updatedBy: row.updated_by
});

const parseMetadata = value => {
  if (value === null || value === undefined) {
    return null;
  }
  if (typeof value === 'string') {
    try {
      return JSON.parse(value);
    } catch {
      return null;
    }
  }
  return value;
};

async function listFlags() {
  const sql = `
    SELECT feature_key, enabled, metadata, updated_by, updated_at
    FROM app_feature_flags
    ORDER BY feature_key ASC
  `;
  const [rows] = await dbPool.execute(sql);
  return rows.map(mapRowToFlag);
}

async function findFlagByKey(featureKey) {
  const sql = `
    SELECT feature_key, enabled, metadata, updated_by, updated_at
    FROM app_feature_flags
    WHERE feature_key = ?
    LIMIT 1
  `;
  const [rows] = await dbPool.execute(sql, [featureKey]);
  return rows.length ? mapRowToFlag(rows[0]) : null;
}

async function upsertFlag({ featureKey, enabled, metadata = null, updatedBy = null }) {
  const serializedMetadata = metadata ? JSON.stringify(metadata) : null;
  const sql = `
    INSERT INTO app_feature_flags (feature_key, enabled, metadata, updated_by, updated_at)
    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
    ON DUPLICATE KEY UPDATE
      enabled = VALUES(enabled),
      metadata = VALUES(metadata),
      updated_by = VALUES(updated_by),
      updated_at = CURRENT_TIMESTAMP
  `;
  await dbPool.execute(sql, [featureKey, enabled ? 1 : 0, serializedMetadata, updatedBy]);
  return findFlagByKey(featureKey);
}

module.exports = {
  listFlags,
  findFlagByKey,
  upsertFlag
};
