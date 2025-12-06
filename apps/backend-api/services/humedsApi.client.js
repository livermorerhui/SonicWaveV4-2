const axios = require('axios');
const logger = require('../logger');
const humedsConfig = require('../config/humeds.config');

const client = axios.create({
  baseURL: humedsConfig.baseUrl,
  timeout: humedsConfig.timeoutMs,
});

function buildHumedsError(message, code, originalError) {
  const error = new Error(message);
  error.code = code;
  if (originalError) {
    error.status = originalError.response ? originalError.response.status : originalError.status;
    error.originalError = originalError;
  }
  return error;
}

async function login({ mobile, password, smscode, regionCode }) {
  try {
    const payload = {
      mobile,
      regionCode: regionCode || humedsConfig.defaultRegionCode,
    };

    if (password) payload.password = password;
    if (smscode) payload.smscode = smscode;

    const res = await client.post('/api/login', payload);
    const data = res.data || {};
    const tokenJwt = data?.data?.token_jwt || data?.data?.tokenJwt;

    if (!tokenJwt) {
      throw buildHumedsError('Humeds login success but token_jwt missing', 'HUMEDS_LOGIN_FAILED');
    }

    return {
      tokenJwt,
      raw: data,
    };
  } catch (err) {
    logger.error('Humeds login error', {
      error: err.message,
      stack: err.stack,
      response: err.response?.data,
      status: err.response?.status,
    });

    if (err.code === 'HUMEDS_LOGIN_FAILED') {
      throw err;
    }

    const message = err.response?.data?.msg || err.message || 'Humeds login failed';
    throw buildHumedsError(message, 'HUMEDS_LOGIN_FAILED', err);
  }
}

async function userExist({ mobile, regionCode }) {
  try {
    const payload = {
      mobile,
      regionCode: regionCode || humedsConfig.defaultRegionCode,
    };

    const res = await client.post('/api/userexist', payload);
    const data = res.data || {};

    return {
      raw: data,
    };
  } catch (err) {
    logger.error('Humeds userExist error', {
      error: err.message,
      stack: err.stack,
      response: err.response?.data,
      status: err.response?.status,
    });

    const message = err.response?.data?.msg || err.message || 'Humeds user existence check failed';
    const wrapped = buildHumedsError(message, 'HUMEDS_USER_EXIST_FAILED', err);
    throw wrapped;
  }
}

module.exports = {
  login,
  userExist,
};
