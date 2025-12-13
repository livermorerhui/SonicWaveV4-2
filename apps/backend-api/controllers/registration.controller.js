const registrationService = require('../services/registration.service');

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

    // 从 service 获取状态信息（自注册 / 对方注册 / 是否需要验证码 / 注册模式）
    const status = await registrationService.sendRegisterCode({ mobile, accountType });

    return res.json(
      buildApiResponse(200, '验证码已发送', null, {
        selfRegistered: status.selfRegistered,
        partnerRegistered: status.partnerRegistered,
        needSmsInput: status.needSmsInput,
        registrationMode: status.registrationMode,
        mobile: status.mobile,
        accountType: status.accountType,
      }),
    );
  } catch (err) {
    console.error(err);

    if (err.code === 'INVALID_INPUT') {
      return res.status(400).json(buildApiResponse(4001, err.message || '参数错误', null));
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

    if (!mobile || !code || !password || !accountType) {
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

    const data = { userId: String(result.userId) };

    return res.json(
      buildApiResponse(200, '注册成功', data, {
        humedsBindStatus: result.humedsBindStatus || 'skipped',
        humedsErrorCode: result.humedsErrorCode || null,
        humedsErrorMessage: result.humedsErrorMessage || null,
      }),
    );
  } catch (err) {
    console.error(err);

    if (err.code === 'INVALID_INPUT') {
      return res.status(400).json(buildApiResponse(4001, err.message || '参数错误', null));
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
