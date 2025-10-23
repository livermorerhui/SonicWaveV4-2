const crypto = require('crypto');
const { dbPool } = require('../config/db');
const logger = require('../logger');

const buildError = (code, message, details) => ({
  error: {
    code,
    message,
    traceId: crypto.randomUUID(),
    details
  }
});

const normalizeNullable = value => {
  if (value === undefined || value === null) {
    return null;
  }
  if (typeof value === 'string') {
    const trimmed = value.trim();
    return trimmed === '' ? null : trimmed;
  }
  return value;
};

const normalizeDate = value => {
  if (!value) return null;

  if (value instanceof Date) {
    if (Number.isNaN(value.getTime())) return null;
    const year = value.getUTCFullYear();
    const month = String(value.getUTCMonth() + 1).padStart(2, '0');
    const day = String(value.getUTCDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  if (typeof value === 'string') {
    const trimmed = value.trim();
    if (trimmed === '') return null;

    const plainMatch = trimmed.match(/^\d{4}-\d{2}-\d{2}$/);
    if (plainMatch) {
      return trimmed;
    }

    const parsed = new Date(trimmed);
    if (Number.isNaN(parsed.getTime())) {
      return null;
    }
    const year = parsed.getUTCFullYear();
    const month = String(parsed.getUTCMonth() + 1).padStart(2, '0');
    const day = String(parsed.getUTCDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  return null;
};

const createCustomer = async (req, res) => {
  const { name, dateOfBirth, gender, phone, email, height, weight } = req.body;
  const userId = req.user?.id || req.user?.userId;

  if (!userId) {
    return res.status(401).json(buildError('UNAUTHENTICATED', '需要登录才能执行该操作'));
  }

  if (!name || !email) {
    return res
      .status(400)
      .json(buildError('INVALID_INPUT', '客户姓名与邮箱为必填字段'));
  }

  logger.info('[Customer] create payload', {
    userId,
    body: req.body
  });

  const connection = await dbPool.getConnection();

  try {
    await connection.beginTransaction();

    const normalizedDate = normalizeDate(dateOfBirth);
    const normalizedGender = normalizeNullable(gender);
    const normalizedPhone = normalizeNullable(phone);
    const normalizedHeight = normalizeNullable(height);
    const normalizedWeight = normalizeNullable(weight);
    const trimmedEmail = email.trim();
    const trimmedName = name.trim();

    const [existingCustomers] = await connection.execute(
      'SELECT id FROM customers WHERE email = ? LIMIT 1',
      [trimmedEmail]
    );

    let customerId;

    if (existingCustomers.length > 0) {
      customerId = existingCustomers[0].id;

      const updateFields = [];
      const updateValues = [];

      const pushUpdate = (field, value) => {
        if (value !== undefined && value !== null && value !== '') {
          updateFields.push(`${field} = ?`);
          updateValues.push(value);
        }
      };

      pushUpdate('name', trimmedName);
      pushUpdate('date_of_birth', normalizedDate);
      pushUpdate('gender', normalizedGender);
      pushUpdate('phone', normalizedPhone);
      pushUpdate('height', normalizedHeight);
      pushUpdate('weight', normalizedWeight);

      if (updateFields.length > 0) {
        await connection.execute(
          `UPDATE customers SET ${updateFields.join(', ')} WHERE id = ?`,
          [...updateValues, customerId]
        );
      }
    } else {
      const [insertResult] = await connection.execute(
        'INSERT INTO customers (name, date_of_birth, gender, phone, email, height, weight) VALUES (?, ?, ?, ?, ?, ?, ?)',
        [
          trimmedName,
          normalizedDate,
          normalizedGender,
          normalizedPhone,
          trimmedEmail,
          normalizedHeight,
          normalizedWeight
        ]
      );
      customerId = insertResult.insertId;
    }

    try {
      await connection.execute(
        'INSERT INTO user_customers (user_id, customer_id) VALUES (?, ?)',
        [userId, customerId]
      );
    } catch (relationError) {
      if (relationError.code === 'ER_DUP_ENTRY') {
        await connection.rollback();
        return res
          .status(409)
          .json(buildError('CUSTOMER_EXISTS', '当前账号下已存在该邮箱的客户'));
      }
      throw relationError;
    }

    await connection.commit();

    res
      .status(existingCustomers.length > 0 ? 200 : 201)
      .json({ id: customerId, message: existingCustomers.length > 0 ? '客户已关联' : '客户创建成功' });
  } catch (error) {
    await connection.rollback();
    logger.error('Error creating customer', { error: error.message });
    res.status(500).json(buildError('INTERNAL_ERROR', '创建客户时出现内部错误'));
  } finally {
    connection.release();
  }
};

const getCustomers = async (req, res) => {
  try {
    const userId = req.user?.id || req.user?.userId;
    if (!userId) {
      return res.status(401).json(buildError('UNAUTHENTICATED', '需要登录才能访问客户数据'));
    }

    const sql = `
      SELECT
        c.id,
        c.name,
        DATE_FORMAT(c.date_of_birth, '%Y-%m-%d') AS dateOfBirth,
        c.gender,
        c.phone,
        c.email,
        c.height,
        c.weight
      FROM customers c
      INNER JOIN user_customers uc ON uc.customer_id = c.id
      WHERE uc.user_id = ?
    `;
    const [rows] = await dbPool.execute(sql, [userId]);

    res.status(200).json(rows);
  } catch (error) {
    logger.error('Error fetching customers', { error: error.message });
    res.status(500).json(buildError('INTERNAL_ERROR', '获取客户数据失败'));
  }
};

const updateCustomer = async (req, res) => {
  const { customerId } = req.params;
  const userId = req.user?.id || req.user?.userId;
  const { name, dateOfBirth, gender, phone, email, height, weight } = req.body;

  try {
    if (!userId) {
      return res.status(401).json(buildError('UNAUTHENTICATED', '需要登录才能更新客户信息'));
    }

    const [ownership] = await dbPool.execute(
      `SELECT 1 FROM customers c INNER JOIN user_customers uc ON uc.customer_id = c.id WHERE c.id = ? AND uc.user_id = ? LIMIT 1`,
      [customerId, userId]
    );

    if (ownership.length === 0) {
      return res
        .status(404)
        .json(buildError('CUSTOMER_NOT_FOUND', '未找到客户信息或该客户未与当前账号关联'));
    }

    const updateFields = [];
    const updateValues = [];

    const pushUpdate = (field, value, normalizer = v => v) => {
      if (value !== undefined) {
        updateFields.push(`${field} = ?`);
        updateValues.push(normalizer(value));
      }
    };

    pushUpdate('name', name, val => (val ? val.trim() : val));
    pushUpdate('date_of_birth', dateOfBirth, normalizeDate);
    pushUpdate('gender', gender, normalizeNullable);
    pushUpdate('phone', phone, normalizeNullable);
    pushUpdate('email', email, value => {
      if (value === undefined || value === null) return value;
      const trimmed = value.trim();
      return trimmed === '' ? null : trimmed;
    });
    pushUpdate('height', height, normalizeNullable);
    pushUpdate('weight', weight, normalizeNullable);

    if (updateFields.length === 0) {
      return res.status(400).json(buildError('INVALID_INPUT', '请提供需要更新的字段'));
    }

    const sql = `UPDATE customers SET ${updateFields.join(', ')} WHERE id = ?`;
    await dbPool.execute(sql, [...updateValues, customerId]);

    res.status(200).json({ message: '客户信息更新成功' });
  } catch (error) {
    if (error.code === 'ER_DUP_ENTRY') {
      return res.status(409).json(buildError('CUSTOMER_EXISTS', '该邮箱已存在于其它客户记录中'));
    }
    logger.error('Error updating customer', { error: error.message });
    res.status(500).json(buildError('INTERNAL_ERROR', '更新客户信息时出现内部错误'));
  }
};

module.exports = {
  createCustomer,
  getCustomers,
  updateCustomer
};
