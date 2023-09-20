package net.teamfruit.eewbot.entity.dmdataapi.ws;

public class DmdataWSPong extends DmdataWSMessage {

    private final String pingId;

    public DmdataWSPong(String pingId) {
        super(Type.PONG);
        this.pingId = pingId;
    }

    public String getPingId() {
        return pingId;
    }

    @Override
    public String toString() {
        return "DmdataWSPong{" +
                "pingId='" + pingId + '\'' +
                '}';
    }
}
