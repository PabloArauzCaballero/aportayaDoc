# syntax=docker/dockerfile:1.7
#
# Un Dockerfile y no catorce. Es la unica excepcion a "el servicio posee todos sus
# archivos", y esta justificada: no tiene contenido propio del servicio (ADR-025).
#
#   docker build -f despliegue/Dockerfile --build-arg SERVICIO=tarifas -t aportaya/tarifas .
#
# Sin root, sin `latest`, sin secretos en la imagen, y con las capas de Spring Boot
# separadas para que un cambio de codigo no empuje 200 MB de dependencias.

ARG SERVICIO
ARG MODULO=servicios

# ---------------------------------------------------------------- construccion --
FROM eclipse-temurin:21-jdk AS construccion
ARG SERVICIO
ARG MODULO
WORKDIR /construccion

# Python, y no por gusto: `comun-web:erroresCatalogo` genera el catalogo
# `constraint_name -> R-XXX-nn` con scripts/generar_errores.py, y sin el la imagen no
# compila. Vive solo en la etapa de construccion — la de runtime no lo lleva.
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 \
    && rm -rf /var/lib/apt/lists/*

# Primero lo que casi nunca cambia: asi el cache sobrevive a un cambio de codigo.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY buildSrc buildSrc
COPY bd bd

# El catalogo de errores se GENERA desde `sql/` en cada build: es lo que impide que
# el mensaje de una restriccion y su codigo AP-CU se separen. Por eso `sql/` y
# `scripts/` entran a la imagen de construccion, aunque no al runtime.
COPY scripts scripts
COPY sql/40_reglas/restricciones.sql sql/40_reglas/restricciones.sql
COPY sql/50_verificacion/prueba_humo.sql sql/50_verificacion/prueba_humo.sql

# Solo plataforma/ y EL servicio que se empaqueta: settings.gradle.kts descubre
# servicios/ por barrido, asi que la imagen de un servicio no arrastra a los otros
# trece.
COPY plataforma plataforma
COPY ${MODULO}/${SERVICIO} ${MODULO}/${SERVICIO}
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :${MODULO}:${SERVICIO}:bootJar -x test

RUN mkdir -p /capas && cd /capas \
    && java -Djarmode=tools -jar /construccion/${MODULO}/${SERVICIO}/build/libs/*.jar extract --layers --launcher --destination /capas

# --------------------------------------------------------------------- runtime --
FROM eclipse-temurin:21-jre-alpine AS runtime
ARG SERVICIO
ENV SERVICIO=${SERVICIO}

# Un proceso que corre como root dentro del contenedor convierte cualquier escape en
# un compromiso de la maquina.
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app

# El orden importa: de lo que menos cambia a lo que mas.
COPY --from=construccion --chown=app:app /capas/dependencies/ ./
COPY --from=construccion --chown=app:app /capas/spring-boot-loader/ ./
COPY --from=construccion --chown=app:app /capas/snapshot-dependencies/ ./
COPY --from=construccion --chown=app:app /capas/application/ ./

USER app
EXPOSE 8080

# Readiness mira base y Kafka; liveness solo el proceso.
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", \
            "-Djava.security.egd=file:/dev/urandom", \
            "org.springframework.boot.loader.launch.JarLauncher"]
