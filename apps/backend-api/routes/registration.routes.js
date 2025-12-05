const express = require('express');
const router = express.Router();
const registrationController = require('../controllers/registration.controller');

router.post('/send_code', registrationController.sendRegisterCode);
router.post('/submit', registrationController.submitRegister);

module.exports = router;
