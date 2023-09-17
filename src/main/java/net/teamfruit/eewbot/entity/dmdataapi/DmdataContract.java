package net.teamfruit.eewbot.entity.dmdataapi;

import java.util.List;

public class DmdataContract {

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
        private int planId;
        private String planName;
        private String classification;
        private Price price;
        private String start;
        private boolean isValid;
        private int connectionCounts;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public int getPlanId() {
            return planId;
        }

        public void setPlanId(int planId) {
            this.planId = planId;
        }

        public String getPlanName() {
            return planName;
        }

        public void setPlanName(String planName) {
            this.planName = planName;
        }

        public String getClassification() {
            return classification;
        }

        public void setClassification(String classification) {
            this.classification = classification;
        }

        public Price getPrice() {
            return price;
        }

        public void setPrice(Price price) {
            this.price = price;
        }

        public String getStart() {
            return start;
        }

        public void setStart(String start) {
            this.start = start;
        }

        public boolean isValid() {
            return isValid;
        }

        public void setValid(boolean valid) {
            isValid = valid;
        }

        public int getConnectionCounts() {
            return connectionCounts;
        }

        public void setConnectionCounts(int connectionCounts) {
            this.connectionCounts = connectionCounts;
        }

        @Override
        public String toString() {
            return "Item{" +
                    "id=" + id +
                    ", planId=" + planId +
                    ", planName='" + planName + '\'' +
                    ", classification='" + classification + '\'' +
                    ", price=" + price +
                    ", start='" + start + '\'' +
                    ", isValid=" + isValid +
                    ", connectionCounts=" + connectionCounts +
                    '}';
        }
    }

    public static class Price {

        private int day;
        private int month;

        public int getDay() {
            return day;
        }

        public void setDay(int day) {
            this.day = day;
        }

        public int getMonth() {
            return month;
        }

        public void setMonth(int month) {
            this.month = month;
        }

        @Override
        public String toString() {
            return "Price{" +
                    "day=" + day +
                    ", month=" + month +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "DmdataContract{" +
                "responseId='" + responseId + '\'' +
                ", responseTime='" + responseTime + '\'' +
                ", status='" + status + '\'' +
                ", items=" + items +
                '}';
    }
}
