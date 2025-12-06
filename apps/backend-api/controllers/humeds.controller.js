const logger = require('../logger');
const HumedsAccountService = require('../services/humedsAccount.service');

function buildApiResponse(code, msg, data) {
  return { code, msg, data };
}

async function getTokenForUser(req, res) {
  try {
    const { userId, mobile, password, smscode, regionCode } = req.body || {};

    if (!userId || !mobile) {
      return res.status(400).json(
        buildApiResponse(4001, 'userId 和 mobile 为必填字段', null)
      );
    }

    const tokenJwt = await HumedsAccountService.ensureTokenForUser({
      userId,
      mobile,
      password,
      smscode,
      regionCode,
    });

    return res.json(buildApiResponse(200, 'ok', { token_jwt: tokenJwt }));
  } catch (err) {
    logger.error('getTokenForUser error', {
      error: err.message,
      stack: err.stack,
      body: req.body,
    });

    if (err.code === 'HUMEDS_PARAM_INVALID') {
      return res.status(400).json(buildApiResponse(4002, err.message, null));
    }

    if (err.code === 'HUMEDS_LOGIN_FAILED') {
      return res
        .status(err.status && err.status < 500 ? err.status : 422)
        .json(buildApiResponse(4200, err.message || '对方登录失败', null));
    }

    return res
      .status(500)
      .json(buildApiResponse(5000, '获取 Humeds token 失败', null));
  }
}

module.exports = {
  getTokenForUser,
};
