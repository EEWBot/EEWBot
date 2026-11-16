package net.teamfruit.eewbot.gateway;

import com.fasterxml.jackson.core.JacksonException;
import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.entity.dmdata.ws.DmdataWSData;
import net.teamfruit.eewbot.entity.jma.telegram.AbstractEEW;
import net.teamfruit.eewbot.entity.jma.telegram.VXSE43Impl;
import net.teamfruit.eewbot.entity.jma.telegram.VXSE45Impl;
import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

/** Turns a DMDATA WebSocket data message into an EEW telegram. Stateless. */
final class DmdataTelegramDecoder {

    private DmdataTelegramDecoder() {
    }

    /** Returns the telegram XML, undoing the transport compression if any. */
    static String decodeBody(DmdataWSData data) throws IOException {
        if (Strings.CS.equals(data.getCompression(), "gzip"))
            return decompressGZIPBase64(data.getBody());
        if (Strings.CS.equals(data.getCompression(), "zip"))
            return decompressZipBase64(data.getBody());
        return data.getBody();
    }

    /** Returns null for telegram types this bot does not handle. */
    static @Nullable AbstractEEW parse(String telegramType, String xml) throws JacksonException {
        Class<? extends AbstractEEW> eewClass;
        if ("VXSE45".equals(telegramType)) {
            eewClass = VXSE45Impl.class;
        } else if ("VXSE43".equals(telegramType)) {
            eewClass = VXSE43Impl.class;
        } else {
            return null;
        }
        AbstractEEW eew = Codecs.XML_MAPPER.readValue(xml, eewClass);
        eew.setRawData(xml);
        return eew;
    }

    static String decompressGZIPBase64(String compressedBase64) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(compressedBase64)))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static String decompressZipBase64(String compressedBase64) throws IOException {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(compressedBase64)))) {
            in.getNextEntry();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
