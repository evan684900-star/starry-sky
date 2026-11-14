package fr.evan.starrysky.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.evan.starrysky.StarrySky;
import java.io.IOException;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/**
 * Charge les shaders du mod ({@code assets/starrysky/shaders/core/}).
 * <ul>
 *     <li>{@code star} : étoiles rondes et douces à taille fixe en pixels, éclats en croix sur les plus brillantes,
 *     scintillement (calculé à partir d'une horloge envoyée en uniform, le maillage n'est donc jamais modifié pour
 *     l'animation) et atténuation près de l'horizon ;</li>
 *     <li>{@code milky_way} : lueur de la Voie lactée ;</li>
 *     <li>{@code shooting_star} : traînée des étoiles filantes ;</li>
 *     <li>{@code aurora} : rideaux des aurores boréales.</li>
 * </ul>
 * Les shaders déclarent dans leur JSON un mélange additif ({@code "blend"}), appliqué par le jeu à chaque
 * utilisation : la lumière des étoiles s'ajoute au ciel au lieu de le recouvrir.
 * <p>
 * Si un shader ne peut pas être chargé, le mod se désactive proprement et les étoiles de base restent visibles.
 */
public final class StarShaders {
    @Nullable
    private static ShaderInstance starShader;
    @Nullable
    private static ShaderInstance milkyWayShader;
    @Nullable
    private static ShaderInstance shootingStarShader;
    @Nullable
    private static ShaderInstance auroraShader;

    private StarShaders() {
    }

    /** Appelé sur le bus du mod à chaque (re)chargement des ressources. */
    public static void onRegisterShaders(RegisterShadersEvent event) {
        starShader = null;
        milkyWayShader = null;
        shootingStarShader = null;
        auroraShader = null;
        register(event, "aurora", DefaultVertexFormat.POSITION_TEX_COLOR, shader -> auroraShader = shader);
        register(event, "star", DefaultVertexFormat.POSITION_TEX_COLOR, shader -> starShader = shader);
        register(event, "milky_way", DefaultVertexFormat.POSITION_COLOR, shader -> milkyWayShader = shader);
        register(event, "shooting_star", DefaultVertexFormat.POSITION_TEX_COLOR, shader -> shootingStarShader = shader);
    }

    private static void register(RegisterShadersEvent event, String name, VertexFormat format, Consumer<ShaderInstance> onLoaded) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(StarrySky.MODID, name);
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), id, format), onLoaded);
        } catch (IOException e) {
            StarrySky.LOGGER.error("Starry Sky : impossible de charger le shader {}, le mod est désactivé.", id, e);
        }
    }

    /** Le shader des étoiles, ou {@code null} s'il n'est pas (encore) chargé. */
    @Nullable
    public static ShaderInstance star() {
        return starShader;
    }

    /** Le shader de la lueur de la Voie lactée, ou {@code null} s'il n'est pas (encore) chargé. */
    @Nullable
    public static ShaderInstance milkyWay() {
        return milkyWayShader;
    }

    /** Le shader des étoiles filantes, ou {@code null} s'il n'est pas (encore) chargé. */
    @Nullable
    public static ShaderInstance shootingStar() {
        return shootingStarShader;
    }

    /** Le shader des aurores boréales, ou {@code null} s'il n'est pas (encore) chargé. */
    @Nullable
    public static ShaderInstance aurora() {
        return auroraShader;
    }

    /** Vrai si tous les shaders indispensables sont prêts (les aurores sont facultatives). */
    public static boolean ready() {
        return starShader != null && milkyWayShader != null && shootingStarShader != null;
    }
}
