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

function getHumedsResponse(err) {
  return (
    err?.originalError?.humedsResponse ||
    err?.response?.data ||
    err?.response ||
    null
  );
}

function isHumedsBizFailure(err) {
  const r = getHumedsResponse(err);
  const code = r?.code;
  if (code === 201) return true;
  if (typeof code === 'number' && code >= 200 && code < 500) {
    return code !== 200;
  }
  return false;
}

function logHumedsError(action, err, ctx = {}) {
  const r = getHumedsResponse(err);

  const meta = {
    action,
    mobile: ctx.mobile,
    errCode: err?.code,
    error: err?.message,
    response: r ? { code: r.code, msg: r.msg, data: r.data } : null,
  };

  if (isHumedsBizFailure(err)) {
    logger.warn(`Humeds ${action} failed(biz)`, meta);
  } else {
    logger.error(`Humeds ${action} failed(system)`, { ...meta, stack: err?.stack });
  }
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
    logHumedsError('login', err, { mobile });

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

async function signup({ mobile, password, smscode, regionCode }) {
  try {
    if (!mobile || !password || !smscode) {
      throw buildHumedsError(
        'mobile/password/smscode are required for Humeds signup',
        'HUMEDS_SIGNUP_FAILED',
      );
    }

    const form = new URLSearchParams();
    form.append('mobile', mobile);
    form.append('password', password);
    form.append('smscode', smscode);

    const region =
      regionCode || humedsConfig.defaultRegionCode || process.env.HUMEDS_REGION_CODE || '86';
    form.append('regionCode', region);

    const res = await client.post('/api/signup', form.toString(), {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    });

    const data = res.data || {};
    const humedsCode = data.code;
    const humedsMsg = data.msg || data.desc || (typeof data.data === 'string' ? data.data : '');

    if (humedsCode !== 200) {
      const message = humedsMsg
        ? `Humeds signup failed: ${humedsMsg} (code=${humedsCode})`
        : `Humeds signup failed with code ${humedsCode}`;
      throw buildHumedsError(message, 'HUMEDS_SIGNUP_FAILED', { humedsResponse: data });
    }

    return { raw: data };
  } catch (err) {
    logHumedsError('signup', err, { mobile });
    throw err;
  }
}

async function sendSmsCode({ mobile, regionCode }) {
  try {
    if (!mobile) {
      throw buildHumedsError('mobile is required for Humeds smscode', 'HUMEDS_SMSCODE_FAILED');
    }

    const form = new URLSearchParams();
    form.append('mobile', mobile);

    const region = regionCode || humedsConfig.defaultRegionCode || process.env.HUMEDS_REGION_CODE || '86';
    form.append('regionCode', region);

    const res = await client.post('/api/smscode', form.toString(), {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    });

    const data = res.data || {};
    const humedsCode = data.code;
    const humedsMsg = data.msg || data.desc || data.data || '';

    if (humedsCode !== 200) {
      const message = humedsMsg
        ? `Humeds smscode failed: ${humedsMsg} (code=${humedsCode})`
        : `Humeds smscode failed with code ${humedsCode}`;
      throw buildHumedsError(message, 'HUMEDS_SMSCODE_FAILED', { humedsResponse: data });
    }

    return { raw: data };
  } catch (err) {
    // 不在这里吞异常；上层（registration.service）会做 best-effort mirror
    logHumedsError('smscode', err, { mobile });
    throw err;
  }
}

async function userExist({ mobile, regionCode }) {
  try {
    if (!mobile) {
      throw buildHumedsError('mobile is required for Humeds userExist', 'HUMEDS_USER_EXIST_FAILED');
    }

    const form = new URLSearchParams();
    form.append('mobile', mobile);

    const region = regionCode || humedsConfig.defaultRegionCode || process.env.HUMEDS_REGION_CODE || '86';
    form.append('regionCode', region);

    const res = await client.post('/api/userexist', form.toString(), {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    });

    const data = res.data || {};
    const humedsCode = data.code;
    const humedsMsg = data.msg || data.desc || '';

    const dataText =
      typeof data.data === 'string' ? data.data :
      typeof humedsMsg === 'string' ? humedsMsg :
      '';

    const textHasNotExist = dataText && dataText.includes('不存在');

    if (humedsCode === 200) {
      const parsedExist = textHasNotExist ? false : true;
      return { raw: data, parsedExist };
    }

    if (humedsCode === 201) {
      // Humeds 用 201 表示“手机号不存在”，这是正常业务态
      return { raw: data, parsedExist: false };
    }

    // 其他 code 才当作失败
    const message = humedsMsg && humedsCode
      ? `Humeds userExist failed: ${humedsMsg} (code=${humedsCode})`
      : (humedsMsg || `Humeds userExist failed with code ${humedsCode}`);

    throw buildHumedsError(message, 'HUMEDS_USER_EXIST_FAILED', { humedsResponse: data });
  } catch (err) {
    logHumedsError('userExist', err, { mobile });

    const message = err.response?.data?.msg || err.message || 'Humeds user existence check failed';
    const wrapped = buildHumedsError(message, 'HUMEDS_USER_EXIST_FAILED', err);
    throw wrapped;
  }
}

module.exports = {
  login,
  userExist,
  signup,
  sendSmsCode,
};
