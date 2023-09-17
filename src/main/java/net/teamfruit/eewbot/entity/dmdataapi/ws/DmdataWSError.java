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

    public void setError(String error) {
        this.error = error;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public boolean isClose() {
        return close;
    }

    public void setClose(boolean close) {
        this.close = close;
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
