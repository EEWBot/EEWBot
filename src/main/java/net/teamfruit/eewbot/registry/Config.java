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
    private int quakeInfoDelay = 15;
    private String dmdataAPIKey = "";
    private String dmdataOrigin = "";
    private boolean dmdataMultiSocketConnect = false;
    private String duplicatorAddress = "";
    private int poolingMax = 20;
    private int poolingMaxPerRoute = 20;
    private String nptServer = "time.google.com";
    private String defaultLanuage = "ja_jp";
    private String systemChannel = "";
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
        return enableKyoshin;
    }

    public int getKyoshinDelay() {
        return Math.max(this.kyoshinDelay, 1);
    }

    public int getQuakeInfoDelay() {
        return Math.max(this.quakeInfoDelay, 10);
    }

    public String getDmdataAPIKey() {
        return dmdataAPIKey;
    }

    public void setDmdataAPIKey(String dmdataAPIKey) {
        this.dmdataAPIKey = dmdataAPIKey;
    }

    public String getDmdataOrigin() {
        return dmdataOrigin;
    }

    public boolean isDmdataMultiSocketConnect() {
        return dmdataMultiSocketConnect;
    }

    public String getDuplicatorAddress() {
        return duplicatorAddress;
    }

    public int getPoolingMax() {
        return poolingMax;
    }

    public int getPoolingMaxPerRoute() {
        return poolingMaxPerRoute;
    }

    public String getNptServer() {
        return this.nptServer;
    }

    public String getDefaultLanuage() {
        return this.defaultLanuage;
    }

    public String getSystemChannel() {
        return this.systemChannel;
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
        return true;
    }

    @Override
    public String toString() {
        return "Config{" +
                "token='" + token + '\'' +
                ", enableKyoshin=" + enableKyoshin +
                ", kyoshinDelay=" + kyoshinDelay +
                ", quakeInfoDelay=" + quakeInfoDelay +
                ", dmdataAPIKey='" + dmdataAPIKey + '\'' +
                ", dmdataOrigin='" + dmdataOrigin + '\'' +
                ", dmdataMultiSocketConnect=" + dmdataMultiSocketConnect +
                ", poolingMax=" + poolingMax +
                ", poolingMaxPerRoute=" + poolingMaxPerRoute +
                ", nptServer='" + nptServer + '\'' +
                ", defaultLanuage='" + defaultLanuage + '\'' +
                ", systemChannel='" + systemChannel + '\'' +
                ", debug=" + debug +
                '}';
    }

}
