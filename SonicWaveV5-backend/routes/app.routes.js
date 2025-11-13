const express = require('express');
const router = express.Router();
const appController = require('../controllers/app.controller');

router.post('/usage', appController.recordAppUsage);
router.get('/feature-flags', appController.getFeatureFlags);

module.exports = router;
