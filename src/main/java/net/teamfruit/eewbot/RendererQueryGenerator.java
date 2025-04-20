package net.teamfruit.eewbot;

import net.teamfruit.eewbot.entity.jma.telegram.VXSE53;
import net.teamfruit.eewbot.entity.jma.telegram.VXSE53Impl;
import net.teamfruit.eewbot.entity.renderer.RendererQueryFactory;

import java.io.FileInputStream;

public class RendererQueryGenerator {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java RendererQueryGenerator <HMAC_KEY> <PATH_TO_XML>");
            System.exit(1);
        }
        String hmacKey = args[0];
        String path = args[1];

        RendererQueryFactory rendererQueryFactory = new RendererQueryFactory("https://h.eewbot.net", hmacKey);

        try (var inputStream = new FileInputStream(path)) {
            VXSE53 report = EEWBot.XML_MAPPER.readValue(inputStream, VXSE53Impl.class);
            String data = rendererQueryFactory.generateURL(report);
            Log.logger.info(data);
        }
    }
}
