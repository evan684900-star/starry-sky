package fr.evan.starrysky.telescope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Catalogue des étoiles brillantes du ciel de Starry Sky, rempli par {@link fr.evan.starrysky.render.StarField} à
 * chaque (re)construction du champ d'étoiles.
 * <p>
 * Il contient deux sortes d'étoiles :
 * <ul>
 *     <li>les étoiles des <b>vraies constellations</b> (Orion, Grande Ourse…), placées à la main avec leurs vraies
 *     caractéristiques ({@link fr.evan.starrysky.render.Constellations}) ;</li>
 *     <li>les étoiles brillantes tirées au hasard (graine fixe) : les plus lumineuses reçoivent le nom d'une vraie
 *     étoile, les autres une désignation de catalogue. Leur constellation dépend de la région du ciel.</li>
 * </ul>
 * Les planètes, qui bougent d'une nuit à l'autre, sont gérées à part ({@link fr.evan.starrysky.render.Planets}).
 */
public final class StarCatalog {
    /** Au-dessus de cette « magnitude interne » (0 à 1), une étoile entre dans le catalogue. Environ 20 % des étoiles. */
    public static final float CATALOG_THRESHOLD = 0.55F;
    /** Au-dessus de celle-ci (les étoiles à éclats, environ 3 %), elle reçoit un nom propre. */
    public static final float NAMED_THRESHOLD = 0.92F;

    /**
     * Noms de vraies étoiles, attribués aux étoiles les plus lumineuses tirées au hasard.
     * Les étoiles des constellations dessinées (Véga, Rigel, Dubhe…) n'y figurent pas : elles ont leur vraie place.
     */
    private static final String[] NAMES = {
            "Sirius", "Arcturus", "Capella", "Procyon", "Achernar", "Altair", "Aldebaran", "Antares", "Spica",
            "Pollux", "Fomalhaut", "Regulus", "Adhara", "Castor", "Shaula", "Elnath", "Miaplacidus", "Alnair",
            "Mirfak", "Wezen", "Sargas", "Kaus Australis", "Avior", "Menkalinan", "Atria", "Alhena", "Peacock",
            "Polaris", "Mirzam", "Alphard", "Hamal", "Algieba", "Diphda", "Nunki", "Menkent", "Mirach", "Alpheratz",
            "Rasalhague", "Kochab", "Denebola", "Algol", "Tiaki", "Muhlifain", "Aspidiske", "Suhail", "Alphecca",
            "Eltanin", "Naos", "Almach", "Izar", "Enif", "Scheat", "Markab", "Alderamin", "Rasalgethi",
            "Zubenelgenubi", "Unukalhai", "Sabik", "Vindemiatrix", "Acrux", "Mimosa", "Hadar", "Canopus", "Gacrux",
            "Ankaa", "Acamar", "Zosma", "Thuban", "Alcyone", "Algenib", "Arneb", "Nihal", "Phact", "Wazn", "Ascella",
            "Kornephoros", "Sheratan", "Menkar", "Zaurak", "Cursa", "Talitha", "Tarazed", "Sadalmelik", "Sadalsuud",
            "Skat", "Porrima"
    };

    /** Constellations des régions du ciel (noms latins). Celles qui sont dessinées en vrai n'y figurent pas. */
    private static final String[] CONSTELLATIONS = {
            "Andromeda", "Aquila", "Aries", "Auriga", "Bootes", "Canis Major", "Carina", "Centaurus", "Cepheus",
            "Corona Borealis", "Draco", "Gemini", "Hercules", "Leo", "Pegasus", "Perseus", "Sagittarius", "Scorpius",
            "Taurus", "Ursa Minor", "Virgo", "Libra", "Ophiuchus", "Crux", "Pavo", "Eridanus", "Hydra"
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

        /** Ajoute une étoile aux caractéristiques connues (étoile d'une constellation dessinée). */
        public void addFixed(CatalogStar star) {
            list.add(star);
        }

        /**
         * Propose une étoile tirée au hasard. Appelé pour <b>chaque</b> étoile générée, dans l'ordre, afin que la
         * numérotation reste stable.
         *
         * @param strength magnitude interne de l'étoile, de 0 (faible) à 1 (très brillante)
         * @param tintKind 0 = blanche, 1 = bleutée, 2 = jaune pâle
         * @param seed     valeur aléatoire propre à l'étoile (0 à 1)
         */
        public void offer(float x, float y, float z, float strength, int tintKind, float seed) {
            int id = index++;
            if (strength < CATALOG_THRESHOLD) {
                return;
            }
            int h = mix(id * 0x9E3779B9 + 0x51ED27);

            boolean named = strength >= NAMED_THRESHOLD && namedCount < NAMES.length;
            String name = named ? NAMES[namedCount++] : "HIP " + (10000 + Integer.remainderUnsigned(h, 110000));

            // Classe spectrale cohérente avec la couleur affichée.
            float pick = ((h >>> 8) & 0xFFFF) / 65536.0F;
            char spectral = switch (tintKind) {
                case 1 -> strength > 0.85F || pick < 0.35F ? 'B' : 'A';
                case 2 -> pick < 0.5F ? 'G' : 'K';
                default -> pick < 0.55F ? 'A' : 'F';
            };
            int subclass = (int) (seed * 10.0F) % 10;
            float hot = 1.0F - subclass / 9.0F;
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

            list.add(new CatalogStar(x, y, z, name, named, constellationAt(x, y, z), spectral, subclass,
                    magnitude, distance, temperature, colorOf(spectral), null, null, 0.0F));
        }

        /** Publie le catalogue construit (remplace l'ancien d'un coup). */
        public void publish() {
            stars = Collections.unmodifiableList(new ArrayList<>(list));
        }
    }

    /** Constellation de la région du ciel : la sphère est découpée en 6 bandes × 8 secteurs. */
    public static String constellationAt(float x, float y, float z) {
        double latitude = Math.toDegrees(Math.asin(Math.max(-1.0F, Math.min(1.0F, y))));
        double longitude = Math.toDegrees(Math.atan2(z, x)) + 180.0;
        int band = Math.min(5, (int) ((latitude + 90.0) / 30.0));
        int sector = Math.min(7, (int) (longitude / 45.0));
        int cell = band * 8 + sector;
        return CONSTELLATIONS[Integer.remainderUnsigned(mix(cell * 0x2545F491 + 7), CONSTELLATIONS.length)];
    }

    /** Couleur d'affichage associée à une classe spectrale. */
    public static int colorOf(char spectral) {
        return switch (spectral) {
            case 'B' -> 0x9DB8FF;
            case 'A' -> 0xCAD7FF;
            case 'F' -> 0xF6F3FF;
            case 'G' -> 0xFFEBC4;
            case 'K' -> 0xFFD2A1;
            default -> 0xFFB08A;
        };
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    /** Mélange de bits (hachage entier), utilisé pour tout ce qui doit être « aléatoire » mais reproductible. */
    public static int mix(int h) {
        h ^= h >>> 16;
        h *= 0x7FEB352D;
        h ^= h >>> 15;
        h *= 0x846CA68B;
        h ^= h >>> 16;
        return h;
    }
}
