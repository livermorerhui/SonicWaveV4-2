#!/usr/bin/env bash
set -euo pipefail

########################################
# 配置区：按你自己的实际情况修改
########################################

# 阿里云登录账号 & 主机
REMOTE_USER="root"
REMOTE_HOST="47.107.66.156"

# 服务器上 backend-api 目录（之前你就是放这里）
REMOTE_DIR="/root/backend-api"

# Knex 使用的环境名称（看 knexfile.js，一般是 production）
KNEX_ENV="production"

# 本地 backend-api 目录（脚本放在项目根目录时，这个写法就可以直接用）
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
LOCAL_BACKEND_DIR="$PROJECT_ROOT/apps/backend-api"

########################################
# 1. rsync 同步代码到阿里云
#    - 保留服务器上的 .env / node_modules / 日志 / data 等运行时文件
#    - 只让“代码和配置文件”保持与本地一致
########################################

echo "==> 同步本地 backend-api 到 ${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR} ..."

rsync -avz --delete \
  --exclude 'node_modules' \
  --exclude '.git' \
  --exclude '.env' \
  --exclude 'logs' \
  --exclude '*.log' \
  --exclude 'npm-debug.log' \
  --exclude 'data' \
  "${LOCAL_BACKEND_DIR}/" \
  "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}/"

echo "==> 同步完成"

########################################
# 2. 在阿里云执行：npm install + knex migrate + pm2 restart
########################################

echo "==> 登录阿里云并执行部署步骤 ..."

ssh "${REMOTE_USER}@${REMOTE_HOST}" << EOF
  set -e

  cd "${REMOTE_DIR}"

  echo "---- 当前目录: \$(pwd)"

  # 2.1 安装依赖
  # 如果你线上已经锁定依赖且不常改，可以改成 npm ci 或按需跳过
  echo "---- npm install --production ..."
  npm install --production

  # 2.2 跑 Knex migration（确保 login_mode 等在云端 DB 生效）
  echo "---- npx knex migrate:latest --env ${KNEX_ENV} ..."
  npx knex migrate:latest --knexfile ./knexfile.js --env ${KNEX_ENV}

  # 2.3 重启 pm2 里的 backend-api
  echo "---- pm2 restart backend-api ..."
  pm2 restart backend-api --update-env

  echo "---- pm2 status backend-api:"
  pm2 status backend-api
EOF

echo "==> 部署完成"
