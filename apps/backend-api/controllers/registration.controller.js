const registrationService = require('../services/registration.service');
const logger = require('../logger');

const BUSINESS_ERROR_CODES = new Set([
  // 本地注册/验证码
  'CODE_MISMATCH',
  'CODE_EXPIRED',
  'MOBILE_EXISTS',

  // 账号/权限
  'INVALID_CREDENTIALS',

  // Humeds 业务失败
  'HUMEDS_LOGIN_FAILED',
  'HUMEDS_USER_NOT_EXISTS',
  'HUMEDS_SIGNUP_FAILED',
  'HUMEDS_SMSCODE_FAILED',
  'HUMEDS_USER_EXIST_FAILED',
]);

function isBusinessError(err) {
  if (!err) return false;
  if (typeof err.code === 'string' && BUSINESS_ERROR_CODES.has(err.code)) return true;
  if (typeof err.apiCode === 'string' && BUSINESS_ERROR_CODES.has(err.apiCode)) return true;
  return false;
}

function buildErrMeta(req, err, extra = {}) {
  const traceId =
    req?.headers?.['x-trace-id'] ||
    req?.headers?.['x-request-id'] ||
    req?.traceId ||
    req?.id ||
    undefined;

  return {
    traceId,
    path: req?.originalUrl,
    method: req?.method,
    mobile: req?.body?.mobile || req?.query?.mobile,
    errCode: err?.code || err?.apiCode,
    message: err?.message,
    ...extra,
  };
}

function buildApiResponse(code, msg, data, extra) {
  const base = { code, msg, data };
  if (extra && typeof extra === 'object') {
    return { ...base, ...extra };
  }
  return base;
}

async function sendRegisterCode(req, res) {
  try {
    const { mobile, accountType } = req.body || {};

    if (!mobile || !accountType) {
      return res.status(400).json(buildApiResponse(4001, '参数错误', null));
    }

    const status = await registrationService.sendRegisterCode({ mobile, accountType });
    const msg = status.needSmsInput ? '验证码已发送' : 'Humeds 已注册，无需验证码';
    return res.json(
      buildApiResponse(200, msg, {
        mobile: status.mobile,
        accountType: status.accountType,
        selfRegistered: status.selfRegistered,
        selfBound: status.selfBound,
        partnerRegistered: status.partnerRegistered,
        needSmsInput: status.needSmsInput,
        registrationMode: status.registrationMode,
      }),
    );
  } catch (err) {
    const meta = buildErrMeta(req, err);
    if (isBusinessError(err)) {
      logger.warn('sendRegisterCode business error', meta);
    } else {
      logger.error('sendRegisterCode unexpected error', { ...meta, stack: err?.stack });
    }

    if (err.code === 'INVALID_INPUT') {
      return res.status(400).json(buildApiResponse(4001, err.message || '参数错误', null));
    }

    if (err.code === 'HUMEDS_SMSCODE_FAILED') {
      return res.status(500).json(buildApiResponse(5006, '验证码发送失败，请稍后重试', null));
    }

    if (err.code === 'HUMEDS_USER_EXIST_FAILED') {
      return res.status(500).json(buildApiResponse(5008, 'Humeds 状态查询失败，请稍后重试', null));
    }

    if (err.code === 'MOBILE_EXISTS') {
      return res.status(409).json(buildApiResponse(4091, err.message || '手机号已注册', null));
    }

    return res.status(500).json(buildApiResponse(5000, '服务器内部错误', null));
  }
}

async function submitRegister(req, res) {
  try {
    const { mobile, code, password, accountType, birthday, orgName } = req.body || {};

    if (!mobile || !password || !accountType) {
      return res.status(400).json(buildApiResponse(4001, '参数错误', null));
    }

    const result = await registrationService.submitRegister({
      mobile,
      code,
      password,
      accountType,
      birthday,
      orgName,
    });

    return res.json(
      buildApiResponse(200, '注册成功', {
        userId: String(result.userId),
        partnerRegistered: result.partnerRegistered ?? null,
        needSmsInput: result.needSmsInput ?? null,
        registrationMode: result.registrationMode ?? null,
        humedsBindStatus: result.humedsBindStatus ?? null,
        humedsErrorCode: result.humedsErrorCode ?? null,
        humedsErrorMessage: result.humedsErrorMessage ?? null,
      }),
    );
  } catch (err) {
    const meta = buildErrMeta(req, err);
    if (isBusinessError(err)) {
      logger.warn('submitRegister business error', meta);
    } else {
      logger.error('submitRegister unexpected error', { ...meta, stack: err?.stack });
    }

    if (err.code === 'INVALID_INPUT') {
      return res.status(400).json(buildApiResponse(4001, err.message || '参数错误', null));
    }

    if (err.code === 'HUMEDS_SMSCODE_FAILED') {
      return res.status(500).json(buildApiResponse(5006, '验证码发送失败，请稍后重试', null));
    }

    if (err.code === 'HUMEDS_SIGNUP_FAILED') {
      return res.status(500).json(buildApiResponse(5007, 'Humeds 注册失败，请稍后重试', null));
    }

    if (err.code === 'HUMEDS_USER_EXIST_FAILED') {
      return res.status(500).json(buildApiResponse(5008, 'Humeds 状态查询失败，请稍后重试', null));
    }

    if (err.code === 'CODE_EXPIRED' || err.code === 'CODE_MISMATCH') {
      return res
        .status(400)
        .json(buildApiResponse(4002, err.message || '验证码错误或已过期', null));
    }

    if (err.code === 'MOBILE_EXISTS') {
      return res.status(409).json(buildApiResponse(4091, err.message || '手机号已注册', null));
    }

    return res.status(500).json(buildApiResponse(5000, '服务器内部错误', null));
  }
}

module.exports = {
  sendRegisterCode,
  submitRegister,
};
