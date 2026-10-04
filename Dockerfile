# ---- etapa de build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# Primero solo el pom, para cachear las dependencias entre builds.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
ARG MODULO=integrado
RUN case "$MODULO" in \
      integrado) perfil=""; artefacto="ticketera-backend" ;; \
      reclamos|ia) perfil="-P$MODULO"; artefacto="ticketera-$MODULO" ;; \
      *) echo "MODULO debe ser integrado, reclamos o ia" >&2; exit 1 ;; \
    esac && \
    mvn -B -q clean package -DskipTests $perfil && \
    cp "target/$artefacto.jar" /build/app.jar

# ---- etapa de runtime ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S ticketera && adduser -S ticketera -G ticketera
COPY --from=build /build/app.jar app.jar
USER ticketera
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
