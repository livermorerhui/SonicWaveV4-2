// knexfile.js
require('dotenv').config(); // 读取 .env 文件

module.exports = {
  development: {
    client: 'mysql2',
    connection: {
      host: process.env.DB_HOST || '127.0.0.1',
      user: process.env.DB_USER || 'root',
      password: process.env.DB_PASSWORD || 'vscode89xuhang',
      database: process.env.DB_NAME || 'sonicwave_db'
    },
    migrations: {
      directory: './db/migrations'
    }
  },
  production: {
    client: 'mysql2',
    connection: {
      host: process.env.DB_HOST,
      user: process.env.DB_USER,
      password: process.env.DB_PASSWORD,
      database: process.env.DB_NAME
    },
    migrations: {
      directory: './db/migrations'
    }
  }
};
