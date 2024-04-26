package net.teamfruit.eewbot.entity.jma.telegram.seis;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import net.teamfruit.eewbot.entity.LgIntensity;
import reactor.util.annotation.Nullable;

import java.util.List;

@SuppressWarnings("unused")
public class IntensityStation {

    @JacksonXmlProperty(localName = "Name")
    protected String name;

    @JacksonXmlProperty(localName = "Code")
    protected String code;

    @JacksonXmlProperty(localName = "Int")
    private @Nullable String intensity;

    @JacksonXmlProperty(localName = "K")
    private @Nullable Float k;

    @JacksonXmlProperty(localName = "LgInt")
    private @Nullable LgIntensity lgInt;

    @JacksonXmlProperty(localName = "LgIntPerPeriod")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<LgIntPerPeriod> lgIntPerPeriods;

    @JacksonXmlProperty(localName = "Sva")
    private Sva sva;

    @JacksonXmlProperty(localName = "SvaPerPeriod")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<SvaPerPeriod> svaPerPeriods;

    @JacksonXmlProperty(localName = "Revise")
    protected @Nullable String revise;

    public String getName() {
        return this.name;
    }

    public String getCode() {
        return this.code;
    }

    @Nullable
    public String getIntensity() {
        return this.intensity;
    }

    @Nullable
    public Float getK() {
        return this.k;
    }

    @Nullable
    public LgIntensity getLgInt() {
        return this.lgInt;
    }

    public List<LgIntPerPeriod> getLgIntPerPeriods() {
        return this.lgIntPerPeriods;
    }

    public Sva getSva() {
        return this.sva;
    }

    public List<SvaPerPeriod> getSvaPerPeriods() {
        return this.svaPerPeriods;
    }

    @Nullable
    public String getRevise() {
        return this.revise;
    }

    public static class LgIntPerPeriod {

        @JacksonXmlText
        private String value;

        @JacksonXmlProperty(localName = "PeriodicBand", isAttribute = true)
        private @Nullable Integer periodicBand;

        @JacksonXmlProperty(localName = "Period", isAttribute = true)
        private @Nullable Float period;

        @JacksonXmlProperty(localName = "PeriodUnit", isAttribute = true)
        private @Nullable String periodUnit;

        public String getValue() {
            return this.value;
        }

        @Nullable
        public Integer getPeriodicBand() {
            return this.periodicBand;
        }

        @Nullable
        public Float getPeriod() {
            return this.period;
        }

        @Nullable
        public String getPeriodUnit() {
            return this.periodUnit;
        }

        @Override
        public String toString() {
            return "LgIntPerPeriod{" +
                    "value='" + this.value + '\'' +
                    ", periodicBand=" + this.periodicBand +
                    ", period=" + this.period +
                    ", periodUnit='" + this.periodUnit + '\'' +
                    '}';
        }
    }

    public static class Sva {

        @JacksonXmlText
        private float value;

        @JacksonXmlProperty(isAttribute = true)
        private String unit;

        public float getValue() {
            return this.value;
        }

        public String getUnit() {
            return this.unit;
        }

        @Override
        public String toString() {
            return "Sva{" +
                    "value=" + this.value +
                    ", unit='" + this.unit + '\'' +
                    '}';
        }
    }

    public static class SvaPerPeriod {

        @JacksonXmlText
        private float value;

        @JacksonXmlProperty(isAttribute = true)
        private String unit;

        @JacksonXmlProperty(localName = "PeriodicBand", isAttribute = true)
        private @Nullable Integer periodicBand;

        @JacksonXmlProperty(localName = "Period", isAttribute = true)
        private @Nullable Float period;

        @JacksonXmlProperty(localName = "PeriodUnit", isAttribute = true)
        private @Nullable String periodUnit;

        public float getValue() {
            return this.value;
        }

        public String getUnit() {
            return this.unit;
        }

        @Nullable
        public Integer getPeriodicBand() {
            return this.periodicBand;
        }

        @Nullable
        public Float getPeriod() {
            return this.period;
        }

        @Nullable
        public String getPeriodUnit() {
            return this.periodUnit;
        }

        @Override
        public String toString() {
            return "SvaPerPeriod{" +
                    "value=" + this.value +
                    ", unit='" + this.unit + '\'' +
                    ", periodicBand=" + this.periodicBand +
                    ", period=" + this.period +
                    ", periodUnit='" + this.periodUnit + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "IntensityStation{" +
                "name='" + this.name + '\'' +
                ", code='" + this.code + '\'' +
                ", intensity='" + this.intensity + '\'' +
                ", k=" + this.k +
                ", lgInt=" + this.lgInt +
                ", lgIntPerPeriods=" + this.lgIntPerPeriods +
                ", sva=" + this.sva +
                ", svaPerPeriods=" + this.svaPerPeriods +
                ", revise='" + this.revise + '\'' +
                '}';
    }
}
