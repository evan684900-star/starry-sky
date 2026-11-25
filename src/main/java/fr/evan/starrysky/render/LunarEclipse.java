package fr.evan.starrysky.render;

import fr.evan.starrysky.telescope.StarCatalog;

/**
 * Éclipses de lune : certaines nuits de pleine lune (une sur {@link #ONE_IN}), la lune passe dans l'ombre de la
 * Terre autour de minuit. Elle s'assombrit puis prend une teinte cuivrée (« lune de sang ») pendant la totalité,
 * le halo rougit et faiblit, et le ciel plus sombre laisse mieux voir les étoiles et la Voie lactée.
 * <p>
 * Tout est déterminé par l'heure du jeu : deux joueurs sur le même serveur voient la même éclipse, sans réseau.
 */
public final class LunarEclipse {
    /** Une pleine lune sur ONE_IN est éclipsée (une pleine lune a lieu toutes les 8 nuits). */
    public static final int ONE_IN = 3;
    /** Milieu de l'éclipse : minuit. */
    private static final int MIDDLE = 18000;
    /** Début et fin de la phase partielle, en ticks depuis minuit. */
    private static final float PARTIAL = 3600.0F;
    /** Début et fin de la totalité, en ticks depuis minuit. */
    private static final float TOTAL = 1100.0F;

    /** Teinte de la lune en pleine totalité (multiplie sa couleur). */
    private static final float RED = 0.85F;
    private static final float GREEN = 0.30F;
    private static final float BLUE = 0.20F;

    private LunarEclipse() {
    }

    /** Vrai si la nuit qui contient ce moment connaît une éclipse (pleine lune choisie). */
    public static boolean isEclipseNight(long dayTime, int moonPhase) {
        if (moonPhase != 0) {
            return false;
        }
        long night = Math.floorDiv(dayTime, 24000L);
        int h = StarCatalog.mix((int) night * 0x45D9F3B + 0x3C6EF372);
        return Integer.remainderUnsigned(h, ONE_IN) == 0;
    }

    /** Avancement de l'éclipse : 0 = pas d'ombre, 1 = totalité. */
    public static float factor(long dayTime, float partialTick, int moonPhase) {
        if (!isEclipseNight(dayTime, moonPhase)) {
            return 0.0F;
        }
        float t = Math.abs(Math.floorMod(dayTime, 24000L) + partialTick - MIDDLE);
        if (t >= PARTIAL) {
            return 0.0F;
        }
        if (t <= TOTAL) {
            return 1.0F;
        }
        float x = (PARTIAL - t) / (PARTIAL - TOTAL);
        return x * x * (3.0F - 2.0F * x);
    }

    /** Teinte de la lune pour un avancement donné ({r, g, b} écrits dans out). */
    public static void moonTint(float eclipse, float[] out) {
        // L'ombre assombrit d'abord la lune, puis la lumière rougie par l'atmosphère de la Terre la colore.
        float dim = 1.0F - 0.35F * eclipse;
        out[0] = (1.0F + (RED - 1.0F) * eclipse) * dim;
        out[1] = (1.0F + (GREEN - 1.0F) * eclipse) * dim;
        out[2] = (1.0F + (BLUE - 1.0F) * eclipse) * dim;
    }
}
