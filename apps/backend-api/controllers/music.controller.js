const fs = require('fs');
const path = require('path');
const { dbPool } = require('../config/db');
const logger = require('../logger');

// 获取音乐列表
const getMusicList = async (req, res) => {
  try {
    const { categoryId } = req.query;

    let sql = `
      SELECT
        t.id,
        t.title,
        t.artist,
        t.file_key,
        t.category_id,
        t.created_at,
        c.name AS category_name
      FROM music_tracks t
      LEFT JOIN music_categories c
        ON t.category_id = c.id
    `;
    const params = [];

    if (categoryId !== undefined && categoryId !== null && categoryId !== '') {
      const parsed = Number(categoryId);
      if (Number.isNaN(parsed) || !Number.isInteger(parsed) || parsed <= 0) {
        return res.status(400).json({ message: 'Invalid categoryId' });
      }
      sql += ' WHERE t.category_id = ?';
      params.push(parsed);
    }

    sql += ' ORDER BY t.created_at DESC, t.id DESC';

    const [rows] = await dbPool.execute(sql, params);

    const tracks = rows.map(row => ({
      // Core fields
      id: row.id,
      title: row.title,
      artist: row.artist,

      // Legacy fields for backward compatibility
      file_key: row.file_key,
      category_id: row.category_id ?? null,

      // New fields for admin-web
      fileKey: row.file_key,
      categoryId: row.category_id ?? null,
      categoryName: row.category_name || null,
      createdAt: row.created_at || null
    }));

    return res.status(200).json({
      message: 'Successfully fetched music list.',
      tracks
    });
  } catch (error) {
    logger.error('Error fetching music list:', { error: error.message });
    return res.status(500).json({ message: 'Internal server error.' });
  }
};

// 上传音乐文件
const uploadMusic = async (req, res) => {
  try {
    const userId = req.user?.id || req.user?.userId;
    const { title, artist } = req.body;
    const file = req.file;

    if (!userId) {
      return res.status(401).json({ message: 'Unauthenticated: user id not found in token.' });
    }

    if (!file) {
      return res.status(400).json({ message: 'No music file uploaded.' });
    }
    if (!title) {
      return res.status(400).json({ message: 'Music title is required.' });
    }

    // file.path 是由 multer.diskStorage 生成的相对路径（例如 "uploads/musicFile-xxx.mp3"）
    const file_key = file.path;

    logger.info(`File received and stored at: ${file_key}, size: ${file.size}`);

    const rawCategoryId = req.body.categoryId;
    let categoryId = null;

    if (rawCategoryId !== undefined && rawCategoryId !== null && rawCategoryId !== '') {
      const parsed = Number(rawCategoryId);
      if (!Number.isNaN(parsed) && Number.isInteger(parsed) && parsed > 0) {
        categoryId = parsed;
      }
    }

    const sql = 'INSERT INTO music_tracks (title, artist, file_key, uploader_id, category_id) VALUES (?, ?, ?, ?, ?)';
    const [result] = await dbPool.execute(sql, [title, artist || 'Unknown Artist', file_key, userId, categoryId]);

    res.status(201).json({
      message: 'File uploaded and metadata saved successfully!',
      trackId: result.insertId,
      file_key: file_key,
      categoryId
    });

  } catch (error) {
    logger.error('Error during file upload:', { error: error.message });
    res.status(500).json({ message: 'Internal server error.' });
  }
};

// --- Music categories & per-track category management ---

/**
 * GET /api/v1/music/categories
 * Returns all music categories for admin-web.
 */
const getMusicCategories = async (req, res) => {
  try {
    const [rows] = await dbPool.query('SELECT * FROM music_categories ORDER BY id ASC');

    const categories = rows.map(row => ({
      id: row.id,
      name: row.name,
      createdAt: row.created_at || null,
      updatedAt: row.updated_at || null
    }));

    res.json({
      message: 'Successfully fetched music categories.',
      categories
    });
  } catch (error) {
    logger.error('Error fetching music categories:', { error: error.message });
    res.status(500).json({ message: 'Internal server error.' });
  }
};

/**
 * POST /api/v1/music/categories
 * Body: { name: string }
 */
const createMusicCategory = async (req, res) => {
  try {
    const rawName = req.body && req.body.name;
    const name = typeof rawName === 'string' ? rawName.trim() : '';

    if (!name) {
      return res.status(400).json({ message: 'Category name is required.' });
    }

    // 1) Generate a code from the name (simple slug)
    let code = name
      .toLowerCase()
      .replace(/\s+/g, '_')
      .replace(/[^a-z0-9_]/g, '')
      .slice(0, 32);

    if (!code) {
      code = `cat_${Date.now()}`;
    }

    // 2) Compute next sort_order = current max + 1
    const [maxRows] = await dbPool.query(
      'SELECT COALESCE(MAX(sort_order), 0) AS maxSort FROM music_categories'
    );
    const nextSortOrder = (maxRows[0]?.maxSort ?? 0) + 1;

    // 3) Insert with all required fields to avoid NOT NULL errors
    const insertSql =
      'INSERT INTO music_categories (code, name, sort_order, is_active) VALUES (?, ?, ?, 1)';
    const [result] = await dbPool.execute(insertSql, [code, name, nextSortOrder]);

    // 4) Fetch the inserted row
    const [rows] = await dbPool.query('SELECT * FROM music_categories WHERE id = ?', [
      result.insertId
    ]);
    const row = rows[0];

    const category = {
      id: row.id,
      name: row.name,
      createdAt: row.created_at || null,
      updatedAt: row.updated_at || null
    };

    return res.status(201).json({
      message: 'Category created successfully.',
      category
    });
  } catch (error) {
    logger.error('Error creating music category:', { error: error.message });
    return res.status(500).json({ message: 'Internal server error.' });
  }
};

/**
 * PATCH /api/v1/music/categories/:id
 * Body: { name: string }
 */
const updateMusicCategory = async (req, res) => {
  try {
    const id = Number.parseInt(req.params.id, 10);
    if (!Number.isInteger(id) || id <= 0) {
      return res.status(400).json({ message: 'Invalid category id.' });
    }

    const rawName = req.body && req.body.name;
    const name = typeof rawName === 'string' ? rawName.trim() : '';

    if (!name) {
      return res.status(400).json({ message: 'Category name is required.' });
    }

    const [result] = await dbPool.execute(
      'UPDATE music_categories SET name = ? WHERE id = ?',
      [name, id]
    );

    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Category not found.' });
    }

    const [rows] = await dbPool.query('SELECT * FROM music_categories WHERE id = ?', [id]);
    const row = rows[0];

    const category = {
      id: row.id,
      name: row.name,
      createdAt: row.created_at || null,
      updatedAt: row.updated_at || null
    };

    res.json({
      message: 'Category updated successfully.',
      category
    });
  } catch (error) {
    logger.error('Error updating music category:', { error: error.message });
    res.status(500).json({ message: 'Internal server error.' });
  }
};

/**
 * DELETE /api/v1/music/categories/:id
 * - Set category_id = NULL for all tracks with this category
 * - Then delete the category
 */
const deleteMusicCategory = async (req, res) => {
  try {
    const id = Number.parseInt(req.params.id, 10);
    if (!Number.isInteger(id) || id <= 0) {
      return res.status(400).json({ message: 'Invalid category id.' });
    }

    // First, detach tracks from this category
    await dbPool.execute(
      'UPDATE music_tracks SET category_id = NULL WHERE category_id = ?',
      [id]
    );

    // Then delete category
    const [result] = await dbPool.execute(
      'DELETE FROM music_categories WHERE id = ?',
      [id]
    );

    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Category not found.' });
    }

    res.json({
      message: 'Category deleted successfully.'
    });
  } catch (error) {
    logger.error('Error deleting music category:', { error: error.message });
    res.status(500).json({ message: 'Internal server error.' });
  }
};

/**
 * PATCH /api/v1/music/:id/category
 * Body: { categoryId: number | null }
 */
const updateMusicTrackCategory = async (req, res) => {
  try {
    const trackId = Number.parseInt(req.params.id, 10);
    if (!Number.isInteger(trackId) || trackId <= 0) {
      return res.status(400).json({ message: 'Invalid track id.' });
    }

    const rawCategoryId = req.body && req.body.categoryId;
    let categoryId = null;

    if (rawCategoryId !== null && rawCategoryId !== undefined && rawCategoryId !== '') {
      const parsed = Number.parseInt(rawCategoryId, 10);
      if (!Number.isInteger(parsed) || parsed <= 0) {
        return res.status(400).json({ message: 'Invalid categoryId.' });
      }
      categoryId = parsed;

      // Optional: validate category exists
      const [catRows] = await dbPool.query(
        'SELECT id FROM music_categories WHERE id = ?',
        [categoryId]
      );
      if (catRows.length === 0) {
        return res.status(404).json({ message: 'Category not found.' });
      }
    }

    const [result] = await dbPool.execute(
      'UPDATE music_tracks SET category_id = ? WHERE id = ?',
      [categoryId, trackId]
    );

    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Track not found.' });
    }

    const [rows] = await dbPool.query(
      'SELECT * FROM music_tracks WHERE id = ?',
      [trackId]
    );
    const row = rows[0];

    const track = {
      id: row.id,
      title: row.title,
      artist: row.artist,

      // Legacy fields
      file_key: row.file_key,
      category_id: row.category_id ?? null,

      // New fields
      fileKey: row.file_key,
      categoryId: row.category_id ?? null,
      createdAt: row.created_at || null
    };

    return res.json({
      message: 'Track category updated successfully.',
      track
    });
  } catch (error) {
    logger.error('Error updating track category:', { error: error.message });
    res.status(500).json({ message: 'Internal server error.' });
  }
};

/**
 * PATCH /api/v1/music/:id
 * Body: { title?: string; artist?: string }
 * Updates track metadata such as title and artist.
 */
const updateMusicTrackMetadata = async (req, res) => {
  try {
    const trackId = Number.parseInt(req.params.id, 10);
    if (!Number.isInteger(trackId) || trackId <= 0) {
      return res.status(400).json({ message: 'Invalid track id.' });
    }

    const { title, artist } = req.body || {};

    let normalizedTitle =
      typeof title === 'string' ? title.trim() : undefined;
    let normalizedArtist =
      typeof artist === 'string' ? artist.trim() : undefined;

    if (!normalizedTitle && !normalizedArtist) {
      return res
        .status(400)
        .json({ message: 'Title or artist is required.' });
    }

    const fields = [];
    const params = [];

    if (normalizedTitle) {
      fields.push('title = ?');
      params.push(normalizedTitle);
    }

    if (normalizedArtist) {
      fields.push('artist = ?');
      params.push(normalizedArtist);
    }

    if (fields.length === 0) {
      return res
        .status(400)
        .json({ message: 'Nothing to update.' });
    }

    params.push(trackId);

    const sql = `UPDATE music_tracks SET ${fields.join(', ')} WHERE id = ?`;
    const [result] = await dbPool.execute(sql, params);

    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Track not found.' });
    }

    const [rows] = await dbPool.query(
      'SELECT t.id, t.title, t.artist, t.file_key, t.category_id, t.created_at, c.name AS category_name FROM music_tracks t LEFT JOIN music_categories c ON t.category_id = c.id WHERE t.id = ?',
      [trackId]
    );

    if (rows.length === 0) {
      return res.status(404).json({ message: 'Track not found.' });
    }

    const row = rows[0];

    const track = {
      id: row.id,
      title: row.title,
      artist: row.artist,
      // legacy fields
      file_key: row.file_key,
      category_id: row.category_id ?? null,
      // new fields
      fileKey: row.file_key,
      categoryId: row.category_id ?? null,
      categoryName: row.category_name || null,
      createdAt: row.created_at || null
    };

    return res.json({
      message: 'Track updated successfully.',
      track
    });
  } catch (error) {
    logger.error('Error updating music track metadata:', {
      error: error.message
    });
    return res.status(500).json({ message: 'Internal server error.' });
  }
};

/**
 * DELETE /api/v1/music/:id
 * Deletes a music track and (best-effort) its underlying file.
 */
const deleteMusicTrack = async (req, res) => {
  try {
    const trackId = Number.parseInt(req.params.id, 10);
    if (!Number.isInteger(trackId) || trackId <= 0) {
      return res.status(400).json({ message: 'Invalid track id.' });
    }

    // 1) Fetch track to get file_key
    const [rows] = await dbPool.query(
      'SELECT file_key FROM music_tracks WHERE id = ?',
      [trackId]
    );

    if (rows.length === 0) {
      return res.status(404).json({ message: 'Track not found.' });
    }

    const fileKey = rows[0].file_key;

    // 2) Delete DB record
    const [result] = await dbPool.execute(
      'DELETE FROM music_tracks WHERE id = ?',
      [trackId]
    );

    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Track not found.' });
    }

    // 3) Best-effort removal of the file on disk
    if (fileKey) {
      try {
        const absolutePath = path.isAbsolute(fileKey)
          ? fileKey
          : path.join(process.cwd(), fileKey);

        fs.unlink(absolutePath, err => {
          if (err) {
            logger.warn('Failed to delete music file on disk', {
              fileKey,
              absolutePath,
              error: err.message
            });
          } else {
            logger.info('Deleted music file on disk', { fileKey, absolutePath });
          }
        });
      } catch (err) {
        logger.warn('Error while resolving music file path for deletion', {
          fileKey,
          error: err.message
        });
      }
    }

    return res.json({
      message: 'Track deleted successfully.'
    });
  } catch (error) {
    logger.error('Error deleting music track:', { error: error.message });
    return res.status(500).json({ message: 'Internal server error.' });
  }
};

module.exports = {
  getMusicList,
  uploadMusic,
  getMusicCategories,
  createMusicCategory,
  updateMusicCategory,
  deleteMusicCategory,
  updateMusicTrackCategory,
  deleteMusicTrack,
  updateMusicTrackMetadata
};
