package fr.evan.starrysky.telescope;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.evan.starrysky.StarrySky;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Notification « Succès d'astronome » en haut à droite, dans le style des progrès de Minecraft. */
public final class AchievementToast implements Toast {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("toast/advancement");
    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(StarrySky.MODID, "telescope/star");
    private static final long DISPLAY_MILLIS = 5000L;

    private final Achievement achievement;

    public AchievementToast(Achievement achievement) {
        this.achievement = achievement;
    }

    @Override
    public Visibility render(GuiGraphics graphics, ToastComponent toasts, long timeSinceLastVisible) {
        Font font = toasts.getMinecraft().font;
        graphics.blitSprite(BACKGROUND, 0, 0, width(), height());

        int color = achievement.color();
        RenderSystem.enableBlend();
        graphics.setColor(((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, 1.0F);
        graphics.blitSprite(ICON, 8, 8, 16, 16);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        graphics.drawString(font, Component.translatable("starrysky.achievement.toast"), 30, 7, 0xFFE8C77A, false);
        graphics.drawString(font, font.substrByWidth(achievement.title(), width() - 36).getString(), 30, 18, 0xFFFFFFFF, false);

        return timeSinceLastVisible >= DISPLAY_MILLIS * toasts.getNotificationDisplayTimeMultiplier()
                ? Visibility.HIDE : Visibility.SHOW;
    }
}
