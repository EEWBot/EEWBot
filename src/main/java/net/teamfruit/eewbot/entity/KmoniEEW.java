package net.teamfruit.eewbot.entity;

import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.Color;
import net.teamfruit.eewbot.i18n.I18nEmbedCreateSpec;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class KmoniEEW implements Entity {
    public static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").withZone(ZoneId.of("Asia/Tokyo"));

    private String alertflg;
    private String calcintensity;
    private String depth;
    private boolean is_cancel;
    private boolean is_final;
    private boolean is_training;
    private String latitude;
    private String longitude;
    private String magunitude;
    private String origin_time;
    private String region_code;
    private String region_name;
    private String report_id;
    private String report_num;
    private String report_time;
    private String request_hypo_type;
    private String request_time;
    private Result result;
    private Security security;

    private KmoniEEW prev;

    public static class Result {
        private boolean is_auth;
        private String message;
        private String status;

        public boolean isAuth() {
            return this.is_auth;
        }

        public String getMessage() {
            return this.message;
        }

        public boolean getStatus() {
            return "success".equals(this.status);
        }

        @Override
        public String toString() {
            return "Result [is_auth=" + this.is_auth + ", message=" + this.message + ", status=" + this.status + "]";
        }

    }

    public static class Security {
        private String hash;
        private String realm;

        public String getHash() {
            return this.hash;
        }

        public String getReam() {
            return this.realm;
        }

        @Override
        public String toString() {
            return "Security [hash=" + this.hash + ", realm=" + this.realm + "]";
        }
    }

    public boolean isEEW() {
        return getAlertFlg() != null;
    }

    public String getAlertFlg() {
        return this.alertflg;
    }

    public boolean isAlert() {
        return "警報".equals(this.alertflg);
    }

    public Optional<SeismicIntensity> getIntensity() {
        return SeismicIntensity.get(this.calcintensity);
    }

    public int getDepth() {
        if (StringUtils.isEmpty(this.depth))
            return -1;
        return Integer.parseInt(this.depth.substring(0, this.depth.length() - 2));
    }

    public boolean isCancel() {
        return this.is_cancel;
    }

    public boolean isInitial() {
        return getReportNum() == 1;
    }

    public boolean isFinal() {
        return this.is_final;
    }

    public boolean isTraining() {
        return this.is_training;
    }

    public float getLat() {
        if (StringUtils.isEmpty(this.latitude))
            return -1;
        return Float.parseFloat(this.latitude);
    }

    public float getLon() {
        if (StringUtils.isEmpty(this.longitude))
            return -1;
        return Float.parseFloat(this.longitude);
    }

    public float getMagnitude() {
        if (StringUtils.isEmpty(this.magunitude))
            return -1;
        return Float.parseFloat(this.magunitude);
    }

    public Instant getOriginTime() {
        if (StringUtils.isEmpty(this.origin_time))
            return null;
        return FORMAT.parse(this.origin_time, Instant::from);
    }

    @Deprecated
    public int getRegionCode() {
        if (StringUtils.isEmpty(this.region_code))
            return -1;
        return Integer.parseInt(this.region_code);
    }

    public String getRegionName() {
        return this.region_name;
    }

    public long getReportId() {
        if (StringUtils.isEmpty(this.report_id))
            return -1;
        return Long.parseLong(this.report_id);
    }

    public int getReportNum() {
        if (StringUtils.isEmpty(this.report_num))
            return -1;
        return Integer.parseInt(this.report_num);
    }

    public Instant getReportTime() {
        if (StringUtils.isEmpty(this.report_time))
            return null;
        return FORMAT.parse(this.report_time, Instant::from);
    }

    public String getRequestHypoType() {
        return this.request_hypo_type;
    }

    public Instant getRequestTime() {
        if (StringUtils.isEmpty(this.request_time))
            return null;
        return FORMAT.parse(this.request_time, Instant::from);
    }

    public KmoniEEW setPrev(final KmoniEEW eew) {
        this.prev = eew;
        return this;
    }

    public KmoniEEW getPrev() {
        return this.prev;
    }

    @Override
    public String toString() {
        return "EEW [alertflg=" + this.alertflg + ", calcintensity=" + this.calcintensity + ", depth=" + this.depth + ", is_cancel=" + this.is_cancel + ", is_final=" + this.is_final + ", is_training=" + this.is_training + ", latitude=" + this.latitude + ", longitude=" + this.longitude + ", magunitude=" + this.magunitude + ", origin_time=" + this.origin_time + ", region_code=" + this.region_code + ", region_name=" + this.region_name + ", report_id=" + this.report_id + ", report_num=" + this.report_num + ", report_time=" + this.report_time + ", request_hypo_type="
                + this.request_hypo_type + ", request_time=" + this.request_time + ", result=" + this.result
                + ", security=" + this.security + "]";
    }

    @Override
    public MessageCreateSpec createMessage(final String lang) {
        if (isCancel())
            return MessageCreateSpec.builder().addEmbed(I18nEmbedCreateSpec.builder(lang)
                    .title("eewbot.eew.eewcancel")
                    .timestamp(getReportTime())
                    .description("eewbot.eew.cancel")
                    .color(discord4j.rest.util.Color.YELLOW)
                    .footer("eewbot.eew.newkyoshinmonitor", null)
                    .build()).build();
        return MessageCreateSpec.builder().addEmbed(I18nEmbedCreateSpec.builder(lang)
                .title(isAlert() ? isFinal() ? "eewbot.eew.eewalert.final" : "eewbot.eew.eewalert.num" : isFinal() ? "eewbot.eew.eewprediction.final" : "eewbot.eew.eewprediction.num", getReportNum())
                .timestamp(getReportTime())
                .addField("eewbot.eew.epicenter", getRegionName(), true)
                .addField("eewbot.eew.depth", "eewbot.eew.km", true, getDepth())
                .addField("eewbot.eew.magnitude", String.valueOf(getMagnitude()), true)
                .addField("eewbot.eew.seismicintensity", getIntensity().map(SeismicIntensity::getSimple).orElse("eewbot.eew.unknown"), false)
                .color(isAlert() ? Color.RED : Color.BLUE)
                .footer("eewbot.eew.newkyoshinmonitor", null)
                .build()).build();
    }
}
