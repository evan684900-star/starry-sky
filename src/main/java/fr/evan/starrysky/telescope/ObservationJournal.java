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
 * Carnet d'observation : les astres déjà observés à la longue-vue.
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

    private static final Set<String> discovered = new LinkedHashSet<>();
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
        try {
            Path file = file(loadedFor);
            Files.createDirectories(file.getParent());
            Files.writeString(file, star.name() + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            StarrySky.LOGGER.warn("Starry Sky : impossible d'enregistrer le carnet d'observation", e);
        }
        return true;
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
            return;
        }
        if (id.equals(loadedFor)) {
            return;
        }
        loadedFor = id;
        discovered.clear();
        lastDiscovery = null;
        Path file = file(id);
        if (Files.isRegularFile(file)) {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String name = line.trim();
                    if (!name.isEmpty()) {
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
