#!/bin/sh
set -e
# ============================================================
# runner-proxy 容器启动脚本
# 功能：
#   1. 修复 volume 挂载目录的权限（宿主机挂载覆盖镜像内属主）
#   2. 通过 -XX:MaxRAMPercentage 让 JVM 自动按容器内存上限比例分配堆
#   3. 以非 root 用户 shellproxy 启动 Java 进程
# 注意：脚本必须保持 LF 换行；CRLF 会导致 Linux 容器 shebang 解析失败
# ============================================================

# 修复 volume 挂载目录权限（失败不阻塞启动）
# /opt/tools 为 Python/Node 依赖安装卷，挂载后属主被宿主机覆盖，需一并修复
chown -R shellproxy:shellproxy /app/logs /app/.apboa /opt/tools 2>/dev/null || true

# JVM 堆占容器内存上限的百分比，默认 50%
# 剩余内存留给 shell 子进程
HEAP_PERCENTAGE=${SHELLPROXY_JAVA_HEAP_PERCENTAGE:-50.0}

echo "runner-proxy JVM MaxRAMPercentage=${HEAP_PERCENTAGE}%"
exec gosu shellproxy java \
    -XX:MaxRAMPercentage=${HEAP_PERCENTAGE} \
    -XX:InitialRAMPercentage=${HEAP_PERCENTAGE} \
    -XX:+UseContainerSupport \
    -XX:+ExitOnOutOfMemoryError \
    -Djava.io.tmpdir=/tmp \
    -jar /app/app.jar
