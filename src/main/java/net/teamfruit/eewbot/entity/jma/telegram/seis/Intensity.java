package net.teamfruit.eewbot.entity.jma.telegram.seis;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import net.teamfruit.eewbot.entity.LgIntensity;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import reactor.util.annotation.Nullable;

import java.util.List;

@SuppressWarnings("unused")
public class Intensity {

    @JacksonXmlProperty(localName = "Forecast")
    private @Nullable IntensityDetail forecast;

    @JacksonXmlProperty(localName = "Observation")
    private @Nullable IntensityDetail observation;

    @Nullable
    public IntensityDetail getForecast() {
        return this.forecast;
    }

    @Nullable
    public IntensityDetail getObservation() {
        return this.observation;
    }

    public static class IntensityDetail {

        @JacksonXmlProperty(localName = "CodeDefine")
        private @Nullable CodeDefine codeDefine;

        @JacksonXmlProperty(localName = "MaxInt")
        private @Nullable SeismicIntensity maxInt;

        @JacksonXmlProperty(localName = "MaxLgInt")
        private @Nullable LgIntensity maxLgInt;

        @JacksonXmlProperty(localName = "LgCategory")
        private @Nullable String lgCategory;

        @JacksonXmlProperty(localName = "ForecastInt")
        private @Nullable ForecastInt forecastInt;

        @JacksonXmlProperty(localName = "ForecastLgInt")
        private @Nullable ForecastLgInt forecastLgInt;

        @JacksonXmlProperty(localName = "Appendix")
        private @Nullable IntensityAppendix appendix;

        @JacksonXmlProperty(localName = "Pref")
        @JacksonXmlElementWrapper(useWrapping = false)
        private List<IntensityPref> intensityPref;

        @Nullable
        public CodeDefine getCodeDefine() {
            return this.codeDefine;
        }

        @Nullable
        public SeismicIntensity getMaxInt() {
            return this.maxInt;
        }

        @Nullable
        public LgIntensity getMaxLgInt() {
            return this.maxLgInt;
        }

        @Nullable
        public String getLgCategory() {
            return this.lgCategory;
        }

        @Nullable
        public ForecastInt getForecastInt() {
            return this.forecastInt;
        }

        @Nullable
        public ForecastLgInt getForecastLgInt() {
            return this.forecastLgInt;
        }

        @Nullable
        public IntensityAppendix getAppendix() {
            return this.appendix;
        }

        public List<IntensityPref> getIntensityPref() {
            return this.intensityPref;
        }

        public static class IntensityAppendix {

            @JacksonXmlProperty(localName = "MaxIntChange")
            private int maxIntChange;

            @JacksonXmlProperty(localName = "MaxLgIntChange")
            private @Nullable Integer maxLgIntChange;

            @JacksonXmlProperty(localName = "MaxIntChangeReason")
            private int maxIntChangeReason;

            @JacksonXmlProperty(localName = "MaxLgIntChangeReason")
            private @Nullable Integer maxLgIntChangeReason;

            public int getMaxIntChange() {
                return this.maxIntChange;
            }

            @Nullable
            public Integer getMaxLgIntChange() {
                return this.maxLgIntChange;
            }

            public int getMaxIntChangeReason() {
                return this.maxIntChangeReason;
            }

            @Nullable
            public Integer getMaxLgIntChangeReason() {
                return this.maxLgIntChangeReason;
            }

            @Override
            public String toString() {
                return "IntensityAppendix{" +
                        "maxIntChange=" + this.maxIntChange +
                        ", maxLgIntChange=" + this.maxLgIntChange +
                        ", maxIntChangeReason=" + this.maxIntChangeReason +
                        ", maxLgIntChangeReason=" + this.maxLgIntChangeReason +
                        '}';
            }
        }

        @Override
        public String toString() {
            return "IntensityDetail{" +
                    "codeDefine=" + this.codeDefine +
                    ", maxInt=" + this.maxInt +
                    ", maxLgInt=" + this.maxLgInt +
                    ", lgCategory='" + this.lgCategory + '\'' +
                    ", forecastInt=" + this.forecastInt +
                    ", forecastLgInt=" + this.forecastLgInt +
                    ", appendix=" + this.appendix +
                    ", intensityPref=" + this.intensityPref +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "Intensity{" +
                "forecast=" + this.forecast +
                ", observation=" + this.observation +
                '}';
    }
}
