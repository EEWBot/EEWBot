package net.teamfruit.eewbot.registry;

import net.teamfruit.eewbot.Log;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;

@SuppressWarnings("FieldMayBeFinal")
public class Config {

    private String token = "";
    private boolean enableKyoshin = false;
    private int kyoshinDelay = 1;
    private boolean enableLegacyQuakeInfo = true;
    private int quakeInfoDelay = 15;
    private String dmdataAPIKey = "";
    private String dmdataOrigin = "";
    private boolean dmdataMultiSocketConnect = false;
    private String duplicatorAddress = "";
    private int poolingMax = 20;
    private int poolingMaxPerRoute = 20;
    private boolean webhookMigration = false;
    private String redisAddress = "";
    private String nptServer = "time.google.com";
    private String defaultLanuage = "ja_jp";
    private boolean debug = false;

    public Config() {
    }

    public String getToken() {
        return this.token;
    }

    public void setToken(final String token) {
        this.token = token;
    }

    public boolean isEnableKyoshin() {
        return this.enableKyoshin;
    }

    public int getKyoshinDelay() {
        return Math.max(this.kyoshinDelay, 1);
    }

    public boolean isEnableLegacyQuakeInfo() {
        return this.enableLegacyQuakeInfo;
    }

    public int getQuakeInfoDelay() {
        return Math.max(this.quakeInfoDelay, 10);
    }

    public String getDmdataAPIKey() {
        return this.dmdataAPIKey;
    }

    public void setDmdataAPIKey(String dmdataAPIKey) {
        this.dmdataAPIKey = dmdataAPIKey;
    }

    public String getDmdataOrigin() {
        return this.dmdataOrigin;
    }

    public boolean isDmdataMultiSocketConnect() {
        return this.dmdataMultiSocketConnect;
    }

    public String getDuplicatorAddress() {
        return this.duplicatorAddress;
    }

    public int getPoolingMax() {
        return this.poolingMax;
    }

    public int getPoolingMaxPerRoute() {
        return this.poolingMaxPerRoute;
    }

    public boolean isWebhookMigration() {
        return this.webhookMigration;
    }

    public String getRedisAddress() {
        return this.redisAddress;
    }

    public String getNptServer() {
        return this.nptServer;
    }

    public String getDefaultLanguage() {
        return this.defaultLanuage;
    }

    public boolean isDebug() {
        return this.debug;
    }

    public boolean validate() {
        if (StringUtils.isEmpty(getToken())) {
            Log.logger.info("Please set a discord token");
            return false;
        }
        boolean isDmdataAPIKeyProvided = !StringUtils.isEmpty(getDmdataAPIKey());
        if (!isEnableKyoshin() && !isDmdataAPIKeyProvided) {
            Log.logger.info("Please set a DMDATA API key");
            return false;
        }
        if (isEnableKyoshin() && isDmdataAPIKeyProvided) {
            this.enableKyoshin = false;
            Log.logger.info("Dmdata API key provided, disabling Kyoshin");
        }
        if (isEnableKyoshin()) {
            Log.logger.warn("Kyoshin EEW is enabled, please consider using DMDATA");
        }
        if (StringUtils.isNotEmpty(getDuplicatorAddress())) {
            try {
                new URI(getDuplicatorAddress());
            } catch (URISyntaxException e) {
                Log.logger.info("Invalid duplicator address");
                return false;
            }
        }
        if (StringUtils.isNotEmpty(getRedisAddress())) {
            try {
                new URI(getRedisAddress());
            } catch (URISyntaxException e) {
                Log.logger.info("Invalid redis address");
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "Config{" +
                "token='" + this.token + '\'' +
                ", enableKyoshin=" + this.enableKyoshin +
                ", kyoshinDelay=" + this.kyoshinDelay +
                ", enableLegacyQuakeInfo=" + this.enableLegacyQuakeInfo +
                ", quakeInfoDelay=" + this.quakeInfoDelay +
                ", dmdataAPIKey='" + this.dmdataAPIKey + '\'' +
                ", dmdataOrigin='" + this.dmdataOrigin + '\'' +
                ", dmdataMultiSocketConnect=" + this.dmdataMultiSocketConnect +
                ", duplicatorAddress='" + this.duplicatorAddress + '\'' +
                ", poolingMax=" + this.poolingMax +
                ", poolingMaxPerRoute=" + this.poolingMaxPerRoute +
                ", webhookMigration=" + this.webhookMigration +
                ", redisAddress='" + this.redisAddress + '\'' +
                ", nptServer='" + this.nptServer + '\'' +
                ", defaultLanuage='" + this.defaultLanuage + '\'' +
                ", debug=" + this.debug +
                '}';
    }
}
