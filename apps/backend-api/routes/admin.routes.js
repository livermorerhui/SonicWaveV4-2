const router = require('express').Router();
const { query, body, param } = require('express-validator');
const adminController = require('../controllers/admin.controller');
const authenticateToken = require('../middleware/auth');
const adminAuth = require('../middleware/adminAuth');
const validate = require('../middleware/validate');

const paginationValidators = [
  query('page').optional().isInt({ min: 1 }).withMessage('page 必须为正整数'),
  query('pageSize')
    .optional()
    .isInt({ min: 1, max: 200 })
    .withMessage('pageSize 范围需在 1~200 之间'),
  query('keyword').optional().trim().isLength({ max: 100 }).withMessage('keyword 最长 100 个字符')
];

const usersFilterValidators = [
  query('role').optional().isIn(['user', 'admin', 'all']).withMessage('role 仅支持 user/admin/all'),
  query('accountType')
    .optional()
    .isIn(['normal', 'test', 'all'])
    .withMessage('accountType 仅支持 normal/test/all'),
  query('sortBy')
    .optional()
    .isIn(['createdAt', 'email', 'username', 'role'])
    .withMessage('sortBy 非法'),
  query('sortOrder')
    .optional()
    .isIn(['asc', 'ASC', 'desc', 'DESC'])
    .withMessage('sortOrder 仅支持 asc/desc')
];

const customersFilterValidators = [
  query('sortBy').optional().isIn(['createdAt', 'name', 'email']).withMessage('sortBy 非法'),
  query('sortOrder')
    .optional()
    .isIn(['asc', 'ASC', 'desc', 'DESC'])
    .withMessage('sortOrder 仅支持 asc/desc')
];

const deviceFilterValidators = [
  query('offlineAllowed')
    .optional()
    .isBoolean()
    .withMessage('offlineAllowed 需为布尔值')
    .toBoolean(),
  query('onlyOnline').optional().isBoolean().withMessage('onlyOnline 需为布尔值').toBoolean(),
  query('onlineWindowSeconds')
    .optional()
    .isInt({ min: 10, max: 3600 })
    .withMessage('onlineWindowSeconds 范围需在 10~3600')
    .toInt(),
  query('keyword').optional().trim().isLength({ max: 191 }).withMessage('keyword 最长 191 个字符')
];

router.use(authenticateToken);
router.use(adminAuth);

/**
 * @swagger
 * /api/admin/users:
 *   get:
 *     summary: 管理员获取用户列表
 *     tags: [Admin]
 *     parameters:
 *       - in: query
 *         name: page
 *         schema:
 *           type: integer
 *           default: 1
 *       - in: query
 *         name: pageSize
 *         schema:
 *           type: integer
 *           default: 20
 *       - in: query
 *         name: keyword
 *         schema:
 *           type: string
 *           description: 支持邮箱或用户名模糊搜索
 *       - in: query
 *         name: role
 *         schema:
 *           type: string
 *           enum: [user, admin, all]
 *       - in: query
 *         name: accountType
 *         schema:
 *           type: string
 *           enum: [normal, test, all]
 *       - in: query
 *         name: sortBy
 *         schema:
 *           type: string
 *           enum: [createdAt, email, username, role]
 *       - in: query
 *         name: sortOrder
 *         schema:
 *           type: string
 *           enum: [asc, desc]
 *     responses:
 *       200:
 *         description: 用户分页数据
 *       403:
 *         description: 未授权的访问
 */
router.get('/users', [...paginationValidators, ...usersFilterValidators], validate, adminController.getAllUsers);

router.get(
  '/users/:id',
  [param('id').isInt({ min: 1 }).withMessage('id 必须为正整数')],
  validate,
  adminController.getUserDetail
);

/**
 * @swagger
 * /api/admin/users/{id}/role:
 *   patch:
 *     summary: 更新用户角色
 *     tags: [Admin]
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             properties:
 *               role:
 *                 type: string
 *                 enum: [user, admin]
 *     responses:
 *       200:
 *         description: 更新成功
 *       404:
 *         description: 用户不存在
 */
router.patch(
  '/users/:id/role',
  [
    param('id').isInt({ min: 1 }).withMessage('id 必须为正整数'),
    body('role').isIn(['user', 'admin']).withMessage('role 仅支持 user 或 admin')
  ],
  validate,
  adminController.updateUserRole
);

/**
 * @swagger
 * /api/admin/users/{id}/account-type:
 *   patch:
 *     summary: 更新用户账号类型
 *     tags: [Admin]
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             properties:
 *               accountType:
 *                 type: string
 *                 enum: [normal, test]
 *     responses:
 *       200:
 *         description: 更新成功
 *       404:
 *         description: 用户不存在
 */
router.patch(
  '/users/:id/account-type',
  [
    param('id').isInt({ min: 1 }).withMessage('id 必须为正整数'),
    body('accountType').isIn(['normal', 'test']).withMessage('accountType 仅支持 normal 或 test')
  ],
  validate,
  adminController.updateUserAccountType
);

router.patch(
  '/users/:id/password',
  [
    param('id').isInt({ min: 1 }).withMessage('id 必须为正整数'),
    body('password')
      .isString()
      .isLength({ min: 8, max: 100 })
      .withMessage('密码长度应在 8~100 之间')
  ],
  validate,
  adminController.updateUserPassword
);

/**
 * @swagger
 * /api/admin/users/{id}:
 *   delete:
 *     summary: 删除用户
 *     tags: [Admin]
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *       - in: query
 *         name: force
 *         schema:
 *           type: boolean
 *           default: false
 *     responses:
 *       200:
 *         description: 删除完成
 *       404:
 *         description: 用户不存在
 */
router.delete(
  '/users/:id',
  [
    param('id').isInt({ min: 1 }).withMessage('id 必须为正整数'),
    query('force').optional().isBoolean().withMessage('force 需为布尔值').toBoolean()
  ],
  validate,
  adminController.deleteUser
);

/**
 * @swagger
 * /api/admin/customers:
 *   get:
 *     summary: 管理员获取客户列表
 *     tags: [Admin]
 *     parameters:
 *       - in: query
 *         name: page
 *         schema:
 *           type: integer
 *           default: 1
 *       - in: query
 *         name: pageSize
 *         schema:
 *           type: integer
 *           default: 20
 *       - in: query
 *         name: keyword
 *         schema:
 *           type: string
 *       - in: query
 *         name: sortBy
 *         schema:
 *           type: string
 *           enum: [createdAt, name, email]
 *       - in: query
 *         name: sortOrder
 *         schema:
 *           type: string
 *           enum: [asc, desc]
 *     responses:
 *       200:
 *         description: 客户分页数据
 */
router.get(
  '/customers',
  [...paginationValidators, ...customersFilterValidators],
  validate,
  adminController.getAllCustomers
);

router.get(
  '/customers/:id',
  [param('id').isInt({ min: 1 }).withMessage('id 必须为正整数')],
  validate,
  adminController.getCustomerDetail
);

const customerUpdateValidators = [
  param('id').isInt({ min: 1 }).withMessage('id 必须为正整数'),
  body('name').optional().isString().isLength({ max: 255 }).withMessage('姓名长度需小于 255 个字符'),
  body('dateOfBirth').optional().isString().isLength({ max: 10 }).withMessage('出生日期格式不正确'),
  body('gender').optional().isString().isLength({ max: 50 }).withMessage('性别长度过长'),
  body('phone').optional().isString().isLength({ max: 100 }).withMessage('联系方式长度过长'),
  body('email').optional().isEmail().withMessage('邮箱格式不正确').bail().isLength({ max: 255 }),
  body('height')
    .optional({ nullable: true })
    .custom(value => value === null || typeof value === 'number')
    .withMessage('身高需为数字或留空')
    .custom(value => value === null || value >= 0)
    .withMessage('身高需为非负数字'),
  body('weight')
    .optional({ nullable: true })
    .custom(value => value === null || typeof value === 'number')
    .withMessage('体重需为数字或留空')
    .custom(value => value === null || value >= 0)
    .withMessage('体重需为非负数字')
];

router.patch('/customers/:id', customerUpdateValidators, validate, adminController.updateCustomer);

router.get('/feature-flags', adminController.getFeatureFlags);

router.patch(
  '/feature-flags/offline-mode',
  [
    body('enabled').isBoolean().withMessage('enabled 需为布尔值'),
    body('notifyOnline').optional().isBoolean().withMessage('notifyOnline 需为布尔值').toBoolean()
  ],
  validate,
  adminController.updateOfflineModeFlag
);

router.patch(
  '/feature-flags/register-rollout-profile',
  [body('profile').isIn(['NORMAL', 'ROLLBACK_A', 'ROLLBACK_B']).withMessage('profile 非法')],
  validate,
  adminController.updateRegisterRolloutProfile
);

router.post(
  '/feature-flags/offline-mode/force-exit',
  [
    body('countdownSec')
      .isInt({ min: 5, max: 120 })
      .withMessage('countdownSec 需为 5~120 之间的整数')
      .toInt()
  ],
  validate,
  adminController.forceExitOfflineMode
);

router.get(
  '/devices',
  [...paginationValidators, ...deviceFilterValidators],
  validate,
  adminController.getDevices
);

router.get(
  '/devices/:deviceId',
  [param('deviceId').isString().isLength({ min: 1, max: 191 }).withMessage('deviceId 长度需在 1~191 之间')],
  validate,
  adminController.getDeviceDetail
);

router.patch(
  '/devices/:deviceId/offline',
  [
    param('deviceId').isString().isLength({ min: 1, max: 191 }).withMessage('deviceId 长度需在 1~191 之间'),
    body('offlineAllowed').isBoolean().withMessage('offlineAllowed 需为布尔值'),
    body('notifyOnline').optional().isBoolean().withMessage('notifyOnline 需为布尔值').toBoolean()
  ],
  validate,
  adminController.updateDeviceOfflinePermission
);

router.post(
  '/devices/:deviceId/force-exit',
  [
    param('deviceId').isString().isLength({ min: 1, max: 191 }).withMessage('deviceId 长度需在 1~191 之间'),
    body('countdownSec')
      .isInt({ min: 5, max: 120 })
      .withMessage('countdownSec 需为 5~120 之间的整数')
      .toInt()
  ],
  validate,
  adminController.forceExitDeviceOffline
);

module.exports = router;
