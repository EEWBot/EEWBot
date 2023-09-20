package net.teamfruit.eewbot.entity.dmdataapi;

import java.util.List;

public class DmdataSocketList {

    private String responseId;
    private String responseTime;
    private String status;
    private List<Item> items;

    public String getResponseId() {
        return responseId;
    }

    public String getResponseTime() {
        return responseTime;
    }

    public String getStatus() {
        return status;
    }

    public List<Item> getItems() {
        return items;
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
            return id;
        }

        public String getTicket() {
            return ticket;
        }

        public List<String> getTypes() {
            return types;
        }

        public String getTest() {
            return test;
        }

        public List<String> getClassifications() {
            return classifications;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public String getStatus() {
            return status;
        }

        public String getServer() {
            return server;
        }

        public String getStart() {
            return start;
        }

        public String getEnd() {
            return end;
        }

        public String getPing() {
            return ping;
        }

        public String getAppName() {
            return appName;
        }

        @Override
        public String toString() {
            return "Item{" +
                    "id=" + id +
                    ", ticket='" + ticket + '\'' +
                    ", types=" + types +
                    ", test='" + test + '\'' +
                    ", classifications=" + classifications +
                    ", ipAddress='" + ipAddress + '\'' +
                    ", status='" + status + '\'' +
                    ", server='" + server + '\'' +
                    ", start='" + start + '\'' +
                    ", end='" + end + '\'' +
                    ", ping='" + ping + '\'' +
                    ", appName='" + appName + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "DmdataSocketList{" +
                "responseId='" + responseId + '\'' +
                ", responseTime='" + responseTime + '\'' +
                ", status='" + status + '\'' +
                ", items=" + items +
                '}';
    }
}
