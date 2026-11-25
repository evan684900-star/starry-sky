package fr.evan.starrysky.telescope;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.evan.starrysky.StarrySky;
import fr.evan.starrysky.render.Comet;
import fr.evan.starrysky.render.Planets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Carnet d'observation, en deux onglets : la liste des astres découverts à la longue-vue dans ce monde (avec un
 * compteur), et les succès d'astronome. S'ouvre avec la touche du carnet (J par défaut) et se ferme avec la même
 * touche ou Échap. Tab passe d'un onglet à l'autre.
 */
public final class JournalScreen extends Screen {
    private static final ResourceLocation ICON_STAR = ResourceLocation.fromNamespaceAndPath(StarrySky.MODID, "telescope/star");
    private static final ResourceLocation ICON_SMALL_STAR = ResourceLocation.fromNamespaceAndPath(StarrySky.MODID, "telescope/magnitude");
    private static final ResourceLocation ICON_CONSTELLATION = ResourceLocation.fromNamespaceAndPath(StarrySky.MODID, "telescope/constellation");

    private static final int PANEL_BG = 0xE00B0F1A;
    private static final int BORDER = 0x30FFFFFF;
    private static final int TITLE = 0xFFF2F4FA;
    private static final int MUTED = 0xFF8E9AB0;
    private static final int ACCENT = 0xFF7FA8FF;
    private static final int GOLD = 0xFFE8C77A;
    private static final int ROW_HEIGHT = 24;
    private static final int ACHIEVEMENT_ROW_HEIGHT = 30;
    private static final int LOCKED = 0xFF4A5368;
    private static final int TAB_HEIGHT = 14;

    private final KeyMapping toggleKey;
    private final List<CatalogStar> entries = new ArrayList<>();
    private int total;
    private int discoveredStars;
    private double scroll;
    private boolean achievementsTab;
    // Zones cliquables des onglets (x0, x1, y0, y1), calculées au rendu.
    private final int[] tabStars = new int[4];
    private final int[] tabAchievements = new int[4];

    public JournalScreen(KeyMapping toggleKey) {
        super(Component.translatable("starrysky.journal.title"));
        this.toggleKey = toggleKey;
    }

    @Override
    protected void init() {
        // Tous les astres identifiables : planètes, constellations, étoiles nommées, puis étoiles de catalogue.
        List<CatalogStar> all = new ArrayList<>(Planets.catalog());
        all.addAll(StarCatalog.stars());
        total = all.size();
        entries.clear();
        for (CatalogStar star : all) {
            if (ObservationJournal.isDiscovered(star.name())) {
                entries.add(star);
            }
        }
        discoveredStars = entries.size();
        // Comètes observées : elles ne sont plus forcément dans le ciel, on retrouve leurs caractéristiques.
        for (String name : ObservationJournal.discoveredComets()) {
            CatalogStar comet = Comet.describe(name);
            if (comet != null) {
                entries.add(comet);
            }
        }
        entries.sort(Comparator.comparingInt(JournalScreen::rank).thenComparing(CatalogStar::magnitude));
        ObservationJournal.checkAchievements();
    }

    /** Ordre d'affichage : planètes, comètes, étoiles des constellations, étoiles nommées, catalogue. */
    private static int rank(CatalogStar star) {
        if (star.isComet()) {
            return 1;
        }
        if (star.isPlanet()) {
            return 0;
        }
        if (star.figure() != null) {
            return 2;
        }
        return star.named() ? 3 : 4;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int panelWidth = Math.min(340, width - 20);
        int x0 = (width - panelWidth) / 2;
        int x1 = x0 + panelWidth;
        int y0 = 16;
        int y1 = height - 16;

        // Panneau.
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, PANEL_BG);
        graphics.fill(x0 + 1, y0, x1 - 1, y0 + 1, BORDER);
        graphics.fill(x0 + 1, y1 - 1, x1 - 1, y1, BORDER);
        graphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, BORDER);
        graphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, BORDER);

        // En-tête : icône, titre, compteur, barre de progression.
        icon(graphics, ICON_STAR, x0 + 10, y0 + 10, 16, GOLD);
        graphics.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), x0 + 32, y0 + 11, TITLE, false);
        int unlocked = ObservationJournal.unlockedCount();
        int achievementCount = Achievement.values().length;
        Component counter = achievementsTab
                ? Component.translatable("starrysky.journal.achievement_counter",
                        StarText.formatInt(unlocked), StarText.formatInt(achievementCount))
                : Component.translatable("starrysky.journal.counter",
                        StarText.formatInt(discoveredStars), StarText.formatInt(total));
        graphics.drawString(font, counter, x0 + 32, y0 + 22, MUTED, false);

        int barX0 = x0 + 10;
        int barX1 = x1 - 10;
        int barY = y0 + 36;
        graphics.fill(barX0, barY, barX1, barY + 3, 0x30FFFFFF);
        float fraction = achievementsTab ? unlocked / (float) achievementCount
                : total > 0 ? discoveredStars / (float) total : 0.0F;
        graphics.fill(barX0, barY, barX0 + Math.round((barX1 - barX0) * Math.min(1.0F, fraction)), barY + 3,
                achievementsTab ? GOLD : ACCENT);

        // Onglets.
        int tabY = barY + 9;
        int tabX = drawTab(graphics, Component.translatable("starrysky.journal.tab.stars"), barX0, tabY, !achievementsTab, tabStars);
        drawTab(graphics, Component.translatable("starrysky.journal.tab.achievements"), tabX + 6, tabY, achievementsTab, tabAchievements);
        graphics.fill(barX0, tabY + TAB_HEIGHT, barX1, tabY + TAB_HEIGHT + 1, BORDER);

        // Liste (avec découpe et défilement).
        int listTop = tabY + TAB_HEIGHT + 5;
        int listBottom = y1 - 8;
        if (achievementsTab) {
            drawAchievements(graphics, x0, x1, listTop, listBottom);
            return;
        }
        if (entries.isEmpty()) {
            Component empty = Component.translatable("starrysky.journal.empty");
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(empty, panelWidth - 40);
            int y = (listTop + listBottom) / 2 - lines.size() * 5;
            for (net.minecraft.util.FormattedCharSequence line : lines) {
                graphics.drawString(font, line, (width - font.width(line)) / 2, y, MUTED, false);
                y += 10;
            }
            return;
        }

        int contentHeight = entries.size() * ROW_HEIGHT;
        double maxScroll = clampScroll(contentHeight, listBottom - listTop);

        graphics.enableScissor(x0 + 1, listTop, x1 - 1, listBottom);
        int first = (int) (scroll / ROW_HEIGHT);
        for (int i = first; i < entries.size(); i++) {
            int y = listTop + i * ROW_HEIGHT - (int) scroll;
            if (y > listBottom) {
                break;
            }
            drawRow(graphics, entries.get(i), x0 + 10, x1 - 14, y, i % 2 == 0);
        }
        graphics.disableScissor();
        drawScrollbar(graphics, x1, listTop, listBottom, contentHeight, maxScroll);
    }

    /** Onglet de l'en-tête ; renvoie sa bordure droite et retient sa zone cliquable. */
    private int drawTab(GuiGraphics graphics, Component label, int x, int y, boolean active, int[] area) {
        int w = font.width(label) + 12;
        if (active) {
            graphics.fill(x, y, x + w, y + TAB_HEIGHT, 0x22FFFFFF);
            graphics.fill(x, y + TAB_HEIGHT - 1, x + w, y + TAB_HEIGHT + 1, ACCENT);
        }
        graphics.drawString(font, label, x + 6, y + 3, active ? TITLE : MUTED, false);
        area[0] = x;
        area[1] = x + w;
        area[2] = y;
        area[3] = y + TAB_HEIGHT;
        return x + w;
    }

    /** Onglet des succès : icône, titre, description et progression de chaque succès. */
    private void drawAchievements(GuiGraphics graphics, int x0, int x1, int listTop, int listBottom) {
        Achievement[] all = Achievement.values();
        int contentHeight = all.length * ACHIEVEMENT_ROW_HEIGHT;
        double maxScroll = clampScroll(contentHeight, listBottom - listTop);

        graphics.enableScissor(x0 + 1, listTop, x1 - 1, listBottom);
        for (int i = 0; i < all.length; i++) {
            int y = listTop + i * ACHIEVEMENT_ROW_HEIGHT - (int) scroll;
            if (y + ACHIEVEMENT_ROW_HEIGHT < listTop || y > listBottom) {
                continue;
            }
            drawAchievement(graphics, all[i], x0 + 10, x1 - 14, y, i % 2 == 0);
        }
        graphics.disableScissor();
        drawScrollbar(graphics, x1, listTop, listBottom, contentHeight, maxScroll);
    }

    private void drawAchievement(GuiGraphics graphics, Achievement achievement, int x0, int x1, int y, boolean even) {
        boolean unlocked = ObservationJournal.isUnlocked(achievement);
        if (even) {
            graphics.fill(x0 - 4, y, x1 + 4, y + ACHIEVEMENT_ROW_HEIGHT - 2, 0x0CFFFFFF);
        }
        icon(graphics, ICON_STAR, x0, y + 5, 16, unlocked ? achievement.color() : LOCKED);

        int textX = x0 + 22;
        // À droite : coche dorée si débloqué, sinon progression (« 12 / 25 ») pour les succès à objectif.
        Component right = null;
        int rightColor = MUTED;
        if (unlocked) {
            right = Component.literal("\u2714");
            rightColor = GOLD;
        } else if (!achievement.isEvent()) {
            right = Component.literal(StarText.formatInt(Math.min(achievement.progress(), achievement.goal()))
                    + " / " + StarText.formatInt(achievement.goal()));
        }
        int rightWidth = right == null ? 0 : font.width(right) + 6;
        int textWidth = x1 - textX - rightWidth;

        graphics.drawString(font, font.substrByWidth(achievement.title(), textWidth).getString(), textX, y + 4,
                unlocked ? TITLE : 0xFFB6C0D4, false);
        graphics.drawString(font, font.substrByWidth(achievement.description(), textWidth).getString(), textX, y + 15,
                MUTED, false);
        if (right != null) {
            graphics.drawString(font, right, x1 - font.width(right), y + 4, rightColor, false);
        }
        // Petite barre de progression sous le texte pour les succès à objectif pas encore atteints.
        if (!unlocked && !achievement.isEvent() && achievement.goal() > 1) {
            int barY = y + ACHIEVEMENT_ROW_HEIGHT - 5;
            int barX1 = x1;
            graphics.fill(textX, barY, barX1, barY + 1, 0x20FFFFFF);
            float f = Math.min(1.0F, achievement.progress() / (float) achievement.goal());
            graphics.fill(textX, barY, textX + Math.round((barX1 - textX) * f), barY + 1, 0xA0000000 | (achievement.color() & 0xFFFFFF));
        }
    }

    /** Borne le défilement au contenu ; renvoie le défilement maximal. */
    private double clampScroll(int contentHeight, int viewHeight) {
        double maxScroll = Math.max(0, contentHeight - viewHeight);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        return maxScroll;
    }

    private void drawScrollbar(GuiGraphics graphics, int x1, int listTop, int listBottom, int contentHeight, double maxScroll) {
        int viewHeight = listBottom - listTop;
        if (maxScroll > 0) {
            int trackX = x1 - 6;
            int thumbHeight = Math.max(16, viewHeight * viewHeight / contentHeight);
            int thumbY = listTop + (int) ((viewHeight - thumbHeight) * (scroll / maxScroll));
            graphics.fill(trackX, listTop, trackX + 2, listBottom, 0x18FFFFFF);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0x80FFFFFF);
        }
    }

    private void drawRow(GuiGraphics graphics, CatalogStar star, int x0, int x1, int y, boolean even) {
        if (even) {
            graphics.fill(x0 - 4, y, x1 + 4, y + ROW_HEIGHT - 2, 0x0CFFFFFF);
        }
        int color = 0xFF000000 | star.color();
        icon(graphics, star.figure() != null ? ICON_CONSTELLATION : ICON_SMALL_STAR, x0, y + 4, 9, star.color());

        graphics.drawString(font, StarText.name(star), x0 + 14, y + 3, TITLE, false);
        Component detail = star.isPlanet()
                ? StarText.type(star).append(" · ").append(StarText.distance(star))
                : Component.literal(star.constellation() + " · ").append(StarText.type(star));
        graphics.drawString(font, detail, x0 + 14, y + 13, MUTED, false);

        Component magnitude = Component.literal("mag ").append(StarText.magnitude(star));
        graphics.drawString(font, magnitude, x1 - font.width(magnitude), y + 3, color, false);
    }

    private static void icon(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int size, int color) {
        RenderSystem.enableBlend();
        graphics.setColor(((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, 1.0F);
        graphics.blitSprite(sprite, x, y, size, size);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * ROW_HEIGHT * 2;
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (inside(tabStars, mouseX, mouseY)) {
                selectTab(false);
                return true;
            }
            if (inside(tabAchievements, mouseX, mouseY)) {
                selectTab(true);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean inside(int[] area, double x, double y) {
        return x >= area[0] && x < area[1] && y >= area[2] && y < area[3];
    }

    private void selectTab(boolean achievements) {
        if (achievementsTab != achievements) {
            achievementsTab = achievements;
            scroll = 0;
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0F));
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (toggleKey.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            selectTab(!achievementsTab);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
