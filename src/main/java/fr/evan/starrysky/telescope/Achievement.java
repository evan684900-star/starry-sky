package fr.evan.starrysky.telescope;

import fr.evan.starrysky.render.Planets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;

/**
 * Succès du carnet d'observation. Les libellés viennent des fichiers de langue :
 * {@code starrysky.achievement.<id>} (titre) et {@code starrysky.achievement.<id>.desc} (description).
 * <p>
 * Certains se débloquent avec le nombre d'astres observés, d'autres sur un événement du ciel (éclipse, comète,
 * pluie d'étoiles filantes, aurores). Ils sont enregistrés avec le carnet, un par monde.
 */
public enum Achievement {
    FIRST_LIGHT("first_light", 0xF2F4FA, 1, Kind.COUNT),
    AMATEUR("amateur", 0x9DB8FF, 25, Kind.COUNT),
    CONFIRMED("confirmed", 0x7FA8FF, 100, Kind.COUNT),
    PLANETS("planets", 0xFFC58A, 4, Kind.PLANETS),
    FIGURE("figure", 0xA9C4FF, 1, Kind.FIGURES),
    CARTOGRAPHER("cartographer", 0xC9B6FF, -1, Kind.FIGURES),
    COMET("comet", 0xB4FFD7, 1, Kind.EVENT),
    ECLIPSE("eclipse", 0xFF7A5C, 1, Kind.EVENT),
    SHOWER("shower", 0xBFE3FF, 1, Kind.EVENT),
    AURORA("aurora", 0x7DFFB8, 1, Kind.EVENT),
    MASTER("master", 0xE8C77A, -1, Kind.COUNT);

    /** Comment se mesure la progression. */
    private enum Kind {
        /** Nombre d'astres observés (étoiles et planètes). */
        COUNT,
        /** Nombre de planètes observées. */
        PLANETS,
        /** Nombre de constellations dont toutes les étoiles ont été observées. */
        FIGURES,
        /** Événement du ciel : débloqué directement. */
        EVENT
    }

    private final String id;
    private final int color;
    /** Objectif ; -1 = « tous » (calculé d'après le catalogue). */
    private final int goal;
    private final Kind kind;

    Achievement(String id, int color, int goal, Kind kind) {
        this.id = id;
        this.color = color;
        this.goal = goal;
        this.kind = kind;
    }

    public String id() {
        return id;
    }

    public int color() {
        return color;
    }

    public Component title() {
        return Component.translatable("starrysky.achievement." + id);
    }

    public Component description() {
        return Component.translatable("starrysky.achievement." + id + ".desc");
    }

    /** Vrai pour les succès débloqués par un événement du ciel (pas de barre de progression). */
    public boolean isEvent() {
        return kind == Kind.EVENT;
    }

    /** Objectif à atteindre (au moins 1). */
    public int goal() {
        if (goal > 0) {
            return goal;
        }
        return Math.max(1, kind == Kind.FIGURES ? figureSizes().size() : Planets.catalog().size() + StarCatalog.stars().size());
    }

    /** Progression actuelle, d'après le carnet (0 pour un événement). */
    public int progress() {
        return switch (kind) {
            case COUNT -> ObservationJournal.countDiscovered(Planets.catalog())
                    + ObservationJournal.countDiscovered(StarCatalog.stars());
            case PLANETS -> ObservationJournal.countDiscovered(Planets.catalog());
            case FIGURES -> completedFigures();
            case EVENT -> 0;
        };
    }

    /** Succès débloqué par un événement du ciel, d'après son identifiant. */
    public static Achievement byId(String id) {
        for (Achievement achievement : values()) {
            if (achievement.id.equals(id)) {
                return achievement;
            }
        }
        return null;
    }

    /** Nombre d'étoiles de chaque constellation dessinée. */
    private static Map<String, Integer> figureSizes() {
        Map<String, Integer> sizes = new HashMap<>();
        for (CatalogStar star : StarCatalog.stars()) {
            if (star.figure() != null) {
                sizes.merge(star.figure(), 1, Integer::sum);
            }
        }
        return sizes;
    }

    /** Nombre de constellations dont toutes les étoiles ont été observées. */
    private static int completedFigures() {
        Map<String, Integer> remaining = figureSizes();
        List<CatalogStar> stars = StarCatalog.stars();
        for (CatalogStar star : stars) {
            if (star.figure() != null && ObservationJournal.isDiscovered(star.name())) {
                remaining.merge(star.figure(), -1, Integer::sum);
            }
        }
        int done = 0;
        for (int left : remaining.values()) {
            if (left <= 0) {
                done++;
            }
        }
        return done;
    }
}
