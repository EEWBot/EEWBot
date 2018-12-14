# 移行用。最初から各種jsonファイルを要求します。
# 現状のシステムだと一からDockerでデプロイするのが難しい・・・
FROM openjdk:8-jre-alpine

RUN apk --no-cache add curl \
 && curl -L https://github.com/Team-Fruit/EEWBot/releases/download/1.4.3/eewbot-1.4.3.jar -o eewbot.jar \
 && apk del --purge curl

ENV CONFIG_DIRECTORY=/config \
    DATA_DIRECTORY=/data

VOLUME ${DATA_DIRECTORY}

# COPY channels.json ${DATA_DIRECTORY}/channels.json
# COPY config.json ${CONFIG_DIRECTORY}/config.json
# COPY permission.json ${CONFIG_DIRECTORY}/permission.json

ENTRYPOINT ["java", "-jar", "eewbot.jar"]