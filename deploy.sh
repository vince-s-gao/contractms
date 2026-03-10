#!/bin/bash

# 合同管理系统部署脚本
set -e

echo "=== 合同管理系统部署开始 ==="

# 检查Docker是否安装
if ! command -v docker &> /dev/null; then
    echo "错误: Docker未安装"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "错误: Docker Compose未安装"
    exit 1
fi

# 设置环境变量
ENV_FILE=".env.production"
if [ ! -f "$ENV_FILE" ]; then
    echo "错误: 环境配置文件 $ENV_FILE 不存在"
    exit 1
fi

# 加载环境变量
export $(grep -v '^#' $ENV_FILE | xargs)

# 创建必要的目录
mkdir -p nginx/conf nginx/ssl nginx/logs mysql/init mysql/conf

# 停止现有服务
echo "停止现有服务..."
docker-compose down || true

# 清理旧镜像
echo "清理旧镜像..."
docker system prune -f

# 构建镜像
echo "构建应用镜像..."
docker-compose build --no-cache

# 启动服务
echo "启动服务..."
docker-compose up -d

# 等待服务启动
echo "等待服务启动..."
sleep 30

# 检查服务状态
echo "检查服务状态..."

# 检查MySQL
if docker exec contract-mysql mysqladmin ping -h localhost -u root -p$MYSQL_ROOT_PASSWORD &> /dev/null; then
    echo "✓ MySQL服务正常"
else
    echo "✗ MySQL服务异常"
    exit 1
fi

# 检查Redis
if docker exec contract-redis redis-cli --pass $REDIS_PASSWORD ping &> /dev/null; then
    echo "✓ Redis服务正常"
else
    echo "✗ Redis服务异常"
    exit 1
fi

# 检查应用
if curl -f http://localhost:8080/actuator/health &> /dev/null; then
    echo "✓ 应用服务正常"
else
    echo "✗ 应用服务异常"
    exit 1
fi

# 执行数据库迁移
echo "执行数据库迁移..."
sleep 10

# 等待应用完全启动
for i in {1..30}; do
    if curl -f http://localhost:8080/actuator/health &> /dev/null; then
        echo "应用已启动，开始数据库初始化..."
        
        # 执行数据库初始化脚本
        docker exec contract-mysql mysql -h localhost -u root -p$MYSQL_ROOT_PASSWORD $MYSQL_DATABASE -e "SOURCE /docker-entrypoint-initdb.d/01-init.sql"
        docker exec contract-mysql mysql -h localhost -u root -p$MYSQL_ROOT_PASSWORD $MYSQL_DATABASE -e "SOURCE /docker-entrypoint-initdb.d/02-schema.sql"
        docker exec contract-mysql mysql -h localhost -u root -p$MYSQL_ROOT_PASSWORD $MYSQL_DATABASE -e "SOURCE /docker-entrypoint-initdb.d/03-data.sql"
        
        echo "数据库初始化完成"
        break
    fi
    echo "等待应用启动...($i/30)"
    sleep 5
done

if [ $i -eq 30 ]; then
    echo "错误: 应用启动超时"
    exit 1
fi

# 显示部署信息
echo ""
echo "=== 部署完成 ==="
echo "应用地址: http://localhost:8080"
echo "API文档: http://localhost:8080/swagger-ui.html"
echo "数据库管理: localhost:3306"
echo "Redis管理: localhost:6379"
echo "RabbitMQ管理: http://localhost:15672"
echo ""

# 显示容器状态
echo "容器状态:"
docker-compose ps

echo ""
echo "部署日志查看: docker-compose logs -f app"
echo "停止服务: docker-compose down"
echo "重启服务: docker-compose restart"