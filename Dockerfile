FROM openjdk:8-jre-alpine

RUN apk --no-cache add curl \
 && curl -L http://zerono.teamfruit.net/eewbot-2.0.1.jar -o eewbot.jar \
 && apk del --purge curl

ENV CONFIG_DIRECTORY=/etc/eewbot \
    DATA_DIRECTORY=/var/lib/eewbot \
    TZ=Asia/Tokyo

ENTRYPOINT ["java", "-jar", "eewbot.jar"]
