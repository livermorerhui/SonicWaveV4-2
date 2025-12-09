const logger = require('../logger');
const humedsApi = require('./humedsApi.client');
const humedsAccountRepo = require('../repositories/humedsAccount.repository');
const humedsConfig = require('../config/humeds.config');

class HumedsAccountService {
  static async ensureTokenForUser(params) {
    // Used after successful local registration or when explicitly requested via /api/humeds/token
    const {
      userId,
      mobile,
      password,
      smscode,
      regionCode = humedsConfig.defaultRegionCode,
    } = params || {};

    if (!userId || !mobile) {
      const error = new Error('userId and mobile are required');
      error.code = 'HUMEDS_PARAM_INVALID';
      throw error;
    }

    const loginResult = await humedsApi.login({
      mobile,
      password,
      smscode,
      regionCode,
    });

    const tokenJwt = loginResult.tokenJwt;

    const existing = await humedsAccountRepo.findByUserId(userId);
    if (!existing) {
      await humedsAccountRepo.insertAccount({
        userId,
        mobile,
        regionCode,
        tokenJwt,
        status: 'active',
      });
      logger.info('Humeds account created', { userId, mobile });
    } else {
      await humedsAccountRepo.updateToken({ userId, tokenJwt });
      logger.info('Humeds account token updated', { userId, mobile });
    }

    return { tokenJwt, raw: loginResult.raw };
  }

  /**
   * 获取或刷新（当前版本仅获取缓存）指定用户的 Humeds token
   * @param {number} userId
   * @returns {Promise<{ token_jwt: string, source: string }>}
   */
  static async getOrRefreshHumedsTokenForUser(userId) {
    if (!userId) {
      const error = new Error('userId is required');
      error.code = 'HUMEDS_PARAM_INVALID';
      throw error;
    }

    const account = await humedsAccountRepo.findByUserId(userId);
    if (account?.token_jwt) {
      return { token_jwt: account.token_jwt, source: 'cached' };
    }

    const error = new Error('Humeds account not bound for this user');
    error.code = 'HUMEDS_NOT_BOUND';
    throw error;
  }

  /**
   * 只从本地 humeds_accounts 读取 token，不访问 Humeds 服务器。
   * - 如果找到绑定记录且有 token_jwt，则直接返回 { tokenJwt, account }。
   * - 如果没有绑定记录或没有 token_jwt，则抛出带 code 的错误。
   *
   * @param {number} userId
   * @returns {Promise<{ tokenJwt: string, account: any }>}
   */
  static async getExistingTokenForUser(userId) {
    if (!userId) {
      const error = new Error('Humeds token lookup requires userId');
      error.code = 'HUMEDS_PARAM_INVALID';
      throw error;
    }

    const account = await humedsAccountRepo.findByUserId(userId);

    if (!account || !account.token_jwt) {
      const error = new Error('Humeds account or token not found for user');
      error.code = 'HUMEDS_TOKEN_NOT_FOUND';
      throw error;
    }

    return {
      tokenJwt: account.token_jwt,
      account,
    };
  }
}

module.exports = HumedsAccountService;
