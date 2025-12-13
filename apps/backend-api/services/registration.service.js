const bcrypt = require('bcrypt');
const { dbPool } = require('../config/db');
const smsCodeService = require('./smsCode.service');
const HumedsAccountService = require('./humedsAccount.service');
const logger = require('../logger');
const humedsApi = require('./humedsApi.client');

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
    [normalizedMobile],
  );
  if (existing.length > 0) {
    logger.info('sendRegisterCode: mobile already exists, skip sms', {
      mobile: normalizedMobile,
    });

    const error = new Error('手机号已注册');
    error.code = 'MOBILE_EXISTS';
    throw error;
  }

  // 调用 Humeds /userexist，推断 partnerRegistered
  let partnerRegistered = null;

  if (HUMEDS_CHECK_USER_EXIST_ON_REGISTER) {
    try {
      const userExistResult = await humedsApi.userExist({
        mobile: normalizedMobile,
        // regionCode 留空，客户端会使用默认值
      });

      const raw = userExistResult.raw || {};
      const humedsCode = raw.code;
      const humedsData = raw.data || {};

      if (humedsCode === 200) {
        const existFlag =
          humedsData.exist !== undefined ? humedsData.exist : humedsData.exists;
        partnerRegistered = existFlag === true || existFlag === 1 || existFlag === '1';
      }
    } catch (err) {
      logger.warn('sendRegisterCode: Humeds userExist failed, fallback to null', {
        mobile: normalizedMobile,
        error: err.message,
        code: err.code,
      });
      partnerRegistered = null;
    }
  }

  let registrationMode = 'LOCAL_ONLY';
  if (HUMEDS_BIND_ON_REGISTER) {
    if (partnerRegistered === true) {
      registrationMode = 'LOCAL_AND_BIND_HUMEDS_EXISTING';
    } else if (partnerRegistered === false) {
      registrationMode = 'LOCAL_AND_BIND_HUMEDS_NEW';
    } else {
      registrationMode = 'LOCAL_AND_BIND_HUMEDS_UNKNOWN';
    }
  }

  // 仍然发送本地短信验证码，行为不变
  await smsCodeService.sendCode(normalizedMobile, 'register');

  // 返回状态骨架，供 controller 展开到响应顶层
  return {
    mobile: normalizedMobile,
    accountType,
    selfRegistered: false,
    partnerRegistered,
    needSmsInput: REGISTER_SMS_REQUIRED,
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

  if (HUMEDS_BIND_ON_REGISTER && REGISTER_SMS_REQUIRED && normalizedCode) {
    logger.info('Humeds bind on register start', {
      userId,
      mobile: normalizedMobile,
    });

    try {
      await HumedsAccountService.ensureTokenForUser({
        userId,
        mobile: normalizedMobile,
        smscode: normalizedCode,
        loginMode: 'APP_SMS_REGISTER',
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
