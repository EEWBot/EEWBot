package net.teamfruit.eewbot.entity.dmdata.ws;

import com.google.gson.annotations.SerializedName;

public class DmdataWSMessage {

    private final Type type;

    public DmdataWSMessage(Type type) {
        this.type = type;
    }

    public Type getType() {
        return this.type;
    }

    @Override
    public String toString() {
        return "DmdataWSMessage{" +
                "type=" + this.type +
                '}';
    }

    public enum Type {
        @SerializedName("start")
        START,
        @SerializedName("ping")
        PING,
        @SerializedName("pong")
        PONG,
        @SerializedName("data")
        DATA,
        @SerializedName("error")
        ERROR,
    }
}
