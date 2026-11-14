package fr.evan.starrysky.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.evan.starrysky.telescope.StarCatalog;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;

/**
 * Le champ d'étoiles : un maillage statique stocké dans un {@link VertexBuffer} (donc sur la carte graphique).
 * <p>
 * Les étoiles sont générées avec une graine fixe : elles sont toujours au même endroit d'une partie à l'autre.
 * Le maillage n'est reconstruit que si le nombre d'étoiles, l'option de couleur ou la Voie lactée changent.
 * La taille, la luminosité, le scintillement et la rotation sont gérés par le shader ({@code star.vsh}) :
 * les modifier ne reconstruit rien.
 * <p>
 * Chaque étoile est un « sprite » de 4 sommets, déployé par la carte graphique à une taille exprimée en pixels.
 * Format de chaque sommet ({@link DefaultVertexFormat#POSITION_TEX_COLOR}) :
 * <ul>
 *     <li>Position : centre de l'étoile sur une sphère de rayon {@link #RADIUS} (identique pour les 4 coins) ;</li>
 *     <li>UV.x : signe du coin × demi-taille de base ; UV.y : signe du coin × (1 + force des éclats) ;</li>
 *     <li>Couleur RGB : teinte × intensité de l'étoile ; Alpha : valeur aléatoire propre à l'étoile,
 *     utilisée par le shader pour la phase et la fréquence du scintillement.</li>
 * </ul>
 */
public final class StarField {
    /** Distance des étoiles à la caméra, identique aux étoiles de base. Doit correspondre à RADIUS dans star.vsh. */
    public static final float RADIUS = 100.0F;
    /** Période de l'horloge de scintillement, en secondes. Doit correspondre à PERIOD dans star.vsh. */
    public static final float TWINKLE_PERIOD = 1000.0F;

    private static final long STAR_SEED = 0x57A2_5C1EL;
    private static final long DUST_SEED = 0x4D57_D057L;

    /** Poussières d'étoiles de la Voie lactée ajoutées pour chaque étoile « normale ». */
    private static final float DUST_PER_STAR = 2.0F;
    private static final int MAX_DUST = 15000;

    /** Au-dessus de cette magnitude (environ 3 % des étoiles), l'étoile reçoit des éclats en croix (et un nom). */
    private static final float GLINT_THRESHOLD = StarCatalog.NAMED_THRESHOLD;

    // Teintes : blanc, bleuté, jaune pâle.
    private static final float[] WHITE = {1.00F, 1.00F, 1.00F};
    private static final float[] BLUISH = {0.72F, 0.83F, 1.00F};
    private static final float[] PALE_YELLOW = {1.00F, 0.93F, 0.72F};
    // Teintes plus discrètes pour les poussières d'étoiles.
    private static final float[] DUST_BLUISH = {0.80F, 0.88F, 1.00F};
    private static final float[] DUST_WARM = {1.00F, 0.94F, 0.82F};

    private static final float[][] CORNERS = {{-1.0F, -1.0F}, {1.0F, -1.0F}, {1.0F, 1.0F}, {-1.0F, 1.0F}};

    @Nullable
    private VertexBuffer buffer;
    private int builtCount = -1;
    private boolean builtColored;
    private boolean builtMilkyWay;

    /**
     * Reconstruit le maillage si (et seulement si) un paramètre qui le concerne a changé depuis la dernière fois.
     */
    public void rebuildIfNeeded(int count, boolean colored, boolean milkyWay) {
        if (buffer != null && count == builtCount && colored == builtColored && milkyWay == builtMilkyWay) {
            return;
        }
        rebuild(count, colored, milkyWay);
        builtCount = count;
        builtColored = colored;
        builtMilkyWay = milkyWay;
    }

    /** Dessine les étoiles avec le shader et les matrices fournis. */
    public void draw(Matrix4f modelView, Matrix4f projection, ShaderInstance shader) {
        if (buffer == null) {
            return;
        }
        buffer.bind();
        buffer.drawWithShader(modelView, projection, shader);
        VertexBuffer.unbind();
    }

    private void rebuild(int count, boolean colored, boolean milkyWay) {
        int dustTarget = milkyWay ? Math.min(Math.round(count * DUST_PER_STAR), MAX_DUST) : 0;
        VertexFormat format = DefaultVertexFormat.POSITION_TEX_COLOR;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder((count + dustTarget) * 4 * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, format);

            RandomSource random = RandomSource.create(STAR_SEED);
            StarCatalog.Builder catalog = StarCatalog.builder();
            for (int i = 0; i < count; i++) {
                addStar(builder, random, colored, catalog);
            }
            catalog.publish();
            if (dustTarget > 0) {
                addMilkyWayDust(builder, dustTarget, colored);
            }

            if (buffer == null) {
                buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            }
            buffer.bind();
            buffer.upload(builder.buildOrThrow()); // upload() libère aussi le maillage temporaire
            VertexBuffer.unbind();
        }
    }

    /**
     * Ajoute une étoile. Chaque étoile consomme toujours le même nombre de valeurs aléatoires :
     * changer les couleurs ne déplace donc pas les étoiles, et augmenter le nombre d'étoiles ne fait
     * qu'en ajouter de nouvelles.
     */
    private static void addStar(BufferBuilder builder, RandomSource random, boolean colored, StarCatalog.Builder catalog) {
        // Direction uniforme sur la sphère.
        float z = random.nextFloat() * 2.0F - 1.0F;
        float angle = random.nextFloat() * (float) (Math.PI * 2.0);
        float ring = (float) Math.sqrt(1.0F - z * z);
        float x = ring * (float) Math.cos(angle);
        float y = ring * (float) Math.sin(angle);

        // Magnitude : beaucoup d'étoiles faibles et quelques étoiles brillantes (et plus grosses).
        float magnitude = (float) Math.pow(random.nextFloat(), 2.4);
        float sizeJitter = 0.85F + random.nextFloat() * 0.30F;
        float colorPick = random.nextFloat();
        float colorBlend = random.nextFloat();
        float twinkleSeed = random.nextFloat();

        float size = (0.30F + 0.55F * magnitude) * sizeJitter;
        float intensity = 0.30F + 0.70F * magnitude;
        // Éclats en croix, réservés aux étoiles les plus brillantes.
        float glint = magnitude > GLINT_THRESHOLD
                ? 0.35F + 0.65F * (magnitude - GLINT_THRESHOLD) / (1.0F - GLINT_THRESHOLD)
                : 0.0F;

        float[] tint = WHITE;
        float towardWhite = 0.0F;
        if (colored) {
            if (colorPick < 0.25F) {
                tint = BLUISH;
            } else if (colorPick < 0.40F) {
                tint = PALE_YELLOW;
            }
            // Mélange partiel vers le blanc pour des nuances plus variées.
            towardWhite = colorBlend * 0.5F;
        }
        float r = (tint[0] + (1.0F - tint[0]) * towardWhite) * intensity;
        float g = (tint[1] + (1.0F - tint[1]) * towardWhite) * intensity;
        float b = (tint[2] + (1.0F - tint[2]) * towardWhite) * intensity;

        sprite(builder, x, y, z, size, glint, r, g, b, twinkleSeed);

        // Les étoiles brillantes entrent dans le catalogue de la longue-vue. Le type de couleur est transmis même
        // si les couleurs sont désactivées, pour qu'une étoile garde toujours la même classe spectrale.
        int tintKind = colorPick < 0.25F ? 1 : colorPick < 0.40F ? 2 : 0;
        catalog.offer(x, y, z, magnitude, tintKind, twinkleSeed);
    }

    /**
     * Ajoute les poussières d'étoiles de la Voie lactée : de petites étoiles pâles, tirées au hasard dans la bande
     * et conservées avec une probabilité égale à la densité de la Voie lactée à cet endroit
     * ({@link MilkyWay#structure}). Elles dessinent ainsi les mêmes nuages et la même traînée sombre que la lueur.
     */
    private static void addMilkyWayDust(BufferBuilder builder, int target, boolean colored) {
        RandomSource random = RandomSource.create(DUST_SEED);
        double[] dir = new double[3];
        double maxLatitude = Math.toRadians(50.0);
        int added = 0;
        // Garde-fou : le tirage s'arrête quoi qu'il arrive après un nombre borné d'essais.
        for (int attempts = target * 40; added < target && attempts > 0; attempts--) {
            double longitude = random.nextFloat() * Math.PI * 2.0;
            // Un cœur dense et étroit, plus un halo plus large et clairsemé.
            double spread = random.nextFloat() < 0.7F ? Math.toRadians(6.5) : Math.toRadians(15.0);
            double latitude = random.nextGaussian() * spread;
            float keep = random.nextFloat();
            if (Math.abs(latitude) > maxLatitude) {
                continue;
            }
            MilkyWay.direction(longitude, latitude, dir);
            if (keep >= MilkyWay.structure(longitude, latitude, dir) * 1.25) {
                continue;
            }

            float size = 0.13F + 0.11F * random.nextFloat();
            float u = random.nextFloat();
            float intensity = 0.25F + 0.40F * u * u;
            float colorPick = random.nextFloat();
            float twinkleSeed = random.nextFloat();

            float[] tint = WHITE;
            if (colored) {
                if (colorPick < 0.55F) {
                    tint = DUST_BLUISH;
                } else if (colorPick < 0.70F) {
                    tint = DUST_WARM;
                }
            }
            sprite(builder, (float) dir[0], (float) dir[1], (float) dir[2], size, 0.0F,
                    tint[0] * intensity, tint[1] * intensity, tint[2] * intensity, twinkleSeed);
            added++;
        }
    }

    /** Écrit les 4 sommets d'une étoile. (x, y, z) est une direction de longueur 1. */
    private static void sprite(BufferBuilder builder, float x, float y, float z, float size, float glint,
                               float r, float g, float b, float seed) {
        for (float[] corner : CORNERS) {
            builder.addVertex(x * RADIUS, y * RADIUS, z * RADIUS)
                    .setUv(corner[0] * size, corner[1] * (1.0F + glint))
                    .setColor(r, g, b, seed);
        }
    }
}
