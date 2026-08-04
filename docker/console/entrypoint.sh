#!/bin/sh
set -e
# ============================================================
# runner-console 容器启动脚本
# 功能：
#   1. 以 root 身份直接启动 Java 进程，容器内拥有完整文件系统权限
#      （可随意创建文件、下载依赖，安全边界由容器隔离承担）
#   2. 通过 -XX:MaxRAMPercentage 让 JVM 自动按容器内存上限比例分配堆
# 注意：脚本必须保持 LF 换行；CRLF 会导致 Linux 容器 shebang 解析失败
# ============================================================

# JVM 堆占容器内存上限的百分比，默认 75%
# -XX:+UseContainerSupport（Java 10+ 默认开启）让 JVM 自动读取 cgroup 限制
HEAP_PERCENTAGE=${CONSOLE_JAVA_HEAP_PERCENTAGE:-75.0}

echo "runner-console JVM MaxRAMPercentage=${HEAP_PERCENTAGE}%"
exec java \
    -XX:MaxRAMPercentage=${HEAP_PERCENTAGE} \
    -XX:InitialRAMPercentage=${HEAP_PERCENTAGE} \
    -XX:+UseG1GC \
    -XX:+UseContainerSupport \
    -jar /app/app.jar
