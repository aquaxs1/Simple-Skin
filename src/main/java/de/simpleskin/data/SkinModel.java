package de.simpleskin.data;

import net.minecraft.entity.player.PlayerSkinType;

public enum SkinModel {
    WIDE,
    SLIM;

    public PlayerSkinType toMinecraft() {
        return this == SLIM ? PlayerSkinType.SLIM : PlayerSkinType.WIDE;
    }

    public static SkinModel fromMetadata(String model) {
        return "slim".equalsIgnoreCase(model) ? SLIM : WIDE;
    }
}
