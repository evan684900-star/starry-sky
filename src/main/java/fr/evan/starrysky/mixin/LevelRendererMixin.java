package fr.evan.starrysky.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.evan.starrysky.render.SkyRenderHandler;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin du rendu du ciel : masque les étoiles de base de Minecraft, fait s'effacer les nuages et teinte la lune
 * pendant les éclipses.
 * <p>
 * Pourquoi un mixin ? Les étoiles de base sont dessinées au milieu de {@code LevelRenderer#renderSky}, depuis un
 * tampon privé, sans événement NeoForge pour les désactiver. Remplacer tout le ciel (via
 * {@code DimensionSpecialEffects#renderSky}) obligerait à recopier le rendu du soleil, de la lune, des couleurs du
 * crépuscule, etc. — beaucoup plus intrusif.
 * <p>
 * Comment ? Dans {@code renderSky}, le jeu fait :
 * <pre>
 *   float starBrightness = this.level.getStarBrightness(partialTick) * (1 - pluie);
 *   if (starBrightness > 0) { ...dessine les étoiles... }
 * </pre>
 * On remplace la valeur renvoyée par {@code getStarBrightness} par 0 <b>uniquement à cet endroit</b>, ce qui saute
 * le dessin des étoiles. Le reste du jeu (et le rendu de Starry Sky) voit toujours la vraie valeur.
 * <p>
 * {@code require = 0} : si un autre mod réécrit {@code renderSky}, le mixin ne s'applique pas mais le jeu ne plante
 * pas ; les étoiles de base restent simplement visibles. En développement ({@code ./gradlew runClient}), l'option
 * {@code mixin.debug.countInjections} (voir build.gradle) fait au contraire échouer le lancement pour repérer le problème.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @ModifyExpressionValue(
            method = "renderSky",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getStarBrightness(F)F"),
            require = 0)
    private float starrysky$hideVanillaStars(float original) {
        return SkyRenderHandler.shouldHideVanillaStars() ? 0.0F : original;
    }

    /**
     * Fondu des nuages à la tombée de la nuit : les nuages du jeu de base sont dessinés avec une opacité de 0,8,
     * fixée par {@code RenderSystem.setShaderColor(r, g, b, 0.8)} ; on la multiplie par le facteur de fondu.
     * Seules les valeurs inférieures à 1 sont touchées, pour ne pas modifier la remise à zéro (1, 1, 1, 1) qui suit.
     */
    @ModifyArg(
            method = "renderClouds",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V"),
            index = 3,
            require = 0)
    private float starrysky$fadeClouds(float alpha) {
        return alpha < 0.999F ? alpha * SkyRenderHandler.cloudFactor() : alpha;
    }

    /**
     * Éclipse de lune : juste avant que la lune soit dessinée (au moment où le jeu lit sa phase), on teinte la
     * couleur du shader en rouge cuivré. L'alpha (pluie) est conservé. La couleur est remise à zéro par le jeu
     * juste après (dessin des étoiles ou {@code setShaderColor(1, 1, 1, 1)}).
     */
    @Inject(
            method = "renderSky",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMoonPhase()I"),
            require = 0)
    private void starrysky$tintEclipsedMoon(CallbackInfo ci) {
        float[] tint = SkyRenderHandler.moonTint();
        if (tint != null) {
            float[] current = RenderSystem.getShaderColor();
            RenderSystem.setShaderColor(current[0] * tint[0], current[1] * tint[1], current[2] * tint[2], current[3]);
        }
    }
}
