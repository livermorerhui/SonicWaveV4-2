const { validationResult } = require('express-validator');
const crypto = require('crypto');

const buildErrorEnvelope = (code, message) => ({
  error: {
    code,
    message,
    traceId: crypto.randomUUID()
  }
});

module.exports = (req, res, next) => {
  const errors = validationResult(req);
  if (errors.isEmpty()) {
    return next();
  }

  const traceId = crypto.randomUUID();
  const formatted = errors.array().map(err => ({
    field: err.param,
    message: err.msg
  }));

  return res.status(400).json({
    error: {
      code: 'VALIDATION_ERROR',
      message: '请求参数校验失败',
      traceId,
      details: formatted
    }
  });
};
