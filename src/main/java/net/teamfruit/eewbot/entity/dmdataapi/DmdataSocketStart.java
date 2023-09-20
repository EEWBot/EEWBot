package net.teamfruit.eewbot.entity.dmdataapi;

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
            return classifications;
        }

        public List<String> getTypes() {
            return types;
        }

        public String getTest() {
            return test;
        }

        public String getAppName() {
            return appName;
        }

        public String getFormatMode() {
            return formatMode;
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
                    "classifications=" + classifications +
                    ", types=" + types +
                    ", test='" + test + '\'' +
                    ", appName='" + appName + '\'' +
                    ", formatMode='" + formatMode + '\'' +
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
            return responseId;
        }

        public String getResponseTime() {
            return responseTime;
        }

        public String getStatus() {
            return status;
        }

        public String getTicket() {
            return ticket;
        }

        public WebSocket getWebsocket() {
            return websocket;
        }

        public List<String> getClassifications() {
            return classifications;
        }

        public String getTest() {
            return test;
        }

        public List<String> getTypes() {
            return types;
        }

        public List<String> getFormats() {
            return formats;
        }

        public String getAppName() {
            return appName;
        }

        public static class WebSocket {

            private int id;
            private String url;
            private List<String> protocol;
            private int expiration;

            public int getId() {
                return id;
            }

            public String getUrl() {
                return url;
            }

            public List<String> getProtocol() {
                return protocol;
            }

            public int getExpiration() {
                return expiration;
            }

            @Override
            public String toString() {
                return "WebSocket{" +
                        "id=" + id +
                        ", url='" + url + '\'' +
                        ", protocol=" + protocol +
                        ", expiration=" + expiration +
                        '}';
            }
        }

        @Override
        public String toString() {
            return "Response{" +
                    "responseId='" + responseId + '\'' +
                    ", responseTime='" + responseTime + '\'' +
                    ", status='" + status + '\'' +
                    ", ticket='" + ticket + '\'' +
                    ", websocket=" + websocket +
                    ", classifications=" + classifications +
                    ", test='" + test + '\'' +
                    ", types=" + types +
                    ", formats=" + formats +
                    ", appName='" + appName + '\'' +
                    '}';
        }
    }
}
