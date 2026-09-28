# ---- etapa de build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# Primero solo el pom, para cachear las dependencias entre builds.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- etapa de runtime ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S ticketera && adduser -S ticketera -G ticketera
COPY --from=build /build/target/ticketera-backend.jar app.jar
USER ticketera
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
