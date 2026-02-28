package net.teamfruit.eewbot.registry.config;

import net.teamfruit.eewbot.Log;
import org.apache.commons.lang3.StringUtils;

@SuppressWarnings("FieldMayBeFinal")
public class ConfigV2 {

    private Base base = new Base();
    private Database database = new Database();
    private Redis redis = new Redis();
    private DMData dmdata = new DMData();
    private Renderer renderer = new Renderer();
    private WebhookSender webhookSender = new WebhookSender();
    private ExternalWebhook externalWebhook = new ExternalWebhook();
    private Advanced advanced = new Advanced();
    private Legacy legacy = new Legacy();

    public Base getBase() {
        return this.base;
    }

    public Database getDatabase() {
        return this.database;
    }

    public Redis getRedis() {
        return this.redis;
    }

    public DMData getDmdata() {
        return this.dmdata;
    }

    public Renderer getRenderer() {
        return this.renderer;
    }

    public WebhookSender getWebhookSender() {
        return this.webhookSender;
    }

    public ExternalWebhook getExternalWebhook() {
        return this.externalWebhook;
    }

    public Advanced getAdvanced() {
        return this.advanced;
    }

    public Legacy getLegacy() {
        return this.legacy;
    }

    public static class Base {

        private String discordToken = "";
        private String defaultLanguage = "ja_JP";

        public String getDiscordToken() {
            return this.discordToken;
        }

        public void setDiscordToken(final String discordToken) {
            this.discordToken = discordToken;
        }

        public String getDefaultLanguage() {
            return this.defaultLanguage;
        }

        public void setDefaultLanguage(final String defaultLanguage) {
            this.defaultLanguage = defaultLanguage;
        }

        @Override
        public String toString() {
            return "Base{" +
                    "discordToken='" + this.discordToken + '\'' +
                    ", defaultLanguage='" + this.defaultLanguage + '\'' +
                    '}';
        }
    }

    public static class Database {

        private String type = "";
        private SQLite sqlite = new SQLite();
        private PostgreSQL postgresql = new PostgreSQL();

        public String getType() {
            return this.type;
        }

        public void setType(final String type) {
            this.type = type;
        }

        public SQLite getSqlite() {
            return this.sqlite;
        }

        public void setSqlite(final SQLite sqlite) {
            this.sqlite = sqlite;
        }

        public PostgreSQL getPostgresql() {
            return this.postgresql;
        }

        public void setPostgresql(final PostgreSQL postgresql) {
            this.postgresql = postgresql;
        }

        @Override
        public String toString() {
            return "Database{" +
                    "type='" + this.type + '\'' +
                    ", sqlite=" + this.sqlite +
                    ", postgresql=" + this.postgresql +
                    '}';
        }
    }

    public static class SQLite {

        private String path = "channels.db";

        public String getPath() {
            return this.path;
        }

        public void setPath(final String path) {
            this.path = path;
        }

        @Override
        public String toString() {
            return "SQLite{" +
                    "path='" + this.path + '\'' +
                    '}';
        }
    }

    public static class PostgreSQL {

        private String host = "localhost";
        private int port = 5432;
        private String database = "eewbot";
        private String username = "";
        private String password = "";
        private int maxPoolSize = 10;
        private int minIdle = 2;

        public String getHost() {
            return this.host;
        }

        public void setHost(final String host) {
            this.host = host;
        }

        public int getPort() {
            return this.port;
        }

        public void setPort(final int port) {
            this.port = port;
        }

        public String getDatabase() {
            return this.database;
        }

        public void setDatabase(final String database) {
            this.database = database;
        }

        public String getUsername() {
            return this.username;
        }

        public void setUsername(final String username) {
            this.username = username;
        }

        public String getPassword() {
            return this.password;
        }

        public void setPassword(final String password) {
            this.password = password;
        }

        public int getMaxPoolSize() {
            return this.maxPoolSize;
        }

        public void setMaxPoolSize(final int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getMinIdle() {
            return this.minIdle;
        }

        public void setMinIdle(final int minIdle) {
            this.minIdle = minIdle;
        }

        public String getJdbcUrl() {
            return "jdbc:postgresql://" + this.host + ":" + this.port + "/" + this.database;
        }

        @Override
        public String toString() {
            return "PostgreSQL{" +
                    "host='" + this.host + '\'' +
                    ", port=" + this.port +
                    ", database='" + this.database + '\'' +
                    ", username='" + this.username + '\'' +
                    ", maxPoolSize=" + this.maxPoolSize +
                    ", minIdle=" + this.minIdle +
                    '}';
        }
    }

    public static class Redis {

        private String address = "";

        public String getAddress() {
            return this.address;
        }

        public void setRedisAddress(final String address) {
            this.address = address;
        }

        @Override
        public String toString() {
            return "Redis{" +
                    "address='" + this.address + '\'' +
                    '}';
        }
    }

    public static class DMData {

        private String apiKey = "";
        private String origin = "";
        private boolean multiSocketConnect = false;

        public String getAPIKey() {
            return this.apiKey;
        }

        public void setAPIKey(final String apiKey) {
            this.apiKey = apiKey;
        }

        public String getOrigin() {
            return this.origin;
        }

        public void setOrigin(final String origin) {
            this.origin = origin;
        }

        public boolean isMultiSocketConnect() {
            return this.multiSocketConnect;
        }

        public void setMultiSocketConnect(final boolean multiSocketConnect) {
            this.multiSocketConnect = multiSocketConnect;
        }

        public String toString() {
            return "DMData{" +
                    "apiKey='" + this.apiKey + '\'' +
                    ", origin='" + this.origin + '\'' +
                    ", multiSocketConnect=" + this.multiSocketConnect +
                    '}';
        }
    }

    public static class Renderer {

        private String address = "";
        private String key = "";

        public String getAddress() {
            return this.address;
        }

        public void setAddress(final String address) {
            this.address = address;
        }

        public String getKey() {
            return this.key;
        }

        public void setKey(final String key) {
            this.key = key;
        }

        public String toString() {
            return "Renderer{" +
                    "address='" + this.address + '\'' +
                    ", key='" + this.key + '\'' +
                    '}';
        }
    }

    public static class WebhookSender {

        private String address = "";
        private String customHeader = "";

        public String getAddress() {
            return this.address;
        }

        public void setAddress(final String address) {
            this.address = address;
        }

        public String getCustomHeader() {
            return this.customHeader;
        }

        public void setCustomHeader(final String customHeader) {
            this.customHeader = customHeader;
        }

        public String toString() {
            return "WebhookSender{" +
                    "address='" + this.address + '\'' +
                    ", customHeader='" + this.customHeader + '\'' +
                    '}';
        }
    }

    public static class Advanced {

        private int poolingMax = 20;
        private int poolingMaxPerRoute = 20;
        private boolean webhookMigration = false;

        public int getPoolingMax() {
            return this.poolingMax;
        }

        public void setPoolingMax(int poolingMax) {
            this.poolingMax = poolingMax;
        }

        public int getPoolingMaxPerRoute() {
            return this.poolingMaxPerRoute;
        }

        public void setPoolingMaxPerRoute(int poolingMaxPerRoute) {
            this.poolingMaxPerRoute = poolingMaxPerRoute;
        }

        public boolean isWebhookMigration() {
            return this.webhookMigration;
        }

        public void setWebhookMigration(boolean webhookMigration) {
            this.webhookMigration = webhookMigration;
        }

        @Override
        public String toString() {
            return "Advanced{" +
                    "poolingMax=" + this.poolingMax +
                    ", poolingMaxPerRoute=" + this.poolingMaxPerRoute +
                    ", webhookMigration=" + this.webhookMigration +
                    '}';
        }
    }

    public static class Legacy {

        private boolean enableKyoshin = false;
        private int kyoshinDelay = 1;
        private boolean enableLegacyQuakeInfo = false;
        private int legacyQuakeInfoDelay = 15;
        private String ntpServer = "time.google.com";

        public boolean isEnableKyoshin() {
            return this.enableKyoshin;
        }

        public void setEnableKyoshin(boolean enableKyoshin) {
            this.enableKyoshin = enableKyoshin;
        }

        public int getKyoshinDelay() {
            return this.kyoshinDelay;
        }

        public void setKyoshinDelay(int kyoshinDelay) {
            this.kyoshinDelay = Math.max(kyoshinDelay, 1);
        }

        public boolean isEnableLegacyQuakeInfo() {
            return this.enableLegacyQuakeInfo;
        }

        public void setEnableLegacyQuakeInfo(boolean enableLegacyQuakeInfo) {
            this.enableLegacyQuakeInfo = enableLegacyQuakeInfo;
        }

        public int getLegacyQuakeInfoDelay() {
            return this.legacyQuakeInfoDelay;
        }

        public void setLegacyQuakeInfoDelay(int legacyQuakeInfoDelay) {
            this.legacyQuakeInfoDelay = Math.max(legacyQuakeInfoDelay, 10);
        }

        public String getNtpServer() {
            return this.ntpServer;
        }

        public void setNtpServer(String ntpServer) {
            this.ntpServer = ntpServer;
        }

        @Override
        public String toString() {
            return "Legacy{" +
                    "enableKyoshin=" + this.enableKyoshin +
                    ", kyoshinDelay=" + this.kyoshinDelay +
                    ", enableLegacyQuakeInfo=" + this.enableLegacyQuakeInfo +
                    ", legacyQuakeInfoDelay=" + this.legacyQuakeInfoDelay +
                    ", ntpServer='" + this.ntpServer + '\'' +
                    '}';
        }
    }

    public static class ExternalWebhook {

        private java.util.List<String> urls = new java.util.ArrayList<>();

        public java.util.List<String> getUrls() {
            return this.urls;
        }

        public void setUrls(java.util.List<String> urls) {
            this.urls = urls;
        }

        @Override
        public String toString() {
            return "ExternalWebhook{" +
                    "urls=" + this.urls +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "ConfigV2{" +
                "base=" + this.base +
                ", database=" + this.database +
                ", dmdata=" + this.dmdata +
                ", renderer=" + this.renderer +
                ", webhookSender=" + this.webhookSender +
                ", externalWebhook=" + this.externalWebhook +
                ", advanced=" + this.advanced +
                ", legacy=" + this.legacy +
                '}';
    }

    public boolean isValid() {
        boolean errored = false;
        if (StringUtils.isEmpty(getBase().getDiscordToken())) {
            Log.logger.info("Please set a discord token");
            errored = true;
        }
        if (!getLegacy().isEnableKyoshin() && StringUtils.isEmpty(getDmdata().getAPIKey())) {
            Log.logger.info("Please set a DMDATA API key");
            errored = true;
        }
        if (getLegacy().isEnableKyoshin() && StringUtils.isNotEmpty(getDmdata().getAPIKey())) {
            getLegacy().setEnableKyoshin(false);
            Log.logger.info("Dmdata API key provided, disabling Kyoshin");
        }
        if (getLegacy().isEnableKyoshin()) {
            Log.logger.warn("Kyoshin EEW is enabled, please consider using DMDATA");
        }
        return !errored;
    }

    public static ConfigV2 fromV1(Config config) {
        ConfigV2 configV2 = new ConfigV2();

        configV2.base.setDiscordToken(config.getToken());
        configV2.base.setDefaultLanguage(config.getDefaultLanguage());
        configV2.redis.setRedisAddress(config.getRedisAddress());
        configV2.dmdata.setAPIKey(config.getDmdataAPIKey());
        configV2.dmdata.setOrigin(config.getDmdataOrigin());
        configV2.dmdata.setMultiSocketConnect(config.isDmdataMultiSocketConnect());
        configV2.advanced.setPoolingMax(config.getPoolingMax());
        configV2.advanced.setPoolingMaxPerRoute(config.getPoolingMaxPerRoute());
        configV2.advanced.setWebhookMigration(config.isWebhookMigration());
        configV2.legacy.setEnableKyoshin(config.isEnableKyoshin());
        configV2.legacy.setKyoshinDelay(config.getKyoshinDelay());
        configV2.legacy.setEnableLegacyQuakeInfo(config.isEnableLegacyQuakeInfo());
        configV2.legacy.setLegacyQuakeInfoDelay(config.getQuakeInfoDelay());
        configV2.legacy.setNtpServer(config.getNptServer());

        return configV2;
    }
}
