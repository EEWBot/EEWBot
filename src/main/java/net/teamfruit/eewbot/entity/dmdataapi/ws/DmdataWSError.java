package net.teamfruit.eewbot.entity.dmdataapi.ws;

public class DmdataWSError extends DmdataWSMessage {

    private String error;
    private int code;
    private boolean close;

    public DmdataWSError(Type type) {
        super(Type.ERROR);
    }

    public String getError() {
        return error;
    }

    public int getCode() {
        return code;
    }

    public boolean isClose() {
        return close;
    }

    @Override
    public String toString() {
        return "DmdataWSError{" +
                "error='" + error + '\'' +
                ", code=" + code +
                ", close=" + close +
                '}';
    }
}
