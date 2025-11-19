#!/bin/bash
# 当任何命令失败时，立即退出脚本
set -e

echo "=> 1/4: 正在从Git仓库拉取最新代码..."
git pull origin main

echo "=> 2/4: 正在停止并移除旧的Docker容器..."
# 如果服务未运行，`down`命令可能会报错，所以我们先忽略错误
docker-compose down --remove-orphans || true

echo "=> 3/4: 正在构建新的Docker镜像并启动服务..."
# -d 表示在后台运行, --build 强制重新构建镜像
docker-compose up -d --build

echo "=> 4/4: 正在清理无用的旧Docker镜像..."
docker image prune -f

echo "\n🎉 部署成功！您的服务已更新并正在运行。"