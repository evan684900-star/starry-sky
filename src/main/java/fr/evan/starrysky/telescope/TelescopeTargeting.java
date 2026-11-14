package fr.evan.starrysky.telescope;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Détermine quelle étoile du catalogue le joueur vise avec la longue-vue.
 * <p>
 * Appelé pendant le rendu du ciel ({@link fr.evan.starrysky.render.SkyRenderHandler}), uniquement quand le joueur
 * regarde dans la longue-vue : on compare alors la direction du regard aux quelques centaines d'étoiles du catalogue
 * (un simple produit scalaire par étoile, négligeable). L'interface ({@link TelescopeHud}) lit ensuite le résultat.
 */
public final class TelescopeTargeting {
    /** Ce que la longue-vue peut afficher à cette image. */
    public enum State {
        /** Pas de longue-vue, ou mod inactif dans cette dimension. */
        NONE,
        /** Longue-vue utilisée, mais le ciel n'est pas assez sombre (jour, pluie, brouillard...). */
        TOO_BRIGHT,
        /** Ciel étoilé, aucune étoile brillante au centre. */
        SEARCHING,
        /** Une étoile du catalogue est au centre. */
        LOCKED
    }

    /** Une étoile est accrochée quand elle est à moins de 1,3° du centre de la vue... */
    private static final float ACQUIRE_COS = (float) Math.cos(Math.toRadians(1.3));
    /** ...et le reste tant qu'elle ne s'en éloigne pas de plus de 2,2° (évite les clignotements). */
    private static final float KEEP_COS = (float) Math.cos(Math.toRadians(2.2));
    /** En dessous de cette visibilité des étoiles, la longue-vue ne montre rien d'utile. */
    public static final float MIN_VISIBILITY = 0.25F;

    private static State state = State.NONE;
    @Nullable
    private static CatalogStar target;
    // Projection (caméra × rotation du ciel) de la dernière image, pour placer le viseur sur l'étoile.
    private static final Matrix4f SKY_TO_CLIP = new Matrix4f();
    private static final Vector4f TMP = new Vector4f();

    private TelescopeTargeting() {
    }

    /** Vrai si le joueur regarde actuellement dans une longue-vue, en vue à la première personne. */
    public static boolean isScoping(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        return player != null && player.isScoping() && minecraft.options.getCameraType().isFirstPerson();
    }

    /** Début d'une image : rien n'est visé tant que le rendu du ciel n'a pas dit le contraire. */
    public static void beginFrame() {
        state = State.NONE;
    }

    /** La longue-vue est utilisée mais le ciel est trop clair pour observer. */
    public static void markTooBright() {
        state = State.TOO_BRIGHT;
        target = null;
    }

    /**
     * Cherche l'étoile visée.
     *
     * @param skyRotation rotation du ciel (repère du ciel vers repère du monde)
     * @param starView    caméra × rotation du ciel
     * @param look        direction du regard, dans le repère du monde
     */
    public static void update(Matrix4f skyRotation, Matrix4f starView, Matrix4f projection, Vector3f look) {
        // Direction du regard dans le repère du ciel : on applique la rotation inverse (sa transposée).
        float lx = skyRotation.m00() * look.x + skyRotation.m01() * look.y + skyRotation.m02() * look.z;
        float ly = skyRotation.m10() * look.x + skyRotation.m11() * look.y + skyRotation.m12() * look.z;
        float lz = skyRotation.m20() * look.x + skyRotation.m21() * look.y + skyRotation.m22() * look.z;

        CatalogStar best = null;
        float bestScore = -2.0F;
        List<CatalogStar> stars = StarCatalog.stars();
        for (int i = 0; i < stars.size(); i++) {
            CatalogStar star = stars.get(i);
            boolean current = star == target;
            // dot = cosinus de l'angle entre le regard et l'étoile (1 = pile au centre).
            float dot = star.x() * lx + star.y() * ly + star.z() * lz;
            if (dot < (current ? KEEP_COS : ACQUIRE_COS) || !aboveHorizon(skyRotation, star)) {
                continue;
            }
            // L'étoile déjà visée garde un léger avantage, pour ne pas sauter d'une étoile à l'autre.
            float score = current ? dot + 2.0E-4F : dot;
            if (score > bestScore) {
                best = star;
                bestScore = score;
            }
        }
        target = best;
        state = best != null ? State.LOCKED : State.SEARCHING;
        SKY_TO_CLIP.set(projection).mul(starView);
    }

    public static State state() {
        return state;
    }

    @Nullable
    public static CatalogStar target() {
        return state == State.LOCKED ? target : null;
    }

    /**
     * Position à l'écran (en coordonnées d'interface) d'une étoile du catalogue, d'après la dernière image rendue.
     *
     * @return faux si l'étoile est derrière la caméra
     */
    public static boolean screenPosition(CatalogStar star, int guiWidth, int guiHeight, float[] out) {
        float r = 100.0F;
        SKY_TO_CLIP.transform(TMP.set(star.x() * r, star.y() * r, star.z() * r, 1.0F));
        if (TMP.w <= 0.0F) {
            return false;
        }
        out[0] = (TMP.x / TMP.w * 0.5F + 0.5F) * guiWidth;
        out[1] = (0.5F - TMP.y / TMP.w * 0.5F) * guiHeight;
        return true;
    }

    /** L'étoile doit être nettement au-dessus de l'horizon (en dessous, elle n'est pas dessinée). */
    private static boolean aboveHorizon(Matrix4f skyRotation, CatalogStar star) {
        float worldY = skyRotation.m01() * star.x() + skyRotation.m11() * star.y() + skyRotation.m21() * star.z();
        return worldY > 0.03F;
    }
}
