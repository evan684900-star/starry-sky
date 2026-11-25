package fr.evan.starrysky.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.evan.starrysky.telescope.CatalogStar;
import fr.evan.starrysky.telescope.StarCatalog;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

/**
 * Comètes : de temps en temps (une apparition par cycle de {@link #CYCLE_NIGHTS} nuits), une comète reste visible
 * pendant {@link #VISIBLE_NIGHTS} nuits. Elle grandit, atteint son maximum au milieu de son passage, puis s'efface.
 * <p>
 * Elle est faite de trois parties, toutes dans le repère du ciel (elle tourne avec les étoiles) :
 * <ul>
 *     <li>un <b>noyau</b> brillant (un sprite du shader des étoiles, sans scintillement) ;</li>
 *     <li>une <b>chevelure</b> verdâtre (la coma), petits anneaux concentriques ;</li>
 *     <li>deux <b>queues</b> qui pointent toujours à l'opposé du soleil : une queue de poussières, large, courbée et
 *     dorée, et une queue d'ions, fine, droite et bleue.</li>
 * </ul>
 * La chevelure et les queues sont dessinées avec le shader de la Voie lactée (couleurs par sommet). Le maillage
 * (quelques centaines de sommets) n'est reconstruit qu'une fois par minute de jeu, quand la comète a bougé.
 */
public final class Comet {
    /** Une apparition tous les CYCLE_NIGHTS nuits... */
    public static final int CYCLE_NIGHTS = 24;
    /** ...pendant VISIBLE_NIGHTS nuits. */
    public static final int VISIBLE_NIGHTS = 5;
    /** Intensité maximale de la lueur (queues et chevelure). */
    public static final float GLOW_PEAK = 1.0F;

    /** Noms imaginaires des comètes (le numéro d'apparition les rend uniques : « C/7 Lumen »). */
    private static final String[] NAMES = {
            "Lumen", "Aster", "Brisa", "Calyx", "Dorée", "Ember", "Faro", "Gaïa", "Hélios", "Iris", "Jade", "Kiri",
            "Lyre", "Mistral", "Nova", "Opale", "Perle", "Quasar", "Rhéa", "Sélène", "Tess", "Ulysse", "Véga", "Zéphyr"
    };

    private static final int TAIL_SEGMENTS = 28;
    private static final int COMA_RINGS = 6;
    private static final int COMA_SEGMENTS = 24;
    private static final float[] ACROSS = {-1.0F, -0.5F, 0.0F, 0.5F, 1.0F};

    @Nullable
    private VertexBuffer glowBuffer;
    @Nullable
    private VertexBuffer nucleusBuffer;
    private long builtStep = Long.MIN_VALUE;
    private float envelope;
    private static volatile List<CatalogStar> catalog = List.of();

    /** La comète visible (vide s'il n'y en a pas), comme astre identifiable à la longue-vue. */
    public static List<CatalogStar> catalog() {
        return catalog;
    }

    /** Éclat actuel de la comète, de 0 (absente) à 1 (au plus fort de son passage). */
    public float envelope() {
        return envelope;
    }

    /** Oublie la comète (option désactivée, changement de monde). */
    public void clear() {
        envelope = 0.0F;
        builtStep = Long.MIN_VALUE;
        catalog = List.of();
    }

    /** Numéro de l'apparition en cours à ce moment du jeu, ou -1 s'il n'y a pas de comète. */
    public static long apparitionAt(long dayTime) {
        double nights = (dayTime - 12000L) / 24000.0;
        long cycle = (long) Math.floor(nights / CYCLE_NIGHTS);
        double start = cycle * (double) CYCLE_NIGHTS + startOffset(cycle);
        return nights >= start && nights < start + VISIBLE_NIGHTS ? cycle : -1L;
    }

    /** Nom de la comète d'une apparition donnée. */
    public static String nameOf(long cycle) {
        int h = StarCatalog.mix((int) cycle * 0x3C6EF372 + 0x1B873593);
        return "C/" + (cycle + 1) + " " + NAMES[Integer.remainderUnsigned(h, NAMES.length)];
    }

    /** Caractéristiques d'une comète d'après son nom (pour le carnet, même quand elle n'est plus visible). */
    @Nullable
    public static CatalogStar describe(String name) {
        if (!name.startsWith("C/")) {
            return null;
        }
        int space = name.indexOf(' ');
        try {
            long cycle = Long.parseLong(name.substring(2, space < 0 ? name.length() : space)) - 1L;
            return entry(cycle, 0.0F, 1.0F, 0.0F, 1.0F);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Met à jour la comète : apparition, position, éclat et maillages. */
    public void update(long dayTime) {
        long step = Math.floorDiv(dayTime, 1200L);
        if (step == builtStep) {
            return;
        }
        builtStep = step;
        long cycle = apparitionAt(dayTime);
        if (cycle < 0L) {
            envelope = 0.0F;
            catalog = List.of();
            return;
        }

        double nights = (dayTime - 12000L) / 24000.0;
        double phase = (nights - (cycle * (double) CYCLE_NIGHTS + startOffset(cycle))) / VISIBLE_NIGHTS;
        envelope = (float) Math.pow(Math.sin(Math.PI * Math.min(1.0, Math.max(0.0, phase))), 0.7);
        int h = StarCatalog.mix((int) cycle * 0x7FEB352D + 0x165667B1);

        // Position : entre 105° et 135° du soleil (donc haut dans le ciel la nuit, loin de la lune),
        // avec une lente dérive d'une nuit à l'autre.
        double theta = Math.toRadians(105.0 + 30.0 * unit(h) + 6.0 * phase);
        double phi = Math.toRadians(360.0 * unit(h >>> 8) + 14.0 * phase);
        float px = (float) (Math.sin(theta) * Math.cos(phi));
        float py = (float) Math.cos(theta);
        float pz = (float) (Math.sin(theta) * Math.sin(phi));

        // Direction de la queue : à l'opposé du soleil (0, 1, 0), dans le plan tangent à la tête.
        float tx = py * px;
        float ty = py * py - 1.0F;
        float tz = py * pz;
        float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        tx /= tl;
        ty /= tl;
        tz /= tl;
        // Côté vers lequel la queue de poussières se courbe.
        float bx = py * tz - pz * ty;
        float by = pz * tx - px * tz;
        float bz = px * ty - py * tx;
        float bend = (h & 0x10000) != 0 ? 1.0F : -1.0F;

        float length = (float) Math.toRadians(16.0 + 20.0 * envelope + 8.0 * unit(h >>> 16));
        buildGlow(px, py, pz, tx, ty, tz, bx * bend, by * bend, bz * bend, length);
        buildNucleus(px, py, pz);
        catalog = List.of(entry(cycle, px, py, pz, envelope));
    }

    /** Dessine la chevelure et les queues (shader de la Voie lactée ; l'appelant règle l'intensité). */
    public void drawGlow(Matrix4f modelView, Matrix4f projection, ShaderInstance shader) {
        if (envelope > 0.0F && glowBuffer != null) {
            glowBuffer.bind();
            glowBuffer.drawWithShader(modelView, projection, shader);
            VertexBuffer.unbind();
        }
    }

    /** Dessine le noyau (shader des étoiles, sans scintillement). */
    public void drawNucleus(Matrix4f modelView, Matrix4f projection, ShaderInstance shader) {
        if (envelope > 0.0F && nucleusBuffer != null) {
            nucleusBuffer.bind();
            nucleusBuffer.drawWithShader(modelView, projection, shader);
            VertexBuffer.unbind();
        }
    }

    // ------------------------------------------------------------------------------------------------- maillages

    private void buildGlow(float px, float py, float pz, float tx, float ty, float tz,
                           float bx, float by, float bz, float length) {
        VertexFormat format = DefaultVertexFormat.POSITION_COLOR;
        int quads = 2 * TAIL_SEGMENTS * (ACROSS.length - 1) + COMA_RINGS * COMA_SEGMENTS;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(quads * 4 * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, format);
            // Queue de poussières : large, courbée, dorée.
            tail(builder, px, py, pz, tx, ty, tz, bx, by, bz, length, 0.45F, 0.16F, 255, 236, 200, 1.0F);
            // Queue d'ions : fine, droite, bleue, un peu plus longue.
            tail(builder, px, py, pz, tx, ty, tz, bx, by, bz, length * 1.25F, 0.0F, 0.045F, 130, 185, 255, 0.8F);
            coma(builder, px, py, pz, tx, ty, tz, bx, by, bz);
            if (glowBuffer == null) {
                glowBuffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            }
            glowBuffer.bind();
            glowBuffer.upload(builder.buildOrThrow());
            VertexBuffer.unbind();
        }
    }

    /**
     * Une queue : une bande le long d'un arc qui part de la tête dans la direction t et se courbe vers b.
     *
     * @param curve    courbure totale (radians) vers b sur toute la longueur
     * @param maxWidth demi-largeur au bout de la queue (radians)
     */
    private static void tail(BufferBuilder builder, float px, float py, float pz, float tx, float ty, float tz,
                             float bx, float by, float bz, float length, float curve, float maxWidth,
                             int r, int g, int b, float strength) {
        for (int i = 0; i < TAIL_SEGMENTS; i++) {
            for (int j = 0; j < ACROSS.length - 1; j++) {
                tailVertex(builder, px, py, pz, tx, ty, tz, bx, by, bz, length, curve, maxWidth, i, ACROSS[j], r, g, b, strength);
                tailVertex(builder, px, py, pz, tx, ty, tz, bx, by, bz, length, curve, maxWidth, i + 1, ACROSS[j], r, g, b, strength);
                tailVertex(builder, px, py, pz, tx, ty, tz, bx, by, bz, length, curve, maxWidth, i + 1, ACROSS[j + 1], r, g, b, strength);
                tailVertex(builder, px, py, pz, tx, ty, tz, bx, by, bz, length, curve, maxWidth, i, ACROSS[j + 1], r, g, b, strength);
            }
        }
    }

    private static void tailVertex(BufferBuilder builder, float px, float py, float pz, float tx, float ty, float tz,
                                   float bx, float by, float bz, float length, float curve, float maxWidth,
                                   int row, float across, int r, int g, int b, float strength) {
        float f = row / (float) TAIL_SEGMENTS;
        // Direction locale de la queue, qui tourne peu à peu vers b (courbure), et position le long de l'arc.
        double c = curve * f * f;
        double dx = tx * Math.cos(c) + bx * Math.sin(c);
        double dy = ty * Math.cos(c) + by * Math.sin(c);
        double dz = tz * Math.cos(c) + bz * Math.sin(c);
        double a = length * f;
        double ox = px * Math.cos(a) + dx * Math.sin(a);
        double oy = py * Math.cos(a) + dy * Math.sin(a);
        double oz = pz * Math.cos(a) + dz * Math.sin(a);
        // Largeur : étroite près de la tête, s'évase vers le bout ; côté = perpendiculaire à la direction.
        double sx = oy * dz - oz * dy;
        double sy = oz * dx - ox * dz;
        double sz = ox * dy - oy * dx;
        double w = (0.012 + maxWidth * Math.sqrt(f)) * across;
        double x = ox + sx * w;
        double y = oy + sy * w;
        double z = oz + sz * w;
        double l = Math.sqrt(x * x + y * y + z * z);

        // Lumière : plus forte près de la tête, nulle au bout et sur les bords (profil doux).
        double edge = 1.0 - across * across;
        double light = strength * edge * edge * Math.pow(1.0 - f, 1.1) * (0.55 + 0.45 * Math.exp(-f * 4.0));
        int alpha = (int) Math.round(Math.min(1.0, light) * 255.0);
        builder.addVertex((float) (x / l * StarField.RADIUS), (float) (y / l * StarField.RADIUS), (float) (z / l * StarField.RADIUS))
                .setColor(r, g, b, alpha);
    }

    /** Chevelure : petits anneaux concentriques autour de la tête, d'un vert pâle. */
    private static void coma(BufferBuilder builder, float px, float py, float pz, float tx, float ty, float tz,
                             float bx, float by, float bz) {
        for (int ring = 0; ring < COMA_RINGS; ring++) {
            for (int s = 0; s < COMA_SEGMENTS; s++) {
                comaVertex(builder, px, py, pz, tx, ty, tz, bx, by, bz, ring, s);
                comaVertex(builder, px, py, pz, tx, ty, tz, bx, by, bz, ring, s + 1);
                comaVertex(builder, px, py, pz, tx, ty, tz, bx, by, bz, ring + 1, s + 1);
                comaVertex(builder, px, py, pz, tx, ty, tz, bx, by, bz, ring + 1, s);
            }
        }
    }

    private static void comaVertex(BufferBuilder builder, float px, float py, float pz, float tx, float ty, float tz,
                                   float bx, float by, float bz, int ring, int segment) {
        double radiusDeg = 2.2 * ring / COMA_RINGS;
        double r = Math.toRadians(radiusDeg);
        double phi = segment * Math.PI * 2.0 / COMA_SEGMENTS;
        double x = px * Math.cos(r) + (tx * Math.cos(phi) + bx * Math.sin(phi)) * Math.sin(r);
        double y = py * Math.cos(r) + (ty * Math.cos(phi) + by * Math.sin(phi)) * Math.sin(r);
        double z = pz * Math.cos(r) + (tz * Math.cos(phi) + bz * Math.sin(phi)) * Math.sin(r);
        double density = ring == COMA_RINGS ? 0.0 : Math.exp(-radiusDeg / 0.6);
        int alpha = (int) Math.round(Math.min(1.0, density) * 255.0);
        builder.addVertex((float) (x * StarField.RADIUS), (float) (y * StarField.RADIUS), (float) (z * StarField.RADIUS))
                .setColor(180, 255, 215, alpha);
    }

    private void buildNucleus(float px, float py, float pz) {
        VertexFormat format = DefaultVertexFormat.POSITION_TEX_COLOR;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(4 * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, format);
            float i = 0.55F + 0.45F * envelope;
            StarField.sprite(builder, px, py, pz, 0.9F + 0.4F * envelope, 0.0F, 0.85F * i, 1.0F * i, 0.92F * i, 0.5F);
            if (nucleusBuffer == null) {
                nucleusBuffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            }
            nucleusBuffer.bind();
            nucleusBuffer.upload(builder.buildOrThrow());
            VertexBuffer.unbind();
        }
    }

    // -------------------------------------------------------------------------------------------------- outils

    private static CatalogStar entry(long cycle, float x, float y, float z, float envelope) {
        int h = StarCatalog.mix((int) cycle * 0x7FEB352D + 0x165667B1);
        float magnitude = Math.round((4.5F - 3.5F * envelope) * 10.0F) / 10.0F;
        float distanceAu = Math.round((0.4F + 1.4F * unit(h >>> 20)) * 10.0F) / 10.0F;
        int temperature = 150 + Integer.remainderUnsigned(h >>> 4, 150);
        return new CatalogStar(x, y, z, nameOf(cycle), true, StarCatalog.constellationAt(x, y, z), ' ', 0,
                magnitude, 0, temperature, 0xB4FFD7, null, "comet", distanceAu);
    }

    /** Décalage (en nuits) du début de l'apparition dans son cycle, pour qu'elles ne soient pas régulières. */
    private static double startOffset(long cycle) {
        int h = StarCatalog.mix((int) cycle * 0x2C1B3C6D + 0x6A09E667);
        return 2.0 + unit(h) * (CYCLE_NIGHTS - VISIBLE_NIGHTS - 3);
    }

    private static float unit(int h) {
        return (h & 0xFF) / 255.0F;
    }
}
