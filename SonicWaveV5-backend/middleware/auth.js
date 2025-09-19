const jwt = require('jsonwebtoken');
const logger = require('../logger'); // 引入 logger

function authenticateToken(req, res, next) {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];

  if (token == null) {
    // [新增日志]
    logger.warn(`[Auth Middleware] Token is missing. Request to '${req.originalUrl}' denied.`);
    return res.sendStatus(401); // Unauthorized
  }

  jwt.verify(token, process.env.JWT_SECRET, (err, user) => {
    if (err) {
      // [新增日志]
      logger.error(`[Auth Middleware] Token verification failed for request to '${req.originalUrl}'. Error: ${err.message}`);
      return res.sendStatus(403); // Forbidden
    }
    // [新增日志]
    logger.info(`[Auth Middleware] Token verified for userId: ${user.userId}. Granting access to '${req.originalUrl}'.`);
    req.user = user;
    next();
  });
}

module.exports = authenticateToken;
