package de.simpleskin.data;

import net.minecraft.world.entity.player.PlayerModelType;

public enum SkinModel {
    WIDE,
    SLIM;

    public PlayerModelType toMinecraft() {
        return this == SLIM ? PlayerModelType.SLIM : PlayerModelType.WIDE;
    }

    public static SkinModel fromMetadata(String model) {
        return "slim".equalsIgnoreCase(model) ? SLIM : WIDE;
    }
}
