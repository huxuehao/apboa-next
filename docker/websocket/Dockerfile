ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre
# ============================================================
# 阶段一：Maven 构建
# ============================================================
FROM ${MAVEN_IMAGE} AS build

WORKDIR /build

# 复制 Maven 私有仓库配置
COPY docker/maven/settings.xml /root/.m2/settings.xml

# 复制源码并构建
COPY . .
RUN mvn package -pl runner-websocket -am -DskipTests -q

# ============================================================
# 阶段二：运行镜像
# ============================================================
FROM ${JRE_IMAGE}

WORKDIR /app

# 复制构建产物
COPY --from=build /build/runner-websocket/target/runner-websocket-*.jar app.jar

EXPOSE 3064

ENV TZ=Asia/Shanghai

# 直接以 root 启动，无需用户降权（WebSocket 服务不执行用户脚本，只做消息中转）
ENTRYPOINT exec java \
    -XX:MaxRAMPercentage=${WEBSOCKET_JAVA_HEAP_PERCENTAGE:-50.0} \
    -XX:InitialRAMPercentage=${WEBSOCKET_JAVA_HEAP_PERCENTAGE:-50.0} \
    -XX:+UseG1GC \
    -XX:+UseContainerSupport \
    -jar /app/app.jar
