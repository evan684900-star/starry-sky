package fr.evan.starrysky.mixin;

import fr.evan.starrysky.render.SkyRenderHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Fondu des nuages quand Sodium est installé : Sodium dessine les nuages avec son propre code, qui fixe lui aussi
 * l'opacité via {@code RenderSystem.setShaderColor}. {@code @Pseudo} : si Sodium est absent (ou si son code change),
 * ce mixin est simplement ignoré. Le masquage complet en pleine nuit, lui, marche dans tous les cas
 * ({@link OptionsMixin}).
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.immediate.CloudRenderer", remap = false)
public abstract class SodiumCloudRendererMixin {
    @ModifyArg(
            method = "render",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V"),
            index = 3,
            require = 0)
    private float starrysky$fadeClouds(float alpha) {
        return alpha < 0.999F ? alpha * SkyRenderHandler.cloudFactor() : alpha;
    }
}
