package net.teamfruit.eewbot.entity.jma.telegram.seis;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("unused")
public class TsunamiDetail {

    @JacksonXmlProperty(localName = "CodeDefine")
    private @Nullable CodeDefine codeDefine;

    @JacksonXmlProperty(localName = "Item")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<TsunamiItem> items;

    @Nullable
    public CodeDefine getCodeDefine() {
        return this.codeDefine;
    }

    public List<TsunamiItem> getItems() {
        return this.items;
    }

    @Override
    public String toString() {
        return "TsunamiDetail{" +
                "codeDefine=" + this.codeDefine +
                ", items=" + this.items +
                '}';
    }
}
