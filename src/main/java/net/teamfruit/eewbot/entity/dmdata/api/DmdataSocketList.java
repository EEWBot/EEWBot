package net.teamfruit.eewbot.entity.dmdata.api;

import java.util.List;

public class DmdataSocketList {

    private String responseId;
    private String responseTime;
    private String status;
    private List<Item> items;

    public String getResponseId() {
        return this.responseId;
    }

    public String getResponseTime() {
        return this.responseTime;
    }

    public String getStatus() {
        return this.status;
    }

    public List<Item> getItems() {
        return this.items;
    }

    public static class Item {

        private int id;
        private String ticket;
        private List<String> types;
        private String test;
        private List<String> classifications;
        private String ipAddress;
        private String status;
        private String server;
        private String start;
        private String end;
        private String ping;
        private String appName;

        public int getId() {
            return this.id;
        }

        public String getTicket() {
            return this.ticket;
        }

        public List<String> getTypes() {
            return this.types;
        }

        public String getTest() {
            return this.test;
        }

        public List<String> getClassifications() {
            return this.classifications;
        }

        public String getIpAddress() {
            return this.ipAddress;
        }

        public String getStatus() {
            return this.status;
        }

        public String getServer() {
            return this.server;
        }

        public String getStart() {
            return this.start;
        }

        public String getEnd() {
            return this.end;
        }

        public String getPing() {
            return this.ping;
        }

        public String getAppName() {
            return this.appName;
        }

        @Override
        public String toString() {
            return "Item{" +
                    "id=" + this.id +
                    ", ticket='" + this.ticket + '\'' +
                    ", types=" + this.types +
                    ", test='" + this.test + '\'' +
                    ", classifications=" + this.classifications +
                    ", ipAddress='" + this.ipAddress + '\'' +
                    ", status='" + this.status + '\'' +
                    ", server='" + this.server + '\'' +
                    ", start='" + this.start + '\'' +
                    ", end='" + this.end + '\'' +
                    ", ping='" + this.ping + '\'' +
                    ", appName='" + this.appName + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "DmdataSocketList{" +
                "responseId='" + this.responseId + '\'' +
                ", responseTime='" + this.responseTime + '\'' +
                ", status='" + this.status + '\'' +
                ", items=" + this.items +
                '}';
    }
}
