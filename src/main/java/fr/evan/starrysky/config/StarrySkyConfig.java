package fr.evan.starrysky.config;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration client de Starry Sky ({@code config/starrysky-client.toml}).
 * <p>
 * Les libellés et les infobulles affichés dans l'écran de configuration viennent des fichiers de langue
 * ({@code starrysky.configuration.<option>} et {@code starrysky.configuration.<option>.tooltip}).
 * <p>
 * Toutes les valeurs sont relues à chaque image (c'est peu coûteux : NeoForge les met en cache),
 * donc un changement depuis l'écran de configuration s'applique immédiatement, sans redémarrage.
 */
public final class StarrySkyConfig {
    private static final String PREFIX = "starrysky.configuration.";

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue STAR_COUNT;
    public static final ModConfigSpec.DoubleValue STAR_SIZE;
    public static final ModConfigSpec.DoubleValue BRIGHTNESS;
    public static final ModConfigSpec.BooleanValue COLORED_STARS;
    public static final ModConfigSpec.BooleanValue TWINKLE;
    public static final ModConfigSpec.DoubleValue TWINKLE_SPEED;
    public static final ModConfigSpec.BooleanValue SHOOTING_STARS;
    public static final ModConfigSpec.IntValue SHOOTING_STAR_FREQUENCY;
    public static final ModConfigSpec.BooleanValue MILKY_WAY;
    public static final ModConfigSpec.BooleanValue TELESCOPE;
    public static final ModConfigSpec.BooleanValue AURORAS;
    public static final ModConfigSpec.BooleanValue HIDE_VANILLA_STARS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSIONS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        ENABLED = builder
                .comment("Active ou désactive complètement le mod.")
                .translation(PREFIX + "enabled")
                .define("enabled", true);

        STAR_COUNT = builder
                .comment("Nombre d'étoiles générées sur la sphère céleste.")
                .translation(PREFIX + "starCount")
                .defineInRange("starCount", 1500, 100, 10000);

        STAR_SIZE = builder
                .comment("Multiplicateur de taille des étoiles.")
                .translation(PREFIX + "starSize")
                .defineInRange("starSize", 1.0, 0.25, 3.0);

        BRIGHTNESS = builder
                .comment("Luminosité globale des étoiles.")
                .translation(PREFIX + "brightness")
                .defineInRange("brightness", 1.0, 0.1, 2.0);

        COLORED_STARS = builder
                .comment("Légères variations de couleur (blanc, bleuté, jaune pâle).")
                .translation(PREFIX + "coloredStars")
                .define("coloredStars", true);

        TWINKLE = builder
                .comment("Scintillement léger des étoiles.")
                .translation(PREFIX + "twinkle")
                .define("twinkle", true);

        TWINKLE_SPEED = builder
                .comment("Vitesse du scintillement.")
                .translation(PREFIX + "twinkleSpeed")
                .defineInRange("twinkleSpeed", 1.0, 0.1, 5.0);

        SHOOTING_STARS = builder
                .comment("Étoiles filantes occasionnelles.")
                .translation(PREFIX + "shootingStars")
                .define("shootingStars", true);

        SHOOTING_STAR_FREQUENCY = builder
                .comment("Nombre moyen d'étoiles filantes par minute (en pleine nuit). 0 = aucune.")
                .translation(PREFIX + "shootingStarFrequency")
                .defineInRange("shootingStarFrequency", 5, 0, 20);

        MILKY_WAY = builder
                .comment("Voie lactée : une bande lumineuse traverse le ciel (lueur diffuse + poussières d'étoiles).",
                        "C'est l'effet le plus coûteux du mod, même s'il reste léger : à désactiver en premier",
                        "si le jeu ralentit quand on regarde le ciel la nuit.")
                .translation(PREFIX + "milkyWay")
                .define("milkyWay", true);

        AURORAS = builder
                .comment("Aurores boréales vers le nord, la nuit, uniquement dans les biomes froids (neige, glace).")
                .translation(PREFIX + "auroras")
                .define("auroras", true);

        TELESCOPE = builder
                .comment("Longue-vue : en visant une étoile brillante la nuit, affiche son nom et ses caractéristiques.")
                .translation(PREFIX + "telescope")
                .define("telescope", true);

        HIDE_VANILLA_STARS = builder
                .comment("Cache les étoiles de base de Minecraft là où Starry Sky est actif.")
                .translation(PREFIX + "hideVanillaStars")
                .define("hideVanillaStars", true);

        DIMENSIONS = builder
                .comment("Dimensions où le mod est actif (identifiants, ex. minecraft:overworld).",
                        "Seules les dimensions avec un ciel « normal » sont prises en compte : le Nether et l'End restent inchangés.")
                .translation(PREFIX + "dimensions")
                .defineListAllowEmpty("dimensions", List.of("minecraft:overworld"),
                        () -> "minecraft:overworld",
                        StarrySkyConfig::isValidDimensionId);

        SPEC = builder.build();
    }

    private StarrySkyConfig() {
    }

    private static boolean isValidDimensionId(Object value) {
        return value instanceof String s && ResourceLocation.tryParse(s) != null;
    }
}
