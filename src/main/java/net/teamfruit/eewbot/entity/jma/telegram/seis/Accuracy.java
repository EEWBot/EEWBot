package net.teamfruit.eewbot.entity.jma.telegram.seis;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

@SuppressWarnings("unused")
public class Accuracy {

    @JacksonXmlProperty(localName = "Epicenter")
    private AccuracyEpicenter epicenter;

    @JacksonXmlProperty(localName = "Depth")
    private AccuracyDepth depth;

    @JacksonXmlProperty(localName = "Magnitude")
    private AccuracyMagnitude magnitude;

    @JacksonXmlProperty(localName = "NumberOfMagnitudeCalculation")
    private int numberOfMagnitudeCalculation;

    public AccuracyEpicenter getEpicenter() {
        return this.epicenter;
    }

    public AccuracyDepth getDepth() {
        return this.depth;
    }

    public AccuracyMagnitude getMagnitude() {
        return this.magnitude;
    }

    public int getNumberOfMagnitudeCalculation() {
        return this.numberOfMagnitudeCalculation;
    }

    public static class AccuracyEpicenter {

        @JacksonXmlText
        private float value;

        @JacksonXmlProperty(isAttribute = true)
        private int rank;

        @JacksonXmlProperty(isAttribute = true)
        private int rank2;

        public float getValue() {
            return this.value;
        }

        public int getRank() {
            return this.rank;
        }

        public int getRank2() {
            return this.rank2;
        }

        @Override
        public String toString() {
            return "AccuracyEpicenter{" +
                    "value=" + this.value +
                    ", rank=" + this.rank +
                    ", rank2=" + this.rank2 +
                    '}';
        }
    }

    public static class AccuracyDepth {

        @JacksonXmlText
        private int value;

        @JacksonXmlProperty(isAttribute = true)
        private int rank;

        public int getValue() {
            return this.value;
        }

        public int getRank() {
            return this.rank;
        }

        @Override
        public String toString() {
            return "AccuracyDepth{" +
                    "value=" + this.value +
                    ", rank=" + this.rank +
                    '}';
        }
    }

    public static class AccuracyMagnitude {

        @JacksonXmlText
        private float value;

        @JacksonXmlProperty(isAttribute = true)
        private int rank;

        public float getValue() {
            return this.value;
        }

        public int getRank() {
            return this.rank;
        }

        @Override
        public String toString() {
            return "AccuracyMagnitude{" +
                    "value=" + this.value +
                    ", rank=" + this.rank +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "Accuracy{" +
                "epicenter=" + this.epicenter +
                ", depth=" + this.depth +
                ", magnitude=" + this.magnitude +
                ", numberOfMagnitudeCalculation=" + this.numberOfMagnitudeCalculation +
                '}';
    }
}
