require('dotenv').config();

// Provide a safe-ish development fallback so login doesn't crash when JWT_SECRET is missing.
if (!process.env.JWT_SECRET) {
  const fallbackSecret = 'dev-super-secret';
  process.env.JWT_SECRET = fallbackSecret;
  // Avoid logger dependency here to keep config lightweight.
  console.warn('⚠️ JWT_SECRET not set. Using development fallback. Set JWT_SECRET in .env for security.');
}

const ENV = process.env.NODE_ENV || 'development';

let config = {};

if (ENV === 'development') {
  config = require('./development');
} else if (ENV === 'production') {
  config = require('./production');
}

module.exports = config;
