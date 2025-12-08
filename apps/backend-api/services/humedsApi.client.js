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
    if (!mobile) {
      throw buildHumedsError(
        'mobile is required for Humeds login',
        'HUMEDS_LOGIN_FAILED'
      );
    }

    const form = new URLSearchParams();
    form.append('mobile', mobile);

    const region = regionCode || process.env.HUMEDS_REGION_CODE || '86';
    form.append('regionCode', region);

    if (smscode) {
      form.append('smscode', smscode);
    } else if (password) {
      form.append('password', password);
    } else {
      throw buildHumedsError(
        'Either password or smscode is required for Humeds login',
        'HUMEDS_LOGIN_FAILED'
      );
    }

    const res = await client.post('/api/login', form.toString(), {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    });
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
    if (err.code === 'HUMEDS_LOGIN_FAILED') {
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
      throw err;
    }

    logger.error('Humeds login error', {
      error: err.message,
      stack: err.stack,
      status: err.status || err.response?.status,
      response:
        err.response?.data ||
        err.originalError?.humedsResponse ||
        null,
    });

    const fallbackData =
      err.response?.data ||
      err.originalError?.humedsResponse ||
      null;

    const message =
      (fallbackData && fallbackData.msg) ||
      err.message ||
      'Humeds login failed';

    throw buildHumedsError(message, 'HUMEDS_LOGIN_FAILED', {
      response: fallbackData,
      originalError: err,
    });
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

async function signup({ mobile, smscode, password, birthday, regionCode }) {
  try {
    const payload = {
      mobile,
      smscode,
      password,
      regionCode: regionCode || humedsConfig.defaultRegionCode,
    };

    if (birthday) {
      payload.birthday = birthday;
    }

    const res = await client.post('/api/signup', payload);
    const data = res.data || {};
    const code = data.code;

    if (code !== 200) {
      const message = data.desc || data.msg || `Humeds signup failed with code ${code}`;
      throw buildHumedsError(message, 'HUMEDS_SIGNUP_FAILED');
    }

    return {
      raw: data,
    };
  } catch (err) {
    logger.error('Humeds signup error', {
      error: err.message,
      stack: err.stack,
      response: err.response?.data,
      status: err.response?.status,
    });

    if (err.code === 'HUMEDS_SIGNUP_FAILED') {
      throw err;
    }

    const message = err.response?.data?.msg || err.message || 'Humeds signup failed';
    throw buildHumedsError(message, 'HUMEDS_SIGNUP_FAILED', err);
  }
}

async function userExistNormalized({ mobile, regionCode }) {
  const result = await userExist({ mobile, regionCode });
  const raw = result.raw || {};
  const code = raw.code;
  let exists = null;

  if (code === 200) {
    exists = true;
  } else if (code === 201) {
    exists = false;
  } else {
    logger.warn('Unexpected Humeds userexist code', {
      mobile,
      regionCode,
      code,
    });
  }

  return { exists, raw };
}

module.exports = {
  login,
  userExist,
  signup,
  userExistNormalized,
};
