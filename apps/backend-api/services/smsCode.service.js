const localSmsCodeService = require('./smsCode.local.service');
const logger = require('../logger');

const PROVIDER = process.env.SMS_PROVIDER || 'local';

function getImpl() {
  switch (PROVIDER) {
    case 'local':
      return localSmsCodeService;
    default:
      logger.warn('[smsCode.service] Unknown SMS_PROVIDER, fallback to local', {
        provider: PROVIDER,
      });
      return localSmsCodeService;
  }
}

async function sendCode(mobile, scene) {
  const impl = getImpl();
  return impl.sendCode(mobile, scene);
}

async function verifyCode(mobile, scene, code) {
  const impl = getImpl();
  return impl.verifyCode(mobile, scene, code);
}

module.exports = {
  sendCode,
  verifyCode,
};
