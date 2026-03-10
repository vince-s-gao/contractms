#!/bin/bash

# 合同管理系统备份脚本
set -e

echo "=== 合同管理系统备份开始 ==="

# 备份配置
BACKUP_DIR="/opt/contract-management/backups"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_NAME="contract_backup_$DATE"
BACKUP_PATH="$BACKUP_DIR/$BACKUP_NAME"

# 保留备份天数
KEEP_DAYS=30

# 创建备份目录
mkdir -p $BACKUP_PATH

# 加载环境变量
ENV_FILE=".env.production"
if [ -f "$ENV_FILE" ]; then
    export $(grep -v '^#' $ENV_FILE | xargs)
fi

# 备份数据库
echo "备份数据库..."
docker exec contract-mysql mysqldump -u root -p$MYSQL_ROOT_PASSWORD --single-transaction --routines --triggers $MYSQL_DATABASE > $BACKUP_PATH/database.sql

# 备份Redis数据
echo "备份Redis数据..."
docker exec contract-redis redis-cli --pass $REDIS_PASSWORD --rdb /data/dump.rdb
sleep 5
docker cp contract-redis:/data/dump.rdb $BACKUP_PATH/redis.rdb

# 备份应用配置
echo "备份应用配置..."
cp .env.production $BACKUP_PATH/
cp docker-compose.yml $BACKUP_PATH/
cp -r nginx/conf $BACKUP_PATH/
cp -r nginx/ssl $BACKUP_PATH/ 2>/dev/null || true

# 备份应用数据
echo "备份应用数据..."
docker cp contract-app:/app/logs $BACKUP_PATH/ 2>/dev/null || true

# 备份Docker卷数据
echo "备份Docker卷数据..."
docker run --rm -v contract-mysql_data:/source -v $BACKUP_PATH:/backup alpine tar -czf /backup/mysql_data.tar.gz -C /source .
docker run --rm -v contract-redis_data:/source -v $BACKUP_PATH:/backup alpine tar -czf /backup/redis_data.tar.gz -C /source .
docker run --rm -v contract-rabbitmq_data:/source -v $BACKUP_PATH:/backup alpine tar -czf /backup/rabbitmq_data.tar.gz -C /source .

# 创建备份信息文件
cat > $BACKUP_PATH/backup-info.txt << EOF
备份时间: $(date)
备份名称: $BACKUP_NAME
数据库: $MYSQL_DATABASE
应用版本: $(docker inspect --format='{{.Config.Image}}' contract-app 2>/dev/null || echo "未知")
容器状态:
$(docker-compose ps)
EOF

# 创建压缩包
echo "创建备份压缩包..."
cd $BACKUP_DIR
tar -czf $BACKUP_NAME.tar.gz $BACKUP_NAME
rm -rf $BACKUP_NAME

# 清理旧备份
echo "清理过期备份..."
find $BACKUP_DIR -name "contract_backup_*.tar.gz" -mtime +$KEEP_DAYS -delete

# 验证备份文件
if [ -f "$BACKUP_NAME.tar.gz" ]; then
    BACKUP_SIZE=$(du -h $BACKUP_NAME.tar.gz | cut -f1)
    echo ""
    echo "=== 备份完成 ==="
    echo "备份文件: $BACKUP_DIR/$BACKUP_NAME.tar.gz"
    echo "备份大小: $BACKUP_SIZE"
    echo ""
    echo "备份内容:"
    echo "- 数据库: MySQL数据"
    echo "- 缓存: Redis数据"
    echo "- 配置: 环境变量、Nginx配置"
    echo "- 日志: 应用日志"
    echo "- 数据卷: MySQL、Redis、RabbitMQ数据"
    echo ""
    echo "恢复命令: ./restore.sh $BACKUP_NAME.tar.gz"
else
    echo "错误: 备份文件创建失败"
    exit 1
fi