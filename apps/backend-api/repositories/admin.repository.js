const { dbPool } = require('../config/db');
const { hasColumn } = require('../utils/schema');

const DEFAULT_PAGE = 1;
const DEFAULT_PAGE_SIZE = 20;
const MAX_PAGE_SIZE = 200;

const USER_SORT_MAP = {
  createdAt: 'created_at',
  email: 'email',
  username: 'username',
  role: 'role'
};

const CUSTOMER_SORT_MAP = {
  createdAt: 'created_at',
  name: 'name',
  email: 'email'
};

const parsePagination = ({ page, pageSize }) => {
  const parsedPage = Number.parseInt(page, 10);
  const parsedPageSize = Number.parseInt(pageSize, 10);

  const safePage = Number.isFinite(parsedPage) && parsedPage > 0 ? parsedPage : DEFAULT_PAGE;
  const basePageSize = Number.isFinite(parsedPageSize) && parsedPageSize > 0 ? parsedPageSize : DEFAULT_PAGE_SIZE;
  const safePageSize = Math.min(basePageSize, MAX_PAGE_SIZE);

  return {
    page: safePage,
    pageSize: safePageSize,
    offset: (safePage - 1) * safePageSize
  };
};

const normalizeSort = (requestedSortBy, sortMap, defaultKey = 'createdAt') => {
  const sortKey = sortMap[requestedSortBy] ? requestedSortBy : defaultKey;
  return sortMap[sortKey];
};

const normalizeOrder = (value, defaultOrder = 'DESC') => {
  if (typeof value !== 'string') return defaultOrder;
  const normalized = value.toUpperCase();
  if (normalized === 'ASC' || normalized === 'DESC') {
    return normalized;
  }
  return defaultOrder;
};

const mapUserRow = row => ({
  id: row.id,
  email: row.email,
  username: row.username,
  role: row.role,
  accountType: row.account_type,
  createdAt: row.created_at,
  updatedAt: row.updated_at,
  deletedAt: row.deleted_at ?? null
});

const numericOrNull = value => (value === null || value === undefined ? null : Number(value));

const mapCustomerRow = row => ({
  id: row.id,
  name: row.name,
  email: row.email,
  phone: row.phone,
  gender: row.gender,
  dateOfBirth: row.date_of_birth,
  height: numericOrNull(row.height),
  weight: numericOrNull(row.weight),
  createdAt: row.created_at,
  updatedAt: row.updated_at
});

async function listUsers({ page, pageSize, keyword, role, accountType, sortBy, sortOrder }) {
  const { page: safePage, pageSize: safePageSize, offset } = parsePagination({ page, pageSize });
  const filters = [];
  const params = [];

  if (keyword) {
    filters.push('(username LIKE ? OR email LIKE ?)');
    const like = `%${keyword}%`;
    params.push(like, like);
  }

  const hasRoleColumn = await hasColumn('users', 'role');
  const hasAccountTypeColumn = await hasColumn('users', 'account_type');
  const hasUpdatedAt = await hasColumn('users', 'updated_at');
  const hasDeletedAt = await hasColumn('users', 'deleted_at');

  if (hasRoleColumn && role && role !== 'all') {
    filters.push('role = ?');
    params.push(role);
  }

  if (hasAccountTypeColumn && accountType && accountType !== 'all') {
    filters.push('account_type = ?');
    params.push(accountType);
  }

  const whereClause = filters.length ? `WHERE ${filters.join(' AND ')}` : '';
  const requestedSortColumn = normalizeSort(sortBy, USER_SORT_MAP);
  const sortColumn = requestedSortColumn === 'role' && !hasRoleColumn ? 'created_at' : requestedSortColumn;
  const order = normalizeOrder(sortOrder);

  const projections = [
    'id',
    'username',
    'email',
    hasRoleColumn ? 'role' : "'user' AS role",
    hasAccountTypeColumn ? 'account_type' : "'normal' AS account_type",
    'created_at'
  ];

  if (hasUpdatedAt) {
    projections.push('updated_at');
  } else {
    projections.push('created_at AS updated_at');
  }

  if (hasDeletedAt) {
    projections.push('deleted_at');
  } else {
    projections.push('NULL AS deleted_at');
  }

  const limit = Math.max(1, Math.trunc(safePageSize));
  const offsetValue = Math.max(0, Math.trunc(offset));

  const dataSql = `
    SELECT ${projections.join(', ')}
    FROM users
    ${whereClause}
    ORDER BY ${sortColumn} ${order}
    LIMIT ${limit}
    OFFSET ${offsetValue}
  `;

  const totalSql = `
    SELECT COUNT(*) AS total
    FROM users
    ${whereClause}
  `;

  const [rows] = await dbPool.execute(dataSql, params);
  const [countRows] = await dbPool.execute(totalSql, params);

  return {
    items: rows.map(mapUserRow),
    page: safePage,
    pageSize: safePageSize,
    total: countRows[0]?.total ?? 0
  };
}

async function listCustomers({ page, pageSize, keyword, sortBy, sortOrder }) {
  const { page: safePage, pageSize: safePageSize, offset } = parsePagination({ page, pageSize });
  const filters = [];
  const params = [];

  if (keyword) {
    filters.push('(name LIKE ? OR email LIKE ? OR phone LIKE ?)');
    const like = `%${keyword}%`;
    params.push(like, like, like);
  }

  const whereClause = filters.length ? `WHERE ${filters.join(' AND ')}` : '';
  const sortColumn = normalizeSort(sortBy, CUSTOMER_SORT_MAP);
  const order = normalizeOrder(sortOrder);

  const limit = Math.max(1, Math.trunc(safePageSize));
  const offsetValue = Math.max(0, Math.trunc(offset));

  const dataSql = `
    SELECT id, name, email, phone, gender, date_of_birth, height, weight, created_at, updated_at
    FROM customers
    ${whereClause}
    ORDER BY ${sortColumn} ${order}
    LIMIT ${limit}
    OFFSET ${offsetValue}
  `;

  const totalSql = `
    SELECT COUNT(*) AS total
    FROM customers
    ${whereClause}
  `;

  const [rows] = await dbPool.execute(dataSql, params);
  const [countRows] = await dbPool.execute(totalSql, params);

  return {
    items: rows.map(mapCustomerRow),
    page: safePage,
    pageSize: safePageSize,
    total: countRows[0]?.total ?? 0
  };
}

async function findUserDetailedById(id) {
  const hasRoleColumn = await hasColumn('users', 'role');
  const hasAccountTypeColumn = await hasColumn('users', 'account_type');
  const hasDeletedAt = await hasColumn('users', 'deleted_at');
  const hasUpdatedAt = await hasColumn('users', 'updated_at');

  const projections = [
    'id',
    'username',
    'email',
    hasRoleColumn ? 'role' : "'user' AS role",
    hasAccountTypeColumn ? 'account_type' : "'normal' AS account_type",
    'password',
    'created_at'
  ];

  projections.push(hasUpdatedAt ? 'updated_at' : 'created_at AS updated_at');
  projections.push(hasDeletedAt ? 'deleted_at' : 'NULL AS deleted_at');

  const sql = `
    SELECT ${projections.join(', ')}
    FROM users
    WHERE id = ?
    LIMIT 1
  `;

  const [rows] = await dbPool.execute(sql, [id]);
  if (!rows.length) {
    return null;
  }

  const row = rows[0];
  return {
    id: row.id,
    username: row.username,
    email: row.email,
    role: row.role,
    accountType: row.account_type,
    passwordHash: row.password,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    deletedAt: row.deleted_at ?? null
  };
}

async function updateUserPasswordInternal(id, passwordHash) {
  const hasUpdatedAt = await hasColumn('users', 'updated_at');
  const sql = hasUpdatedAt
    ? `
      UPDATE users
      SET password = ?, updated_at = CURRENT_TIMESTAMP
      WHERE id = ?
    `
    : `
      UPDATE users
      SET password = ?
      WHERE id = ?
    `;
  const [result] = await dbPool.execute(sql, [passwordHash, id]);
  return result.affectedRows;
}

async function findCustomerById(id) {
  const sql = `
    SELECT
      id,
      name,
      DATE_FORMAT(date_of_birth, '%Y-%m-%d') AS dateOfBirth,
      gender,
      phone,
      email,
      height,
      weight,
      created_at,
      updated_at
    FROM customers
    WHERE id = ?
    LIMIT 1
  `;

  const [rows] = await dbPool.execute(sql, [id]);
  if (!rows.length) {
    return null;
  }
  const row = rows[0];
  return {
    id: row.id,
    name: row.name,
    dateOfBirth: row.dateOfBirth,
    gender: row.gender,
    phone: row.phone,
    email: row.email,
    height: numericOrNull(row.height),
    weight: numericOrNull(row.weight),
    createdAt: row.created_at,
    updatedAt: row.updated_at
  };
}

async function updateCustomerById(id, payload) {
  const fields = [];
  const values = [];

  const push = (column, value) => {
    if (value !== undefined) {
      fields.push(`${column} = ?`);
      values.push(value);
    }
  };

  push('name', payload.name);
  push('date_of_birth', payload.dateOfBirth);
  push('gender', payload.gender);
  push('phone', payload.phone);
  push('email', payload.email);
  push('height', payload.height);
  push('weight', payload.weight);

  if (!fields.length) {
    return 0;
  }

  const sql = `
    UPDATE customers
    SET ${fields.join(', ')}
    WHERE id = ?
  `;

  const [result] = await dbPool.execute(sql, [...values, id]);
  return result.affectedRows;
}
async function findUserById(id) {
  const hasAccountTypeColumn = await hasColumn('users', 'account_type');
  const hasRoleColumn = await hasColumn('users', 'role');
  const hasDeletedAt = await hasColumn('users', 'deleted_at');
  const hasUpdatedAt = await hasColumn('users', 'updated_at');

  const projections = [
    'id',
    'username',
    'email',
    hasRoleColumn ? 'role' : "'user' AS role",
    hasAccountTypeColumn ? 'account_type' : "'normal' AS account_type",
    'created_at'
  ];

  projections.push(hasUpdatedAt ? 'updated_at' : 'created_at AS updated_at');
  projections.push(hasDeletedAt ? 'deleted_at' : 'NULL AS deleted_at');

  const sql = `
    SELECT ${projections.join(', ')}
    FROM users
    WHERE id = ?
    LIMIT 1
  `;

  const [rows] = await dbPool.execute(sql, [id]);
  return rows[0] ? mapUserRow(rows[0]) : null;
}

async function updateUserRole(id, role) {
  const sql = `
    UPDATE users
    SET role = ?, updated_at = CURRENT_TIMESTAMP
    WHERE id = ?
  `;
  const [result] = await dbPool.execute(sql, [role, id]);
  return result.affectedRows;
}

async function updateUserAccountType(id, accountType) {
  const sql = `
    UPDATE users
    SET account_type = ?, updated_at = CURRENT_TIMESTAMP
    WHERE id = ?
  `;
  const [result] = await dbPool.execute(sql, [accountType, id]);
  return result.affectedRows;
}

async function softDeleteUser(id) {
  const sql = `
    UPDATE users
    SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
    WHERE id = ?
  `;
  const [result] = await dbPool.execute(sql, [id]);
  return result.affectedRows;
}

async function hardDeleteUser(id) {
  const sql = `
    DELETE FROM users
    WHERE id = ?
  `;
  const [result] = await dbPool.execute(sql, [id]);
  return result.affectedRows;
}

module.exports = {
  listUsers,
  listCustomers,
  findUserById,
  findUserDetailedById,
  updateUserRole,
  updateUserAccountType,
  softDeleteUser,
  hardDeleteUser,
  updateUserPasswordInternal,
  findCustomerById,
  updateCustomerById
};
