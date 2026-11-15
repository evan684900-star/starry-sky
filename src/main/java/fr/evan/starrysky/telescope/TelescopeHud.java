package fr.evan.starrysky.telescope;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.evan.starrysky.StarrySky;
import fr.evan.starrysky.config.StarrySkyConfig;
import java.text.NumberFormat;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Interface de la longue-vue : un viseur animé autour de l'étoile visée et un panneau d'information
 * (nom, constellation, classe spectrale, magnitude, distance, température), avec des icônes pixel-art.
 * <p>
 * Calque d'interface enregistré au-dessus de l'overlay de la longue-vue. Il ne fait rien (et ne coûte rien)
 * tant que le joueur ne regarde pas dans une longue-vue.
 */
public final class TelescopeHud implements LayeredDraw.Layer {
    public static final ResourceLocation ID = rl("telescope");

    private static final ResourceLocation ICON_STAR = rl("telescope/star");
    private static final ResourceLocation ICON_CONSTELLATION = rl("telescope/constellation");
    private static final ResourceLocation ICON_SPECTRUM = rl("telescope/spectrum");
    private static final ResourceLocation ICON_MAGNITUDE = rl("telescope/magnitude");
    private static final ResourceLocation ICON_DISTANCE = rl("telescope/distance");
    private static final ResourceLocation ICON_TEMPERATURE = rl("telescope/temperature");
    private static final ResourceLocation ICON_TARGET = rl("telescope/target");

    // Palette.
    private static final int PANEL_BG = 0x0B0F1A;
    private static final int LABEL = 0x8E9AB0;
    private static final int VALUE = 0xF2F4FA;
    private static final int ICON_TINT = 0x8FA8D8;
    private static final int SUBTITLE = 0xA9B8D6;

    private static final int ROW_HEIGHT = 12;
    private static final int PADDING = 8;

    @Nullable
    private CatalogStar shown;
    private float panelAlpha;
    private float hintAlpha;
    private float lockAge;
    private long lastNanos = -1L;
    private final float[] screen = new float[2];

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(StarrySky.MODID, path);
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        float dt = frameDelta();
        boolean scoping = StarrySkyConfig.SPEC.isLoaded() && StarrySkyConfig.TELESCOPE.get()
                && TelescopeTargeting.isScoping(minecraft) && !minecraft.options.hideGui;
        TelescopeTargeting.State state = scoping ? TelescopeTargeting.state() : TelescopeTargeting.State.NONE;
        CatalogStar target = scoping ? TelescopeTargeting.target() : null;

        // Animations : fondu du panneau, petit « verrouillage » du viseur sur une nouvelle étoile.
        if (target != null && target != shown) {
            shown = target;
            lockAge = 0.0F;
        }
        lockAge += dt;
        // Garder l'astre au centre un court instant l'enregistre dans le carnet d'observation.
        if (target != null && lockAge >= ObservationJournal.OBSERVE_SECONDS) {
            ObservationJournal.observe(target);
        }
        panelAlpha = approach(panelAlpha, target != null ? 1.0F : 0.0F, dt * 7.0F);
        boolean hint = state == TelescopeTargeting.State.SEARCHING || state == TelescopeTargeting.State.TOO_BRIGHT;
        hintAlpha = approach(hintAlpha, hint ? 1.0F : 0.0F, dt * 4.0F);
        if (panelAlpha <= 0.0F) {
            shown = null;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        Font font = minecraft.font;

        if (hintAlpha > 0.02F && target == null) {
            Component text = Component.translatable(state == TelescopeTargeting.State.TOO_BRIGHT
                    ? "starrysky.telescope.hint.too_bright" : "starrysky.telescope.hint.aim");
            drawHint(graphics, font, text, width, height, hintAlpha * 0.85F);
        }
        if (shown != null && panelAlpha > 0.02F) {
            if (target != null && shown.figure() != null) {
                drawFigure(graphics, shown.figure(), width, height, panelAlpha);
            }
            if (target != null && TelescopeTargeting.screenPosition(shown, width, height, screen)) {
                drawReticle(graphics, screen[0], screen[1], shown.color(), panelAlpha);
            }
            drawPanel(graphics, font, shown, width, height, ease(panelAlpha));
        }
    }

    // ---------------------------------------------------------------------------------------------------- dessin

    /** Viseur : quatre coins qui se resserrent sur l'étoile au moment où elle est accrochée. */
    private void drawReticle(GuiGraphics graphics, float cx, float cy, int color, float alpha) {
        float t = Math.min(1.0F, lockAge / 0.25F);
        int half = Math.round(6.0F + 8.0F * (1.0F - ease(t)));
        int arm = 3;
        int x = Math.round(cx);
        int y = Math.round(cy);
        int c = argb(color, alpha * 0.9F);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                int px = x + sx * half;
                int py = y + sy * half;
                // branche horizontale et verticale de chaque coin, tournées vers l'intérieur
                hLine(graphics, px, px - sx * arm, py, c);
                vLine(graphics, px, py, py - sy * arm, c);
            }
        }
    }

    /**
     * Traits de la constellation de l'étoile visée : lignes en pointillés entre ses étoiles.
     * La longue-vue grossit beaucoup : on ne voit souvent qu'une partie de la figure, les traits sortent de l'écran.
     */
    private void drawFigure(GuiGraphics graphics, String figure, int width, int height, float alpha) {
        int color = argb(0xA9C4FF, alpha * 0.45F);
        float[] a = new float[2];
        float[] b = new float[2];
        for (float[] segment : fr.evan.starrysky.render.Constellations.segments(figure)) {
            if (!TelescopeTargeting.screenPosition(segment[0], segment[1], segment[2], width, height, a)
                    || !TelescopeTargeting.screenPosition(segment[3], segment[4], segment[5], width, height, b)) {
                continue;
            }
            float dx = b[0] - a[0];
            float dy = b[1] - a[1];
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            // Points tous les 4 pixels, en laissant un espace autour des étoiles ; limité pour rester léger.
            int steps = (int) Math.min(800.0F, length / 4.0F);
            for (int i = 2; i < steps - 1; i++) {
                float t = i / (float) steps;
                int x = Math.round(a[0] + dx * t);
                int y = Math.round(a[1] + dy * t);
                if (x >= 0 && y >= 0 && x < width && y < height) {
                    graphics.fill(x, y, x + 1, y + 1, color);
                }
            }
        }
    }

    private void drawPanel(GuiGraphics graphics, Font font, CatalogStar star, int width, int height, float alpha) {
        Component title = StarText.name(star).copy().withStyle(ChatFormatting.BOLD);
        Component subtitle = Component.literal(star.constellation());

        Component[] labels = {
                StarText.typeLabel(star),
                Component.translatable("starrysky.telescope.magnitude"),
                Component.translatable("starrysky.telescope.distance"),
                Component.translatable("starrysky.telescope.temperature")};
        Component[] values = {
                StarText.type(star),
                StarText.magnitude(star),
                StarText.distance(star),
                StarText.temperature(star)};
        ResourceLocation[] icons = {ICON_SPECTRUM, ICON_MAGNITUDE, ICON_DISTANCE, ICON_TEMPERATURE};

        // Largeur : assez pour la ligne la plus longue.
        int rowsWidth = 0;
        for (int i = 0; i < labels.length; i++) {
            rowsWidth = Math.max(rowsWidth, 13 + font.width(labels[i]) + 14 + font.width(values[i]));
        }
        int headerWidth = 22 + Math.max(font.width(title), 11 + font.width(subtitle));
        int panelWidth = Math.max(170, Math.max(rowsWidth, headerWidth) + 2 * PADDING);
        int panelHeight = PADDING + 22 + 6 + labels.length * ROW_HEIGHT + PADDING - 3;

        // Sous le centre de l'écran, à l'intérieur du cercle de la longue-vue ; glisse légèrement en apparaissant.
        int x0 = (width - panelWidth) / 2;
        int y0 = Math.min(height - panelHeight - 6, height / 2 + Math.round(height * 0.13F)) + Math.round((1.0F - alpha) * 6.0F);
        int x1 = x0 + panelWidth;
        int y1 = y0 + panelHeight;

        // Fond, bordure fine aux coins arrondis, liseré coloré à gauche.
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, argb(PANEL_BG, alpha * 0.86F));
        int border = argb(0xFFFFFF, alpha * 0.16F);
        hLine(graphics, x0 + 1, x1 - 2, y0, border);
        hLine(graphics, x0 + 1, x1 - 2, y1 - 1, border);
        vLine(graphics, x0, y0 + 1, y1 - 2, border);
        vLine(graphics, x1 - 1, y0 + 1, y1 - 2, border);
        graphics.fill(x0 + 1, y0 + 3, x0 + 3, y1 - 3, argb(star.color(), alpha));

        // En-tête : icône d'étoile (couleur de l'étoile), nom, constellation.
        int hx = x0 + PADDING + 2;
        int hy = y0 + PADDING;
        icon(graphics, ICON_STAR, hx, hy + 2, 16, star.color(), alpha);
        graphics.drawString(font, title, hx + 22, hy + 1, argb(VALUE, alpha), false);
        icon(graphics, ICON_CONSTELLATION, hx + 22, hy + 12, 9, ICON_TINT, alpha * 0.8F);
        graphics.drawString(font, subtitle, hx + 33, hy + 13, argb(SUBTITLE, alpha), false);

        // Séparateur.
        int sepY = hy + 26;
        hLine(graphics, x0 + PADDING, x1 - PADDING - 1, sepY, argb(0xFFFFFF, alpha * 0.10F));

        // Lignes d'information : icône + libellé à gauche, valeur alignée à droite.
        int rowY = sepY + 5;
        for (int i = 0; i < labels.length; i++) {
            int y = rowY + i * ROW_HEIGHT;
            icon(graphics, icons[i], hx, y, 9, ICON_TINT, alpha);
            graphics.drawString(font, labels[i], hx + 13, y + 1, argb(LABEL, alpha), false);
            int valueX = x1 - PADDING - font.width(values[i]);
            graphics.drawString(font, values[i], valueX, y + 1, argb(VALUE, alpha), false);
        }

        // Badge « Nouvelle découverte » au-dessus du panneau, quelques secondes après la première observation.
        if (ObservationJournal.isFreshDiscovery(star.name())) {
            Component badge = Component.translatable("starrysky.journal.new_discovery");
            int bw = 9 + 5 + font.width(badge) + 12;
            int bx = (width - bw) / 2;
            int by = y0 - 17;
            graphics.fill(bx, by, bx + bw, by + 13, argb(0x2A2210, alpha * 0.9F));
            hLine(graphics, bx, bx + bw - 1, by, argb(0xE8C77A, alpha * 0.6F));
            icon(graphics, ICON_MAGNITUDE, bx + 6, by + 2, 9, 0xE8C77A, alpha);
            graphics.drawString(font, badge, bx + 19, by + 3, argb(0xF4DFA6, alpha), false);
        }
    }

    private void drawHint(GuiGraphics graphics, Font font, Component text, int width, int height, float alpha) {
        int textWidth = font.width(text);
        int total = 9 + 5 + textWidth;
        int x = (width - total) / 2;
        int y = height / 2 + Math.round(height * 0.22F);
        graphics.fill(x - 6, y - 4, x + total + 6, y + 13, argb(PANEL_BG, alpha * 0.55F));
        icon(graphics, ICON_TARGET, x, y, 9, ICON_TINT, alpha);
        graphics.drawString(font, text, x + 14, y + 1, argb(LABEL, alpha), false);
    }

    // -------------------------------------------------------------------------------------------------- outils

    /** Icône pixel-art blanche, teintée à l'affichage. */
    private static void icon(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int size, int color, float alpha) {
        RenderSystem.enableBlend();
        graphics.setColor(((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, alpha);
        graphics.blitSprite(sprite, x, y, size, size);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void hLine(GuiGraphics graphics, int x0, int x1, int y, int color) {
        graphics.fill(Math.min(x0, x1), y, Math.max(x0, x1) + 1, y + 1, color);
    }

    private static void vLine(GuiGraphics graphics, int x, int y0, int y1, int color) {
        graphics.fill(x, Math.min(y0, y1), x + 1, Math.max(y0, y1) + 1, color);
    }

    /** Couleur ARGB ; l'alpha ne descend jamais sous 5, sinon Minecraft dessine le texte opaque. */
    private static int argb(int rgb, float alpha) {
        int a = Math.max(5, Math.min(255, Math.round(alpha * 255.0F)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static float approach(float value, float goal, float step) {
        return value < goal ? Math.min(goal, value + step) : Math.max(goal, value - step);
    }

    private static float ease(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    private float frameDelta() {
        long now = System.nanoTime();
        float delta = lastNanos < 0L ? 0.0F : (now - lastNanos) / 1.0E9F;
        lastNanos = now;
        return Math.min(delta, 0.1F);
    }

    /** Nombre entier avec séparateur de milliers de la langue du jeu (espaces normaux, que la police sait afficher). */
    private static String formatInt(int value, Locale locale) {
        return NumberFormat.getIntegerInstance(locale).format(value).replace(' ', ' ').replace(' ', ' ');
    }

    private static Locale gameLocale() {
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        return Locale.forLanguageTag(code.replace('_', '-'));
    }
}
