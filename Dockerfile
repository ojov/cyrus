# ---- Build Stage ----
FROM amazoncorretto:25-al2023 AS build

WORKDIR /build

RUN dnf install -y tar gzip && \
    dnf clean all && \
    rm -rf /var/cache/dnf

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x mvnw

RUN ./mvnw dependency:go-offline --batch-mode

COPY src ./src

RUN ./mvnw clean package -DskipTests --batch-mode


# ---- Runtime Stage ----
FROM amazoncorretto:25-al2023-headless

WORKDIR /app

RUN dnf install -y shadow-utils && \
    groupadd -r cyrus && \
    useradd -r -g cyrus cyrus && \
    dnf clean all && \
    rm -rf /var/cache/dnf

COPY --from=build /build/target/*.jar app.jar

RUN chown cyrus:cyrus app.jar

USER cyrus

ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

LABEL authors="VICTOR"
