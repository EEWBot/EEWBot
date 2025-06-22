package net.teamfruit.eewbot.entity.external;

import net.teamfruit.eewbot.entity.dmdata.DmdataEEW;

public class EEWDataWrapper implements ExternalData {
    
    private final DmdataEEW eew;
    
    public EEWDataWrapper(DmdataEEW eew) {
        this.eew = eew;
    }
    
    @Override
    public String getDataType() {
        return "eew";
    }
    
    @Override
    public Object toExternalDto() {
        return eew.toExternalDto();
    }
    
    @Override
    public String getRawData() {
        return eew.getRawData();
    }
    
    public DmdataEEW getEew() {
        return eew;
    }
}