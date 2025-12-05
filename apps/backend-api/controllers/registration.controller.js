const registrationService = require('../services/registration.service');

function buildApiResponse(code, msg, data) {
  return { code, msg, data };
}

async function sendRegisterCode(req, res) {
  try {
    const { mobile, accountType } = req.body || {};

    if (!mobile || !accountType) {
      return res.status(400).json(buildApiResponse(4001, '参数错误', null));
    }

    await registrationService.sendRegisterCode({ mobile, accountType });

    return res.json(buildApiResponse(200, '验证码已发送', null));
  } catch (err) {
    console.error(err);

    if (err.code === 'INVALID_INPUT') {
      return res.status(400).json(buildApiResponse(4001, err.message || '参数错误', null));
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

    return res.json(buildApiResponse(200, '注册成功', { userId: String(result.userId) }));
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
