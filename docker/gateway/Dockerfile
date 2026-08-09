ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre

FROM ${MAVEN_IMAGE} AS build
WORKDIR /build
COPY docker/maven/settings.xml /root/.m2/settings.xml
COPY . .
RUN mvn package -pl runner-gateway -am -DskipTests -q

FROM ${JRE_IMAGE}
WORKDIR /app
COPY --from=build /build/runner-gateway/target/runner-gateway-*.jar app.jar
RUN mkdir -p /app/logs /app/.apboa
EXPOSE 3065
ENV TZ=Asia/Shanghai
COPY docker/gateway/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh
ENTRYPOINT ["/app/entrypoint.sh"]
