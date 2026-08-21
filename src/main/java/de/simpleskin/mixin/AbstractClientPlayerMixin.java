package de.simpleskin.mixin;

import de.simpleskin.skin.SkinOverrideManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void simpleSkin$replaceSkin(CallbackInfoReturnable<PlayerSkin> callback) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        callback.setReturnValue(SkinOverrideManager.apply(player.getUUID(), callback.getReturnValue()));
    }
}
