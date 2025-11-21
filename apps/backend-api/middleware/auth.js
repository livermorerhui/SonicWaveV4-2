const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const logger = require('../logger');

const buildError = (code, message) => ({
  error: {
    code,
    message,
    traceId: crypto.randomUUID()
  }
});

function authenticateToken(req, res, next) {
  const authHeader = req.headers.authorization;
  const token = authHeader && authHeader.split(' ')[1];

  if (!token) {
    logger.warn(`[Auth Middleware] Token missing. Request to '${req.originalUrl}' denied.`);
    return res.status(401).json(buildError('UNAUTHENTICATED', '缺少访问令牌，请先登录'));
  }

  jwt.verify(token, process.env.JWT_SECRET, (err, user) => {
    if (err) {
      if (err.name === 'TokenExpiredError') {
        logger.warn(`[Auth Middleware] Token expired for request to '${req.originalUrl}'.`);
        return res.status(401).json(buildError('TOKEN_EXPIRED', '访问令牌已过期'));
      }

      logger.error(`[Auth Middleware] Token verification failed for request to '${req.originalUrl}'. Error: ${err.message}`);
      return res.status(403).json(buildError('INVALID_TOKEN', '访问令牌无效'));
    }

    logger.info(`[Auth Middleware] Token verified for userId: ${user.id || user.userId}. Granting access to '${req.originalUrl}'.`);
    req.user = user;
    next();
  });
}

module.exports = authenticateToken;
