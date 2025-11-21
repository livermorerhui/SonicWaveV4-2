const router = require('express').Router();
const { body } = require('express-validator');
const validate = require('../middleware/validate');
const deviceController = require('../controllers/device.controller');

router.post(
  '/heartbeat',
  [
    body('deviceId').isString().isLength({ min: 4, max: 191 }).withMessage('deviceId 长度需在 4~191 之间'),
    body('ipAddress').optional().isString().isLength({ max: 45 }),
    body('deviceModel').optional().isString().isLength({ max: 255 }),
    body('osVersion').optional().isString().isLength({ max: 64 }),
    body('appVersion').optional().isString().isLength({ max: 64 })
  ],
  validate,
  deviceController.recordDeviceHeartbeat
);

module.exports = router;
