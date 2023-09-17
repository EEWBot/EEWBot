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

    public void setSocketId(int socketId) {
        this.socketId = socketId;
    }

    public List<String> getClassifications() {
        return classifications;
    }

    public void setClassifications(List<String> classifications) {
        this.classifications = classifications;
    }

    public List<String> getTypes() {
        return types;
    }

    public void setTypes(List<String> types) {
        this.types = types;
    }

    public String getTest() {
        return test;
    }

    public void setTest(String test) {
        this.test = test;
    }

    public List<String> getFormats() {
        return formats;
    }

    public void setFormats(List<String> formats) {
        this.formats = formats;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
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
