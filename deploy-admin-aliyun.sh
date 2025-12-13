#!/usr/bin/env bash
set -euo pipefail

########################################
# 配置区：按你自己的实际情况修改
########################################

# 阿里云登录账号 & 主机
REMOTE_USER="root"
REMOTE_HOST="47.107.66.156"

# 服务器上 admin-web 目录
REMOTE_DIR="/root/admin-web"

# 本地 admin-web 目录（脚本放在项目根目录时，这个写法就可以直接用）
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
LOCAL_ADMIN_DIR="$PROJECT_ROOT/apps/admin-web"

# admin-web 对外端口
ADMIN_WEB_PORT="4173"

########################################
# 1. rsync 同步 admin-web 代码到阿里云
#    - 保留服务器上的 node_modules / dist / 运行时文件
#    - 只让“源码和配置文件”保持与本地一致
########################################

echo "==> 同步本地 admin-web 到 ${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR} ..."

rsync -avz --delete \
  --exclude 'node_modules' \
  --exclude '.git' \
  --exclude 'dist' \
  --exclude '.env*' \
  "${LOCAL_ADMIN_DIR}/" \
  "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}/"

echo "==> 同步完成"

########################################
# 2. 在阿里云执行：npm install + npm run build + pm2 启动/重启 admin-web
########################################

echo "==> 登录阿里云并执行部署步骤 ..."

ssh "${REMOTE_USER}@${REMOTE_HOST}" << EOF
  set -e

  cd "${REMOTE_DIR}"

  echo "---- 当前目录: \$(pwd)"

  # 2.1 安装依赖
  echo "---- npm install（包含 devDependencies，用于构建） ..."
  npm install

  # 2.2 生产构建（使用 .env.production 中的配置，例如 VITE_API_BASE_URL）
  echo "---- npm run build ..."
  npm run build

  # 2.3 用 pm2 启动/重启 admin-web 静态服务
  APP_NAME="admin-web"

  echo "---- 使用 pm2 管理进程: \${APP_NAME}"

  # 如果已存在同名进程，先删除
  if pm2 describe "\${APP_NAME}" >/dev/null 2>&1; then
    echo "---- 已存在 pm2 进程 \${APP_NAME}，先删除 ..."
    pm2 delete "\${APP_NAME}"
  fi

  # 为运行 serve 的 Node 进程加一个较小的堆内存上限（256MB 够用）
  NODE_ARGS="--max_old_space_size=256"

  echo "---- 启动 admin-web（npx serve -s dist -l ${ADMIN_WEB_PORT}） ..."
  pm2 start npx --name "\${APP_NAME}" --node-args="\${NODE_ARGS}" -- serve -s dist -l ${ADMIN_WEB_PORT}

  echo "---- 当前 pm2 列表:"
  pm2 status "\${APP_NAME}"

EOF

echo "==> admin-web 部署完成（请访问： http://${REMOTE_HOST}:${ADMIN_WEB_PORT} ）"
