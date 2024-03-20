package net.teamfruit.eewbot.entity.dmdata.api;

import java.util.List;

public class DmdataContract {

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
        private int planId;
        private String planName;
        private String classification;
        private Price price;
        private String start;
        private boolean isValid;
        private int connectionCounts;

        public int getId() {
            return this.id;
        }

        public int getPlanId() {
            return this.planId;
        }

        public String getPlanName() {
            return this.planName;
        }

        public String getClassification() {
            return this.classification;
        }

        public Price getPrice() {
            return this.price;
        }

        public String getStart() {
            return this.start;
        }

        public boolean isValid() {
            return this.isValid;
        }

        public int getConnectionCounts() {
            return this.connectionCounts;
        }

        @Override
        public String toString() {
            return "Item{" +
                    "id=" + this.id +
                    ", planId=" + this.planId +
                    ", planName='" + this.planName + '\'' +
                    ", classification='" + this.classification + '\'' +
                    ", price=" + this.price +
                    ", start='" + this.start + '\'' +
                    ", isValid=" + this.isValid +
                    ", connectionCounts=" + this.connectionCounts +
                    '}';
        }
    }

    public static class Price {

        private int day;
        private int month;

        public int getDay() {
            return this.day;
        }

        public int getMonth() {
            return this.month;
        }

        @Override
        public String toString() {
            return "Price{" +
                    "day=" + this.day +
                    ", month=" + this.month +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "DmdataContract{" +
                "responseId='" + this.responseId + '\'' +
                ", responseTime='" + this.responseTime + '\'' +
                ", status='" + this.status + '\'' +
                ", items=" + this.items +
                '}';
    }
}
