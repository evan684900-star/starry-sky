package fr.evan.starrysky.telescope;

import fr.evan.starrysky.StarrySky;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.sounds.SoundEvents;

/**
 * Carnet d'observation : les astres déjà observés à la longue-vue, et les succès débloqués.
 * <p>
 * Un astre est « découvert » quand on le garde au centre de la longue-vue pendant {@link #OBSERVE_SECONDS} secondes.
 * Les découvertes sont enregistrées <b>sur ton ordinateur</b>, un fichier par monde solo ou par serveur, dans
 * {@code .minecraft/starrysky/journal/}. Rien n'est envoyé au serveur.
 */
public final class ObservationJournal {
    /** Temps d'observation nécessaire pour enregistrer une découverte. */
    public static final float OBSERVE_SECONDS = 0.8F;
    /** Durée d'affichage du badge « Nouvelle découverte ». */
    public static final long BADGE_MILLIS = 3500L;

    /** Préfixe des lignes du fichier qui enregistrent un succès (les autres lignes sont des astres). */
    private static final String ACHIEVEMENT_PREFIX = "@";

    private static final Set<String> discovered = new LinkedHashSet<>();
    private static final Set<String> achievements = new LinkedHashSet<>();
    @Nullable
    private static String loadedFor;
    @Nullable
    private static String lastDiscovery;
    private static long lastDiscoveryAt;

    private ObservationJournal() {
    }

    /** Vrai si l'astre a déjà été observé dans ce monde. */
    public static boolean isDiscovered(String name) {
        ensureLoaded();
        return discovered.contains(name);
    }

    /** Nombre d'astres observés parmi ceux de la liste. */
    public static int countDiscovered(List<CatalogStar> stars) {
        ensureLoaded();
        int count = 0;
        for (CatalogStar star : stars) {
            if (discovered.contains(star.name())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Enregistre une observation. Joue un petit carillon et retient l'astre pour le badge si c'est une découverte.
     *
     * @return vrai si l'astre n'avait jamais été observé
     */
    public static boolean observe(CatalogStar star) {
        ensureLoaded();
        if (loadedFor == null || !discovered.add(star.name())) {
            return false;
        }
        lastDiscovery = star.name();
        lastDiscoveryAt = System.currentTimeMillis();
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 0.6F));
        append(star.name());
        if (star.isComet()) {
            unlock(Achievement.COMET);
        }
        checkAchievements();
        return true;
    }

    /** Vrai si ce succès est débloqué dans ce monde. */
    public static boolean isUnlocked(Achievement achievement) {
        ensureLoaded();
        return achievements.contains(achievement.id());
    }

    /** Nombre de succès débloqués dans ce monde. */
    public static int unlockedCount() {
        ensureLoaded();
        int count = 0;
        for (Achievement achievement : Achievement.values()) {
            if (achievements.contains(achievement.id())) {
                count++;
            }
        }
        return count;
    }

    /** Débloque un succès (s'il ne l'est pas déjà) : notification, son, et enregistrement dans le carnet. */
    public static void unlock(Achievement achievement) {
        ensureLoaded();
        if (loadedFor == null || !achievements.add(achievement.id())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getToasts().addToast(new AchievementToast(achievement));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.4F, 0.5F));
        append(ACHIEVEMENT_PREFIX + achievement.id());
    }

    /** Débloque les succès liés au nombre d'astres observés dont l'objectif est atteint. */
    public static void checkAchievements() {
        ensureLoaded();
        // Tant que le ciel n'est pas construit (catalogue vide), les objectifs ne veulent rien dire.
        if (loadedFor == null || StarCatalog.stars().isEmpty()) {
            return;
        }
        for (Achievement achievement : Achievement.values()) {
            if (!achievement.isEvent() && !achievements.contains(achievement.id())
                    && achievement.progress() >= achievement.goal()) {
                unlock(achievement);
            }
        }
    }

    /** Noms des comètes observées dans ce monde (elles ne restent pas dans le ciel, donc pas dans le catalogue). */
    public static List<String> discoveredComets() {
        ensureLoaded();
        List<String> comets = new java.util.ArrayList<>();
        for (String name : discovered) {
            if (name.startsWith("C/")) {
                comets.add(name);
            }
        }
        return comets;
    }

    private static void append(String line) {
        if (loadedFor == null) {
            return;
        }
        try {
            Path file = file(loadedFor);
            Files.createDirectories(file.getParent());
            Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            StarrySky.LOGGER.warn("Starry Sky : impossible d'enregistrer le carnet d'observation", e);
        }
    }

    /** Vrai si cet astre vient d'être découvert (pour afficher le badge quelques secondes). */
    public static boolean isFreshDiscovery(String name) {
        return name.equals(lastDiscovery) && System.currentTimeMillis() - lastDiscoveryAt < BADGE_MILLIS;
    }

    /** Charge le carnet du monde courant si on a changé de monde. */
    private static void ensureLoaded() {
        String id = worldId();
        if (id == null) {
            loadedFor = null;
            discovered.clear();
            achievements.clear();
            return;
        }
        if (id.equals(loadedFor)) {
            return;
        }
        loadedFor = id;
        discovered.clear();
        achievements.clear();
        lastDiscovery = null;
        Path file = file(id);
        if (Files.isRegularFile(file)) {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String name = line.trim();
                    if (name.startsWith(ACHIEVEMENT_PREFIX)) {
                        achievements.add(name.substring(ACHIEVEMENT_PREFIX.length()));
                    } else if (!name.isEmpty()) {
                        discovered.add(name);
                    }
                }
            } catch (IOException e) {
                StarrySky.LOGGER.warn("Starry Sky : impossible de lire le carnet d'observation", e);
            }
        }
    }

    /** Identifiant du monde : « solo-NomDuMonde » ou « serveur-adresse », sans caractères spéciaux. */
    @Nullable
    private static String worldId() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        String raw;
        IntegratedServer server = minecraft.getSingleplayerServer();
        ServerData serverData = minecraft.getCurrentServer();
        if (server != null) {
            raw = "solo-" + server.getWorldData().getLevelName();
        } else if (serverData != null) {
            raw = "serveur-" + serverData.ip;
        } else {
            raw = "inconnu";
        }
        return raw.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static Path file(String id) {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("starrysky").resolve("journal").resolve(id + ".txt");
    }
}
