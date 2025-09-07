const express = require('express');
const sqlite3 = require('sqlite3').verbose();
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');

const app = express();
app.use(express.json());

const port = 3000;
const saltRounds = 10;

// IMPORTANT: Use an environment variable for the secret in a real application!
const JWT_SECRET = 'your-super-secret-key-that-is-long-and-random';

// --- Database Setup ---
const db = new sqlite3.Database('./database.db', (err) => {
    if (err) {
        console.error('Error opening database', err.message);
    } else {
        console.log('Connected to the SQLite database.');
        // Create users table if it doesn't exist
        db.run(`CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            email TEXT UNIQUE,
            password TEXT
        )`, (err) => {
            if (err) {
                console.error('Error creating table', err.message);
            } else {
                console.log('Users table is ready.');
            }
        });
    }
});

// --- Routes ---
app.get('/', (req, res) => {
    res.send('Backend server is running!');
});

app.post('/register', async (req, res) => {
    const { email, password } = req.body;

    if (!email || !password) {
        return res.status(400).json({ error: 'Email and password are required' });
    }

    // Check if user already exists
    db.get('SELECT email FROM users WHERE email = ?', [email], async (err, row) => {
        if (err) {
            return res.status(500).json({ error: 'Database error' });
        }
        if (row) {
            return res.status(409).json({ error: 'User with this email already exists' });
        }

        // Hash password
        try {
            const hashedPassword = await bcrypt.hash(password, saltRounds);

            // Insert new user
            db.run('INSERT INTO users (email, password) VALUES (?, ?)', [email, hashedPassword], function(err) {
                if (err) {
                    return res.status(500).json({ error: 'Could not register user' });
                }
                res.status(201).json({ message: 'User registered successfully', userId: this.lastID });
            });
        } catch (hashError) {
            res.status(500).json({ error: 'Error hashing password' });
        }
    });
});

app.post('/login', (req, res) => {
    const { email, password } = req.body;

    if (!email || !password) {
        return res.status(400).json({ error: 'Email and password are required' });
    }

    // Find user
    db.get('SELECT * FROM users WHERE email = ?', [email], async (err, user) => {
        if (err) {
            return res.status(500).json({ error: 'Database error' });
        }
        if (!user) {
            return res.status(401).json({ error: 'Invalid credentials' });
        }

        // Compare password
        const isMatch = await bcrypt.compare(password, user.password);

        if (!isMatch) {
            return res.status(401).json({ error: 'Invalid credentials' });
        }

        // Create JWT
        const token = jwt.sign({ id: user.id, email: user.email }, JWT_SECRET, { expiresIn: '1h' });

        res.json({ message: 'Logged in successfully', token });
    });
});


// --- Server Start ---
app.listen(port, () => {
    console.log(`Server listening on http://localhost:${port}`);
});
