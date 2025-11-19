const winston = require('winston');
// [修正] 从 winston.format 中解构并导入 errors 函数
const { splat, combine, timestamp, printf, colorize, json, errors } = winston.format;

// --- 日志记录器配置 ---
const logger = winston.createLogger({
  level: process.env.LOG_LEVEL || 'debug',
  
  // 文件日志的格式：保持为JSON，便于机器解析
  format: combine(
    timestamp(),
    errors({ stack: true }), // 现在可以正确调用 errors
    json()
  ),
  
  transports: [
    new winston.transports.File({ filename: 'error.log', level: 'error' }),
    new winston.transports.File({ filename: 'combined.log' }),
  ],
});

// 仅在非生产环境，添加一个格式更丰富的控制台输出
if (process.env.NODE_ENV !== 'production') {
  logger.add(new winston.transports.Console({
    format: combine(
      colorize(),
      timestamp({ format: 'YYYY-MM-DD HH:mm:ss' }),
      splat(),
      printf(info => {
        const { timestamp, level, message, ...meta } = info;
        
        // 检查是否有堆栈信息（来自 errors({ stack: true })）
        const stack = info.stack ? `\n${info.stack}` : '';

        // 检查是否有附加的元数据对象 (来自 splat())
        const splatData = meta[Symbol.for('splat')];
        const metaString = splatData ? `\n${JSON.stringify(splatData[0], null, 2)}` : '';
        
        return `${timestamp} ${level}: ${message}${metaString}${stack}`;
      })
    ),
  }));
}

module.exports = logger;
