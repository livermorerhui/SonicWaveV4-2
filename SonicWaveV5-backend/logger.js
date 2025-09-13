const winston = require('winston');

// --- 日志记录器配置 ---
const logger = winston.createLogger({
  // 日志级别：info 及以上级别的日志才会被记录
  level: 'info',
  // 日志格式：输出为 JSON 格式，并包含时间戳
  format: winston.format.combine(
    winston.format.timestamp(),
    winston.format.json()
  ),
  // 日志输出目标
  transports: [
    // 1. 将 error 级别的日志写入 error.log 文件
    new winston.transports.File({ filename: 'error.log', level: 'error' }),
    // 2. 将所有日志写入 combined.log 文件
    new winston.transports.File({ filename: 'combined.log' }),
  ],
});

// 如果不是在生产环境，我们希望日志也能漂亮地打印在控制台上
if (process.env.NODE_ENV !== 'production') {
  logger.add(new winston.transports.Console({
    format: winston.format.combine(
      winston.format.colorize(), // 添加颜色
      winston.format.simple()    // 简单格式
    ),
  }));
}

module.exports = logger;