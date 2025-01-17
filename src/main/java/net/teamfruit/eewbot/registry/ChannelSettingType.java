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

    public static boolean hasCustomId(String customId) {
        for (ChannelSettingType value : values()) {
            if (value.customId.equals(customId))
                return true;
        }
        return false;
    }
}
