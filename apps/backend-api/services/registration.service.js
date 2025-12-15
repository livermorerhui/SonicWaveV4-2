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
const REGISTER_SMS_PROVIDER = (process.env.REGISTER_SMS_PROVIDER || 'local').toLowerCase();

const HUMEDS_CHECK_USER_EXIST_ON_REGISTER =
  (process.env.HUMEDS_CHECK_USER_EXIST_ON_REGISTER || '').toLowerCase() === 'true';
const HUMEDS_SIGNUP_ON_REGISTER =
  (process.env.HUMEDS_SIGNUP_ON_REGISTER || '').toLowerCase() === 'true';

const SALT_ROUNDS = 10;
const ACCOUNT_TYPES = ['personal', 'org'];

function computeNeedSmsInput({ registerSmsRequired, humedsBindOnRegister, partnerRegistered }) {
  if (!registerSmsRequired) return false;
  if (humedsBindOnRegister && partnerRegistered === true) return false;
  return true;
}

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

function computeNeedSmsInput({ smsRequired, bindOnRegister, partnerRegistered }) {
  if (!smsRequired) return false;

  // 关键：Humeds 已存在账号 -> 不需要验证码（用于“已有 Humeds 账号”的注册场景）
  if (bindOnRegister && partnerRegistered === true) return false;

  return true;
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
  if (HUMEDS_CHECK_USER_EXIST_ON_REGISTER || HUMEDS_BIND_ON_REGISTER) {
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

  const needSmsInput = computeNeedSmsInput({
    smsRequired: REGISTER_SMS_REQUIRED,
    bindOnRegister: HUMEDS_BIND_ON_REGISTER,
    partnerRegistered,
  });

  if (!needSmsInput) {
    logger.info('sendRegisterCode: skip sms (humeds already registered)', { mobile: normalizedMobile });
  }

  if (needSmsInput) {
    if (REGISTER_SMS_PROVIDER === 'humeds') {
      try {
        await humedsApiClient.sendSmsCode({ mobile: normalizedMobile });
        logger.info('sendRegisterCode: humeds smscode sent', { mobile: normalizedMobile });
      } catch (e) {
        const err = new Error('验证码发送失败（Humeds）');
        err.code = 'HUMEDS_SMSCODE_FAILED';
        throw err;
      }
    } else {
      await smsCodeService.sendCode(normalizedMobile, 'register');
    }
  } else {
    logger.info('sendRegisterCode: humeds user exists, skip smscode', { mobile: normalizedMobile });
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
  let partnerRegistered = null;
  let needSmsInput = true;
  let humedsSignupDone = false;

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

  if (HUMEDS_BIND_ON_REGISTER && HUMEDS_CHECK_USER_EXIST_ON_REGISTER) {
    try {
      const existRes = await humedsApiClient.userExist({ mobile: normalizedMobile });
      if (typeof existRes?.parsedExist === 'boolean') {
        partnerRegistered = existRes.parsedExist;
      } else {
        partnerRegistered = parseHumedsExistFlag(existRes?.raw);
      }
    } catch (_err) {
      partnerRegistered = null;
    }
  }

  needSmsInput = computeNeedSmsInput({
    smsRequired: REGISTER_SMS_REQUIRED,
    bindOnRegister: HUMEDS_BIND_ON_REGISTER,
    partnerRegistered,
  });

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

  if (needSmsInput) {
    if (!normalizedCode) {
      const error = new Error('验证码不能为空');
      error.code = 'INVALID_INPUT';
      throw error;
    }

    if (REGISTER_SMS_PROVIDER === 'humeds') {
      if (partnerRegistered === true) {
        try {
          await humedsApiClient.login({ mobile: normalizedMobile, smscode: normalizedCode });
        } catch (e) {
          const msg = e?.message || '';
          const codeToThrow =
            msg.includes('过期') || msg.includes('超时')
              ? 'CODE_EXPIRED'
              : msg.includes('验证码') || msg.includes('短信')
              ? 'CODE_MISMATCH'
              : 'HUMEDS_SMSCODE_FAILED';
          const err = new Error(msg || 'Humeds 验证码校验失败');
          err.code = codeToThrow;
          throw err;
        }
      } else if (partnerRegistered === false) {
        if (HUMEDS_SIGNUP_ON_REGISTER) {
          try {
            await humedsApiClient.signup({
              mobile: normalizedMobile,
              password,
              smscode: normalizedCode,
            });
            humedsSignupDone = true;
          } catch (e) {
            const msg = e?.message || '';
            const codeToThrow =
              msg.includes('过期') || msg.includes('超时')
                ? 'CODE_EXPIRED'
                : msg.includes('验证码') || msg.includes('短信')
                ? 'CODE_MISMATCH'
                : 'HUMEDS_SIGNUP_FAILED';
            const err = new Error(msg || 'Humeds 注册失败');
            err.code = codeToThrow;
            throw err;
          }
        } else {
          const err = new Error('Humeds 用户不存在');
          err.code = 'HUMEDS_USER_NOT_EXISTS';
          throw err;
        }
      } else {
        const err = new Error('Humeds 状态未知，请稍后重试');
        err.code = 'HUMEDS_USER_EXIST_FAILED';
        throw err;
      }
    } else {
      try {
        const verifyResult = await smsCodeService.verifyCode(normalizedMobile, 'register', normalizedCode);
        if (!verifyResult.ok) {
          const error = new Error(verifyResult.reason === 'EXPIRED' ? '验证码已过期' : '验证码错误');
          error.code = verifyResult.reason === 'EXPIRED' ? 'CODE_EXPIRED' : 'CODE_MISMATCH';
          throw error;
        }
      } catch (e) {
        if (HUMEDS_BIND_ON_REGISTER && partnerRegistered === false && HUMEDS_SIGNUP_ON_REGISTER) {
          logger.warn('submitRegister: local sms verify failed but continue for humeds signup', {
            mobile: normalizedMobile,
            error: e.message,
          });
        } else {
          throw e;
        }
      }
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
  const registrationMode = computeRegistrationMode({
    bindOnRegister: HUMEDS_BIND_ON_REGISTER,
    partnerRegistered,
  });

  if (HUMEDS_BIND_ON_REGISTER && password) {
    if (partnerRegistered === false) {
      if (HUMEDS_SIGNUP_ON_REGISTER) {
        // 如果前面已完成 signup 校验，则跳过再次 signup
        if (!humedsSignupDone) {
          logger.info('Humeds signup on register start', { userId, mobile: normalizedMobile });
          try {
            await humedsApiClient.signup({
              mobile: normalizedMobile,
              password,
              smscode: normalizedCode,
            });
            logger.info('Humeds signup on register success', { userId, mobile: normalizedMobile });
            humedsSignupDone = true;
          } catch (e) {
            humedsBindStatus = 'failed';
            humedsErrorCode = e.code || 'HUMEDS_SIGNUP_FAILED';
            humedsErrorMessage = e.message || 'Humeds signup failed';
            logger.error('Humeds signup on register failed', {
              userId,
              mobile: normalizedMobile,
              error: humedsErrorMessage,
              code: humedsErrorCode,
            });
            if (HUMEDS_STRICT_REGISTER) {
              throw e;
            }
          }
        }

        if (humedsSignupDone) {
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
            humedsErrorCode = null;
            humedsErrorMessage = null;
          } catch (err) {
            const bizCodes = new Set([
              'HUMEDS_LOGIN_FAILED',
              'HUMEDS_SIGNUP_FAILED',
              'HUMEDS_USER_NOT_EXISTS',
              'HUMEDS_SMSCODE_FAILED',
              'HUMEDS_USER_EXIST_FAILED',
            ]);
            const humedsErrCode = err.code || 'HUMEDS_LOGIN_FAILED';
            const humedsErrMessage = err.message || 'Humeds login failed';
            const logMeta = {
              userId,
              mobile: normalizedMobile,
              error: humedsErrMessage,
              code: humedsErrCode,
            };
            if (bizCodes.has(humedsErrCode)) {
              logger.warn('Humeds bind on register failed(biz)', logMeta);
            } else {
              logger.error('Humeds bind on register failed(system)', {
                ...logMeta,
                stack: err?.stack,
              });
            }

            humedsBindStatus = 'failed';
            humedsErrorCode = humedsErrCode;
            humedsErrorMessage = humedsErrMessage;

            if (HUMEDS_STRICT_REGISTER) {
              throw err;
            }
          }
        }
      } else {
        humedsBindStatus = 'failed';
        humedsErrorCode = 'HUMEDS_USER_NOT_EXISTS';
        humedsErrorMessage = 'Humeds 用户不存在（需走 Humeds 注册/验证码流程）';
        logger.warn('Humeds bind on register skipped: user not exists', {
          userId,
          mobile: normalizedMobile,
        });
      }
    } else {
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
        humedsErrorCode = null;
        humedsErrorMessage = null;
      } catch (err) {
        const bizCodes = new Set([
          'HUMEDS_LOGIN_FAILED',
          'HUMEDS_SIGNUP_FAILED',
          'HUMEDS_USER_NOT_EXISTS',
          'HUMEDS_SMSCODE_FAILED',
          'HUMEDS_USER_EXIST_FAILED',
        ]);
        const humedsErrCode = err.code || 'HUMEDS_LOGIN_FAILED';
        const humedsErrMessage = err.message || 'Humeds login failed';
        const logMeta = {
          userId,
          mobile: normalizedMobile,
          error: humedsErrMessage,
          code: humedsErrCode,
        };
        if (bizCodes.has(humedsErrCode)) {
          logger.warn('Humeds bind on register failed(biz)', logMeta);
        } else {
          logger.error('Humeds bind on register failed(system)', {
            ...logMeta,
            stack: err?.stack,
          });
        }

        humedsBindStatus = 'failed';
        humedsErrorCode = humedsErrCode;
        humedsErrorMessage = humedsErrMessage;

        if (HUMEDS_STRICT_REGISTER) {
          throw err;
        }
      }
    }
  }


  return {
    userId,
    partnerRegistered,
    needSmsInput,
    registrationMode,
    humedsBindStatus,
    humedsErrorCode,
    humedsErrorMessage,
  };
}

module.exports = {
  sendRegisterCode,
  submitRegister,
};
