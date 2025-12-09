const logger = require('../logger');
const HumedsAccountService = require('../services/humedsAccount.service');
const humedsApi = require('../services/humedsApi.client');

function buildApiResponse(code, msg, data) {
  return { code, msg, data };
}

async function getOrRefreshHumedsToken(req, res) {
  try {
    const userIdFromAuth = req.user?.id || req.user?.userId;
    // TODO: 后续强制走认证，这里临时允许从 body 读取 userId 便于调试
    const userId = userIdFromAuth || req.body?.userId;

    if (!userId) {
      logger.warn('getOrRefreshHumedsToken missing userId', { headers: req.headers });
      return res
        .status(401)
        .json(buildApiResponse(4010, '缺少用户身份，请登录或传入 userId', null));
    }

    const data = await HumedsAccountService.getOrRefreshHumedsTokenForUser(userId);
    return res.json(buildApiResponse(200, 'ok', data));
  } catch (err) {
    logger.error('getOrRefreshHumedsToken error', {
      error: err.message,
      stack: err.stack,
      userId: req.user?.id || req.user?.userId || req.body?.userId,
    });

    if (err.code === 'HUMEDS_NOT_BOUND') {
      return res
        .status(404)
        .json(
          buildApiResponse(
            4404,
            err.message || 'Humeds account not bound for this user',
            null
          )
        );
    }

    if (err.code === 'HUMEDS_PARAM_INVALID') {
      return res
        .status(400)
        .json(buildApiResponse(4002, err.message || '参数错误', null));
    }

    return res
      .status(500)
      .json(buildApiResponse(5000, '获取 Humeds token 失败', null));
  }
}

/**
 * 统一的 Humeds token 接口：
 *
 * 两种使用方式：
 * 1）只带 userId：
 *    - 只从本地 humeds_accounts 读取已保存的 token_jwt，不访问 Humeds。
 *    - 用于「启动 Humeds」等场景。
 *
 *    请求示例：
 *    POST /api/humeds/token
 *    { "userId": 8 }
 *
 * 2）带上 mobile + (password 或 smscode) + regionCode：
 *    - 调用 Humeds /api/login，成功后写入/更新 humeds_accounts，
 *      并返回 token_jwt 和原始 Humeds 响应（raw）。
 *    - 用于绑定 / 重绑 / 调试等场景。
 *
 *    请求示例：
 *    POST /api/humeds/token
 *    {
 *      "userId": 8,
 *      "mobile": "18900000000",
 *      "password": "xxxxxx",
 *      "regionCode": "86"
 *    }
 */
async function getTokenForUser(req, res) {
  const {
    userId,
    mobile,
    password,
    smscode,
    regionCode,
  } = req.body || {};

  if (!userId) {
    return res.status(400).json({
      code: 4001,
      msg: 'userId is required',
    });
  }

  const hasCredentials =
    !!mobile &&
    (!!password || !!smscode);

  try {
    if (hasCredentials) {
      const { tokenJwt, raw } =
        await HumedsAccountService.ensureTokenForUser({
          userId,
          mobile,
          password,
          smscode,
          regionCode,
        });

      logger.info('Humeds token ensured via remote login', {
        userId,
        mobile,
      });

      return res.json({
        code: 200,
        msg: 'ok',
        data: {
          token_jwt: tokenJwt,
          raw,
        },
      });
    }

    const { tokenJwt } = await HumedsAccountService.getExistingTokenForUser(
      userId,
    );

    logger.info('Humeds token loaded from local humeds_accounts', {
      userId,
    });

    return res.json({
      code: 200,
      msg: 'ok',
      data: {
        token_jwt: tokenJwt,
      },
    });
  } catch (error) {
    logger.error('getTokenForUser error', {
      error: error.message,
      stack: error.stack,
      code: error.code,
    });

    if (
      error.code === 'HUMEDS_LOGIN_FAILED' ||
      error.code === 'HUMEDS_PARAM_INVALID' ||
      error.code === 'HUMEDS_TOKEN_NOT_FOUND'
    ) {
      return res.status(200).json({
        code: 4200,
        msg: error.message || 'Humeds token error',
        data: null,
      });
    }

    return res.status(500).json({
      code: 5000,
      msg: 'Internal server error',
    });
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
  getOrRefreshHumedsToken,
  getTokenForUser,
  testPasswordLogin,
};
