const { dbPool } = require('../config/db');

const createCustomer = async (req, res) => {
    const { name, dateOfBirth, gender, phone, email, height, weight } = req.body;
    const { userId } = req.user; // Extract userId from the authenticated user

    if (!name || !email) {
        return res.status(400).json({ error: 'Name and email are required' });
    }

    try {
        const sql = 'INSERT INTO customers (name, date_of_birth, gender, phone, email, height, weight, user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)';
        const [result] = await dbPool.execute(sql, [name, dateOfBirth, gender, phone, email, height, weight, userId]);
        
        res.status(201).json({ id: result.insertId, message: 'Customer created successfully' });
    } catch (error) {
        console.error('Error creating customer:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
};

const getCustomers = async (req, res) => {
    try {
        const { userId } = req.user; // Get userId from authenticated user

        const sql = 'SELECT id, name, date_of_birth AS dateOfBirth, gender, phone, email, height, weight FROM customers WHERE user_id = ?';
        const [rows] = await dbPool.execute(sql, [userId]);

        res.status(200).json(rows);
    } catch (error) {
        console.error('Error fetching customers:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
};

const updateCustomer = async (req, res) => {
    const { customerId } = req.params;
    const { userId } = req.user; // Authenticated user's ID
    const { name, dateOfBirth, gender, phone, email, height, weight } = req.body;

    try {
        // First, verify ownership
        const [customers] = await dbPool.execute('SELECT user_id FROM customers WHERE id = ?', [customerId]);

        if (customers.length === 0) {
            return res.status(404).json({ message: 'Customer not found.' });
        }

        if (customers[0].user_id !== userId) {
            return res.status(403).json({ message: 'Forbidden: You do not own this customer.' });
        }

        // Build update query dynamically to only update provided fields
        const updateFields = [];
        const updateValues = [];

        if (name !== undefined) { updateFields.push('name = ?'); updateValues.push(name); }
        if (dateOfBirth !== undefined) { updateFields.push('date_of_birth = ?'); updateValues.push(dateOfBirth); }
        if (gender !== undefined) { updateFields.push('gender = ?'); updateValues.push(gender); }
        if (phone !== undefined) { updateFields.push('phone = ?'); updateValues.push(phone); }
        if (email !== undefined) { updateFields.push('email = ?'); updateValues.push(email); }
        if (height !== undefined) { updateFields.push('height = ?'); updateValues.push(height); }
        if (weight !== undefined) { updateFields.push('weight = ?'); updateValues.push(weight); }

        if (updateFields.length === 0) {
            return res.status(400).json({ message: 'No fields to update.' });
        }

        const sql = `UPDATE customers SET ${updateFields.join(', ')} WHERE id = ?`;
        await dbPool.execute(sql, [...updateValues, customerId]);

        res.status(200).json({ message: 'Customer updated successfully.' });

    } catch (error) {
        console.error('Error updating customer:', error);
        res.status(500).json({ error: 'Internal server error.' });
    }
};

module.exports = {
    createCustomer,
    getCustomers,
    updateCustomer
};
