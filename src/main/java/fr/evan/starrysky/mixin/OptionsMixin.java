package fr.evan.starrysky.mixin;

import fr.evan.starrysky.render.SkyRenderHandler;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Masque les nuages en pleine nuit claire (option {@code hideCloudsAtNight}).
 * <p>
 * Le jeu ne dessine les nuages que si {@code Options#getCloudsType()} ne renvoie pas {@code OFF}. On renvoie donc
 * {@code OFF} quand le fondu de la nuit les a rendus invisibles : c'est indépendant du moteur de rendu des nuages,
 * donc ça marche aussi avec Sodium. Le réglage choisi dans les options vidéo n'est pas modifié.
 */
@Mixin(Options.class)
public abstract class OptionsMixin {
    @Inject(method = "getCloudsType", at = @At("HEAD"), cancellable = true, require = 0)
    private void starrysky$hideCloudsAtNight(CallbackInfoReturnable<CloudStatus> cir) {
        if (SkyRenderHandler.cloudFactor() <= 0.01F) {
            cir.setReturnValue(CloudStatus.OFF);
        }
    }
}
