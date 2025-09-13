
const express = require('express');
const http = require('http');
const { WebSocketServer } = require('ws');
const jwt = require('jsonwebtoken');
const url = require('url');

const logger = require('./logger');
const { checkDbConnection } = require('./config/db');
const config = require('./config/config'); // 引入新的配置

// 引入路由模块
const userRouter = require('./routes/users.routes.js');
const musicRouter = require('./routes/music.routes.js');
const createReportRouter = require('./routes/reports.routes.js');
const logsRouter = require('./routes/logs.routes.js');

// 初始化
const app = express();
const PORT = config.port; // 从配置中获取端口

// 全局中间件
app.use(express.json());

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
app.use('/api/users', userRouter);
app.use('/api/music', musicRouter);
// 通过工厂函数注入 wss 和 wsClients 实例
app.use('/api/reports', createReportRouter(wss, wsClients));
app.use('/api/logs', logsRouter);

// 启动应用
async function startApp() {
  // 检查数据库连接
  await checkDbConnection();

  // 启动服务器
  server.listen(PORT, () => {
    logger.info(`✅ Server (HTTP + WebSocket) is running on http://localhost:${PORT}`);
  });
}

startApp();