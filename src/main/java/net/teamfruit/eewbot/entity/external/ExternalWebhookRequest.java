package net.teamfruit.eewbot.entity.external;

public class ExternalWebhookRequest {

    private String type;
    private long timestamp;
    private Object data;

    public ExternalWebhookRequest(String type, long timestamp, Object data) {
        this.type = type;
        this.timestamp = timestamp;
        this.data = data;
    }

    public String getType() {
        return this.type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Object getData() {
        return this.data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "ExternalWebhookRequest{" +
                "type='" + this.type + '\'' +
                ", timestamp=" + this.timestamp +
                ", data=" + this.data +
                '}';
    }
}