FROM openjdk:8-jre-alpine

RUN apk --no-cache add curl \
 && curl -L https://github.com/Team-Fruit/EEWBot/releases/download/1.4.4/eewbot-1.4.4.jar -o eewbot.jar \
 && apk del --purge curl

ENV CONFIG_DIRECTORY=/etc/eewbot \
    DATA_DIRECTORY=/var/lib/eewbot \
    TZ=Asia/Tokyo

VOLUME ${DATA_DIRECTORY}

ENTRYPOINT ["java", "-jar", "eewbot.jar"]