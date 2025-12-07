const bcrypt = require('bcrypt');
const { dbPool } = require('../config/db');
const smsCodeService = require('./smsCode.service');
const HumedsAccountService = require('./humedsAccount.service');
const logger = require('../logger');

const HUMEDS_BIND_ON_REGISTER =
  (process.env.HUMEDS_BIND_ON_REGISTER || '').toLowerCase() === 'true';

const HUMEDS_STRICT_REGISTER =
  (process.env.HUMEDS_STRICT_REGISTER || '').toLowerCase() === 'true';

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

  await smsCodeService.sendCode(normalizedMobile, 'register');
}

async function submitRegister({ mobile, code, password, accountType, birthday, orgName }) {
  const normalizedMobile = typeof mobile === 'string' ? mobile.trim() : '';
  if (!normalizedMobile || !code || !password || !accountType) {
    const error = new Error('参数错误');
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

  const verifyResult = await smsCodeService.verifyCode(normalizedMobile, 'register', code);
  if (!verifyResult.ok) {
    const error = new Error(verifyResult.reason === 'EXPIRED' ? '验证码已过期' : '验证码错误');
    error.code = verifyResult.reason === 'EXPIRED' ? 'CODE_EXPIRED' : 'CODE_MISMATCH';
    throw error;
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

  if (HUMEDS_BIND_ON_REGISTER) {
    try {
      await HumedsAccountService.ensureTokenForUser({
        userId,
        mobile: normalizedMobile,
        smscode: code,
      });
    } catch (err) {
      logger.error('Humeds bind on register failed', {
        userId,
        mobile: normalizedMobile,
        error: err.message,
        code: err.code,
      });

      if (HUMEDS_STRICT_REGISTER) {
        throw err;
      }
    }
  }

  return { userId };
}

module.exports = {
  sendRegisterCode,
  submitRegister,
};
