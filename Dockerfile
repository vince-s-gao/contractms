# 多阶段构建Dockerfile
FROM maven:3.9.4-eclipse-temurin-17 AS backend-build

# 设置工作目录
WORKDIR /app

# 复制后端代码
COPY backend/pom.xml .
COPY backend/src ./src

# 构建后端应用
RUN mvn clean package -DskipTests

# 前端构建阶段
FROM node:22-alpine AS frontend-build

# 设置工作目录
WORKDIR /app

# 复制前端代码
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm ci

COPY frontend/ .
RUN npm run build

# 生产镜像阶段
FROM eclipse-temurin:17-jre

# 安装必要的工具
RUN apt-get update && apt-get install -y tzdata curl && rm -rf /var/lib/apt/lists/*

# 设置时区
ENV TZ=Asia/Shanghai

# 创建应用用户
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# 设置工作目录
WORKDIR /app

# 从构建阶段复制jar包
COPY --from=backend-build /app/target/*.jar app.jar

# 从构建阶段复制前端静态文件
COPY --from=frontend-build /app/dist ./static

# 创建日志目录
RUN mkdir -p /app/logs && chown appuser:appgroup /app/logs

# 切换用户
USER appuser

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -s http://localhost:8080/ > /dev/null || exit 1

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
