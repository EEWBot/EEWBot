FROM openjdk:8-jre-alpine

RUN apk --no-cache add curl \
 && curl -L https://github.com/Team-Fruit/EEWBot/releases/download/2.2.0/eewbot-2.2.0.jar -o eewbot.jar \
 && apk del --purge curl

ENV CONFIG_DIRECTORY=/etc/eewbot \
    DATA_DIRECTORY=/var/lib/eewbot \
    TZ=Asia/Tokyo

ENTRYPOINT ["java", "-jar", "eewbot.jar"]
