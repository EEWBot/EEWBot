package net.teamfruit.eewbot.entity.dmdataapi;

public class DmdataError {

    private String responseId;
    private String responseTime;
    private String status;
    private Error error;

    public String getResponseId() {
        return responseId;
    }

    public void setResponseId(String responseId) {
        this.responseId = responseId;
    }

    public String getResponseTime() {
        return responseTime;
    }

    public void setResponseTime(String responseTime) {
        this.responseTime = responseTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Error getError() {
        return error;
    }

    public void setError(Error error) {
        this.error = error;
    }

    public static class Error {

        private String message;
        private int code;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
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
