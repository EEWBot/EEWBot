package net.teamfruit.eewbot.entity.dmdataapi.ws;

public class DmdataWSPong extends DmdataWSMessage {

    private String pingId;

    public DmdataWSPong(String pingId) {
        super(Type.PONG);
        this.pingId = pingId;
    }

    public String getPingId() {
        return pingId;
    }

    public void setPingId(String pingId) {
        this.pingId = pingId;
    }

    @Override
    public String toString() {
        return "DmdataWSPong{" +
                "pingId='" + pingId + '\'' +
                '}';
    }
}
