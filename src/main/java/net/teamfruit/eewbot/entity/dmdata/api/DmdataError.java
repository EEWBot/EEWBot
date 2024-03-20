package net.teamfruit.eewbot.entity.dmdata.api;

public class DmdataError {

    private String responseId;
    private String responseTime;
    private String status;
    private Error error;

    public String getResponseId() {
        return this.responseId;
    }

    public String getResponseTime() {
        return this.responseTime;
    }

    public String getStatus() {
        return this.status;
    }

    public Error getError() {
        return this.error;
    }

    public static class Error {

        private String message;
        private int code;

        public String getMessage() {
            return this.message;
        }

        public int getCode() {
            return this.code;
        }

        @Override
        public String toString() {
            return "Error{" +
                    "message='" + this.message + '\'' +
                    ", code=" + this.code +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "DmdataError{" +
                "responseId='" + this.responseId + '\'' +
                ", responseTime='" + this.responseTime + '\'' +
                ", status='" + this.status + '\'' +
                ", error=" + this.error +
                '}';
    }
}
