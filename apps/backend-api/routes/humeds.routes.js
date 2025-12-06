const express = require('express');
const router = express.Router();
const humedsController = require('../controllers/humeds.controller');

router.post('/token', humedsController.getTokenForUser);

module.exports = router;
