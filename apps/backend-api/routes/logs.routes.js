const express = require('express');
const router = express.Router();
const logsController = require('../controllers/logs.controller.js');

// POST /api/logs
router.post('/', logsController.createClientLogs);

module.exports = router;
