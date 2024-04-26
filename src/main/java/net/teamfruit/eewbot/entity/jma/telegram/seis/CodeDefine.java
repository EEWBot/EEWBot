package net.teamfruit.eewbot.entity.jma.telegram.seis;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

import java.util.List;

@SuppressWarnings("unused")
public class CodeDefine {

    @JacksonXmlProperty(localName = "Type")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<CodeDefineType> types;

    public List<CodeDefineType> getTypes() {
        return this.types;
    }

    public static class CodeDefineType {

        @JacksonXmlText
        private String value;

        @JacksonXmlProperty(isAttribute = true)
        private String xpath;

        public String getValue() {
            return this.value;
        }

        public String getXpath() {
            return this.xpath;
        }

        @Override
        public String toString() {
            return "CodeDefineType{" +
                    "value='" + this.value + '\'' +
                    ", xpath='" + this.xpath + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "CodeDefine{" +
                "types=" + this.types +
                '}';
    }
}
