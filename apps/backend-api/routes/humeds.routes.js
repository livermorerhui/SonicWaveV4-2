const express = require('express');
const router = express.Router();
const humedsController = require('../controllers/humeds.controller');

router.post('/token', humedsController.getTokenForUser);
router.post('/test/login', humedsController.testPasswordLogin);

module.exports = router;
