package net.teamfruit.eewbot.entity.jma.telegram.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import reactor.util.annotation.Nullable;

@SuppressWarnings("unused")
public class Comment {

    @JacksonXmlProperty(localName = "WarningComment")
    private @Nullable CommentForm warningComment;

    @JacksonXmlProperty(localName = "ForecastComment")
    private @Nullable CommentForm forecastComment;

    @JacksonXmlProperty(localName = "ObservationComment")
    private @Nullable CommentForm observationComment;

    @JacksonXmlProperty(localName = "VarComment")
    private @Nullable CommentForm varComment;

    @JacksonXmlProperty(localName = "FreeFormComment")
    private @Nullable String freeFormComment;

    @JacksonXmlProperty(localName = "URI")
    private @Nullable String uri;

    @Nullable
    public CommentForm getWarningComment() {
        return this.warningComment;
    }

    @Nullable
    public CommentForm getForecastComment() {
        return this.forecastComment;
    }

    @Nullable
    public CommentForm getObservationComment() {
        return this.observationComment;
    }

    @Nullable
    public CommentForm getVarComment() {
        return this.varComment;
    }

    @Nullable
    public String getFreeFormComment() {
        return this.freeFormComment;
    }

    @Nullable
    public String getUri() {
        return this.uri;
    }

    @JsonIgnoreProperties("codeType")
    public static class CommentForm {

        @JacksonXmlProperty(localName = "Text")
        private String text;

        @JacksonXmlProperty(localName = "Code")
        private String code;

        public String getText() {
            return this.text;
        }

        public String getCode() {
            return this.code;
        }

        @Override
        public String toString() {
            return "CommentForm{" +
                    "text='" + this.text + '\'' +
                    ", code='" + this.code + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "Comment{" +
                "warningComment=" + this.warningComment +
                ", forecastComment=" + this.forecastComment +
                ", observationComment=" + this.observationComment +
                ", varComment=" + this.varComment +
                ", freeFormComment='" + this.freeFormComment + '\'' +
                ", uri='" + this.uri + '\'' +
                '}';
    }
}
