const express = require('express');
const router = express.Router();
const appController = require('../controllers/app.controller');

router.post('/usage', appController.recordAppUsage);

module.exports = router;