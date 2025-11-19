// auth.controller.js
const { dbPool } = require('../config/db');
const logger = require('../logger');

// recordLoginEvent 函数保持不变
const recordLoginEvent = async (req, res) => {
  try {
    const userId = req.user?.id || req.user?.userId;
    if (!userId) {
      logger.warn('[Login Event] Missing user identifier on request, aborting login event record.');
      return res.status(400).json({ message: '用户信息缺失，无法记录登录事件' });
    }

    const ipAddress = req.ip || null;
    const userAgent = req.headers['user-agent'] || null;

    const sql = `INSERT INTO user_sessions (user_id, login_time, last_heartbeat_time, ip_address, device_info) VALUES (?, NOW(), NOW(), ?, ?)`;
    const [result] = await dbPool.execute(sql, [userId, ipAddress, userAgent]);

    logger.info(`[Login Event] User ${userId} logged in. Session ID: ${result.insertId}`); // ADDED LOG

    res.status(201).json({ message: 'Login event recorded', sessionId: result.insertId });
  } catch (error) {
    const userId = req.user?.id || req.user?.userId;
    logger.error('Error recording login event:', { userId, error: error.message });
    res.status(500).json({ message: 'Internal server error' });
  }
};

// 记录用户登出事件 (增加了更详细的日志)
const recordLogoutEvent = async (req, res) => {
  try {
    const userId = req.user?.id || req.user?.userId;
    const { sessionId } = req.body;

    // [日志步骤 1] 记录收到的登出请求，这是所有操作的起点。
    logger.info(`[Logout Attempt] Received request for userId: '${userId}', sessionId: '${sessionId}'`);

    if (!sessionId) {
      logger.warn(`[Logout Failure] Request from userId: '${userId}' is missing the sessionId. Aborting.`);
      return res.status(400).json({ message: 'Session ID is required' });
    }

    // [日志步骤 2] 明确记录将要执行的安全检查SQL。
    logger.info(`[Logout Check] Performing security SELECT for sessionId: ${sessionId} and userId: ${userId}`);
    const [rows] = await dbPool.execute(
      `SELECT id FROM user_sessions WHERE id = ? AND user_id = ? AND is_active = TRUE AND logout_time IS NULL`,
      [sessionId, userId]
    );

    // [日志步骤 3] 报告安全检查的结果，这是最关键的一步。
    logger.info(`[Logout Check Result] Found ${rows.length} matching active session(s).`);

    if (rows.length === 0) {
      // 如果检查失败，记录下来，这是定位问题的核心线索。
      logger.warn(`[Logout Failure] Security check failed. No active session found for this combination. No update will be performed.`);
      return res.status(404).json({ message: 'Active session not found or already logged out' });
    }

    // [日志步骤 4] 确认检查通过，并准备更新数据库。
    logger.info(`[Logout Success] Security check passed. Proceeding to update session ${sessionId}.`);
    const sql = `UPDATE user_sessions SET logout_time = NOW(), is_active = FALSE WHERE id = ?`;
    const [result] = await dbPool.execute(sql, [sessionId]);

    // [日志步骤 5] 记录数据库更新操作的结果。
    logger.info(`[Logout DB Update] Database update complete. Affected rows: ${result.affectedRows}.`);

    res.status(200).json({ message: 'Logout event recorded' });
  } catch (error) {
    const userId = req.user?.id || req.user?.userId;
    logger.error('Error during recordLogoutEvent process:', { userId, sessionId, error: error.message });
    res.status(500).json({ message: 'Internal server error' });
  }
};

module.exports = {
  recordLoginEvent,
  recordLogoutEvent
};
