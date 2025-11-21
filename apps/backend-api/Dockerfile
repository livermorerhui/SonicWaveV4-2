# --- 第一阶段：构建阶段 ---
# 使用一个包含完整工具链的 Node.js 镜像作为构建环境
FROM node:18-alpine AS builder

# 设置工作目录
WORKDIR /app

# 复制 package.json 和 package-lock.json
# 这样做可以利用 Docker 的缓存机制，如果依赖没有变化，就不需要重新安装
COPY package*.json ./

# 安装所有依赖，包括开发依赖
RUN npm install

# 复制所有剩余的源代码
COPY . .


# --- 第二阶段：生产阶段 ---
# 使用一个非常轻量的 Node.js 镜像作为最终的运行环境
FROM node:18-alpine

# 设置工作目录
WORKDIR /app

# 从“构建阶段”的镜像中，只复制生产所需的 node_modules 和源代码
# 这样可以避免将开发依赖打包到最终镜像中，减小体积
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app .

# 暴露应用运行的端口
EXPOSE 3000

# 定义容器启动时运行的命令
CMD [ "node", "index.js" ]
