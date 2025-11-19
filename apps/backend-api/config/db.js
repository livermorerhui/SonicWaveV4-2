const mysql = require('mysql2/promise');
const logger = require('../logger');
const config = require('./config'); // 引入新的配置

// 创建一个数据库连接池
const dbPool = mysql.createPool({
  host: config.db.host,
  user: config.db.user,
  password: config.db.password,
  database: config.db.database,
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0
});

// 异步函数，用于检查数据库连接
async function checkDbConnection() {
  try {
    await dbPool.query('SELECT 1');
    logger.info('✅ Successfully connected to the database.');
  } catch (error) {
    logger.error('❌ Failed to connect to the database.', { error: error.message });
    // 退出进程，因为没有数据库，应用无法运行
    process.exit(1);
  }
}

module.exports = {
  dbPool,
  checkDbConnection
};
