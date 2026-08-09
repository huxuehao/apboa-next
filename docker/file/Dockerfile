ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre
# ============================================================
# 阶段一：Maven 构建
# ============================================================
# 构建镜像（由 docker-compose args 传入，支持私有仓库）
FROM ${MAVEN_IMAGE} AS build

WORKDIR /build

# 复制 Maven 私有仓库配置
COPY docker/maven/settings.xml /root/.m2/settings.xml

# 复制源码并构建
COPY . .
RUN mvn package -pl runner-file -am -DskipTests -q

# ============================================================
# 阶段二：运行镜像
# ============================================================
# 运行镜像（由 docker-compose args 传入，支持私有仓库）
FROM ${JRE_IMAGE}

WORKDIR /app

# 以 root 运行：容器内拥有完整文件系统权限（可随意创建文件、下载依赖）
# 安全边界由容器隔离承担；apboa-proxy 仍保持非 root 降权以隔离不可信命令执行

# 安装 curl（基础网络工具）
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# 复制构建产物
COPY --from=build /build/runner-file/target/runner-file-*.jar app.jar

# 创建数据目录（logback 相对路径依赖 /app 可写）
RUN mkdir -p /app/logs /app/.apboa

ENV TZ=Asia/Shanghai
# ---------- 启动脚本 ----------
# 复制 entrypoint.sh（动态 JVM 堆计算 + root 直接启动）
COPY docker/file/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# runner-file 不对外暴露 HTTP 接口（web-application-type=none），无需 EXPOSE

ENTRYPOINT ["/app/entrypoint.sh"]
