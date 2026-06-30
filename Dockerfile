# ---- Build Stage ----
FROM amazoncorretto:25-al2023 AS build

WORKDIR /build

# Copy maven wrapper
COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .

RUN chmod +x mvnw

# Cache dependencies
RUN ./mvnw dependency:go-offline --batch-mode

# Copy source and build
COPY src ./src
RUN ./mvnw clean package -DskipTests --batch-mode


# ---- Runtime Stage ----
FROM amazoncorretto:25-al2023-headless

WORKDIR /app

# Create a non-root user
RUN dnf install -y shadow-utils && \
    groupadd -r cyrus && \
    useradd -r -g cyrus cyrus && \
    dnf clean all && \
    rm -rf /var/cache/dnf

# Copy the built jar
COPY --from=build /build/target/*.jar app.jar

# Change ownership
RUN chown cyrus:cyrus app.jar

USER cyrus

ENV APP_ENV=staging
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=$APP_ENV -jar app.jar"]

LABEL authors="VICTOR"