package fr.evan.starrysky.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * La Voie lactée : une bande lumineuse qui traverse le ciel en diagonale, plus large et plus claire vers son centre,
 * avec des nuages, une traînée sombre irrégulière et des teintes bleues, violettes et dorées.
 * <p>
 * Elle est faite de deux choses :
 * <ul>
 *     <li>une <b>lueur diffuse</b>, dessinée par cette classe ;</li>
 *     <li>des milliers de <b>poussières d'étoiles</b>, ajoutées au champ d'étoiles par {@link StarField}, qui
 *     utilise {@link #structure} pour les placer aux mêmes endroits que la lueur.</li>
 * </ul>
 * <b>Performances.</b> Tout le calcul (bruit, nuages, couleurs) est fait <b>une seule fois</b>, sur le processeur, et
 * stocké dans la couleur des sommets d'un maillage statique enroulé autour de la bande. À chaque image, la carte
 * graphique ne fait qu'interpoler ces couleurs : aucun bruit ni aucune texture ne sont calculés par pixel.
 * Le maillage ne couvre que la zone où la lueur est visible, pour toucher le moins de pixels possible.
 */
public final class MilkyWay {
    /** Intensité de la lueur au cœur de la Voie lactée (0,30 = 30 % de blanc ajouté au ciel, au maximum). */
    public static final float GLOW_PEAK = 0.30F;

    private static final int LONGITUDE_STEPS = 192;
    private static final int LATITUDE_STEPS = 36;

    // Repère « galactique », exprimé dans le repère du ciel (celui qui tourne avec les étoiles).
    // NORMAL = axe perpendiculaire à la bande ; CENTRE = direction du cœur, le plus lumineux, placé de façon à
    // passer haut dans le ciel vers minuit ; TANGENT complète le repère.
    private static final Vector3f NORMAL = new Vector3f(0.5F, 0.57F, 0.65F).normalize();
    private static final Vector3f CENTRE = makeCentre();
    private static final Vector3f TANGENT = new Vector3f(NORMAL).cross(CENTRE);

    // Couleurs de la lueur.
    private static final float[] COOL = {0.45F, 0.60F, 1.00F};   // bleu, sur les bords
    private static final float[] VIOLET = {0.72F, 0.52F, 1.00F}; // nuances violettes par endroits
    private static final float[] WARM = {1.00F, 0.88F, 0.74F};   // doré, vers le cœur

    @Nullable
    private VertexBuffer buffer;

    private static Vector3f makeCentre() {
        Vector3f centre = new Vector3f(0.347F, -0.822F, 0.451F);
        // On retire la composante le long de NORMAL pour que CENTRE soit exactement dans le plan de la bande.
        return centre.sub(new Vector3f(NORMAL).mul(centre.dot(NORMAL))).normalize();
    }

    /** Construit le maillage de la lueur au premier appel (les suivants ne font rien : il ne change jamais). */
    public void buildIfNeeded() {
        if (buffer != null) {
            return;
        }
        // 1) Calcul de la grille de sommets (position + couleur), une seule fois par sommet.
        int columns = LONGITUDE_STEPS;
        int rows = LATITUDE_STEPS + 1;
        float[] positions = new float[columns * rows * 3];
        int[] colors = new int[columns * rows * 4];
        double[] dir = new double[3];
        for (int i = 0; i < columns; i++) {
            double longitude = i / (double) LONGITUDE_STEPS * Math.PI * 2.0;
            double extent = extent(longitude);
            for (int j = 0; j < rows; j++) {
                double latitude = -extent + j / (double) LATITUDE_STEPS * 2.0 * extent;
                direction(longitude, latitude, dir);

                // La lueur s'annule exactement sur les bords du maillage : aucune coupure visible.
                double fade = 1.0 - smoothstep(0.75 * extent, extent, Math.abs(latitude));
                double glow = j == 0 || j == LATITUDE_STEPS ? 0.0 : Math.min(1.0, glow(longitude, latitude, dir) * fade);

                int v = i * rows + j;
                positions[v * 3] = (float) (dir[0] * StarField.RADIUS);
                positions[v * 3 + 1] = (float) (dir[1] * StarField.RADIUS);
                positions[v * 3 + 2] = (float) (dir[2] * StarField.RADIUS);
                tint(longitude, latitude, dir, colors, v * 4);
                colors[v * 4 + 3] = (int) (glow * 255.0);
            }
        }

        // 2) Assemblage des quads et envoi à la carte graphique.
        VertexFormat format = DefaultVertexFormat.POSITION_COLOR;
        int quads = LONGITUDE_STEPS * LATITUDE_STEPS;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(quads * 4 * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, format);
            for (int i = 0; i < LONGITUDE_STEPS; i++) {
                int next = (i + 1) % LONGITUDE_STEPS;
                for (int j = 0; j < LATITUDE_STEPS; j++) {
                    vertex(builder, positions, colors, i * rows + j);
                    vertex(builder, positions, colors, next * rows + j);
                    vertex(builder, positions, colors, next * rows + j + 1);
                    vertex(builder, positions, colors, i * rows + j + 1);
                }
            }
            buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(builder.buildOrThrow());
            VertexBuffer.unbind();
        }
    }

    /** Dessine la lueur. L'intensité est transmise par la couleur globale du shader (ColorModulator). */
    public void draw(Matrix4f modelView, Matrix4f projection, ShaderInstance shader) {
        if (buffer == null) {
            return;
        }
        buffer.bind();
        buffer.drawWithShader(modelView, projection, shader);
        VertexBuffer.unbind();
    }

    private static void vertex(BufferBuilder builder, float[] positions, int[] colors, int v) {
        builder.addVertex(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2])
                .setColor(colors[v * 4], colors[v * 4 + 1], colors[v * 4 + 2], colors[v * 4 + 3]);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Modèle de la Voie lactée. Tout ce qui suit ne sert qu'à la construction des maillages, jamais pendant le rendu.
    // Longitude : angle le long de la bande (0 = cœur). Latitude : écart par rapport au milieu de la bande.
    // ------------------------------------------------------------------------------------------------------------

    /** Direction (dans le repère du ciel) correspondant à une longitude et une latitude galactiques, en radians. */
    static void direction(double longitude, double latitude, double[] out) {
        double cosLat = Math.cos(latitude);
        double sinLat = Math.sin(latitude);
        double cosLon = Math.cos(longitude);
        double sinLon = Math.sin(longitude);
        out[0] = cosLat * (cosLon * CENTRE.x + sinLon * TANGENT.x) + sinLat * NORMAL.x;
        out[1] = cosLat * (cosLon * CENTRE.y + sinLon * TANGENT.y) + sinLat * NORMAL.y;
        out[2] = cosLat * (cosLon * CENTRE.z + sinLon * TANGENT.z) + sinLat * NORMAL.z;
    }

    /**
     * Structure de la bande, entre 0 et 1, sans l'atténuation en latitude : plus lumineuse vers le cœur,
     * avec des nuages et une traînée sombre. Sert aussi de densité pour placer les poussières d'étoiles.
     */
    static double structure(double longitude, double latitude, double[] dir) {
        double centre = centreness(longitude);

        // Nuages : grandes masses + détails plus fins.
        double big = fbm(dir[0] * 3.0, dir[1] * 3.0, dir[2] * 3.0);
        double fine = fbm(dir[0] * 7.5 + 41.0, dir[1] * 7.5 + 41.0, dir[2] * 7.5 + 41.0);
        double clouds = smoothstep(0.30, 0.70, big * 0.65 + fine * 0.35);

        // Traînée sombre : une ligne sinueuse et discontinue près du milieu de la bande.
        double laneCentre = Math.toRadians(2.0) * Math.sin(3.0 * longitude + 0.7)
                + (fbm(dir[0] * 2.2 + 17.0, dir[1] * 2.2 + 17.0, dir[2] * 2.2 + 17.0) - 0.5) * Math.toRadians(12.0);
        double laneWidth = Math.toRadians(2.2 + 1.8 * centre);
        double t = (latitude - laneCentre) / laneWidth;
        double lane = Math.exp(-t * t)
                * smoothstep(0.42, 0.68, fbm(dir[0] * 3.3 + 31.0, dir[1] * 3.3 + 31.0, dir[2] * 3.3 + 31.0));

        double along = 0.38 + 0.62 * centre;
        return along * (0.45 + 0.55 * clouds) * (1.0 - 0.60 * lane);
    }

    /** Intensité de la lueur, entre 0 et 1 : la structure, atténuée quand on s'éloigne du milieu de la bande. */
    private static double glow(double longitude, double latitude, double[] dir) {
        double t = latitude / sigma(longitude);
        return structure(longitude, latitude, dir) * Math.exp(-t * t);
    }

    /** Teinte de la lueur : bleu et violet sur les bords, doré vers le cœur. Écrit R, G, B (0..255) dans out. */
    private static void tint(double longitude, double latitude, double[] dir, int[] out, int offset) {
        double t = latitude / Math.toRadians(10.0);
        double warm = centreness(longitude) * Math.exp(-t * t) * 0.7;
        double violet = smoothstep(0.35, 0.70, fbm(dir[0] * 2.6 + 53.0, dir[1] * 2.6 + 53.0, dir[2] * 2.6 + 53.0)) * 0.8;
        for (int c = 0; c < 3; c++) {
            double cool = COOL[c] + (VIOLET[c] - COOL[c]) * violet;
            out[offset + c] = (int) ((cool + (WARM[c] - cool) * warm) * 255.0);
        }
    }

    /** 1 au cœur de la galaxie, 0 à l'opposé. */
    private static double centreness(double longitude) {
        double c = 0.5 + 0.5 * Math.cos(longitude);
        return c * c;
    }

    /** Demi-épaisseur caractéristique de la bande (plus épaisse vers le cœur), en radians. */
    private static double sigma(double longitude) {
        return Math.toRadians(8.5 + 6.5 * centreness(longitude));
    }

    /** Demi-hauteur du maillage à cette longitude : juste de quoi contenir la lueur visible. */
    private static double extent(double longitude) {
        return Math.min(Math.toRadians(40.0), 1.9 * sigma(longitude) + Math.toRadians(2.0));
    }

    static double smoothstep(double edge0, double edge1, double x) {
        double t = Math.min(1.0, Math.max(0.0, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }

    // ---- Bruit de valeur 3D déterministe (toujours le même ciel, quelle que soit la machine) ----

    private static double hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / 16777216.0;
    }

    private static double valueNoise(double x, double y, double z) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int iz = (int) Math.floor(z);
        double fx = x - ix;
        double fy = y - iy;
        double fz = z - iz;
        fx = fx * fx * (3.0 - 2.0 * fx);
        fy = fy * fy * (3.0 - 2.0 * fy);
        fz = fz * fz * (3.0 - 2.0 * fz);
        double x00 = lerp(hash(ix, iy, iz), hash(ix + 1, iy, iz), fx);
        double x10 = lerp(hash(ix, iy + 1, iz), hash(ix + 1, iy + 1, iz), fx);
        double x01 = lerp(hash(ix, iy, iz + 1), hash(ix + 1, iy, iz + 1), fx);
        double x11 = lerp(hash(ix, iy + 1, iz + 1), hash(ix + 1, iy + 1, iz + 1), fx);
        return lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz);
    }

    /** Bruit fractal (4 octaves), entre 0 et 1. */
    private static double fbm(double x, double y, double z) {
        double sum = 0.0;
        double amplitude = 0.5;
        for (int octave = 0; octave < 4; octave++) {
            sum += amplitude * valueNoise(x, y, z);
            x = x * 2.03 + 11.7;
            y = y * 2.03 + 5.3;
            z = z * 2.03 + 23.1;
            amplitude *= 0.5;
        }
        return sum / 0.9375;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
