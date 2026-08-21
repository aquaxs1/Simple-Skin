package de.simpleskin.mixin;

import de.simpleskin.skin.SkinOverrideManager;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void simpleSkin$replaceSkin(CallbackInfoReturnable<SkinTextures> callback) {
        AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) (Object) this;
        callback.setReturnValue(SkinOverrideManager.apply(player.getUuid(), callback.getReturnValue()));
    }
}
