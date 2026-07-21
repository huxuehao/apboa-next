ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre
# ============================================================
# 阶段一：Maven 构建
# ============================================================
# 构建镜像（由 docker-compose args 传入，支持私有仓库）
FROM ${MAVEN_IMAGE} AS build

WORKDIR /build

# 复制根 pom 及所有模块 pom（保留目录结构），利用 Docker 缓存层加速
# Maven 解析根 pom 时需要所有 <module> 目录存在，否则报错
COPY pom.xml .
# 一级模块 pom
COPY biz/pom.xml ./biz/
COPY common-base/pom.xml ./common-base/
COPY common/pom.xml ./common/
COPY heartbeat/pom.xml ./heartbeat/
COPY engine/pom.xml ./engine/
COPY scheduler/pom.xml ./scheduler/
COPY workflow/pom.xml ./workflow/
COPY runner-console/pom.xml ./runner-console/
COPY runner-runtime/pom.xml ./runner-runtime/
COPY runner-proxy/pom.xml ./runner-proxy/
COPY runner-file/pom.xml ./runner-file/
COPY runner-websocket/pom.xml ./runner-websocket/
# 二级模块 pom（biz 子模块）
COPY biz/biz-a2a/pom.xml ./biz/biz-a2a/
COPY biz/biz-account/pom.xml ./biz/biz-account/
COPY biz/biz-agent/pom.xml ./biz/biz-agent/
COPY biz/biz-hook/pom.xml ./biz/biz-hook/
COPY biz/biz-knowledge/pom.xml ./biz/biz-knowledge/
COPY biz/biz-longterm/pom.xml ./biz/biz-longterm/
COPY biz/biz-mcp/pom.xml ./biz/biz-mcp/
COPY biz/biz-model/pom.xml ./biz/biz-model/
COPY biz/biz-params/pom.xml ./biz/biz-params/
COPY biz/biz-prompt/pom.xml ./biz/biz-prompt/
COPY biz/biz-resource/pom.xml ./biz/biz-resource/
COPY biz/biz-sensitive/pom.xml ./biz/biz-sensitive/
COPY biz/biz-sk/pom.xml ./biz/biz-sk/
COPY biz/biz-skill/pom.xml ./biz/biz-skill/
COPY biz/biz-studio/pom.xml ./biz/biz-studio/
COPY biz/biz-tool/pom.xml ./biz/biz-tool/
COPY biz/biz-workflow/pom.xml ./biz/biz-workflow/
COPY biz/biz-cache/pom.xml ./biz/biz-cache/
COPY biz/biz-datasource/pom.xml ./biz/biz-datasource/
COPY biz/biz-mq/pom.xml ./biz/biz-mq/

# 复制 Maven 私有仓库配置
COPY docker/maven/settings.xml /root/.m2/settings.xml

# 预下载依赖
RUN mvn dependency:go-offline -pl runner-file -am -DskipTests -q

# 复制源码并构建
COPY . .
RUN mvn package -pl runner-file -am -DskipTests -q

# ============================================================
# 阶段二：运行镜像
# ============================================================
# 运行镜像（由 docker-compose args 传入，支持私有仓库）
FROM ${JRE_IMAGE}

WORKDIR /app

# 以非root运行可缩小内核攻击面，配合cap_drop=ALL和no-new-privileges，阻断容器→宿主机逃逸路径
RUN groupadd -r apboa -g 1001 && \
    useradd -r -g apboa -u 1001 -m -s /sbin/nologin -d /app apboa && \
    chown -R apboa:apboa /app

# 安装 gosu（容器降权启动工具）
RUN apt-get update && \
    apt-get install -y --no-install-recommends gosu curl && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# 复制构建产物
COPY --from=build /build/runner-file/target/runner-file-*.jar app.jar

# 创建数据目录并将 /app 全部赋权给 apboa
RUN mkdir -p /app/logs /app/.apboa && \
    chown -R apboa:apboa /app

ENV TZ=Asia/Shanghai
# ---------- 启动脚本 ----------
# 复制 entrypoint.sh（动态 JVM 堆计算 + 权限修复 + gosu 降权启动）
COPY docker/file/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# runner-file 不对外暴露 HTTP 接口（web-application-type=none），无需 EXPOSE

# 以 root 启动 → entrypoint.sh chown 修复 volume 挂载目录权限 → gosu 切到 apboa 用户启动 Java
ENTRYPOINT ["/app/entrypoint.sh"]
