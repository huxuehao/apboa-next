#!/bin/sh
set -e

HEAP_PERCENTAGE=${GATEWAY_JAVA_HEAP_PERCENTAGE:-75.0}

echo "runner-gateway JVM MaxRAMPercentage=${HEAP_PERCENTAGE}%"
exec java \
    -XX:MaxRAMPercentage=${HEAP_PERCENTAGE} \
    -XX:InitialRAMPercentage=${HEAP_PERCENTAGE} \
    -XX:+UseG1GC \
    -XX:+UseContainerSupport \
    -XX:+ExitOnOutOfMemoryError \
    -jar /app/app.jar
