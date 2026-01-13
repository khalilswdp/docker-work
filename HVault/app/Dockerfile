FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src

# ---- Inject truststore into the builder JVM (for Maven HTTPS) ----
RUN --mount=type=secret,id=jdk_cacerts,target=/tmp/cacerts \
    set -e; \
    if [ -f /tmp/cacerts ]; then \
      echo "[INFO] Injecting custom JDK cacerts"; \
      echo "JAVA_HOME=$JAVA_HOME"; \
      cp /tmp/cacerts "$JAVA_HOME/lib/security/cacerts"; \
      chmod 644 "$JAVA_HOME/lib/security/cacerts"; \
    else \
      echo "[INFO] No custom JDK cacerts provided, skipping"; \
    fi

COPY pom.xml .
COPY src ./src
# Mount settings.xml as a secret ONLY during build
RUN --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml \
    set -e; \
    if [ -f /root/.m2/settings.xml ]; then \
      echo "[INFO] Using custom Maven settings.xml"; \
    else \
      echo "[INFO] No Maven settings.xml provided, using defaults"; \
    fi;

RUN mvn -q -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]