package net.teamfruit.eewbot.registry;

public class Config {
    private String token = "";
    private int kyoshinDelay = 1;
    private int quakeInfoDelay = 15;
    private String dmdataAPIKey = "";
    private String dmdataReferer = "";
    private String nptServer = "time.google.com";
    private String defaultLanuage = "ja_jp";
    private boolean enablePermission = true;
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

    public int getKyoshinDelay() {
        return Math.max(this.kyoshinDelay, 1);
    }

    public void setKyoshinDelay(final int kyoshinDelay) {
        this.kyoshinDelay = kyoshinDelay;
    }

    public int getQuakeInfoDelay() {
        return Math.max(this.quakeInfoDelay, 10);
    }

    public void setQuakeInfoDelay(final int quakeInfoDelay) {
        this.quakeInfoDelay = quakeInfoDelay;
    }

    public String getDmdataAPIKey() {
        return dmdataAPIKey;
    }

    public void setDmdataAPIKey(String dmdataAPIKey) {
        this.dmdataAPIKey = dmdataAPIKey;
    }

    public String getDmdataReferer() {
        return dmdataReferer;
    }

    public void setDmdataReferer(String dmdataReferer) {
        this.dmdataReferer = dmdataReferer;
    }

    public String getNptServer() {
        return this.nptServer;
    }

    public void setNptServer(final String nptServer) {
        this.nptServer = nptServer;
    }

    public String getDefaultLanuage() {
        return this.defaultLanuage;
    }

    public void setDefaultLanuage(final String defaultLanuage) {
        this.defaultLanuage = defaultLanuage;
    }

    public boolean isEnablePermission() {
        return this.enablePermission;
    }

    public void setEnablePermission(final boolean enablePermission) {
        this.enablePermission = enablePermission;
    }

    public String getSystemChannel() {
        return this.systemChannel;
    }

    public void setSystemChannel(final String systemChannel) {
        this.systemChannel = systemChannel;
    }

    public boolean isDebug() {
        return this.debug;
    }

    public void setDebug(final boolean debug) {
        this.debug = debug;
    }

    @Override
    public String toString() {
        return "Config{" +
                "token='" + token + '\'' +
                ", kyoshinDelay=" + kyoshinDelay +
                ", quakeInfoDelay=" + quakeInfoDelay +
                ", dmdataAPIKey='" + dmdataAPIKey + '\'' +
                ", dmdataReferer='" + dmdataReferer + '\'' +
                ", nptServer='" + nptServer + '\'' +
                ", defaultLanuage='" + defaultLanuage + '\'' +
                ", enablePermission=" + enablePermission +
                ", systemChannel='" + systemChannel + '\'' +
                ", debug=" + debug +
                '}';
    }
}
