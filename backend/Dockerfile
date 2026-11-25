FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src ./src
RUN ./gradlew build --no-daemon

FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring
RUN mkdir -p /var/lib/super-pont/logos && chown spring:spring /var/lib/super-pont/logos
COPY --from=build --chown=spring:spring /app/build/libs/app.jar app.jar
USER spring:spring
ENV TEAM_LOGO_CACHE_DIR=/var/lib/super-pont/logos

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

