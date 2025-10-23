const { dbPool } = require('../config/db');
const config = require('../config/config');

const columnCache = new Map();

async function hasColumn(tableName, columnName) {
  const cacheKey = `${tableName}.${columnName}`;
  if (columnCache.has(cacheKey)) {
    return columnCache.get(cacheKey);
  }

  const sql = `
    SELECT COUNT(*) AS count
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = ?
      AND TABLE_NAME = ?
      AND COLUMN_NAME = ?
    LIMIT 1
  `;

  const [rows] = await dbPool.execute(sql, [config.db.database, tableName, columnName]);
  const exists = rows[0]?.count > 0;
  columnCache.set(cacheKey, exists);
  return exists;
}

module.exports = {
  hasColumn
};
