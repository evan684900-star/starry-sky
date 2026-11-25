package fr.evan.starrysky.telescope;

import java.text.NumberFormat;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Textes affichés pour un astre (panneau de la longue-vue et carnet d'observation), dans la langue du jeu. */
public final class StarText {
    private StarText() {
    }

    /** Nom affiché : les planètes sont traduites (Vénus, Saturne…), les étoiles gardent leur nom propre. */
    public static Component name(CatalogStar star) {
        return star.isPlanet() && !star.isComet()
                ? Component.translatable("starrysky.planet." + star.name().toLowerCase(Locale.ROOT))
                : Component.literal(star.name());
    }

    /** Libellé de la première ligne : « Classe » pour une étoile, « Type » pour une planète. */
    public static Component typeLabel(CatalogStar star) {
        return Component.translatable(star.isPlanet() ? "starrysky.telescope.type_label" : "starrysky.telescope.class");
    }

    /** « A0 · Étoile blanche » ou « Géante gazeuse ». */
    public static MutableComponent type(CatalogStar star) {
        if (star.isPlanet()) {
            return Component.translatable("starrysky.planet_type." + star.planetType());
        }
        return Component.literal(star.spectralClass() + "" + star.subclass() + " · ")
                .append(Component.translatable("starrysky.telescope.type." + Character.toLowerCase(star.spectralClass())));
    }

    public static Component magnitude(CatalogStar star) {
        return Component.literal(String.format(locale(), "%.1f", star.magnitude()));
    }

    /** « 25 al » pour une étoile, « 5,2 UA » pour une planète. */
    public static Component distance(CatalogStar star) {
        if (star.isPlanet()) {
            return Component.translatable("starrysky.telescope.au", String.format(locale(), "%.1f", star.distanceAu()));
        }
        return Component.translatable("starrysky.telescope.lightyears", formatInt(star.distanceLy()));
    }

    public static Component temperature(CatalogStar star) {
        return Component.literal(formatInt(star.temperatureK()) + " K");
    }

    /** Nombre entier avec le séparateur de milliers de la langue du jeu (espaces normaux, affichables par la police). */
    public static String formatInt(int value) {
        return NumberFormat.getIntegerInstance(locale()).format(value).replace(' ', ' ').replace(' ', ' ');
    }

    public static Locale locale() {
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        return Locale.forLanguageTag(code.replace('_', '-'));
    }
}
