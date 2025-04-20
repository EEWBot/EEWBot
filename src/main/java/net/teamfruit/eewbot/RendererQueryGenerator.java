package net.teamfruit.eewbot;

import net.teamfruit.eewbot.entity.jma.telegram.VXSE53;
import net.teamfruit.eewbot.entity.jma.telegram.VXSE53Impl;
import net.teamfruit.eewbot.entity.renderer.QuakeDataFactory;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

public class RendererQueryGenerator {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java RendererQueryGenerator <HMAC_KEY> <PATH_TO_XML>");
            System.exit(1);
        }
        String hmacKey = args[0];
        String path = args[1];

        try (var inputStream = new FileInputStream(path)) {
            VXSE53 report = EEWBot.XML_MAPPER.readValue(inputStream, VXSE53Impl.class);
            String data = QuakeDataFactory.generate(hmacKey.getBytes(StandardCharsets.UTF_8), report);
            Log.logger.info(data);
        }
    }
}
