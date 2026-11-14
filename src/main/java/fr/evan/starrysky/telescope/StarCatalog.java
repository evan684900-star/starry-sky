package fr.evan.starrysky.telescope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Catalogue des étoiles brillantes du ciel de Starry Sky, rempli par {@link fr.evan.starrysky.render.StarField} à
 * chaque (re)construction du champ d'étoiles.
 * <p>
 * Le ciel du mod est imaginaire : les étoiles sont placées au hasard (avec une graine fixe). Les plus brillantes
 * reçoivent le nom d'une vraie étoile, dans l'ordre de génération, et les autres une désignation de catalogue.
 * La constellation dépend de la région du ciel, donc des étoiles voisines appartiennent à la même constellation.
 */
public final class StarCatalog {
    /** Au-dessus de cette « magnitude interne » (0 à 1), une étoile entre dans le catalogue. Environ 20 % des étoiles. */
    public static final float CATALOG_THRESHOLD = 0.55F;
    /** Au-dessus de celle-ci (les étoiles à éclats, environ 3 %), elle reçoit un nom propre. */
    public static final float NAMED_THRESHOLD = 0.92F;

    /** Noms de vraies étoiles brillantes, attribués aux étoiles les plus lumineuses du ciel du mod. */
    private static final String[] NAMES = {
            "Sirius", "Vega", "Rigel", "Arcturus", "Capella", "Procyon", "Achernar", "Betelgeuse", "Altair",
            "Aldebaran", "Antares", "Spica", "Pollux", "Fomalhaut", "Deneb", "Regulus", "Adhara", "Castor",
            "Shaula", "Bellatrix", "Elnath", "Miaplacidus", "Alnilam", "Alnair", "Alnitak", "Alioth", "Dubhe",
            "Mirfak", "Wezen", "Sargas", "Kaus Australis", "Avior", "Alkaid", "Menkalinan", "Atria", "Alhena",
            "Peacock", "Polaris", "Mirzam", "Alphard", "Hamal", "Algieba", "Diphda", "Nunki", "Menkent", "Mirach",
            "Alpheratz", "Rasalhague", "Kochab", "Saiph", "Denebola", "Algol", "Tiaki", "Muhlifain", "Aspidiske",
            "Suhail", "Alphecca", "Mintaka", "Sadr", "Eltanin", "Schedar", "Naos", "Almach", "Caph", "Izar",
            "Enif", "Scheat", "Markab", "Mizar", "Alderamin", "Rasalgethi", "Zubenelgenubi", "Unukalhai",
            "Sabik", "Albireo", "Vindemiatrix", "Gienah", "Acrux", "Mimosa", "Hadar", "Canopus", "Gacrux"
    };

    /** Constellations (noms latins, identiques dans toutes les langues). */
    private static final String[] CONSTELLATIONS = {
            "Andromeda", "Aquila", "Aries", "Auriga", "Bootes", "Canis Major", "Carina", "Cassiopeia",
            "Centaurus", "Cepheus", "Corona Borealis", "Cygnus", "Draco", "Gemini", "Hercules", "Leo", "Lyra",
            "Orion", "Pegasus", "Perseus", "Sagittarius", "Scorpius", "Taurus", "Ursa Major", "Ursa Minor",
            "Virgo", "Libra", "Ophiuchus", "Crux", "Pavo", "Eridanus", "Hydra"
    };

    private static volatile List<CatalogStar> stars = List.of();

    private StarCatalog() {
    }

    /** Les étoiles du catalogue (liste non modifiable, remplacée en bloc à chaque reconstruction). */
    public static List<CatalogStar> stars() {
        return stars;
    }

    /** Constructeur du catalogue, utilisé pendant la génération du champ d'étoiles. */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<CatalogStar> list = new ArrayList<>();
        private int namedCount;
        private int index;

        private Builder() {
        }

        /**
         * Propose une étoile au catalogue. Appelé pour <b>chaque</b> étoile générée, dans l'ordre, afin que la
         * numérotation reste stable.
         *
         * @param strength  magnitude interne de l'étoile, de 0 (faible) à 1 (très brillante)
         * @param tintKind  0 = blanche, 1 = bleutée, 2 = jaune pâle
         * @param seed      valeur aléatoire propre à l'étoile (0 à 1)
         */
        public void offer(float x, float y, float z, float strength, int tintKind, float seed) {
            int id = index++;
            if (strength < CATALOG_THRESHOLD) {
                return;
            }
            int h = mix(id * 0x9E3779B9 + 0x51ED27);

            String name;
            boolean named = strength >= NAMED_THRESHOLD && namedCount < NAMES.length;
            if (named) {
                name = NAMES[namedCount++];
            } else {
                name = "HIP " + (10000 + Integer.remainderUnsigned(h, 110000));
            }

            // Classe spectrale cohérente avec la couleur affichée.
            float pick = ((h >>> 8) & 0xFFFF) / 65536.0F;
            char spectral;
            float hot; // 0 = bas de la fourchette de température de la classe, 1 = haut
            switch (tintKind) {
                case 1 -> spectral = strength > 0.85F || pick < 0.35F ? 'B' : 'A';
                case 2 -> spectral = pick < 0.5F ? 'G' : 'K';
                default -> spectral = pick < 0.55F ? 'A' : 'F';
            }
            int subclass = (int) (seed * 10.0F) % 10;
            hot = 1.0F - subclass / 9.0F;
            int temperature = switch (spectral) {
                case 'B' -> lerp(10500, 28000, hot);
                case 'A' -> lerp(7500, 10000, hot);
                case 'F' -> lerp(6000, 7400, hot);
                case 'G' -> lerp(5200, 5950, hot);
                default -> lerp(3900, 5150, hot);
            };
            temperature = Math.round(temperature / 50.0F) * 50;

            // Magnitude apparente : de -1,5 (très brillante) à environ 2,4.
            float magnitude = Math.round((5.5F - 7.0F * strength) * 10.0F) / 10.0F;
            float d = ((h >>> 4) & 0xFFFF) / 65536.0F;
            int distance = Math.max(4, Math.round(4.0F + d * d * 1400.0F * (1.2F - strength)));

            list.add(new CatalogStar(x, y, z, name, named, constellation(x, y, z), spectral, subclass,
                    magnitude, distance, temperature, color(spectral)));
        }

        /** Publie le catalogue construit (remplace l'ancien d'un coup). */
        public void publish() {
            stars = Collections.unmodifiableList(new ArrayList<>(list));
        }
    }

    /** Constellation de la région du ciel : la sphère est découpée en 6 bandes × 8 secteurs. */
    private static String constellation(float x, float y, float z) {
        double latitude = Math.toDegrees(Math.asin(Math.max(-1.0F, Math.min(1.0F, y))));
        double longitude = Math.toDegrees(Math.atan2(z, x)) + 180.0;
        int band = Math.min(5, (int) ((latitude + 90.0) / 30.0));
        int sector = Math.min(7, (int) (longitude / 45.0));
        int cell = band * 8 + sector;
        return CONSTELLATIONS[Integer.remainderUnsigned(mix(cell * 0x2545F491 + 7), CONSTELLATIONS.length)];
    }

    /** Couleur d'affichage associée à une classe spectrale. */
    private static int color(char spectral) {
        return switch (spectral) {
            case 'B' -> 0x9DB8FF;
            case 'A' -> 0xCAD7FF;
            case 'F' -> 0xF6F3FF;
            case 'G' -> 0xFFEBC4;
            default -> 0xFFD2A1;
        };
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    private static int mix(int h) {
        h ^= h >>> 16;
        h *= 0x7FEB352D;
        h ^= h >>> 15;
        h *= 0x846CA68B;
        h ^= h >>> 16;
        return h;
    }
}
