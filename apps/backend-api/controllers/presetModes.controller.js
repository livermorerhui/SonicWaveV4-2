const { dbPool } = require('../config/db');
const logger = require('../logger');

const STOP_REASONS = new Set(['manual', 'logout', 'countdown_complete', 'hardware_error', 'unknown']);

const normalizeStopReason = reason => {
  if (!reason) {
    return 'unknown';
  }
  const lowered = String(reason).toLowerCase();
  return STOP_REASONS.has(lowered) ? lowered : null;
};

const startPresetModeRun = async (req, res) => {
  try {
    logger.info('Received request for startPresetModeRun:', req.body);

    const {
      userId,
      userName = null,
      user_email = null,
      customer_id = null,
      customer_name = null,
      presetModeId,
      presetModeName,
      intensityScalePct = null,
      totalDurationSec = null
    } = req.body || {};

    if (!userId || !presetModeId || !presetModeName) {
      return res.status(400).json({ message: 'userId, presetModeId and presetModeName are required.' });
    }

    const startTime = new Date();
    const sql = `INSERT INTO preset_mode_runs
      (user_id, user_name, user_email, customer_id, customer_name, preset_mode_id, preset_mode_name,
       intensity_scale_pct, total_duration_sec, start_time)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`;

    const [result] = await dbPool.execute(sql, [
      userId,
      userName,
      user_email,
      customer_id,
      customer_name,
      presetModeId,
      presetModeName,
      intensityScalePct,
      totalDurationSec,
      startTime
    ]);

    res.status(201).json({
      message: 'Preset mode run started successfully.',
      runId: result.insertId
    });
  } catch (error) {
    logger.error('Error starting preset mode run:', { error: error.message });
    res.status(500).json({ error: 'Internal server error.' });
  }
};

const stopPresetModeRun = async (req, res) => {
  try {
    const { id } = req.params;
    const { reason = 'unknown', detail = null } = req.body || {};

    const normalized = normalizeStopReason(reason);
    if (!normalized) {
      return res.status(400).json({ message: 'Invalid stop reason.' });
    }

    const stopTime = new Date();
    const sql = `UPDATE preset_mode_runs
      SET stop_time = ?, stop_reason = ?, stop_detail = ?
      WHERE id = ?`;

    const [result] = await dbPool.execute(sql, [stopTime, normalized, detail, id]);

    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Preset mode run not found.' });
    }

    res.status(200).json({ message: 'Preset mode run stopped successfully.' });
  } catch (error) {
    logger.error('Error stopping preset mode run:', { error: error.message });
    res.status(500).json({ error: 'Internal server error.' });
  }
};

module.exports = {
  startPresetModeRun,
  stopPresetModeRun
};
