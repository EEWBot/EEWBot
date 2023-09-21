package net.teamfruit.eewbot.registry;

import net.teamfruit.eewbot.Log;
import org.apache.commons.lang3.StringUtils;

public class Config {
    private String token = "";
    private boolean enableKyoshin = false;
    private final int kyoshinDelay = 1;
    private final int quakeInfoDelay = 15;
    private final String dmdataAPIKey = "";
    private final String dmdataOrigin = "";
    private final boolean dmdataMultiSocketConnect = false;
    private final String nptServer = "time.google.com";
    private final String defaultLanuage = "ja_jp";
    private final boolean enablePermission = true;
    private final String systemChannel = "";
    private final boolean debug = false;

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

    public String getDmdataOrigin() {
        return dmdataOrigin;
    }

    public boolean isDmdataMultiSocketConnect() {
        return dmdataMultiSocketConnect;
    }

    public String getNptServer() {
        return this.nptServer;
    }

    public String getDefaultLanuage() {
        return this.defaultLanuage;
    }

    public boolean isEnablePermission() {
        return this.enablePermission;
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
        boolean enableDmdata = !StringUtils.isEmpty(getDmdataAPIKey());
        if (!isEnableKyoshin() && !enableDmdata) {
            Log.logger.info("Please set a DMDATA API key");
            return false;
        }
        if (isEnableKyoshin() && enableDmdata) {
            this.enableKyoshin = false;
            Log.logger.info("Dmdata API key provided, disabling Kyoshin");
        }
        if (isEnableKyoshin()) {
            Log.logger.warn("Kyoshin EEW is enabled, please consider using DMDATA");
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
                ", nptServer='" + nptServer + '\'' +
                ", defaultLanuage='" + defaultLanuage + '\'' +
                ", enablePermission=" + enablePermission +
                ", systemChannel='" + systemChannel + '\'' +
                ", debug=" + debug +
                '}';
    }
}
