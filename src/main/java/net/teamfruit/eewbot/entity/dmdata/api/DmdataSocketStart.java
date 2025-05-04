package net.teamfruit.eewbot.entity.dmdata.api;

import java.util.List;

public class DmdataSocketStart {

    private DmdataSocketStart() {
    }

    public static class Request {

        private List<String> classifications;
        private List<String> types;
        private String test;
        private String appName;
        private String formatMode;

        public List<String> getClassifications() {
            return this.classifications;
        }

        public List<String> getTypes() {
            return this.types;
        }

        public String getTest() {
            return this.test;
        }

        public String getAppName() {
            return this.appName;
        }

        public String getFormatMode() {
            return this.formatMode;
        }

        public static class Builder {

            private final Request request = new Request();

            public Builder setClassifications(List<String> classifications) {
                this.request.classifications = classifications;
                return this;
            }

            public Builder setTypes(List<String> types) {
                this.request.types = types;
                return this;
            }

            public Builder setTest(String test) {
                this.request.test = test;
                return this;
            }

            public Builder setAppName(String appName) {
                this.request.appName = appName;
                return this;
            }

            public Builder setFormatMode(String formatMode) {
                this.request.formatMode = formatMode;
                return this;
            }

            public Request build() {
                return this.request;
            }
        }

        @Override
        public String toString() {
            return "Request{" +
                    "classifications=" + this.classifications +
                    ", types=" + this.types +
                    ", test='" + this.test + '\'' +
                    ", appName='" + this.appName + '\'' +
                    ", formatMode='" + this.formatMode + '\'' +
                    '}';
        }
    }

    public static class Response {

        private String responseId;
        private String responseTime;
        private String status;
        private String ticket;
        private WebSocket websocket;
        private List<String> classifications;
        private String test;
        private List<String> types;
        private List<String> formats;
        private String appName;

        public String getResponseId() {
            return this.responseId;
        }

        public String getResponseTime() {
            return this.responseTime;
        }

        public String getStatus() {
            return this.status;
        }

        public String getTicket() {
            return this.ticket;
        }

        public WebSocket getWebsocket() {
            return this.websocket;
        }

        public List<String> getClassifications() {
            return this.classifications;
        }

        public String getTest() {
            return this.test;
        }

        public List<String> getTypes() {
            return this.types;
        }

        public List<String> getFormats() {
            return this.formats;
        }

        public String getAppName() {
            return this.appName;
        }

        public static class WebSocket {

            private int id;
            private String url;
            private List<String> protocol;
            private int expiration;

            public int getId() {
                return this.id;
            }

            public String getUrl() {
                return this.url;
            }

            public List<String> getProtocol() {
                return this.protocol;
            }

            public int getExpiration() {
                return this.expiration;
            }

            @Override
            public String toString() {
                return "WebSocket{" +
                        "id=" + this.id +
                        ", url='" + this.url + '\'' +
                        ", protocol=" + this.protocol +
                        ", expiration=" + this.expiration +
                        '}';
            }
        }

        @Override
        public String toString() {
            return "Response{" +
                    "responseId='" + this.responseId + '\'' +
                    ", responseTime='" + this.responseTime + '\'' +
                    ", status='" + this.status + '\'' +
                    ", ticket='" + this.ticket + '\'' +
                    ", websocket=" + this.websocket +
                    ", classifications=" + this.classifications +
                    ", test='" + this.test + '\'' +
                    ", types=" + this.types +
                    ", formats=" + this.formats +
                    ", appName='" + this.appName + '\'' +
                    '}';
        }
    }
}
