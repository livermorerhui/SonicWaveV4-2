// knexfile.js
require('dotenv').config(); // 读取 .env 文件

const baseConnection = {
  host: process.env.DB_HOST || '127.0.0.1',
  port: Number(process.env.DB_PORT || 3306),
  user: process.env.DB_USER || 'sonicwave',
  password: process.env.DB_PASSWORD || 'sonicwave_pwd',
  database: process.env.DB_NAME || 'sonicwave_db'
};

const migrations = {
  directory: './db/migrations',
  tableName: 'knex_migrations'
};

const seeds = {
  directory: './db/seeds'
};

module.exports = {
  development: {
    client: 'mysql2',
    connection: baseConnection,
    migrations,
    seeds
  },
  production: {
    client: 'mysql2',
    connection: baseConnection,
    migrations,
    seeds
  }
};
