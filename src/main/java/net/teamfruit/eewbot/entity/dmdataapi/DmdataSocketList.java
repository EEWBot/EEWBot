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

    public void setResponseId(String responseId) {
        this.responseId = responseId;
    }

    public String getResponseTime() {
        return responseTime;
    }

    public void setResponseTime(String responseTime) {
        this.responseTime = responseTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
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

        public void setId(int id) {
            this.id = id;
        }

        public String getTicket() {
            return ticket;
        }

        public void setTicket(String ticket) {
            this.ticket = ticket;
        }

        public List<String> getTypes() {
            return types;
        }

        public void setTypes(List<String> types) {
            this.types = types;
        }

        public String getTest() {
            return test;
        }

        public void setTest(String test) {
            this.test = test;
        }

        public List<String> getClassifications() {
            return classifications;
        }

        public void setClassifications(List<String> classifications) {
            this.classifications = classifications;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public void setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getServer() {
            return server;
        }

        public void setServer(String server) {
            this.server = server;
        }

        public String getStart() {
            return start;
        }

        public void setStart(String start) {
            this.start = start;
        }

        public String getEnd() {
            return end;
        }

        public void setEnd(String end) {
            this.end = end;
        }

        public String getPing() {
            return ping;
        }

        public void setPing(String ping) {
            this.ping = ping;
        }

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
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
