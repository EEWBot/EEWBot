package net.teamfruit.eewbot.entity.dmdata.ws;

public class DmdataWSPing extends DmdataWSMessage {

    private String pingId;

    public DmdataWSPing() {
        super(Type.PING);
    }

    public String getPingId() {
        return this.pingId;
    }

    @Override
    public String toString() {
        return "DmdataWSPing{" +
                "pingId='" + this.pingId + '\'' +
                '}';
    }
}
