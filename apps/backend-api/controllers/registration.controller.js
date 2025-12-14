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

    const status = await registrationService.sendRegisterCode({ mobile, accountType });
    const msg = status.needSmsInput ? '验证码已发送' : '无需验证码';
    return res.json({ ...buildApiResponse(200, msg, null), ...status });
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
