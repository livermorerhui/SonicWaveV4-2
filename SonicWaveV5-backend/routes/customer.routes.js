const express = require('express');
const router = express.Router();
const customerController = require('../controllers/customer.controller');
const authenticateToken = require('../middleware/auth');

router.post('/', authenticateToken, customerController.createCustomer);

module.exports = router;
