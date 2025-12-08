const bcrypt = require('bcrypt');
const { dbPool } = require('../config/db');
const smsCodeService = require('./smsCode.service');
const HumedsAccountService = require('./humedsAccount.service');
const logger = require('../logger');
const humedsAccountRepo = require('../repositories/humedsAccount.repository');
const humedsApi = require('./humedsApi.client');
const humedsConfig = require('../config/humeds.config');

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

function normalizeRegionCode(regionCode) {
  if (typeof regionCode !== 'string') {
    return humedsConfig.defaultRegionCode;
  }

  const trimmed = regionCode.trim();
  if (!trimmed) {
    return humedsConfig.defaultRegionCode;
  }

  return trimmed;
}

async function resolveJointState({ mobile, regionCode }) {
  const normalizedRegionCode = normalizeRegionCode(regionCode);

  const [userRows] = await dbPool.execute(
    'SELECT id FROM users WHERE mobile = ? LIMIT 1',
    [mobile]
  );
  const selfUser = userRows[0] || null;
  const selfRegistered = !!selfUser;

  const binding = await humedsAccountRepo.findByMobile(mobile, normalizedRegionCode);
  const selfBound = !!binding;

  let partnerRegistered = false;

  if (selfBound) {
    partnerRegistered = true;
  } else {
    try {
      const { exists } = await humedsApi.userExistNormalized({
        mobile,
        regionCode: normalizedRegionCode,
      });
      if (exists === true) partnerRegistered = true;
      if (exists === false) partnerRegistered = false;
    } catch (err) {
      logger.error('resolveJointState humeds userExist failed', {
        error: err.message,
        stack: err.stack,
        mobile,
        regionCode: normalizedRegionCode,
      });
      const error = new Error('查询对方账号状态失败');
      error.code = 'HUMEDS_USER_EXIST_FAILED';
      throw error;
    }
  }

  let registrationMode = 'bothNew';

  if (!selfRegistered && !partnerRegistered) {
    registrationMode = 'bothNew';
  } else if (!selfRegistered && partnerRegistered) {
    registrationMode = 'partnerOnly';
  } else if (selfRegistered && !partnerRegistered) {
    registrationMode = 'selfExistsPartnerNew';
  } else if (selfRegistered && selfBound && partnerRegistered) {
    registrationMode = 'alreadyRegistered';
  } else {
    registrationMode = 'selfExistsPartnerNew';
    logger.warn('resolveJointState: unexpected combination', {
      mobile,
      regionCode: normalizedRegionCode,
      selfRegistered,
      selfBound,
      partnerRegistered,
    });
  }

  return {
    selfRegistered,
    selfBound,
    partnerRegistered,
    registrationMode,
    userId: selfUser ? selfUser.id : null,
    regionCode: normalizedRegionCode,
  };
}

async function insertLocalUser({ mobile, password, accountType, birthday, orgName }) {
  const passwordHash = await bcrypt.hash(password, SALT_ROUNDS);

  const insertSql = `
    INSERT INTO users
      (username, email, mobile, mobile_verified, password, account_type, birthday, org_name)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
  `;
  const values = [
    mobile,
    null,
    mobile,
    true,
    passwordHash,
    accountType,
    birthday || null,
    accountType === 'org' ? orgName : null,
  ];

  const [result] = await dbPool.execute(insertSql, values);
  return result.insertId;
}

async function maybeBindHumeds({ userId, mobile, smscode, regionCode, registrationMode }) {
  if (!HUMEDS_BIND_ON_REGISTER) {
    return;
  }

  logger.info('Humeds bind on register start', {
    userId,
    mobile,
    registrationMode,
  });

  try {
    await HumedsAccountService.ensureTokenForUser({
      userId,
      mobile,
      smscode,
      regionCode,
    });
    logger.info('Humeds bind on register success', {
      userId,
      mobile,
      registrationMode,
    });
  } catch (err) {
    logger.error('Humeds bind on register failed', {
      userId,
      mobile,
      registrationMode,
      error: err.message,
      code: err.code,
    });

    if (HUMEDS_STRICT_REGISTER) {
      throw err;
    }
  }
}

async function sendRegisterCode({ mobile, accountType, regionCode }) {
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

  const jointState = await resolveJointState({
    mobile: normalizedMobile,
    regionCode,
  });

  const {
    selfRegistered,
    selfBound,
    partnerRegistered,
    registrationMode,
    regionCode: finalRegionCode,
  } = jointState;

  let needSmsInput = true;
  if (registrationMode === 'alreadyRegistered') {
    needSmsInput = false;
  } else {
    needSmsInput = true;
  }

  logger.info('sendRegisterCode: joint state resolved', {
    mobile: normalizedMobile,
    regionCode: finalRegionCode,
    selfRegistered,
    selfBound,
    partnerRegistered,
    registrationMode,
    needSmsInput,
  });

  return {
    selfRegistered,
    selfBound,
    partnerRegistered,
    registrationMode,
    needSmsInput,
    regionCode: finalRegionCode,
  };
}

async function submitRegister({
  mobile,
  code,
  password,
  accountType,
  birthday,
  orgName,
  regionCode,
}) {
  const normalizedMobile = typeof mobile === 'string' ? mobile.trim() : '';
  const normalizedCode = typeof code === 'string' ? code.trim() : code;
  if (!normalizedMobile || !normalizedCode || !password || !accountType) {
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

  const jointState = await resolveJointState({
    mobile: normalizedMobile,
    regionCode,
  });

  const {
    selfRegistered,
    selfBound,
    partnerRegistered,
    registrationMode,
    userId: existingUserId,
    regionCode: finalRegionCode,
  } = jointState;

  if (registrationMode === 'alreadyRegistered') {
    const error = new Error('账号已注册，请直接登录');
    error.code = 'ACCOUNT_ALREADY_REGISTERED';
    throw error;
  }

  let finalUserId = existingUserId;

  if (registrationMode === 'bothNew') {
    const [existing] = await dbPool.execute(
      'SELECT id FROM users WHERE mobile = ? LIMIT 1',
      [normalizedMobile]
    );
    if (existing.length > 0) {
      const error = new Error('手机号已注册');
      error.code = 'MOBILE_EXISTS';
      throw error;
    }

    await humedsApi.signup({
      mobile: normalizedMobile,
      smscode: normalizedCode,
      password,
      birthday: accountType === 'personal' ? birthday : undefined,
      regionCode: finalRegionCode,
    });

    finalUserId = await insertLocalUser({
      mobile: normalizedMobile,
      password,
      accountType,
      birthday,
      orgName,
    });

    await maybeBindHumeds({
      userId: finalUserId,
      mobile: normalizedMobile,
      smscode: normalizedCode,
      regionCode: finalRegionCode,
      registrationMode,
    });
  } else if (registrationMode === 'partnerOnly') {
    const [existing] = await dbPool.execute(
      'SELECT id FROM users WHERE mobile = ? LIMIT 1',
      [normalizedMobile]
    );
    if (existing.length > 0) {
      const error = new Error('手机号已注册');
      error.code = 'MOBILE_EXISTS';
      throw error;
    }

    finalUserId = await insertLocalUser({
      mobile: normalizedMobile,
      password,
      accountType,
      birthday,
      orgName,
    });

    await maybeBindHumeds({
      userId: finalUserId,
      mobile: normalizedMobile,
      smscode: normalizedCode,
      regionCode: finalRegionCode,
      registrationMode,
    });
  } else if (registrationMode === 'selfExistsPartnerNew') {
    if (!selfRegistered || !finalUserId) {
      const error = new Error('参数错误');
      error.code = 'INVALID_INPUT';
      throw error;
    }

    await humedsApi.signup({
      mobile: normalizedMobile,
      smscode: normalizedCode,
      password,
      birthday: accountType === 'personal' ? birthday : undefined,
      regionCode: finalRegionCode,
    });

    await maybeBindHumeds({
      userId: finalUserId,
      mobile: normalizedMobile,
      smscode: normalizedCode,
      regionCode: finalRegionCode,
      registrationMode,
    });
  }

  return {
    userId: finalUserId,
    mode: registrationMode,
  };
}

module.exports = {
  sendRegisterCode,
  submitRegister,
};
