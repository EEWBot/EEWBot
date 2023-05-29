FROM amazoncorretto:17.0.7-alpine as build

RUN apk add --no-cache binutils

RUN jlink \
         --add-modules java.base,java.compiler,java.desktop,java.naming,java.security.jgss,java.sql,jdk.unsupported,jdk.crypto.ec \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress=2 \
         --output jre-slim

FROM alpine:3.18.0

ENV JAVA_HOME=/jre
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY --from=build jre-slim $JAVA_HOME

COPY target/eewbot-*.jar eewbot.jar

ENV CONFIG_DIRECTORY=/etc/eewbot \
    DATA_DIRECTORY=/var/lib/eewbot \
    TZ=Asia/Tokyo

ENTRYPOINT ["java", "-jar", "eewbot.jar"]
