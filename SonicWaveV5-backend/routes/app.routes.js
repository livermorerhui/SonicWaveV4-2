const express = require('express');
const { body, query } = require('express-validator');
const router = express.Router();
const appController = require('../controllers/app.controller');
const validate = require('../middleware/validate');

const deviceIdValidator = value => (typeof value === 'string' ? value.trim() : value);

router.post(
  '/usage',
  [
    body('launchTime').isInt({ min: 0 }).withMessage('launchTime 必须为毫秒时间戳'),
    body('userId').optional().isString().isLength({ max: 255 }),
    body('deviceId').optional().customSanitizer(deviceIdValidator).isLength({ max: 191 }),
    body('ipAddress').optional().isString().isLength({ max: 45 }),
    body('deviceModel').optional().isString().isLength({ max: 255 }),
    body('osVersion').optional().isString().isLength({ max: 64 }),
    body('appVersion').optional().isString().isLength({ max: 64 })
  ],
  validate,
  appController.recordAppUsage
);
router.get(
  '/feature-flags',
  [query('deviceId').optional().customSanitizer(deviceIdValidator).isLength({ max: 191 })],
  validate,
  appController.getFeatureFlags
);

module.exports = router;
