const logger = require('../logger');
const HumedsAccountService = require('../services/humedsAccount.service');
const humedsApi = require('../services/humedsApi.client');

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

async function testPasswordLogin(req, res) {
  const { mobile, password, regionCode } = req.body || {};

  if (!mobile || !password) {
    return res
      .status(400)
      .json(buildApiResponse(4001, 'mobile 和 password 为必填字段', null));
  }

  try {
    const result = await humedsApi.login({
      mobile,
      password,
      regionCode,
    });

    const responseData = {
      token_jwt: result.tokenJwt,
      raw: result.raw,
    };

    return res.status(200).json(buildApiResponse(200, 'ok', responseData));
  } catch (err) {
    logger.error('humeds testPasswordLogin error', {
      error: err.message,
      stack: err.stack,
      body: req.body,
      status: err.status || err.response?.status,
      response:
        err.originalError?.humedsResponse ||
        err.originalError?.response?.data ||
        err.response?.data ||
        null,
    });

    if (err.code === 'HUMEDS_LOGIN_FAILED') {
      const raw =
        err.originalError?.humedsResponse ||
        err.originalError?.response?.data ||
        err.response?.data ||
        null;

      const httpStatus =
        err.status && err.status < 500 ? err.status : 422;

      return res
        .status(httpStatus)
        .json(
          buildApiResponse(
            4200,
            err.message || '对方登录失败',
            { raw }
          )
        );
    }

    return res
      .status(500)
      .json(buildApiResponse(5000, '调用 Humeds 登录接口失败', null));
  }
}

module.exports = {
  getTokenForUser,
  testPasswordLogin,
};
