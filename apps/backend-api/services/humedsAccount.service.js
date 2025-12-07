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

    return tokenJwt;
  }
}

module.exports = HumedsAccountService;
