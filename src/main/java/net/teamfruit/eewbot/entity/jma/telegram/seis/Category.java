package net.teamfruit.eewbot.entity.jma.telegram.seis;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import reactor.util.annotation.Nullable;

@SuppressWarnings("unused")
public class Category {

    @JacksonXmlProperty(localName = "Kind")
    private Kind kind;

    @JacksonXmlProperty(localName = "LastKind")
    private @Nullable Kind lastKind;

    public Kind getKind() {
        return this.kind;
    }

    @Nullable
    public Kind getLastKind() {
        return this.lastKind;
    }

    public static class Kind {

        @JacksonXmlProperty(localName = "Name")
        private String name;

        @JacksonXmlProperty(localName = "Code")
        private String code;

        public String getName() {
            return this.name;
        }

        public String getCode() {
            return this.code;
        }

        @Override
        public String toString() {
            return "Kind{" +
                    "name='" + this.name + '\'' +
                    ", code='" + this.code + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "Category{" +
                "kind=" + this.kind +
                ", lastKind=" + this.lastKind +
                '}';
    }
}
