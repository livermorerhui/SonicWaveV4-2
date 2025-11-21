const crypto = require('crypto');
const logger = require('../logger');

const buildError = (code, message) => ({
  error: {
    code,
    message,
    traceId: crypto.randomUUID()
  }
});

module.exports = (req, res, next) => {
  if (!req.user) {
    return res.status(401).json(buildError('UNAUTHENTICATED', '需要登录才能访问'));
  }

  if (req.user.role !== 'admin') {
    logger.warn(`User ${req.user.id || req.user.userId || 'unknown'} attempted admin route: ${req.originalUrl}`);
    return res.status(403).json(buildError('FORBIDDEN', '仅限管理员访问'));
  }

  return next();
};
