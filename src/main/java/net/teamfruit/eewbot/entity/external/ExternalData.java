package net.teamfruit.eewbot.entity.external;

public interface ExternalData {
    
    String getDataType();
    
    Object toExternalDto();
    
    String getRawData();
    
}