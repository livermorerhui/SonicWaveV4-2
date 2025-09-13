require('dotenv').config({ path: '../.env' });

const ENV = process.env.NODE_ENV || 'development';

let config = {};

if (ENV === 'development') {
  config = require('./development');
} else if (ENV === 'production') {
  config = require('./production');
}

module.exports = config;