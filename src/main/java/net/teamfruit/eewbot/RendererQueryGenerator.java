package net.teamfruit.eewbot;

import net.teamfruit.eewbot.entity.jma.telegram.VTSE41;
import net.teamfruit.eewbot.entity.jma.telegram.VTSE41Impl;
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

        RendererQueryFactory rendererQueryFactory = new RendererQueryFactory("http://localhost:3000", hmacKey);

        try (var inputStream = new FileInputStream(path)) {
            VTSE41 report = Codecs.XML_MAPPER.readValue(inputStream, VTSE41Impl.class);
            String data = rendererQueryFactory.generateURL(report);
            Log.logger.info(data);
        }
    }
}
