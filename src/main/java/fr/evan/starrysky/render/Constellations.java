package fr.evan.starrysky.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import fr.evan.starrysky.telescope.CatalogStar;
import fr.evan.starrysky.telescope.StarCatalog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vraies constellations, reconnaissables à l'œil nu : Orion, la Grande Ourse, Cassiopée, le Cygne et la Lyre.
 * <p>
 * Chaque figure est décrite par ses étoiles (vrai nom, vraie classe spectrale, magnitude, distance, température) et
 * leurs positions relatives, en degrés, autour d'un centre placé dans le ciel du mod. Les étoiles sont ajoutées au
 * champ d'étoiles et au catalogue ; les traits qui les relient sont affichés à la longue-vue
 * ({@link fr.evan.starrysky.telescope.TelescopeHud}).
 * <p>
 * Toutes les figures sont à au moins 35° de la lune (qui, dans Minecraft, ne bouge pas par rapport aux étoiles) et
 * passent haut dans le ciel au cours de la nuit.
 */
public final class Constellations {
    /** Une étoile d'une figure : position relative (degrés) et caractéristiques réelles. */
    private record Def(String name, float dx, float dy, float magnitude, char spectral, int subclass,
                       int distanceLy, int temperatureK) {
    }

    /** Une figure : nom, centre dans le repère du ciel, étoiles, traits (indices d'étoiles). */
    private record Figure(String name, float cx, float cy, float cz, Def[] stars, int[][] lines) {
    }

    private static final Figure[] FIGURES = {
            new Figure("Orion", 0.45F, -0.75F, 0.45F, new Def[] {
                    new Def("Betelgeuse", -4.0F, 9.0F, 0.5F, 'M', 1, 548, 3600),
                    new Def("Bellatrix", 4.0F, 8.0F, 1.6F, 'B', 2, 250, 22000),
                    new Def("Alnitak", -1.5F, 0.0F, 1.8F, 'B', 0, 1260, 29000),
                    new Def("Alnilam", 0.0F, 0.3F, 1.7F, 'B', 0, 2000, 27000),
                    new Def("Mintaka", 1.5F, 0.7F, 2.2F, 'B', 0, 1200, 30000),
                    new Def("Saiph", -3.5F, -9.0F, 2.1F, 'B', 0, 650, 26000),
                    new Def("Rigel", 5.0F, -8.5F, 0.1F, 'B', 8, 860, 12100)},
                    new int[][] {{0, 1}, {0, 2}, {1, 4}, {2, 3}, {3, 4}, {2, 5}, {4, 6}, {5, 6}}),
            new Figure("Ursa Major", -0.6F, -0.7F, 0.1F, new Def[] {
                    new Def("Dubhe", 6.0F, 3.0F, 1.8F, 'K', 0, 123, 4660),
                    new Def("Merak", 6.0F, -2.0F, 2.4F, 'A', 1, 79, 9380),
                    new Def("Phecda", 0.0F, -3.0F, 2.4F, 'A', 0, 84, 9350),
                    new Def("Megrez", 0.0F, 1.5F, 3.3F, 'A', 3, 81, 9480),
                    new Def("Alioth", -5.0F, 2.0F, 1.8F, 'A', 1, 81, 9020),
                    new Def("Mizar", -9.0F, 3.5F, 2.2F, 'A', 2, 83, 9000),
                    new Def("Alkaid", -14.0F, 1.5F, 1.9F, 'B', 3, 104, 15500)},
                    new int[][] {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {3, 4}, {4, 5}, {5, 6}}),
            new Figure("Cassiopeia", -0.3F, -0.75F, -0.55F, new Def[] {
                    new Def("Caph", -8.0F, 2.0F, 2.3F, 'F', 2, 55, 6700),
                    new Def("Schedar", -4.0F, -2.0F, 2.2F, 'K', 0, 228, 4530),
                    new Def("Navi", 0.0F, 1.0F, 2.4F, 'B', 0, 610, 25000),
                    new Def("Ruchbah", 4.0F, -1.5F, 2.7F, 'A', 5, 99, 8400),
                    new Def("Segin", 8.0F, 1.5F, 3.4F, 'B', 3, 410, 15000)},
                    new int[][] {{0, 1}, {1, 2}, {2, 3}, {3, 4}}),
            new Figure("Cygnus", 0.35F, -0.7F, -0.6F, new Def[] {
                    new Def("Deneb", 0.0F, 9.0F, 1.3F, 'A', 2, 2600, 8500),
                    new Def("Sadr", 0.0F, 2.0F, 2.2F, 'F', 8, 1800, 5790),
                    new Def("Aljanah", -6.0F, 1.0F, 2.5F, 'K', 0, 72, 4700),
                    new Def("Fawaris", 6.0F, 3.0F, 2.9F, 'B', 9, 165, 10000),
                    new Def("Albireo", 0.0F, -9.0F, 3.1F, 'K', 3, 430, 4400)},
                    new int[][] {{0, 1}, {1, 4}, {2, 1}, {1, 3}}),
            new Figure("Lyra", 0.6F, -0.75F, -0.05F, new Def[] {
                    new Def("Vega", 0.0F, 3.0F, 0.0F, 'A', 0, 25, 9600),
                    new Def("Sheliak", -1.0F, -3.0F, 3.5F, 'B', 7, 960, 13300),
                    new Def("Sulafat", 2.0F, -3.5F, 3.3F, 'B', 9, 620, 10000)},
                    new int[][] {{0, 1}, {1, 2}, {2, 0}}),
    };

    /** Directions (repère du ciel) des étoiles de chaque figure, calculées une fois. */
    private static final Map<String, float[][]> DIRECTIONS = new HashMap<>();
    /** Traits de chaque figure : paires de directions {x0, y0, z0, x1, y1, z1}. */
    private static final Map<String, List<float[]>> SEGMENTS = new HashMap<>();

    static {
        for (Figure figure : FIGURES) {
            float[][] dirs = new float[figure.stars().length][];
            for (int i = 0; i < dirs.length; i++) {
                dirs[i] = direction(figure, figure.stars()[i]);
            }
            DIRECTIONS.put(figure.name(), dirs);
            List<float[]> segments = new ArrayList<>();
            for (int[] line : figure.lines()) {
                float[] a = dirs[line[0]];
                float[] b = dirs[line[1]];
                segments.add(new float[] {a[0], a[1], a[2], b[0], b[1], b[2]});
            }
            SEGMENTS.put(figure.name(), Collections.unmodifiableList(segments));
        }
    }

    private Constellations() {
    }

    /** Traits de la figure (vide si ce n'est pas une constellation dessinée). */
    public static List<float[]> segments(String figure) {
        return SEGMENTS.getOrDefault(figure, List.of());
    }

    /** Ajoute les étoiles de toutes les figures au maillage et au catalogue. */
    static void addTo(BufferBuilder builder, StarCatalog.Builder catalog, boolean colored) {
        for (Figure figure : FIGURES) {
            float[][] dirs = DIRECTIONS.get(figure.name());
            for (int i = 0; i < figure.stars().length; i++) {
                Def star = figure.stars()[i];
                float[] d = dirs[i];

                // Magnitude réelle -> éclat et taille dans le ciel du mod (les plus brillantes ont des éclats).
                float strength = clamp((4.2F - star.magnitude()) / 4.5F, 0.35F, 1.0F);
                float size = 0.30F + 0.55F * strength;
                float intensity = 0.30F + 0.70F * strength;
                float glint = star.magnitude() < 0.6F ? 0.45F : 0.0F;
                int rgb = colored ? tint(star.spectral()) : 0xFFFFFF;
                float seed = (Integer.remainderUnsigned(StarCatalog.mix(star.name().hashCode()), 1000)) / 1000.0F;
                StarField.sprite(builder, d[0], d[1], d[2], size, glint,
                        ((rgb >> 16) & 0xFF) / 255.0F * intensity,
                        ((rgb >> 8) & 0xFF) / 255.0F * intensity,
                        (rgb & 0xFF) / 255.0F * intensity, seed);

                catalog.addFixed(new CatalogStar(d[0], d[1], d[2], star.name(), true, figure.name(),
                        star.spectral(), star.subclass(), star.magnitude(), star.distanceLy(), star.temperatureK(),
                        StarCatalog.colorOf(star.spectral()), figure.name(), null, 0.0F));
            }
        }
    }

    /** Couleur rendue dans le ciel selon la classe spectrale (plus discrète que celle de l'interface). */
    private static int tint(char spectral) {
        return switch (spectral) {
            case 'B' -> 0xB8CCFF;
            case 'A' -> 0xE4EBFF;
            case 'F' -> 0xFFFFFF;
            case 'G' -> 0xFFF2D6;
            case 'K' -> 0xFFDDB3;
            default -> 0xFFB892;
        };
    }

    /** Direction d'une étoile : centre de la figure décalé de (dx, dy) degrés dans le plan tangent. */
    private static float[] direction(Figure figure, Def star) {
        double cl = Math.sqrt(figure.cx() * figure.cx() + figure.cy() * figure.cy() + figure.cz() * figure.cz());
        double cx = figure.cx() / cl;
        double cy = figure.cy() / cl;
        double cz = figure.cz() / cl;
        // Axe « droite » : perpendiculaire au centre et à l'axe Y du ciel ; axe « haut » : complète le repère.
        double rx = cz;
        double ry = 0.0;
        double rz = -cx;
        double rl = Math.sqrt(rx * rx + rz * rz);
        rx /= rl;
        rz /= rl;
        double ux = cy * rz - cz * ry;
        double uy = cz * rx - cx * rz;
        double uz = cx * ry - cy * rx;

        double a = Math.toRadians(star.dx());
        double b = Math.toRadians(star.dy());
        double x = cx + Math.tan(a) * rx + Math.tan(b) * ux;
        double y = cy + Math.tan(a) * ry + Math.tan(b) * uy;
        double z = cz + Math.tan(a) * rz + Math.tan(b) * uz;
        double l = Math.sqrt(x * x + y * y + z * z);
        return new float[] {(float) (x / l), (float) (y / l), (float) (z / l)};
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
