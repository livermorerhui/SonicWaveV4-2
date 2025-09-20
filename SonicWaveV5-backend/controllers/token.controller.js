const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const SHA256 = require('crypto-js/sha256');
const { dbPool } = require('../config/db');
const logger = require('../logger');

// This helper function is duplicated from users.controller.js for now.
// In a larger refactor, it should be moved to a shared utility file.
const generateAndStoreRefreshToken = async (userId, dbPool) => {
    const refreshToken = crypto.randomBytes(64).toString('hex');
    const hashedToken = SHA256(refreshToken).toString();
    const expiresInDays = 7;
    const expiresAt = new Date();
    expiresAt.setDate(expiresAt.getDate() + expiresInDays);

    const sql = 'INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES (?, ?, ?)';
    await dbPool.execute(sql, [userId, hashedToken, expiresAt]);

    return refreshToken;
};

const refreshToken = async (req, res) => {
    const { refreshToken: providedToken } = req.body;
    if (!providedToken) {
        return res.status(400).json({ message: 'Refresh token is required.' });
    }

    try {
        const hashedToken = SHA256(providedToken).toString();
        const findTokenSql = 'SELECT * FROM refresh_tokens WHERE token_hash = ?';
        const [tokens] = await dbPool.execute(findTokenSql, [hashedToken]);

        if (tokens.length === 0) {
            logger.warn('Invalid refresh token presented.', { tokenHash: hashedToken });
            return res.status(401).json({ message: 'Invalid refresh token.' });
        }

        const storedToken = tokens[0];

        if (new Date(storedToken.expires_at) < new Date()) {
            logger.warn('Expired refresh token presented.', { tokenId: storedToken.id });
            // Clean up expired token
            await dbPool.execute('DELETE FROM refresh_tokens WHERE id = ?', [storedToken.id]);
            return res.status(401).json({ message: 'Refresh token expired.' });
        }

        // --- Token Rotation --- 
        // 1. Delete the used refresh token
        await dbPool.execute('DELETE FROM refresh_tokens WHERE id = ?', [storedToken.id]);

        // 2. Issue a new access token
        const userSql = 'SELECT id, username FROM users WHERE id = ?';
        const [users] = await dbPool.execute(userSql, [storedToken.user_id]);
        if (users.length === 0) {
            return res.status(401).json({ message: 'User not found for this token.' });
        }
        const user = users[0];

        const accessTokenPayload = { userId: user.id, username: user.username };
        const secret = process.env.JWT_SECRET;
        const accessToken = jwt.sign(accessTokenPayload, secret, { expiresIn: '15m' });

        // 3. Issue a new refresh token
        const newRefreshToken = await generateAndStoreRefreshToken(user.id, dbPool);

        res.json({
            message: 'Tokens refreshed successfully!',
            accessToken: accessToken,
            refreshToken: newRefreshToken
        });

    } catch (error) {
        logger.error('Error during token refresh:', { error: error.message });
        res.status(500).json({ message: 'Internal server error.' });
    }
};

const logout = async (req, res) => {
    const { refreshToken: providedToken } = req.body;
    if (!providedToken) {
        return res.status(400).json({ message: 'Refresh token is required.' });
    }

    try {
        const hashedToken = SHA256(providedToken).toString();
        const deleteTokenSql = 'DELETE FROM refresh_tokens WHERE token_hash = ?';
        const [result] = await dbPool.execute(deleteTokenSql, [hashedToken]);

        if (result.affectedRows > 0) {
            logger.info('User logged out successfully by revoking refresh token.');
        }
        
        // Always return 204, regardless of whether the token was found, to not leak information.
        res.status(204).send();

    } catch (error) {
        logger.error('Error during logout:', { error: error.message });
        res.status(500).json({ message: 'Internal server error.' });
    }
};

module.exports = { refreshToken, logout };
