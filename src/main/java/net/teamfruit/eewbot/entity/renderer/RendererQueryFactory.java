package net.teamfruit.eewbot.entity.renderer;

import net.eewbot.CodeArray;
import net.eewbot.Epicenter;
import net.eewbot.QuakePrefectureV0;
import net.eewbot.TsunamiForecastV0;
import net.eewbot.base32768j.Base32768;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.TsunamiCategory;
import net.teamfruit.eewbot.entity.jma.telegram.common.Coordinate;
import net.teamfruit.eewbot.entity.jma.telegram.seis.*;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class RendererQueryFactory {

    private static final byte VERSIONED_TYPE_QUAKE_PREFECTURE = 0;
    private static final byte VERSIONED_TYPE_TSUNAMI = 1;
    private static final String HMAC_ALGO = "HmacSHA1";

    private static final EnumMap<SeismicIntensity, BiConsumer<QuakePrefectureV0.Builder, CodeArray>> SETTER_MAP =
            new EnumMap<>(SeismicIntensity.class);

    static {
        SETTER_MAP.put(SeismicIntensity.ONE, QuakePrefectureV0.Builder::one);
        SETTER_MAP.put(SeismicIntensity.TWO, QuakePrefectureV0.Builder::two);
        SETTER_MAP.put(SeismicIntensity.THREE, QuakePrefectureV0.Builder::three);
        SETTER_MAP.put(SeismicIntensity.FOUR, QuakePrefectureV0.Builder::four);
        SETTER_MAP.put(SeismicIntensity.FIVE_MINUS, QuakePrefectureV0.Builder::five_minus);
        SETTER_MAP.put(SeismicIntensity.FIVE_PLUS, QuakePrefectureV0.Builder::five_plus);
        SETTER_MAP.put(SeismicIntensity.SIX_MINUS, QuakePrefectureV0.Builder::six_minus);
        SETTER_MAP.put(SeismicIntensity.SIX_PLUS, QuakePrefectureV0.Builder::six_plus);
        SETTER_MAP.put(SeismicIntensity.SEVEN, QuakePrefectureV0.Builder::seven);
    }

    private final String baseURL;
    private final byte[] hmacKey;
    private final boolean isAvailable;

    private final ThreadLocal<Mac> threadLocalMac;

    public RendererQueryFactory(String baseURL, String hmacKey) {
        if (StringUtils.isEmpty(baseURL) || StringUtils.isEmpty(hmacKey)) {
            this.baseURL = null;
            this.hmacKey = null;
            this.threadLocalMac = null;
            this.isAvailable = false;
            return;
        }

        this.baseURL = baseURL;
        this.hmacKey = hmacKey.getBytes(StandardCharsets.UTF_8);

        this.threadLocalMac = ThreadLocal.withInitial(() -> {
            try {
                Mac mac = Mac.getInstance(HMAC_ALGO);
                mac.init(new SecretKeySpec(this.hmacKey, HMAC_ALGO));
                return mac;
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Failed to initialize HMAC Mac", e);
            }
        });
        this.isAvailable = true;
    }

    public boolean isAvailable() {
        return this.isAvailable;
    }

    private Epicenter buildEpicenter(Coordinate coordinate) {
        Float lat = coordinate.getLat(), lon = coordinate.getLon();
        if (lat == null || lon == null) {
            return null;
        }

        return new Epicenter.Builder()
                .lat_x10((int) (lat * 10))
                .lon_x10((int) (lon * 10))
                .build();
    }

    private Map<SeismicIntensity, List<Integer>> buildCodeMap(@Nullable Intensity.IntensityDetail observation) {
        Map<SeismicIntensity, List<Integer>> codeMap = new EnumMap<>(SeismicIntensity.class);
        for (SeismicIntensity intensity : SeismicIntensity.values()) {
            if (intensity != SeismicIntensity.UNKNOWN) {
                codeMap.put(intensity, new ArrayList<>());
            }
        }

        if (observation == null) {
            return codeMap;
        }

        for (IntensityPref pref : observation.getIntensityPref()) {
            for (IntensityArea area : pref.getAreas()) {
                codeMap.get(area.getMaxInt()).add(Integer.valueOf(area.getCode()));
            }
        }
        return codeMap;
    }

    private void applyCodeMap(QuakePrefectureV0.Builder builder, Map<SeismicIntensity, List<Integer>> codeMap) {
        codeMap.forEach((intensity, codes) -> {
            CodeArray codeArray = new CodeArray.Builder().codes(codes).build();
            BiConsumer<QuakePrefectureV0.Builder, CodeArray> setter = SETTER_MAP.get(intensity);
            if (setter != null) {
                setter.accept(builder, codeArray);
            }
        });
    }

    private byte[] computeQuery(byte versionedTypeId, byte[] body) {
        byte[] signingTarget = ByteBuffer.allocate(1 + body.length).put(versionedTypeId).put(body).array();
        byte[] hmac = this.threadLocalMac.get().doFinal(signingTarget);
        ByteBuffer buffer = ByteBuffer.allocate(1 + 1 + hmac.length + body.length);
        buffer.put(versionedTypeId).put((byte) 0xFF).put(hmac).put(body);
        return buffer.array();
    }

    private String generateQuakePrefectureV0(Instant time, @Nullable Coordinate coordinate, @Nullable Intensity.IntensityDetail observation) {
        if (coordinate == null && observation == null) {
            throw new IllegalArgumentException("Either coordinate or observation is required");
        }

        QuakePrefectureV0.Builder builder = new QuakePrefectureV0.Builder();
        builder.time(time.getEpochSecond());

        if (coordinate != null) {
            builder.epicenter(buildEpicenter(coordinate));
        }

        Map<SeismicIntensity, List<Integer>> codeMap = buildCodeMap(observation);
        applyCodeMap(builder, codeMap);

        byte[] body = QuakePrefectureV0.ADAPTER.encode(builder.build());
        byte[] query = computeQuery(VERSIONED_TYPE_QUAKE_PREFECTURE, body);
        return Base32768.getEncoder().encodeToString(query);
    }

    public String generateURL(RenderQuakePrefecture renderQuakePrefecture) {
        if (!isAvailable()) {
            throw new IllegalStateException("Renderer is not available");
        }

        String base32768Str = generateQuakePrefectureV0(renderQuakePrefecture.getTime(), renderQuakePrefecture.getCoordinate(), renderQuakePrefecture.getIntensityDetail());
        String normalizedBaseURL = this.baseURL.endsWith("/") ? this.baseURL : this.baseURL + "/";
        return normalizedBaseURL + base32768Str;
    }

    private String generateTsunamiData(Instant time, List<Coordinate> coordinates, List<TsunamiItem> forecastItems) {
        List<Integer> forecastCodes = new ArrayList<>();
        List<Integer> advisoryCodes = new ArrayList<>();
        List<Integer> warningCodes = new ArrayList<>();
        List<Integer> majorWarningCodes = new ArrayList<>();

        for (TsunamiItem item : forecastItems) {
            Category category = item.getCategory();
            if (category == null) {
                continue;
            }

            TsunamiCategory tsunamiCategory = TsunamiCategory.fromCode(category.getKind().getCode());
            int areaCode = Integer.parseInt(item.getArea().getCode());

            switch (tsunamiCategory.getLevel()) {
                case 1 -> forecastCodes.add(areaCode);
                case 2 -> advisoryCodes.add(areaCode);
                case 3 -> warningCodes.add(areaCode);
                case 4 -> majorWarningCodes.add(areaCode);
            }
        }

        TsunamiForecastV0.Builder builder = new TsunamiForecastV0.Builder();
        builder.time(time.getEpochSecond());

        List<Epicenter> epicenters = new ArrayList<>();
        for (Coordinate coordinate : coordinates) {
            Epicenter epicenter = buildEpicenter(coordinate);
            if (epicenter != null) {
                epicenters.add(epicenter);
            }
        }
        builder.epicenter(epicenters);

        builder.forecast(new CodeArray.Builder().codes(forecastCodes).build());
        builder.advisory(new CodeArray.Builder().codes(advisoryCodes).build());
        builder.warning(new CodeArray.Builder().codes(warningCodes).build());
        builder.major_warning(new CodeArray.Builder().codes(majorWarningCodes).build());

        byte[] body = TsunamiForecastV0.ADAPTER.encode(builder.build());
        byte[] query = computeQuery(VERSIONED_TYPE_TSUNAMI, body);
        return Base32768.getEncoder().encodeToString(query);
    }

    public String generateURL(RenderTsunami renderTsunami) {
        if (!isAvailable()) {
            throw new IllegalStateException("Renderer is not available");
        }

        String base32768Str = generateTsunamiData(renderTsunami.getTime(), renderTsunami.getCoordinates(), renderTsunami.getForecastItems());
        String normalizedBaseURL = this.baseURL.endsWith("/") ? this.baseURL : this.baseURL + "/";
        return normalizedBaseURL + base32768Str;
    }
}
