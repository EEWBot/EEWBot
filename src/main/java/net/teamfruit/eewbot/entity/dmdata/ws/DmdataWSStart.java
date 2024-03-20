package net.teamfruit.eewbot.entity.dmdata.ws;

import net.teamfruit.eewbot.entity.dmdata.ws.DmdataWSMessage;

import java.util.List;

public class DmdataWSStart extends DmdataWSMessage {

    private int socketId;
    private List<String> classifications;
    private List<String> types;
    private String test;
    private List<String> formats;
    private String appName;
    private String time;

    public DmdataWSStart() {
        super(Type.START);
    }

    public int getSocketId() {
        return this.socketId;
    }

    public List<String> getClassifications() {
        return this.classifications;
    }

    public List<String> getTypes() {
        return this.types;
    }

    public String getTest() {
        return this.test;
    }

    public List<String> getFormats() {
        return this.formats;
    }

    public String getAppName() {
        return this.appName;
    }

    public String getTime() {
        return this.time;
    }

    @Override
    public String toString() {
        return "DmdataWSStart{" +
                "socketId=" + this.socketId +
                ", classifications=" + this.classifications +
                ", types=" + this.types +
                ", test='" + this.test + '\'' +
                ", formats=" + this.formats +
                ", appName='" + this.appName + '\'' +
                ", time='" + this.time + '\'' +
                '}';
    }
}
