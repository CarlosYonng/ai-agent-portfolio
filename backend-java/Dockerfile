# Java 后端 Dockerfile。
# 多阶段构建能让最终镜像只包含运行所需的 jar，并复用 Maven 依赖缓存。

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY agent-domain/pom.xml agent-domain/pom.xml
COPY agent-infrastructure/pom.xml agent-infrastructure/pom.xml
COPY agent-application/pom.xml agent-application/pom.xml
COPY agent-api/pom.xml agent-api/pom.xml
COPY agent-boot/pom.xml agent-boot/pom.xml
RUN mvn -q -pl agent-boot -am -DskipTests dependency:go-offline
COPY agent-domain/src agent-domain/src
COPY agent-infrastructure/src agent-infrastructure/src
COPY agent-application/src agent-application/src
COPY agent-api/src agent-api/src
COPY agent-boot/src agent-boot/src
RUN mvn -q -pl agent-boot -am -DskipTests package

FROM eclipse-temurin:21-jre

LABEL org.opencontainers.image.version="1.1.0" \
      org.opencontainers.image.ref.name="agent-backend-java"
WORKDIR /app
COPY --from=build /app/agent-boot/target/agent-boot-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
