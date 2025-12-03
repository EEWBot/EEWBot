FROM amazoncorretto:21.0.9-alpine as build

RUN apk add --no-cache binutils

RUN jlink \
         --add-modules java.base,java.compiler,java.desktop,java.naming,java.net.http,java.security.jgss,java.sql,jdk.unsupported,jdk.crypto.ec,java.management \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress=2 \
         --output jre-slim

FROM alpine:3.22.2

ENV JAVA_HOME=/jre
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY --from=build jre-slim $JAVA_HOME

COPY build/libs/eewbot-*.jar eewbot.jar

ENV CONFIG_DIRECTORY=/etc/eewbot \
    DATA_DIRECTORY=/var/lib/eewbot \
    TZ=Asia/Tokyo

ENTRYPOINT ["java", "-jar", "eewbot.jar"]
