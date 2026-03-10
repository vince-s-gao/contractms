#!/bin/bash

# 合同管理系统恢复脚本
set -e

if [ $# -eq 0 ]; then
    echo "用法: $0 <备份文件>"
    echo "示例: $0 contract_backup_20231201_120000.tar.gz"
    exit 1
fi

BACKUP_FILE="$1"
BACKUP_DIR="/opt/contract-management/backups"

if [ ! -f "$BACKUP_FILE" ]; then
    # 如果在当前目录找不到，尝试在备份目录查找
    if [ ! -f "$BACKUP_DIR/$BACKUP_FILE" ]; then
        echo "错误: 备份文件 $BACKUP_FILE 不存在"
        exit 1
    else
        BACKUP_FILE="$BACKUP_DIR/$BACKUP_FILE"
    fi
fi

echo "=== 合同管理系统恢复开始 ==="
echo "备份文件: $BACKUP_FILE"

# 确认操作
read -p "此操作将覆盖当前数据，是否继续? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "操作已取消"
    exit 0
fi

# 停止服务
echo "停止服务..."
docker-compose down || true

# 创建临时目录
TEMP_DIR="/tmp/contract_restore_$(date +%s)"
mkdir -p $TEMP_DIR

# 解压备份文件
echo "解压备份文件..."
tar -xzf "$BACKUP_FILE" -C $TEMP_DIR

RESTORE_DIR=$(find $TEMP_DIR -maxdepth 1 -type d -name "contract_backup_*" | head -1)

if [ -z "$RESTORE_DIR" ]; then
    echo "错误: 备份文件格式不正确"
    rm -rf $TEMP_DIR
    exit 1
fi

echo "恢复目录: $RESTORE_DIR"

# 恢复数据库
echo "恢复数据库..."
if [ -f "$RESTORE_DIR/database.sql" ]; then
    # 启动MySQL服务
    docker-compose up -d mysql
    
    # 等待MySQL启动
    sleep 30
    
    # 导入数据
    docker exec -i contract-mysql mysql -u root -p$MYSQL_ROOT_PASSWORD $MYSQL_DATABASE < "$RESTORE_DIR/database.sql"
    echo "✓ 数据库恢复完成"
else
    echo "⚠ 数据库备份文件不存在，跳过数据库恢复"
fi

# 恢复Redis数据
echo "恢复Redis数据..."
if [ -f "$RESTORE_DIR/redis.rdb" ]; then
    # 停止Redis
    docker stop contract-redis || true
    
    # 复制RDB文件
    docker cp "$RESTORE_DIR/redis.rdb" contract-redis:/data/dump.rdb
    
    # 启动Redis
    docker start contract-redis
    echo "✓ Redis数据恢复完成"
else
    echo "⚠ Redis备份文件不存在，跳过Redis恢复"
fi

# 恢复配置
echo "恢复配置文件..."
if [ -f "$RESTORE_DIR/.env.production" ]; then
    cp "$RESTORE_DIR/.env.production" ./
    echo "✓ 环境配置恢复完成"
fi

if [ -f "$RESTORE_DIR/docker-compose.yml" ]; then
    cp "$RESTORE_DIR/docker-compose.yml" ./
    echo "✓ Docker配置恢复完成"
fi

if [ -d "$RESTORE_DIR/conf" ]; then
    mkdir -p nginx/conf
    cp -r "$RESTORE_DIR/conf/"* nginx/conf/
    echo "✓ Nginx配置恢复完成"
fi

if [ -d "$RESTORE_DIR/ssl" ]; then
    mkdir -p nginx/ssl
    cp -r "$RESTORE_DIR/ssl/"* nginx/ssl/
    echo "✓ SSL证书恢复完成"
fi

# 恢复数据卷（可选）
read -p "是否恢复数据卷? 这将覆盖现有数据 (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "恢复数据卷..."
    
    if [ -f "$RESTORE_DIR/mysql_data.tar.gz" ]; then
        docker run --rm -v contract-mysql_data:/target -v "$RESTORE_DIR:/source" alpine sh -c "cd /target && rm -rf ./* && tar -xzf /source/mysql_data.tar.gz"
        echo "✓ MySQL数据卷恢复完成"
    fi
    
    if [ -f "$RESTORE_DIR/redis_data.tar.gz" ]; then
        docker run --rm -v contract-redis_data:/target -v "$RESTORE_DIR:/source" alpine sh -c "cd /target && rm -rf ./* && tar -xzf /source/redis_data.tar.gz"
        echo "✓ Redis数据卷恢复完成"
    fi
    
    if [ -f "$RESTORE_DIR/rabbitmq_data.tar.gz" ]; then
        docker run --rm -v contract-rabbitmq_data:/target -v "$RESTORE_DIR:/source" alpine sh -c "cd /target && rm -rf ./* && tar -xzf /source/rabbitmq_data.tar.gz"
        echo "✓ RabbitMQ数据卷恢复完成"
    fi
fi

# 启动所有服务
echo "启动服务..."
docker-compose up -d

# 等待服务启动
echo "等待服务启动..."
sleep 30

# 验证恢复结果
echo "验证恢复结果..."

# 检查数据库连接
if docker exec contract-mysql mysql -u root -p$MYSQL_ROOT_PASSWORD -e "USE $MYSQL_DATABASE; SELECT COUNT(*) FROM users;" &> /dev/null; then
    echo "✓ 数据库连接正常"
else
    echo "✗ 数据库连接异常"
fi

# 检查应用健康
if curl -f http://localhost:8080/actuator/health &> /dev/null; then
    echo "✓ 应用服务正常"
else
    echo "✗ 应用服务异常"
fi

# 清理临时文件
rm -rf $TEMP_DIR

echo ""
echo "=== 恢复完成 ==="
echo "恢复时间: $(date)"
echo "恢复文件: $BACKUP_FILE"
echo ""
echo "服务状态:"
docker-compose ps

echo ""
echo "访问地址:"
echo "- 应用: http://localhost:8080"
echo "- API文档: http://localhost:8080/swagger-ui.html"
echo "- 数据库: localhost:3306"
echo "- Redis: localhost:6379"
echo "- RabbitMQ管理: http://localhost:15672"