FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
# Mount settings.xml as a secret ONLY during build
RUN --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml \
    mvn -q -DskipTests clean package \

RUN mvn -q -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]