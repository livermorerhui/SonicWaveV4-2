const { dbPool } = require('../config/db');
const logger = require('../logger');

async function findByUserId(userId) {
  try {
    const [rows] = await dbPool.execute(
      'SELECT * FROM humeds_accounts WHERE user_id = ? LIMIT 1',
      [userId]
    );
    return rows[0] || null;
  } catch (err) {
    logger.error('Failed to find humeds account by user_id', {
      error: err.message,
      stack: err.stack,
      userId,
    });
    throw err;
  }
}

async function findByMobile(mobile, regionCode) {
  try {
    const [rows] = await dbPool.execute(
      'SELECT * FROM humeds_accounts WHERE mobile = ? AND region_code = ? LIMIT 1',
      [mobile, regionCode]
    );
    return rows[0] || null;
  } catch (err) {
    logger.error('Failed to find humeds account by mobile', {
      error: err.message,
      stack: err.stack,
      mobile,
      regionCode,
    });
    throw err;
  }
}

async function insertAccount({ userId, mobile, regionCode, tokenJwt, status }) {
  const now = new Date();
  try {
    const [result] = await dbPool.execute(
      `INSERT INTO humeds_accounts
        (user_id, mobile, region_code, token_jwt, status, last_login_at, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        userId,
        mobile,
        regionCode,
        tokenJwt || null,
        status || 'active',
        now,
        now,
        now,
      ]
    );

    return result.insertId;
  } catch (err) {
    logger.error('Failed to insert humeds account', {
      error: err.message,
      stack: err.stack,
      userId,
      mobile,
      regionCode,
    });
    throw err;
  }
}

async function updateToken({ userId, tokenJwt }) {
  const now = new Date();
  try {
    const [result] = await dbPool.execute(
      `UPDATE humeds_accounts
        SET token_jwt = ?, last_login_at = ?, updated_at = ?
        WHERE user_id = ?`,
      [tokenJwt, now, now, userId]
    );
    return result.affectedRows;
  } catch (err) {
    logger.error('Failed to update humeds token', {
      error: err.message,
      stack: err.stack,
      userId,
    });
    throw err;
  }
}

module.exports = {
  findByUserId,
  findByMobile,
  insertAccount,
  updateToken,
};
