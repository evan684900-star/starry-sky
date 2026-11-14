package fr.evan.starrysky.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import fr.evan.starrysky.render.SkyRenderHandler;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Seul mixin du mod : masque les étoiles de base de Minecraft.
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
}
