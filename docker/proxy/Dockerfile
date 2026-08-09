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
RUN mvn package -pl runner-proxy -am -DskipTests -q

# ============================================================
# 阶段二：运行镜像（JRE + Python + Node.js）
# ============================================================
# 运行镜像（由 docker-compose args 传入，支持私有仓库）
FROM ${JRE_IMAGE}

WORKDIR /app

# 以非root运行可缩小内核攻击面，配合cap_drop=ALL和no-new-privileges，阻断容器→宿主机逃逸路径
RUN groupadd -r shellproxy -g 1002 && \
    useradd -r -g shellproxy -u 1002 -m -s /sbin/nologin -d /app shellproxy && \
    chown -R shellproxy:shellproxy /app

# ---------- 安装运行环境依赖 ----------
# Python 3 + Node.js 22 + gosu（容器降权启动工具）
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        python3 \
        python3-pip \
        python3-venv \
        gosu \
        curl \
        ca-certificates \
    && ln -sf /usr/bin/python3 /usr/bin/python \
    && curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y nodejs \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# 验证运行时
RUN python --version && node --version && npm --version && gosu --version

# 复制构建产物
COPY --from=build /build/runner-proxy/target/runner-proxy-*.jar app.jar

# 创建数据目录并将 /app 全部赋权给 shellproxy（logback 相对路径依赖 /app 可写）
RUN mkdir -p /app/logs && \
    chown -R shellproxy:shellproxy /app

EXPOSE 3062

ENV TZ=Asia/Shanghai
# 追加 npm-global bin 到 PATH（不覆盖基础镜像的 Java 路径）
ENV PATH="/opt/tools/npm-global/bin:${PATH}"
# ---------- 启动脚本 ----------
# 复制 entrypoint.sh（动态 JVM 堆计算 + 权限修复 + gosu 降权启动）
COPY docker/proxy/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# 以 root 启动 → entrypoint.sh chown 修复 volume 挂载目录权限 → gosu 切到 shellproxy 用户启动 Java
ENTRYPOINT ["/app/entrypoint.sh"]
