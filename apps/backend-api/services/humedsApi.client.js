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
    const humedsCode = data.code;
    const humedsMsg = data.msg || data.desc || '';
    const humedsData = data.data || {};

    if (humedsCode !== 200) {
      const baseMessage = humedsMsg || `Humeds login failed with code ${humedsCode}`;
      const message = humedsMsg && humedsCode
        ? `Humeds login failed: ${humedsMsg} (code=${humedsCode})`
        : baseMessage;

      throw buildHumedsError(message, 'HUMEDS_LOGIN_FAILED', {
        humedsResponse: data,
      });
    }

    const tokenJwt = humedsData?.token_jwt || humedsData?.tokenJwt;

    if (!tokenJwt) {
      const message = 'Humeds login success but token_jwt missing (code=200)';
      throw buildHumedsError(message, 'HUMEDS_LOGIN_FAILED', {
        humedsResponse: data,
      });
    }

    return {
      tokenJwt,
      raw: data,
    };
  } catch (err) {
    logger.error('Humeds login error', {
      error: err.message,
      stack: err.stack,
      status: err.status || err.response?.status,
      response:
        err.originalError?.humedsResponse ||
        err.originalError?.response?.data ||
        err.response?.data ||
        null,
    });

    if (err.code === 'HUMEDS_LOGIN_FAILED') {
      throw err;
    }

    const fallbackData =
      err.response?.data ||
      err.originalError?.humedsResponse ||
      null;
    const message =
      fallbackData?.msg ||
      err.message ||
      'Humeds login failed';
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
