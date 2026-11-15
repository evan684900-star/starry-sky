package fr.evan.starrysky;

import com.mojang.logging.LogUtils;
import fr.evan.starrysky.config.StarrySkyConfig;
import fr.evan.starrysky.render.SkyRenderHandler;
import fr.evan.starrysky.render.StarShaders;
import com.mojang.blaze3d.platform.InputConstants;
import fr.evan.starrysky.telescope.JournalScreen;
import fr.evan.starrysky.telescope.TelescopeHud;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Point d'entrée du mod Starry Sky.
 * <p>
 * {@code dist = Dist.CLIENT} : cette classe (et donc tout le mod) n'est jamais chargée sur un serveur dédié.
 * Le mod n'enregistre ni bloc, ni item, ni paquet réseau : il ne fait que dessiner le ciel.
 */
@Mod(value = StarrySky.MODID, dist = Dist.CLIENT)
public final class StarrySky {
    public static final String MODID = "starrysky";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Touche qui ouvre le carnet d'observation. */
    public static final KeyMapping JOURNAL_KEY = new KeyMapping("key.starrysky.journal",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.starrysky");

    public StarrySky(IEventBus modEventBus, ModContainer container) {
        // Configuration client (fichier config/starrysky-client.toml).
        container.registerConfig(ModConfig.Type.CLIENT, StarrySkyConfig.SPEC);

        // Écran de configuration généré par NeoForge, accessible depuis la liste des mods (bouton « Config »).
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // Bus du mod : chargement des shaders, interface de la longue-vue (au-dessus de l'overlay de la longue-vue).
        modEventBus.addListener(StarShaders::onRegisterShaders);
        modEventBus.addListener((RegisterGuiLayersEvent event) ->
                event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, TelescopeHud.ID, new TelescopeHud()));

        // Touche du carnet d'observation (J par défaut, modifiable dans Options > Commandes).
        modEventBus.addListener((RegisterKeyMappingsEvent event) -> event.register(JOURNAL_KEY));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            Minecraft minecraft = Minecraft.getInstance();
            while (JOURNAL_KEY.consumeClick()) {
                if (minecraft.screen == null && minecraft.level != null) {
                    minecraft.setScreen(new JournalScreen(JOURNAL_KEY));
                }
            }
        });

        // Bus du jeu : rendu des étoiles juste après le ciel.
        NeoForge.EVENT_BUS.addListener(SkyRenderHandler::onRenderLevelStage);
    }
}
