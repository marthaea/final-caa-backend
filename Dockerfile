FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src/ src/
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S caa && adduser -S -G caa caa \
    && mkdir -p /app/uploads \
    && chown -R caa:caa /app
WORKDIR /app
COPY --from=build /workspace/target/recruitment-*.jar app.jar
USER caa
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
