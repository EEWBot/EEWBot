package net.teamfruit.eewbot.entity.external;

import java.util.List;

public class EEWExternalData {

    private boolean isWarning;
    private boolean isFinal;
    private boolean isCanceled;
    private String serialNo;
    private String reportDateTime;
    private String epicenter;
    private String depth;
    private String magnitude;
    private String maxIntensity;
    private List<String> regions;
    private String text;
    private String condition;
    private boolean concurrent;
    private int concurrentIndex;

    public EEWExternalData() {
    }

    public EEWExternalData(boolean isWarning, boolean isFinal, boolean isCanceled, String serialNo, 
                           String reportDateTime, String epicenter, String depth, String magnitude, 
                           String maxIntensity, List<String> regions, String text, String condition,
                           boolean concurrent, int concurrentIndex) {
        this.isWarning = isWarning;
        this.isFinal = isFinal;
        this.isCanceled = isCanceled;
        this.serialNo = serialNo;
        this.reportDateTime = reportDateTime;
        this.epicenter = epicenter;
        this.depth = depth;
        this.magnitude = magnitude;
        this.maxIntensity = maxIntensity;
        this.regions = regions;
        this.text = text;
        this.condition = condition;
        this.concurrent = concurrent;
        this.concurrentIndex = concurrentIndex;
    }

    public boolean isWarning() {
        return isWarning;
    }

    public void setWarning(boolean warning) {
        isWarning = warning;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public void setFinal(boolean aFinal) {
        isFinal = aFinal;
    }

    public boolean isCanceled() {
        return isCanceled;
    }

    public void setCanceled(boolean canceled) {
        isCanceled = canceled;
    }

    public String getSerialNo() {
        return serialNo;
    }

    public void setSerialNo(String serialNo) {
        this.serialNo = serialNo;
    }

    public String getReportDateTime() {
        return reportDateTime;
    }

    public void setReportDateTime(String reportDateTime) {
        this.reportDateTime = reportDateTime;
    }

    public String getEpicenter() {
        return epicenter;
    }

    public void setEpicenter(String epicenter) {
        this.epicenter = epicenter;
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

    public List<String> getRegions() {
        return regions;
    }

    public void setRegions(List<String> regions) {
        this.regions = regions;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public boolean isConcurrent() {
        return concurrent;
    }

    public void setConcurrent(boolean concurrent) {
        this.concurrent = concurrent;
    }

    public int getConcurrentIndex() {
        return concurrentIndex;
    }

    public void setConcurrentIndex(int concurrentIndex) {
        this.concurrentIndex = concurrentIndex;
    }

    @Override
    public String toString() {
        return "EEWExternalData{" +
                "isWarning=" + isWarning +
                ", isFinal=" + isFinal +
                ", isCanceled=" + isCanceled +
                ", serialNo='" + serialNo + '\'' +
                ", reportDateTime='" + reportDateTime + '\'' +
                ", epicenter='" + epicenter + '\'' +
                ", depth='" + depth + '\'' +
                ", magnitude='" + magnitude + '\'' +
                ", maxIntensity='" + maxIntensity + '\'' +
                ", regions=" + regions +
                ", text='" + text + '\'' +
                ", condition='" + condition + '\'' +
                ", concurrent=" + concurrent +
                ", concurrentIndex=" + concurrentIndex +
                '}';
    }
}