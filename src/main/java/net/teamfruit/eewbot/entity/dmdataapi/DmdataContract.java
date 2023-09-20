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

        public int getPlanId() {
            return planId;
        }

        public String getPlanName() {
            return planName;
        }

        public String getClassification() {
            return classification;
        }

        public Price getPrice() {
            return price;
        }

        public String getStart() {
            return start;
        }

        public boolean isValid() {
            return isValid;
        }

        public int getConnectionCounts() {
            return connectionCounts;
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

        public int getMonth() {
            return month;
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
