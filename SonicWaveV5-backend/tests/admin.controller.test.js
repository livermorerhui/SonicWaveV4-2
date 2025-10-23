const test = require('node:test');
const assert = require('node:assert/strict');
const {
  getAllUsers,
  getAllCustomers,
  updateUserRole,
  updateUserAccountType,
  deleteUser,
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
