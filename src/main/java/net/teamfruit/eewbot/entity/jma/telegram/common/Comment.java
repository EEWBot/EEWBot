package net.teamfruit.eewbot.entity.jma.telegram.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import reactor.util.annotation.Nullable;

import java.util.Optional;

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

    public Optional<CommentForm> getWarningComment() {
        return Optional.ofNullable(this.warningComment);
    }

    public Optional<CommentForm> getForecastComment() {
        return Optional.ofNullable(this.forecastComment);
    }

    public Optional<CommentForm> getObservationComment() {
        return Optional.ofNullable(this.observationComment);
    }

    public Optional<CommentForm> getVarComment() {
        return Optional.ofNullable(this.varComment);
    }

    public Optional<String> getFreeFormComment() {
        return Optional.ofNullable(this.freeFormComment);
    }

    public Optional<String> getUri() {
        return Optional.ofNullable(this.uri);
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
