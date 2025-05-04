package net.teamfruit.eewbot.entity.dmdata.ws;

public class DmdataWSError extends DmdataWSMessage {

    private String error;
    private int code;
    private boolean close;

    public DmdataWSError(Type type) {
        super(Type.ERROR);
    }

    public String getError() {
        return this.error;
    }

    public int getCode() {
        return this.code;
    }

    public boolean isClose() {
        return this.close;
    }

    @Override
    public String toString() {
        return "DmdataWSError{" +
                "error='" + this.error + '\'' +
                ", code=" + this.code +
                ", close=" + this.close +
                '}';
    }
}
