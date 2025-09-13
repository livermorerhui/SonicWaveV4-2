module.exports = {
  db: {
    host: process.env.DB_HOST,
    user: process.env.DB_USER || 'root',
    password: process.env.DB_PASSWORD || '',
    database: process.env.DB_NAME || 'sonicwave_db'
  },
  jwt: {
    secret: process.env.JWT_SECRET
  },
  port: process.env.PORT || 3000
};