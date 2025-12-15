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

        // splat() 传入的附加参数（通常是一个 meta object）
        const splatData = meta[Symbol.for('splat')];

        let metaObj = null;
        let stackValue = info.stack;

        if (Array.isArray(splatData) && splatData.length > 0) {
          const first = splatData[0];

          // 常见：logger.xxx('msg', { ... })
          if (first && typeof first === 'object' && !Array.isArray(first)) {
            metaObj = { ...first };

            // 如果 stack 被放进了 meta（常见写法：{ stack: err.stack }），则只打印一次：
            // 1) 从 meta 里读出来作为 stackValue
            // 2) 从 metaString 中移除 stack 字段，避免 JSON 和尾部 stack 重复
            if (!stackValue && typeof metaObj.stack === 'string' && metaObj.stack.trim().length > 0) {
              stackValue = metaObj.stack;
            }
            if (metaObj.stack !== undefined) {
              delete metaObj.stack;
            }
          } else if (first !== undefined) {
            // 兜底：如果不是 object，就直接 stringify
            metaObj = first;
          }
        }

        const metaString =
          metaObj === null
            ? ''
            : typeof metaObj === 'object'
            ? `\n${JSON.stringify(metaObj, null, 2)}`
            : `\n${String(metaObj)}`;

        const stack = stackValue ? `\n${stackValue}` : '';

        return `${timestamp} ${level}: ${message}${metaString}${stack}`;
      })
    ),
  }));
}

module.exports = logger;
