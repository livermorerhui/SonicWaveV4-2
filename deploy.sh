#!/bin/bash
# 当任何命令失败时，立即停止脚本
set -e

# --- 在这里配置您的服务器信息 ---
REMOTE_USER="root"
REMOTE_HOST="47.107.66.156"
REMOTE_PROJECT_PATH="/root/apps/backend-api"
LOCAL_PROJECT_PATH="./apps/backend-api"
# ---------------------------------

echo "✅ 步骤 1/5: 停止服务器上旧的服务..."
# '|| true' 确保即使服务不存在或已停止，脚本也不会因错误而停止
ssh $REMOTE_USER@$REMOTE_HOST "cd $REMOTE_PROJECT_PATH && docker-compose down || true"

echo "✅ 步骤 2/5: 删除服务器上的旧代码..."
ssh $REMOTE_USER@$REMOTE_HOST "rm -rf $REMOTE_PROJECT_PATH"

echo "✅ 步骤 3/5: 上传新代码到服务器..."
scp -r $LOCAL_PROJECT_PATH $REMOTE_USER@$REMOTE_HOST:/root/

echo "✅ 步骤 4/5: 自动修改服务器上的 .env 文件..."
# 自动将数据库主机从本地开发配置切换为 Docker Compose 服务名
ssh $REMOTE_USER@$REMOTE_HOST "sed -i 's/DB_HOST=127.0.0.1/DB_HOST=db/g' $REMOTE_PROJECT_PATH/.env"

echo "✅ 步骤 5/5: 在服务器上构建并启动新服务..."
ssh $REMOTE_USER@$REMOTE_HOST "cd $REMOTE_PROJECT_PATH && docker-compose up --build -d"

echo "🚀 部署成功完成！"
