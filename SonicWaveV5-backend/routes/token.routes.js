const express = require('express');
const router = express.Router();
const tokenController = require('../controllers/token.controller.js');

// Route to refresh an access token
router.post('/refresh', tokenController.refreshToken);

// Route to logout (revoke a refresh token)
router.post('/logout', tokenController.logout);

module.exports = router;
