const test = require('node:test');
const assert = require('node:assert/strict');
const {
  getAllUsers,
  getAllCustomers,
  getUserDetail,
  updateUserRole,
  updateUserAccountType,
  updateUserPassword,
  deleteUser,
  getCustomerDetail,
  updateCustomer,
  setAdminService
} = require('../controllers/admin.controller');
const realService = require('../services/admin.service');

const createMockRes = () => {
  const res = {
    statusCode: 200,
    jsonPayload: null,
    status(code) {
      this.statusCode = code;
      return this;
    },
    json(payload) {
      this.jsonPayload = payload;
      return this;
    }
  };
  return res;
};

test.afterEach(() => {
  setAdminService(realService);
});

test('admin controller returns users data envelope', async () => {
  const expected = { items: [], page: 1, pageSize: 20, total: 0 };
  setAdminService({
    getAllUsers: async () => expected,
    getAllCustomers: async () => expected
  });

  const req = { query: {} };
  const res = createMockRes();
  await getAllUsers(req, res);

  assert.equal(res.statusCode, 200);
  assert.deepEqual(res.jsonPayload, expected);
});

test('admin controller handles user fetch errors gracefully', async () => {
  setAdminService({
    getAllUsers: async () => {
      throw new Error('boom');
    },
    getAllCustomers: async () => ({ items: [], page: 1, pageSize: 20, total: 0 })
  });

  const req = { query: {} };
  const res = createMockRes();
  await getAllUsers(req, res);

  assert.equal(res.statusCode, 500);
  assert.equal(res.jsonPayload?.error?.code, 'INTERNAL_ERROR');
});

test('admin controller returns customers data envelope', async () => {
  const expected = { items: [], page: 1, pageSize: 20, total: 0 };
  setAdminService({
    getAllUsers: async () => expected,
    getAllCustomers: async () => expected
  });

  const req = { query: {} };
  const res = createMockRes();
  await getAllCustomers(req, res);

  assert.equal(res.statusCode, 200);
  assert.deepEqual(res.jsonPayload, expected);
});

test('get user detail returns payload when found', async () => {
  const expected = { id: 1, email: 'admin@example.com' };
  setAdminService({
    getUserDetail: async () => expected
  });

  const req = { params: { id: '1' } };
  const res = createMockRes();
  await getUserDetail(req, res);

  assert.equal(res.statusCode, 200);
  assert.deepEqual(res.jsonPayload, expected);
});

test('get user detail returns 404 when missing', async () => {
  setAdminService({
    getUserDetail: async () => null
  });

  const req = { params: { id: '99' } };
  const res = createMockRes();
  await getUserDetail(req, res);

  assert.equal(res.statusCode, 404);
  assert.equal(res.jsonPayload?.error?.code, 'USER_NOT_FOUND');
});

test('update user role propagates success payload', async () => {
  const expectedUser = { id: 7, role: 'admin' };
  setAdminService({
    changeUserRole: async () => expectedUser
  });

  const req = {
    params: { id: '7' },
    body: { role: 'admin' },
    user: { id: 99 },
    headers: {},
    ip: '127.0.0.1'
  };
  const res = createMockRes();
  await updateUserRole(req, res);

  assert.equal(res.statusCode, 200);
  assert.equal(res.jsonPayload?.user?.role, 'admin');
});

test('update user account type handles service errors', async () => {
  const serviceError = new Error('boom');
  serviceError.code = 'USER_NOT_FOUND';
  setAdminService({
    changeUserAccountType: async () => {
      throw serviceError;
    }
  });

  const req = {
    params: { id: '42' },
    body: { accountType: 'test' },
    user: { id: 1 },
    headers: {},
    ip: '127.0.0.1'
  };
  const res = createMockRes();
  await updateUserAccountType(req, res);

  assert.equal(res.statusCode, 404);
  assert.equal(res.jsonPayload?.error?.code, 'USER_NOT_FOUND');
});

test('update user password returns success message', async () => {
  setAdminService({
    resetUserPassword: async () => {}
  });

  const req = {
    params: { id: '5' },
    body: { password: 'Secret123!' },
    user: { id: 2 },
    headers: {},
    ip: '127.0.0.1'
  };
  const res = createMockRes();
  await updateUserPassword(req, res);

  assert.equal(res.statusCode, 200);
  assert.equal(res.jsonPayload?.message, '密码已重置');
});

test('delete user returns confirmation', async () => {
  setAdminService({
    deleteUser: async () => {}
  });

  const req = {
    params: { id: '11' },
    query: { force: false },
    user: { id: 1 },
    headers: {},
    ip: '127.0.0.1'
  };
  const res = createMockRes();
  await deleteUser(req, res);

  assert.equal(res.statusCode, 200);
  assert.equal(res.jsonPayload?.force, false);
});

test('get customer detail returns data', async () => {
  const expected = { id: 3, name: '客户A' };
  setAdminService({
    getCustomerDetail: async () => expected
  });

  const req = { params: { id: '3' } };
  const res = createMockRes();
  await getCustomerDetail(req, res);

  assert.equal(res.statusCode, 200);
  assert.deepEqual(res.jsonPayload, expected);
});

test('update customer wraps service errors', async () => {
  setAdminService({
    updateCustomer: async () => {
      throw new Error('unexpected');
    }
  });

  const req = {
    params: { id: '8' },
    body: { name: '新的名字' },
    user: { id: 1 },
    headers: {},
    ip: '127.0.0.1'
  };
  const res = createMockRes();
  await updateCustomer(req, res);

  assert.equal(res.statusCode, 500);
  assert.equal(res.jsonPayload?.error?.code, 'INTERNAL_ERROR');
});
