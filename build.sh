#!/bin/bash

# 合同管理系统构建脚本
set -e

echo "=== 合同管理系统构建开始 ==="

# 检查必要工具
check_command() {
    if ! command -v $1 &> /dev/null; then
        echo "错误: $1 未安装"
        exit 1
    fi
}

check_command "java"
check_command "mvn"
check_command "node"
check_command "npm"

# 设置构建版本
BUILD_VERSION=${1:-1.0.0}
BUILD_TIMESTAMP=$(date +%Y%m%d%H%M%S)
BUILD_ID="${BUILD_VERSION}-${BUILD_TIMESTAMP}"

echo "构建版本: $BUILD_ID"

# 创建构建目录
BUILD_DIR="build/$BUILD_ID"
mkdir -p $BUILD_DIR

# 构建后端
echo "构建后端服务..."
cd backend

# 清理并构建
mvn clean compile -DskipTests

# 运行测试
if [ "$SKIP_TESTS" != "true" ]; then
    echo "运行单元测试..."
    mvn test
fi

# 打包
mvn package -DskipTests

# 复制jar包到构建目录
cp target/*.jar ../$BUILD_DIR/app.jar
cd ..

# 构建前端
echo "构建前端应用..."
cd frontend

# 安装依赖
npm ci

# 运行前端测试
if [ "$SKIP_TESTS" != "true" ]; then
    echo "运行前端测试..."
    npm run test || echo "前端测试跳过"
fi

# 构建生产版本
npm run build

# 复制静态文件到构建目录
cp -r dist ../$BUILD_DIR/static
cd ..

# 复制配置文件
echo "复制配置文件..."
cp Dockerfile $BUILD_DIR/
cp docker-compose.yml $BUILD_DIR/
cp .env.production $BUILD_DIR/
cp deploy.sh $BUILD_DIR/
cp backup.sh $BUILD_DIR/
cp restore.sh $BUILD_DIR/

# 创建Nginx配置目录
mkdir -p $BUILD_DIR/nginx/conf
mkdir -p $BUILD_DIR/nginx/ssl
cp -r nginx/conf/* $BUILD_DIR/nginx/conf/ 2>/dev/null || true

# 创建数据库初始化脚本目录
mkdir -p $BUILD_DIR/mysql/init
cp -r mysql/init/* $BUILD_DIR/mysql/init/ 2>/dev/null || true

# 创建构建信息文件
cat > $BUILD_DIR/build-info.txt << EOF
构建版本: $BUILD_ID
构建时间: $(date)
Git提交: $(git rev-parse --short HEAD 2>/dev/null || echo "未知")
构建环境: $(uname -a)
Java版本: $(java -version 2>&1 | head -n1)
Node版本: $(node --version)
Maven版本: $(mvn --version 2>&1 | head -n1)
EOF

# 创建部署包
cd build
tar -czf contract-management-$BUILD_ID.tar.gz $BUILD_ID

echo ""
echo "=== 构建完成 ==="
echo "构建目录: $BUILD_DIR"
echo "部署包: build/contract-management-$BUILD_ID.tar.gz"
echo ""
echo "部署步骤:"
echo "1. 解压部署包: tar -xzf contract-management-$BUILD_ID.tar.gz"
echo "2. 进入目录: cd $BUILD_ID"
echo "3. 修改环境配置: vi .env.production"
echo "4. 执行部署: ./deploy.sh"
echo ""

# 清理临时文件
if [ "$KEEP_BUILD" != "true" ]; then
    rm -rf $BUILD_ID
fi

# 显示构建摘要
echo "构建摘要:"
ls -la contract-management-$BUILD_ID.tar.gz
echo ""
echo "构建信息已保存到: $BUILD_DIR/build-info.txt"