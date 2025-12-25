package net.teamfruit.eewbot.entity.external;

import net.teamfruit.eewbot.entity.jma.AbstractJMAReport;

import java.util.List;

public class QuakeInfoExternalData {

    private AbstractJMAReport.Control control;
    private AbstractJMAReport.Head head;
    private List<IntensityArea> intensityAreas;

    private QuakeInfoExternalData(Builder builder) {
        this.control = builder.control;
        this.head = builder.head;
        this.intensityAreas = builder.intensityAreas;
    }

    public static Builder builder() {
        return new Builder();
    }

    public AbstractJMAReport.Control getControl() {
        return this.control;
    }

    public AbstractJMAReport.Head getHead() {
        return this.head;
    }

    public List<IntensityArea> getIntensityAreas() {
        return this.intensityAreas;
    }

    @Override
    public String toString() {
        return "QuakeInfoExternalData{" +
                "control=" + this.control +
                ", head=" + this.head +
                ", intensityAreas=" + this.intensityAreas +
                '}';
    }

    public static class Builder {
        private AbstractJMAReport.Control control;
        private AbstractJMAReport.Head head;
        private List<IntensityArea> intensityAreas;

        public Builder control(AbstractJMAReport.Control control) {
            this.control = control;
            return this;
        }

        public Builder head(AbstractJMAReport.Head head) {
            this.head = head;
            return this;
        }

        public Builder intensityAreas(List<IntensityArea> intensityAreas) {
            this.intensityAreas = intensityAreas;
            return this;
        }

        public QuakeInfoExternalData build() {
            return new QuakeInfoExternalData(this);
        }
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
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getIntensity() {
            return this.intensity;
        }

        public void setIntensity(String intensity) {
            this.intensity = intensity;
        }

        @Override
        public String toString() {
            return "IntensityArea{" +
                    "name='" + this.name + '\'' +
                    ", intensity='" + this.intensity + '\'' +
                    '}';
        }
    }
}
