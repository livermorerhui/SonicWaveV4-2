const express = require('express');
const router = express.Router();
const customerController = require('../controllers/customer.controller');
const authenticateToken = require('../middleware/auth');

router.post('/', authenticateToken, customerController.createCustomer);
router.get('/', authenticateToken, customerController.getCustomers);
router.put('/:customerId', authenticateToken, customerController.updateCustomer);

module.exports = router;
