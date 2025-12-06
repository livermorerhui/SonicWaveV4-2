const mysql = require('mysql2/promise');
const logger = require('../logger');
const appConfig = require('./config'); // 按环境加载 development / production 配置

// 统一从 .env 里取端口，默认 3306
const DB_PORT = Number(process.env.DB_PORT || 3306);

// 创建一个数据库连接池
const dbPool = mysql.createPool({
  host: appConfig.db.host,
  port: DB_PORT,
  user: appConfig.db.user,
  password: appConfig.db.password,
  database: appConfig.db.database,
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0
});

// 异步函数，用于检查数据库连接（index.js 会 await 这个函数）
async function checkDbConnection() {
  try {
    await dbPool.query('SELECT 1');
    logger.info('✅ Successfully connected to the database.', {
      host: appConfig.db.host,
      port: DB_PORT,
      database: appConfig.db.database
    });
  } catch (error) {
    logger.error('❌ Failed to connect to the database.', { error: error.message });
    // 保持原来的语义：连不上直接退出，让 pm2 重启
    process.exit(1);
  }
}

module.exports = {
  dbPool,
  checkDbConnection
};