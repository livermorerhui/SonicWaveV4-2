const { dbPool } = require('../config/db');

const createCustomer = async (req, res) => {
    const { name, dateOfBirth, gender, phone, email, height, weight } = req.body;

    if (!name || !email) {
        return res.status(400).json({ error: 'Name and email are required' });
    }

    try {
        const sql = 'INSERT INTO customers (name, date_of_birth, gender, phone, email, height, weight) VALUES (?, ?, ?, ?, ?, ?, ?)';
        const [result] = await dbPool.execute(sql, [name, dateOfBirth, gender, phone, email, height, weight]);
        
        res.status(201).json({ id: result.insertId, message: 'Customer created successfully' });
    } catch (error) {
        console.error('Error creating customer:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
};

module.exports = {
    createCustomer
};
