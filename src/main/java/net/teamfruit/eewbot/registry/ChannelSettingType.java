package net.teamfruit.eewbot.registry;

public enum ChannelSettingType {
    BASE("base"),
    MODIFIER("modifier");

    private final String customId;

    ChannelSettingType(String customId) {
        this.customId = customId;
    }

    public String getCustomId() {
        return this.customId;
    }
}
