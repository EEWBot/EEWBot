package net.teamfruit.eewbot.entity.renderer;

import net.eewbot.base65536j.Base65536;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.telegram.common.Coordinate;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Intensity;
import net.teamfruit.eewbot.entity.jma.telegram.seis.IntensityArea;
import net.teamfruit.eewbot.entity.jma.telegram.seis.IntensityPref;
import quake_prefecture_v0.CodeArray;
import quake_prefecture_v0.Epicenter;
import quake_prefecture_v0.QuakePrefectureData;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class QuakeDataFactory {

    private static final byte VERSION = 0;
    private static final String HMAC_ALGO = "HmacSHA1";

    private static final EnumMap<SeismicIntensity, BiConsumer<QuakePrefectureData.Builder, CodeArray>> SETTER_MAP =
            new EnumMap<>(SeismicIntensity.class);

    static {
        SETTER_MAP.put(SeismicIntensity.ONE, QuakePrefectureData.Builder::one);
        SETTER_MAP.put(SeismicIntensity.TWO, QuakePrefectureData.Builder::two);
        SETTER_MAP.put(SeismicIntensity.THREE, QuakePrefectureData.Builder::three);
        SETTER_MAP.put(SeismicIntensity.FOUR, QuakePrefectureData.Builder::four);
        SETTER_MAP.put(SeismicIntensity.FIVE_MINUS, QuakePrefectureData.Builder::five_minus);
        SETTER_MAP.put(SeismicIntensity.FIVE_PLUS, QuakePrefectureData.Builder::five_plus);
        SETTER_MAP.put(SeismicIntensity.SIX_MINUS, QuakePrefectureData.Builder::six_minus);
        SETTER_MAP.put(SeismicIntensity.SIX_PLUS, QuakePrefectureData.Builder::six_plus);
        SETTER_MAP.put(SeismicIntensity.SEVEN, QuakePrefectureData.Builder::seven);
    }

    private QuakeDataFactory() {
    }

    public static String generateQuakeOrefectureData(Coordinate coordinate, Intensity.IntensityDetail observation) throws NoSuchAlgorithmException, InvalidKeyException {
        Float lat = coordinate.getLat();
        Float lon = coordinate.getLon();

        Epicenter epicenter = null;
        if (lat != null && lon != null) {
            epicenter = new Epicenter.Builder()
                    .lat_x10((int) (lat * 10))
                    .lon_x10((int) (lon * 10))
                    .build();
        }

        Map<SeismicIntensity, List<Integer>> codeMap = new EnumMap<>(SeismicIntensity.class);
        for (SeismicIntensity intensity : SeismicIntensity.values()) {
            if (intensity != SeismicIntensity.UNKNOWN) {
                codeMap.put(intensity, new ArrayList<>());
            }
        }
        for (IntensityPref pref : observation.getIntensityPref()) {
            for (IntensityArea area : pref.getAreas()) {
                codeMap.get(area.getMaxInt()).add(Integer.valueOf(area.getCode()));
            }
        }

        QuakePrefectureData.Builder builder = new QuakePrefectureData.Builder();
        if (epicenter != null) {
            builder.epicenter(epicenter);
        }

        codeMap.forEach((intensity, codes) -> {
            CodeArray codeArray = new CodeArray.Builder().codes(codes).build();
            BiConsumer<QuakePrefectureData.Builder, CodeArray> setter = SETTER_MAP.get(intensity);
            if (setter != null) {
                setter.accept(builder, codeArray);
            }
        });

        QuakePrefectureData quakePrefectureData = builder.build();
        byte[] body = QuakePrefectureData.ADAPTER.encode(quakePrefectureData);

        Mac mac = Mac.getInstance(HMAC_ALGO);
        mac.init(new SecretKeySpec(EEWBot.instance.getConfig().getRendererKey().getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
        byte[] hmac = mac.doFinal(body);

        ByteBuffer buffer = ByteBuffer.allocate(1 + hmac.length + body.length);
        buffer.put(VERSION);
        buffer.put(hmac);
        buffer.put(body);

        ByteBuffer bufferBase65536 = Base65536.getEncoder().encode(buffer);
        return bufferBase65536.toString();
    }
}
