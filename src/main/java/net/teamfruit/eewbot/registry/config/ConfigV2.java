package net.teamfruit.eewbot.registry.config;

import net.teamfruit.eewbot.Log;
import org.apache.commons.lang3.StringUtils;

@SuppressWarnings("FieldMayBeFinal")
public class ConfigV2 {

    private Base base = new Base();
    private Database database = new Database();
    private DMData dmdata = new DMData();
    private Renderer renderer = new Renderer();
    private WebhookSender webhookSender = new WebhookSender();
    private ExternalWebhook externalWebhook = new ExternalWebhook();
    private Advanced advanced = new Advanced();
    private Debug debug = new Debug();

    public Base getBase() {
        return this.base;
    }

    public Database getDatabase() {
        return this.database;
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

    public Debug getDebug() {
        return this.debug;
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

        private String type = "sqlite";
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

        @Override
        public String toString() {
            return "Advanced{}";
        }
    }

    public static class Debug {

        private String jmaXmlFeedRoot = "";
        private int jmaXmlPollIntervalSeconds = 0;
        private String dmdataWsBaseUri = "";

        public String getJmaXmlFeedRoot() {
            return this.jmaXmlFeedRoot;
        }

        public void setJmaXmlFeedRoot(String jmaXmlFeedRoot) {
            this.jmaXmlFeedRoot = jmaXmlFeedRoot;
        }

        public int getJmaXmlPollIntervalSeconds() {
            return this.jmaXmlPollIntervalSeconds;
        }

        public void setJmaXmlPollIntervalSeconds(int jmaXmlPollIntervalSeconds) {
            this.jmaXmlPollIntervalSeconds = jmaXmlPollIntervalSeconds;
        }

        public String getDmdataWsBaseUri() {
            return this.dmdataWsBaseUri;
        }

        public void setDmdataWsBaseUri(String dmdataWsBaseUri) {
            this.dmdataWsBaseUri = dmdataWsBaseUri;
        }

        public boolean isAnyOverrideActive() {
            return StringUtils.isNotEmpty(this.jmaXmlFeedRoot) ||
                    this.jmaXmlPollIntervalSeconds > 0 ||
                    StringUtils.isNotEmpty(this.dmdataWsBaseUri);
        }

        @Override
        public String toString() {
            return "Debug{" +
                    "jmaXmlFeedRoot='" + this.jmaXmlFeedRoot + '\'' +
                    ", jmaXmlPollIntervalSeconds=" + this.jmaXmlPollIntervalSeconds +
                    ", dmdataWsBaseUri='" + this.dmdataWsBaseUri + '\'' +
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
                ", debug=" + this.debug +
                '}';
    }

    public boolean isValid() {
        boolean errored = false;
        if (StringUtils.isEmpty(getBase().getDiscordToken())) {
            Log.logger.info("Please set a discord token");
            errored = true;
        }
        if (StringUtils.isEmpty(getDmdata().getAPIKey())) {
            Log.logger.info("Please set a DMDATA API key");
            errored = true;
        }
        if (getDebug().isAnyOverrideActive()) {
            Log.logger.warn("Debug config overrides are active: {}", getDebug());
        }
        return !errored;
    }
}
