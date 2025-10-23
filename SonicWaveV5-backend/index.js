
require('dotenv').config();
console.log("--- Backend service starting ---");

const express = require('express');
const cors = require('cors');
const http = require('http');
const { WebSocketServer } = require('ws');
const jwt = require('jsonwebtoken');
const url = require('url');
const swaggerUi = require('swagger-ui-express');

const logger = require('./logger');
const { checkDbConnection } = require('./config/db');
const { startManagerIntervals } = require('./onlineStatusManager');
const config = require('./config/config'); // 引入新的配置
const swaggerSpec = require('./docs/swagger');

// 引入路由模块
const userRouter = require('./routes/users.routes.js');
const musicRouter = require('./routes/music.routes.js');
const createReportRouter = require('./routes/reports.routes.js');
const logsRouter = require('./routes/logs.routes.js');
const appRouter = require('./routes/app.routes.js');
const operationsRouter = require('./routes/operations.routes.js');
const heartbeatRouter = require('./routes/heartbeat.routes.js');
const authEventRouter = require('./routes/auth.routes.js');
const tokenRouter = require('./routes/token.routes.js');
const adminRouter = require('./routes/admin.routes.js');
const { ensureSeedAdmin } = require('./services/admin.service');
const customerRouter = require('./routes/customer.routes.js');

// 初始化
const app = express();
const PORT = config.port; // 从配置中获取端口

// 全局中间件
app.use(express.json());
app.use(
  cors({
    origin: process.env.ADMIN_WEB_ORIGIN || 'http://localhost:5173',
    credentials: true
  })
);

// 创建 HTTP 和 WebSocket 服务器
const server = http.createServer(app);
const wss = new WebSocketServer({ server });

// 用于存储 userId 和 WebSocket 连接的映射
const wsClients = new Map();

wss.on('connection', (ws, req) => {
  // 从连接 URL 中获取 token
  const token = url.parse(req.url, true).query.token;

  if (!token) {
    logger.warn('WebSocket connection attempt without token. Closing.');
    ws.close();
    return;
  }

  // 验证 token
  jwt.verify(token, config.jwt.secret, (err, user) => { // 从配置中获取 JWT 密钥
    if (err) {
      logger.warn('WebSocket connection with invalid token. Closing.');
      ws.close();
      return;
    }

    // 存储已认证的连接
    const { userId } = user;
    wsClients.set(userId, ws);
    logger.info(`User ${userId} connected via WebSocket.`);

    ws.on('message', message => {
      logger.info(`Received WebSocket message from user ${userId}: ${message}`);
    });

    ws.on('close', () => {
      wsClients.delete(userId);
      logger.info(`User ${userId} disconnected from WebSocket.`);
    });
  });
});

// 挂载 API 路由
app.get('/', (req, res) => res.send('Welcome to the refactored SonicWaveV5 Backend!'));
app.use('/api/v1/users', userRouter);
app.use('/api/v1/music', musicRouter);
app.use('/api/v1/reports', createReportRouter(wss, wsClients));
app.use('/api/v1/logs', logsRouter);
app.use('/api/v1/app', appRouter);
app.use('/api/v1/operations', operationsRouter);
app.use('/api/v1/heartbeat', heartbeatRouter);
app.use('/api/v1/auth', authEventRouter);
app.use('/api/v1/token', tokenRouter); // 新增
app.use('/api/v1/customers', customerRouter);
app.use('/api/admin', adminRouter);
app.use('/docs', swaggerUi.serve, swaggerUi.setup(swaggerSpec));

// 启动应用
async function startApp() {
  // 检查数据库连接
  await checkDbConnection();
  await ensureSeedAdmin();

  // 启动服务器
  server.listen(PORT, () => {
    logger.info(`✅ Server (HTTP + WebSocket) is running on http://localhost:${PORT}`);
    // 在服务器启动后，开始在线用户清理任务
    startManagerIntervals();
  });
}

startApp();
