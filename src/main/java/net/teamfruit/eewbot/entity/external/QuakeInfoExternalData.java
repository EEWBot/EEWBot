package net.teamfruit.eewbot.entity.external;

import java.util.List;

public class QuakeInfoExternalData {

    private long eventId;
    private String reportDateTime;
    private String title;
    private String infoType;
    private String serial;
    private String status;
    private String epicenter;
    private String originTime;
    private String depth;
    private String magnitude;
    private String maxIntensity;
    private List<IntensityArea> intensityAreas;
    private String text;
    private String comments;

    public QuakeInfoExternalData() {
    }

    public QuakeInfoExternalData(long eventId, String reportDateTime, String title, String infoType, 
                                String serial, String status, String epicenter, String originTime, 
                                String depth, String magnitude, String maxIntensity, 
                                List<IntensityArea> intensityAreas, String text, String comments) {
        this.eventId = eventId;
        this.reportDateTime = reportDateTime;
        this.title = title;
        this.infoType = infoType;
        this.serial = serial;
        this.status = status;
        this.epicenter = epicenter;
        this.originTime = originTime;
        this.depth = depth;
        this.magnitude = magnitude;
        this.maxIntensity = maxIntensity;
        this.intensityAreas = intensityAreas;
        this.text = text;
        this.comments = comments;
    }

    public long getEventId() {
        return eventId;
    }

    public void setEventId(long eventId) {
        this.eventId = eventId;
    }

    public String getReportDateTime() {
        return reportDateTime;
    }

    public void setReportDateTime(String reportDateTime) {
        this.reportDateTime = reportDateTime;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getInfoType() {
        return infoType;
    }

    public void setInfoType(String infoType) {
        this.infoType = infoType;
    }

    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getEpicenter() {
        return epicenter;
    }

    public void setEpicenter(String epicenter) {
        this.epicenter = epicenter;
    }

    public String getOriginTime() {
        return originTime;
    }

    public void setOriginTime(String originTime) {
        this.originTime = originTime;
    }

    public String getDepth() {
        return depth;
    }

    public void setDepth(String depth) {
        this.depth = depth;
    }

    public String getMagnitude() {
        return magnitude;
    }

    public void setMagnitude(String magnitude) {
        this.magnitude = magnitude;
    }

    public String getMaxIntensity() {
        return maxIntensity;
    }

    public void setMaxIntensity(String maxIntensity) {
        this.maxIntensity = maxIntensity;
    }

    public List<IntensityArea> getIntensityAreas() {
        return intensityAreas;
    }

    public void setIntensityAreas(List<IntensityArea> intensityAreas) {
        this.intensityAreas = intensityAreas;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public static class IntensityArea {
        private String name;
        private String intensity;

        public IntensityArea() {
        }

        public IntensityArea(String name, String intensity) {
            this.name = name;
            this.intensity = intensity;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getIntensity() {
            return intensity;
        }

        public void setIntensity(String intensity) {
            this.intensity = intensity;
        }

        @Override
        public String toString() {
            return "IntensityArea{" +
                    "name='" + name + '\'' +
                    ", intensity='" + intensity + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "QuakeInfoExternalData{" +
                "eventId=" + eventId +
                ", reportDateTime='" + reportDateTime + '\'' +
                ", title='" + title + '\'' +
                ", infoType='" + infoType + '\'' +
                ", serial='" + serial + '\'' +
                ", status='" + status + '\'' +
                ", epicenter='" + epicenter + '\'' +
                ", originTime='" + originTime + '\'' +
                ", depth='" + depth + '\'' +
                ", magnitude='" + magnitude + '\'' +
                ", maxIntensity='" + maxIntensity + '\'' +
                ", intensityAreas=" + intensityAreas +
                ", text='" + text + '\'' +
                ", comments='" + comments + '\'' +
                '}';
    }
}