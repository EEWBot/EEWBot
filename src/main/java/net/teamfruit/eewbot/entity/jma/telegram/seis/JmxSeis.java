package net.teamfruit.eewbot.entity.jma.telegram.seis;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import net.teamfruit.eewbot.entity.jma.AbstractJMAReport;
import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import reactor.util.annotation.Nullable;

import java.util.List;

@SuppressWarnings("unused")
public abstract class JmxSeis extends AbstractJMAReport {

    @JacksonXmlProperty(localName = "Body")
    protected Body body;

    public Body getBody() {
        return this.body;
    }

    public static class Body {

        // TODO
        // Naming
        // Tsunami

        @JacksonXmlProperty(localName = "Earthquake")
        @JacksonXmlElementWrapper(useWrapping = false)
        protected List<Earthquake> earthquakes;

        @JacksonXmlProperty(localName = "Intensity")
        protected @Nullable Intensity intensity;

        // Tokai
        // EarthquakeInfo
        // EarthquakeCount
        // Aftershock

        @JacksonXmlProperty(localName = "Text")
        protected @Nullable String text;

        // NextAdvisory

        @JacksonXmlProperty(localName = "Comments")
        protected @Nullable Comment comments;

        public List<Earthquake> getEarthquakes() {
            return this.earthquakes;
        }

        @Nullable
        public Intensity getIntensity() {
            return this.intensity;
        }

        @Nullable
        public String getText() {
            return this.text;
        }

        @Nullable
        public Comment getComments() {
            return this.comments;
        }

        @Override
        public String toString() {
            return "Body{" +
                    "earthquakes=" + this.earthquakes +
                    ", intensity=" + this.intensity +
                    ", text='" + this.text + '\'' +
                    ", comments=" + this.comments +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "JmxSeis{" +
                "body=" + this.body +
                '}';
    }
}
