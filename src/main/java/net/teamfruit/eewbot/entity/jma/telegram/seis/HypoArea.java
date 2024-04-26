package net.teamfruit.eewbot.entity.jma.telegram.seis;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import net.teamfruit.eewbot.entity.jma.telegram.common.Coordinate;
import reactor.util.annotation.Nullable;

import java.util.List;

@SuppressWarnings("unused")
public class HypoArea {

    @JacksonXmlProperty(localName = "Name")
    private String name;

    @JacksonXmlProperty(localName = "Code")
    private HypoAreaCode code;

    @JacksonXmlProperty(localName = "Coordinate")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<Coordinate> coordinate;

    @JacksonXmlProperty(localName = "ReduceName")
    private @Nullable String reduceName;

    @JacksonXmlProperty(localName = "ReduceCode")
    private @Nullable HypoAreaReduceCode reduceCode;

    @JacksonXmlProperty(localName = "DetailedName")
    private @Nullable String detailedName;

    @JacksonXmlProperty(localName = "DetailedCode")
    private @Nullable HypoAreaDetailedCode detailedCode;

    @JacksonXmlProperty(localName = "NameFromMark")
    private @Nullable String nameFromMark;

    @JacksonXmlProperty(localName = "MarkCode")
    private @Nullable HypoAreaMarkCode markCode;

    @JacksonXmlProperty(localName = "Direction")
    private @Nullable String direction;

    @JacksonXmlProperty(localName = "Distance")
    private @Nullable HypoAreaDistance distance;

    @JacksonXmlProperty(localName = "LandOrSea")
    private @Nullable LandOrSea landOrSea;

    public String getName() {
        return this.name;
    }

    public HypoAreaCode getCode() {
        return this.code;
    }

    public List<Coordinate> getCoordinate() {
        return this.coordinate;
    }

    @Nullable
    public String getReduceName() {
        return this.reduceName;
    }

    @Nullable
    public HypoAreaReduceCode getReduceCode() {
        return this.reduceCode;
    }

    @Nullable
    public String getDetailedName() {
        return this.detailedName;
    }

    @Nullable
    public HypoAreaDetailedCode getDetailedCode() {
        return this.detailedCode;
    }

    @Nullable
    public String getNameFromMark() {
        return this.nameFromMark;
    }

    @Nullable
    public HypoAreaMarkCode getMarkCode() {
        return this.markCode;
    }

    @Nullable
    public String getDirection() {
        return this.direction;
    }

    @Nullable
    public HypoAreaDistance getDistance() {
        return this.distance;
    }

    @Nullable
    public LandOrSea getLandOrSea() {
        return this.landOrSea;
    }

    public static class HypoAreaCode {

        @JacksonXmlText
        private String value;

        @JacksonXmlProperty(isAttribute = true)
        private String type;

        public String getValue() {
            return this.value;
        }

        public String getType() {
            return this.type;
        }

        @Override
        public String toString() {
            return "HypoAreaCode{" +
                    "value='" + this.value + '\'' +
                    ", type='" + this.type + '\'' +
                    '}';
        }
    }

    public static class HypoAreaReduceCode {

        @JacksonXmlText
        private String value;

        @JacksonXmlProperty(isAttribute = true)
        private String type;

        public String getValue() {
            return this.value;
        }

        public String getType() {
            return this.type;
        }

        @Override
        public String toString() {
            return "HypoAreaReduceCode{" +
                    "value='" + this.value + '\'' +
                    ", type='" + this.type + '\'' +
                    '}';
        }
    }

    public static class HypoAreaDetailedCode {

        @JacksonXmlText
        private String value;

        @JacksonXmlProperty(isAttribute = true)
        private String type;

        public String getValue() {
            return this.value;
        }

        public String getType() {
            return this.type;
        }

        @Override
        public String toString() {
            return "HypoAreaDetailedCode{" +
                    "value='" + this.value + '\'' +
                    ", type='" + this.type + '\'' +
                    '}';
        }
    }

    public static class HypoAreaMarkCode {

        @JacksonXmlText
        private String value;

        @JacksonXmlProperty(isAttribute = true)
        private String type;

        public String getValue() {
            return this.value;
        }

        public String getType() {
            return this.type;
        }

        @Override
        public String toString() {
            return "HypoAreaMarkCode{" +
                    "value='" + this.value + '\'' +
                    ", type='" + this.type + '\'' +
                    '}';
        }
    }

    public static class HypoAreaDistance {

        @JacksonXmlText
        private int value;

        @JacksonXmlProperty(isAttribute = true)
        private String unit;

        public int getValue() {
            return this.value;
        }

        public String getUnit() {
            return this.unit;
        }

        @Override
        public String toString() {
            return "HypoAreaDistance{" +
                    "value=" + this.value +
                    ", unit='" + this.unit + '\'' +
                    '}';
        }
    }

    @SuppressWarnings("NonAsciiCharacters")
    public enum LandOrSea {
        内陸,
        海域
    }

    @Override
    public String toString() {
        return "HypoArea{" +
                "name='" + this.name + '\'' +
                ", code=" + this.code +
                ", coordinate=" + this.coordinate +
                ", reduceName='" + this.reduceName + '\'' +
                ", reduceCode=" + this.reduceCode +
                ", detailedName='" + this.detailedName + '\'' +
                ", detailedCode=" + this.detailedCode +
                ", nameFromMark='" + this.nameFromMark + '\'' +
                ", markCode=" + this.markCode +
                ", direction='" + this.direction + '\'' +
                ", distance=" + this.distance +
                ", landOrSea=" + this.landOrSea +
                '}';
    }
}
