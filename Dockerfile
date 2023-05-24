FROM amazoncorretto:17-alpine

COPY target/eewbot-*.jar eewbot.jar

ENV CONFIG_DIRECTORY=/etc/eewbot \
    DATA_DIRECTORY=/var/lib/eewbot \
    TZ=Asia/Tokyo

ENTRYPOINT ["java", "-jar", "eewbot.jar"]
