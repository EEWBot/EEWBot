package net.teamfruit.eewbot.entity.dmdataapi.ws;

public class DmdataWSPing extends DmdataWSMessage {

    private String pingId;

    public DmdataWSPing() {
        super(Type.PING);
    }

    public String getPingId() {
        return pingId;
    }

    @Override
    public String toString() {
        return "DmdataWSPing{" +
                "pingId='" + pingId + '\'' +
                '}';
    }
}
