package net.teamfruit.eewbot.entity.dmdataapi;

public class DmdataError {

    private String responseId;
    private String responseTime;
    private String status;
    private Error error;

    public String getResponseId() {
        return responseId;
    }

    public String getResponseTime() {
        return responseTime;
    }

    public String getStatus() {
        return status;
    }

    public Error getError() {
        return error;
    }

    public static class Error {

        private String message;
        private int code;

        public String getMessage() {
            return message;
        }

        public int getCode() {
            return code;
        }

        @Override
        public String toString() {
            return "Error{" +
                    "message='" + message + '\'' +
                    ", code=" + code +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "DmdataError{" +
                "responseId='" + responseId + '\'' +
                ", responseTime='" + responseTime + '\'' +
                ", status='" + status + '\'' +
                ", error=" + error +
                '}';
    }
}
