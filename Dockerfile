FROM eclipse-temurin:21-jre-alpine

RUN apk upgrade --no-cache \
    && addgroup -S -g 10001 app && adduser -S -u 10001 app -G app

WORKDIR /app
COPY target/finsecure-pay.jar app.jar

USER 10001
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s \
  CMD wget -qO- http://localhost:8080/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
