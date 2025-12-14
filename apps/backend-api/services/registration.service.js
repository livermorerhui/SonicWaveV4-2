const bcrypt = require('bcrypt');
const { dbPool } = require('../config/db');
const smsCodeService = require('./smsCode.service');
const HumedsAccountService = require('./humedsAccount.service');
const humedsApiClient = require('./humedsApi.client');
const logger = require('../logger');

const HUMEDS_BIND_ON_REGISTER =
  (process.env.HUMEDS_BIND_ON_REGISTER || '').toLowerCase() === 'true';

const HUMEDS_STRICT_REGISTER =
  (process.env.HUMEDS_STRICT_REGISTER || '').toLowerCase() === 'true';

const REGISTER_SMS_REQUIRED =
  (process.env.REGISTER_SMS_REQUIRED || '').toLowerCase() === 'true';

const HUMEDS_CHECK_USER_EXIST_ON_REGISTER =
  (process.env.HUMEDS_CHECK_USER_EXIST_ON_REGISTER || '').toLowerCase() === 'true';

const SALT_ROUNDS = 10;
const ACCOUNT_TYPES = ['personal', 'org'];

function isValidBirthday(value) {
  if (typeof value !== 'string') return false;
  const match = /^\d{4}-\d{2}-\d{2}$/.test(value);
  if (!match) return false;
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(year, month - 1, day);
  return (
    date.getFullYear() === year && date.getMonth() + 1 === month && date.getDate() === day
  );
}

function parseBoolLike(v) {
  if (v === undefined || v === null) return null;
  if (typeof v === 'boolean') return v;
  if (typeof v === 'number') {
    if (v === 1) return true;
    if (v === 0) return false;
    return null;
  }
  if (typeof v === 'string') {
    const s = v.trim().toLowerCase();
    if (['1', 'true', 'yes', 'y', 'exist', 'exists'].includes(s)) return true;
    if (['0', 'false', 'no', 'n', 'not', 'none'].includes(s)) return false;
  }
  return null;
}

function parseHumedsExistFlag(raw) {
  if (!raw || typeof raw !== 'object') return null;

  // code 不是 200 直接当不可解析
  if (typeof raw.code === 'number' && raw.code !== 200) return null;

  const data = raw.data;

  // 关键增强：data 本身就是 0/1/true/false/"0"/"1"
  const direct = parseBoolLike(data);
  if (direct !== null) return direct;

  // 如果 data 不是对象，无法继续解析
  if (!data || typeof data !== 'object') {
    return null;
  }

  // 常见字段候选（尽量宽松）
  const keys = [
    'exist', 'exists', 'isExist', 'isexist', 'userExist', 'userexist',
    'registered', 'isRegistered', 'isregistered', 'hasUser', 'hasuser',
    'flag', 'value', 'result', 'status', 'state'
  ];

  for (const k of keys) {
    if (Object.prototype.hasOwnProperty.call(data, k)) {
      const parsed = parseBoolLike(data[k]);
      if (parsed !== null) return parsed;
    }
  }

  // 有些接口会把存在性塞在嵌套字段里
  const nestedCandidates = [
    data.data, data.result, data.payload
  ];
  for (const n of nestedCandidates) {
    const parsed = parseBoolLike(n);
    if (parsed !== null) return parsed;
    if (n && typeof n === 'object') {
      for (const k of keys) {
        if (Object.prototype.hasOwnProperty.call(n, k)) {
          const p = parseBoolLike(n[k]);
          if (p !== null) return p;
        }
      }
    }
  }

  return null;
}

function computeRegistrationMode({ bindOnRegister, partnerRegistered }) {
  if (!bindOnRegister) return 'LOCAL_ONLY';
  if (partnerRegistered === true) return 'LOCAL_AND_BIND_HUMEDS_EXISTING';
  if (partnerRegistered === false) return 'LOCAL_AND_BIND_HUMEDS_NEW';
  return 'LOCAL_AND_BIND_HUMEDS_UNKNOWN';
}

async function sendRegisterCode({ mobile, accountType }) {
  const normalizedMobile = typeof mobile === 'string' ? mobile.trim() : '';
  if (!normalizedMobile) {
    const error = new Error('手机号不能为空');
    error.code = 'INVALID_INPUT';
    throw error;
  }

  if (normalizedMobile.length > 32) {
    const error = new Error('手机号长度不合法');
    error.code = 'INVALID_INPUT';
    throw error;
  }

  if (!ACCOUNT_TYPES.includes(accountType)) {
    const error = new Error('账号类型不合法');
    error.code = 'INVALID_INPUT';
    throw error;
  }

  const [existing] = await dbPool.execute(
    'SELECT id FROM users WHERE mobile = ? LIMIT 1',
    [normalizedMobile]
  );
  if (existing.length > 0) {
    logger.info('sendRegisterCode: mobile already exists, skip sms', {
      mobile: normalizedMobile,
    });

    const error = new Error('手机号已注册');
    error.code = 'MOBILE_EXISTS';
    throw error;
  }

  let partnerRegistered = null;
  if (HUMEDS_BIND_ON_REGISTER) {
    try {
      const existRes = await humedsApiClient.userExist({ mobile: normalizedMobile });
      if (typeof existRes?.parsedExist === 'boolean') {
        partnerRegistered = existRes.parsedExist;
      } else {
        partnerRegistered = parseHumedsExistFlag(existRes?.raw);
      }
      if (process.env.HUMEDS_DEBUG_USEREXIST === 'true') {
        logger.info('sendRegisterCode: humeds userExist raw(debug)', {
          mobile: normalizedMobile,
          raw: existRes?.raw,
          parsed: partnerRegistered,
        });
      }
    } catch (err) {
      logger.warn('sendRegisterCode: humeds userExist failed', {
        mobile: normalizedMobile,
        error: err.message,
        code: err.code,
      });
      partnerRegistered = null;
    }
  }

  const needSmsInput = !!REGISTER_SMS_REQUIRED;
  if (needSmsInput) {
    await smsCodeService.sendCode(normalizedMobile, 'register');
  }

  const registrationMode = computeRegistrationMode({
    bindOnRegister: HUMEDS_BIND_ON_REGISTER,
    partnerRegistered,
  });

  return {
    mobile: normalizedMobile,
    accountType,
    selfRegistered: false,
    selfBound: false,
    partnerRegistered,
    needSmsInput,
    registrationMode,
  };
}

async function submitRegister({ mobile, code, password, accountType, birthday, orgName }) {
  const normalizedMobile = typeof mobile === 'string' ? mobile.trim() : '';
  const normalizedCode = typeof code === 'string' ? code.trim() : '';
  if (!normalizedMobile || !password || !accountType) {
    const error = new Error('手机号、密码和账号类型为必填项');
    error.code = 'INVALID_INPUT';
    throw error;
  }

  if (normalizedMobile.length > 32) {
    const error = new Error('手机号长度不合法');
    error.code = 'INVALID_INPUT';
    throw error;
  }

  if (!ACCOUNT_TYPES.includes(accountType)) {
    const error = new Error('账号类型不合法');
    error.code = 'INVALID_INPUT';
    throw error;
  }

  if (accountType === 'personal') {
    const normalizedBirthday = typeof birthday === 'string' ? birthday.trim() : '';
    if (!normalizedBirthday || !isValidBirthday(normalizedBirthday)) {
      const error = new Error('生日格式不正确');
      error.code = 'INVALID_INPUT';
      throw error;
    }
    birthday = normalizedBirthday;
  }

  if (accountType === 'org') {
    const normalizedOrgName = typeof orgName === 'string' ? orgName.trim() : '';
    if (!normalizedOrgName) {
      const error = new Error('机构名称不能为空');
      error.code = 'INVALID_INPUT';
      throw error;
    }
    orgName = normalizedOrgName;
  }

  if (REGISTER_SMS_REQUIRED) {
    if (!normalizedCode) {
      const error = new Error('验证码不能为空');
      error.code = 'INVALID_INPUT';
      throw error;
    }

    const verifyResult = await smsCodeService.verifyCode(normalizedMobile, 'register', normalizedCode);
    if (!verifyResult.ok) {
      const error = new Error(verifyResult.reason === 'EXPIRED' ? '验证码已过期' : '验证码错误');
      error.code = verifyResult.reason === 'EXPIRED' ? 'CODE_EXPIRED' : 'CODE_MISMATCH';
      throw error;
    }
  }

  const [existing] = await dbPool.execute('SELECT id FROM users WHERE mobile = ? LIMIT 1', [normalizedMobile]);
  if (existing.length > 0) {
    const error = new Error('手机号已注册');
    error.code = 'MOBILE_EXISTS';
    throw error;
  }

  const passwordHash = await bcrypt.hash(password, SALT_ROUNDS);

  const insertSql = `
    INSERT INTO users
      (username, email, mobile, mobile_verified, password, account_type, birthday, org_name)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
  `;
  const values = [
    normalizedMobile,
    null,
    normalizedMobile,
    true,
    passwordHash,
    accountType,
    birthday || null,
    accountType === 'org' ? orgName : null,
  ];

  const [result] = await dbPool.execute(insertSql, values);
  const userId = result.insertId;

  let humedsBindStatus = 'skipped';
  let humedsErrorCode = null;
  let humedsErrorMessage = null;

  if (HUMEDS_BIND_ON_REGISTER && password) {
    logger.info('Humeds bind on register start', {
      userId,
      mobile: normalizedMobile,
    });

    try {
      await HumedsAccountService.ensureTokenForUser({
        userId,
        mobile: normalizedMobile,
        password,
        loginMode: 'APP_SHARED_PASSWORD_REGISTER',
      });
      logger.info('Humeds bind on register success', {
        userId,
        mobile: normalizedMobile,
      });
      humedsBindStatus = 'success';
    } catch (err) {
      logger.error('Humeds bind on register failed', {
        userId,
        mobile: normalizedMobile,
        error: err.message,
        code: err.code,
      });

      humedsBindStatus = 'failed';
      humedsErrorCode = err.code || null;
      humedsErrorMessage = err.message || null;

      if (HUMEDS_STRICT_REGISTER) {
        throw err;
      }
    }
  }


  return {
    userId,
    humedsBindStatus,
    humedsErrorCode,
    humedsErrorMessage,
  };
}

module.exports = {
  sendRegisterCode,
  submitRegister,
};
