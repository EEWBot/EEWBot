package net.teamfruit.eewbot.entity.dmdataapi.ws;

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
        return socketId;
    }

    public List<String> getClassifications() {
        return classifications;
    }

    public List<String> getTypes() {
        return types;
    }

    public String getTest() {
        return test;
    }

    public List<String> getFormats() {
        return formats;
    }

    public String getAppName() {
        return appName;
    }

    public String getTime() {
        return time;
    }

    @Override
    public String toString() {
        return "DmdataWSStart{" +
                "socketId=" + socketId +
                ", classifications=" + classifications +
                ", types=" + types +
                ", test='" + test + '\'' +
                ", formats=" + formats +
                ", appName='" + appName + '\'' +
                ", time='" + time + '\'' +
                '}';
    }
}
