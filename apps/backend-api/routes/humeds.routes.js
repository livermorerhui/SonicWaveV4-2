const express = require('express');
const router = express.Router();
const humedsController = require('../controllers/humeds.controller');
const authenticateToken = require('../middleware/auth.js');

router.get('/token', authenticateToken, humedsController.getOrRefreshHumedsToken);
router.post('/token', humedsController.getTokenForUser);
router.post('/test/login', humedsController.testPasswordLogin);

module.exports = router;
