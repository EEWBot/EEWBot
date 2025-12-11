package net.teamfruit.eewbot.entity.external;

public class ExternalWebhookRequest {

    private String type;
    private long timestamp;
    private Object data;
    private Object eewbot;

    public ExternalWebhookRequest() {
    }

    public ExternalWebhookRequest(String type, long timestamp, Object data) {
        this.type = type;
        this.timestamp = timestamp;
        this.data = data;
    }

    public ExternalWebhookRequest(String type, long timestamp, Object data, Object eewbot) {
        this.type = type;
        this.timestamp = timestamp;
        this.data = data;
        this.eewbot = eewbot;
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

    public Object getEewbot() {
        return this.eewbot;
    }

    public void setEewbot(Object eewbot) {
        this.eewbot = eewbot;
    }

    @Override
    public String toString() {
        return "ExternalWebhookRequest{" +
                "type='" + this.type + '\'' +
                ", timestamp=" + this.timestamp +
                ", data=" + this.data +
                ", eewbot=" + this.eewbot +
                '}';
    }
}