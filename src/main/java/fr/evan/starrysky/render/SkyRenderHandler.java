package fr.evan.starrysky.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.evan.starrysky.config.StarrySkyConfig;
import fr.evan.starrysky.telescope.TelescopeTargeting;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Relie la configuration, le champ d'étoiles, la Voie lactée et les étoiles filantes au rendu du jeu.
 * <p>
 * Le rendu se fait pendant {@link RenderLevelStageEvent.Stage#AFTER_SKY}, c'est-à-dire juste après le ciel de
 * base et avant le terrain : les blocs, les nuages, etc. passent donc naturellement devant les étoiles.
 * <p>
 * <b>Performances.</b> Ce code s'exécute à chaque image : il ne crée aucun objet (les matrices sont réutilisées),
 * ne régénère aucune étoile, et se limite à 3 appels de dessin au maximum (lueur, étoiles, étoiles filantes)
 * sur des maillages déjà stockés dans la carte graphique. Le jour, sous la pluie ou sous terre, rien n'est dessiné.
 */
public final class SkyRenderHandler {
    /** Force du scintillement (0 = aucun ; 1 = l'étoile peut presque s'éteindre). 0,4 = léger. */
    private static final float TWINKLE_STRENGTH = 0.4F;
    /** En dessous de cette intensité, la lueur de la Voie lactée serait invisible : on ne la dessine pas. */
    private static final float MIN_GLOW = 1.0F / 255.0F;

    private static final StarField STAR_FIELD = new StarField();
    private static final MilkyWay MILKY_WAY = new MilkyWay();
    private static final ShootingStars SHOOTING_STARS = new ShootingStars();
    private static final AuroraBorealis AURORA = new AuroraBorealis();

    /** Intensité maximale des aurores. */
    private static final float AURORA_PEAK = 0.85F;
    /** Durée du fondu quand on entre dans un biome froid ou qu'on en sort, en secondes. */
    private static final float AURORA_FADE_SECONDS = 4.0F;
    /** Intervalle entre deux vérifications du biome, en secondes. */
    private static final float BIOME_CHECK_INTERVAL = 0.5F;

    private static final BlockPos.MutableBlockPos BIOME_POS = new BlockPos.MutableBlockPos();
    private static float auroraFactor;
    private static float auroraClock;
    private static float biomeCheckTimer;
    private static boolean coldBiome;

    // Matrices réutilisées d'une image à l'autre (aucune allocation pendant le rendu).
    private static final Matrix4f SKY_ROTATION = new Matrix4f();
    private static final Matrix4f STAR_VIEW = new Matrix4f();

    private static long lastFrameNanos = -1L;
    private static float twinkleClock;
    @Nullable
    private static ClientLevel lastLevel;

    // Cache de la liste des dimensions (reconstruit uniquement quand la config change).
    @Nullable
    private static List<? extends String> cachedDimensionList;
    private static Set<ResourceLocation> cachedDimensions = Set.of();

    private SkyRenderHandler() {
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        float deltaSeconds = frameDeltaSeconds();
        TelescopeTargeting.beginFrame();

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level != lastLevel) {
            SHOOTING_STARS.clear();
            lastLevel = level;
            // Nouveau monde : pas de fondu, l'état du biome est réévalué tout de suite.
            biomeCheckTimer = 0.0F;
            auroraFactor = -1.0F;
        }
        ShaderInstance starShader = StarShaders.star();
        ShaderInstance milkyWayShader = StarShaders.milkyWay();
        ShaderInstance shootingStarShader = StarShaders.shootingStar();
        if (level == null || starShader == null || milkyWayShader == null || shootingStarShader == null || !isActiveIn(level)) {
            SHOOTING_STARS.clear();
            return;
        }

        // Maillages : construits une fois (dès la première image dans le monde, pour ne pas provoquer de
        // à-coup à la tombée de la nuit), puis reconstruits seulement si un paramètre qui les concerne change.
        boolean milkyWay = StarrySkyConfig.MILKY_WAY.get();
        STAR_FIELD.rebuildIfNeeded(StarrySkyConfig.STAR_COUNT.get(), StarrySkyConfig.COLORED_STARS.get(), milkyWay);
        if (milkyWay) {
            MILKY_WAY.buildIfNeeded();
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float visibility = starVisibility(level, partialTick);

        // Horloges : le scintillement tourne en temps réel ; les étoiles filantes s'arrêtent quand le jeu est en pause.
        boolean twinkle = StarrySkyConfig.TWINKLE.get();
        if (twinkle) {
            float speed = StarrySkyConfig.TWINKLE_SPEED.get().floatValue();
            twinkleClock = (twinkleClock + deltaSeconds * speed) % StarField.TWINKLE_PERIOD;
        }
        SHOOTING_STARS.update(minecraft.isPaused() ? 0.0F : deltaSeconds, visibility,
                StarrySkyConfig.SHOOTING_STARS.get(), StarrySkyConfig.SHOOTING_STAR_FREQUENCY.get());

        // Aurores : uniquement dans les biomes froids, avec un fondu quand on entre dans le biome ou qu'on en sort.
        boolean auroras = StarrySkyConfig.AURORAS.get() && StarShaders.aurora() != null;
        updateAuroraFactor(level, event.getCamera(), auroras, deltaSeconds);
        if (auroras && auroraFactor > 0.0F) {
            AURORA.buildIfNeeded();
            auroraClock = (auroraClock + deltaSeconds) % AuroraBorealis.TIME_PERIOD;
        }

        // Longue-vue : n'est utile que si le ciel est assez sombre.
        boolean skyVisible = isSkyVisible(minecraft, level, event.getCamera());
        boolean telescope = StarrySkyConfig.TELESCOPE.get() && TelescopeTargeting.isScoping(minecraft);
        if (telescope && (visibility < TelescopeTargeting.MIN_VISIBILITY || !skyVisible)) {
            TelescopeTargeting.markTooBright();
            telescope = false;
        }

        // Le jour, sous la pluie, dans la lave, etc. : rien à dessiner, on s'arrête là.
        if (visibility <= 0.0F || !skyVisible) {
            return;
        }

        float brightness = StarrySkyConfig.BRIGHTNESS.get().floatValue();
        float intensity = visibility * brightness;

        // Même rotation que le soleil, la lune et les étoiles de base (voir LevelRenderer#renderSky) :
        // -90° autour de Y, puis l'heure du jour autour de X.
        SKY_ROTATION.rotationY((float) (-Math.PI / 2.0))
                .rotateX(level.getTimeOfDay(partialTick) * (float) (Math.PI * 2.0));
        Matrix4f cameraView = event.getModelViewMatrix();
        STAR_VIEW.set(cameraView).mul(SKY_ROTATION);
        Matrix4f projection = event.getProjectionMatrix();

        if (telescope) {
            TelescopeTargeting.update(SKY_ROTATION, STAR_VIEW, projection, event.getCamera().getLookVector());
        }

        // État de rendu : la lumière s'ajoute au ciel (les shaders produisent une couleur déjà multipliée par leur
        // opacité ; l'alpha de l'écran n'est pas modifié), pas d'écriture de profondeur, quads visibles des deux
        // côtés. Le même mélange est aussi déclaré dans les JSON des shaders.
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);

        // 1) Lueur de la Voie lactée. Très pâle, elle n'apparaît qu'en pleine nuit (visibilité au carré).
        if (milkyWay) {
            float glow = intensity * visibility * MilkyWay.GLOW_PEAK;
            if (glow >= MIN_GLOW) {
                RenderSystem.setShaderColor(glow, glow, glow, 1.0F);
                milkyWayShader.safeGetUniform("SkyMat").set(SKY_ROTATION);
                MILKY_WAY.draw(STAR_VIEW, projection, milkyWayShader);
            }
        }

        // 1 bis) Aurores boréales : dans le repère du monde (vers le nord), sans la rotation du ciel.
        ShaderInstance auroraShader = StarShaders.aurora();
        if (auroras && auroraShader != null && auroraFactor > 0.0F) {
            // Elles demandent un ciel bien noir : visibilité au carré, comme la Voie lactée.
            float strength = AURORA_PEAK * auroraFactor * visibility * visibility;
            if (strength >= MIN_GLOW) {
                RenderSystem.setShaderColor(strength, strength, strength, 1.0F);
                auroraShader.safeGetUniform("AuroraTime").set(auroraClock);
                AURORA.draw(cameraView, projection, auroraShader);
            }
        }

        // 2) Étoiles.
        Window window = minecraft.getWindow();
        RenderSystem.setShaderColor(intensity, intensity, intensity, 1.0F);
        starShader.safeGetUniform("SkyMat").set(SKY_ROTATION);
        starShader.safeGetUniform("ScreenSize").set((float) window.getWidth(), (float) window.getHeight());
        starShader.safeGetUniform("StarSize").set(StarrySkyConfig.STAR_SIZE.get().floatValue());
        starShader.safeGetUniform("StarTime").set(twinkleClock);
        starShader.safeGetUniform("TwinkleAmount").set(twinkle ? TWINKLE_STRENGTH : 0.0F);
        STAR_FIELD.draw(STAR_VIEW, projection, starShader);

        // 3) Étoiles filantes : dans le repère du monde (sans la rotation du ciel).
        SHOOTING_STARS.render(cameraView, projection, shootingStarShader, intensity);

        // Restauration de l'état attendu par la suite du rendu.
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    /**
     * Appelé par {@link fr.evan.starrysky.mixin.LevelRendererMixin} pendant le rendu du ciel de base :
     * indique s'il faut masquer les étoiles de Minecraft.
     */
    public static boolean shouldHideVanillaStars() {
        if (!StarShaders.ready() || !StarrySkyConfig.SPEC.isLoaded() || !StarrySkyConfig.HIDE_VANILLA_STARS.get()) {
            return false;
        }
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && isActiveIn(level);
    }

    /** Le mod est-il actif dans ce monde ? (activé, dimension autorisée, ciel de type « normal ») */
    private static boolean isActiveIn(ClientLevel level) {
        return StarrySkyConfig.SPEC.isLoaded()
                && StarrySkyConfig.ENABLED.get()
                && level.effects().skyType() == DimensionSpecialEffects.SkyType.NORMAL
                && allowedDimensions().contains(level.dimension().location());
    }

    /**
     * Visibilité des étoiles, de 0 à 1 : suit la luminosité des étoiles de base (apparition au crépuscule,
     * disparition à l'aube) et diminue avec la pluie, exactement comme dans le jeu de base.
     */
    private static float starVisibility(ClientLevel level, float partialTick) {
        // getStarBrightness() vaut au maximum 0,5 en pleine nuit : on ramène l'échelle à [0, 1].
        float night = Mth.clamp(level.getStarBrightness(partialTick) * 2.0F, 0.0F, 1.0F);
        return night * (1.0F - level.getRainLevel(partialTick));
    }

    /**
     * Reprend les conditions dans lesquelles le jeu de base ne dessine pas le ciel
     * (brouillard épais, lave, neige poudreuse, cécité, obscurité).
     */
    private static boolean isSkyVisible(Minecraft minecraft, ClientLevel level, Camera camera) {
        FogType fog = camera.getFluidInCamera();
        if (fog == FogType.POWDER_SNOW || fog == FogType.LAVA) {
            return false;
        }
        if (camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) {
            return false;
        }
        Vec3 pos = camera.getPosition();
        boolean foggy = level.effects().isFoggyAt(Mth.floor(pos.x), Mth.floor(pos.y))
                || minecraft.gui.getBossOverlay().shouldCreateWorldFog();
        return !foggy;
    }

    /**
     * Fait évoluer l'intensité des aurores vers 1 dans un biome froid, vers 0 ailleurs.
     * Le biome n'est consulté que deux fois par seconde, et au niveau de la mer : on regarde le climat de la région,
     * pas l'altitude du joueur (sinon n'importe quel sommet de montagne déclencherait des aurores).
     */
    private static void updateAuroraFactor(ClientLevel level, Camera camera, boolean enabled, float deltaSeconds) {
        biomeCheckTimer -= deltaSeconds;
        if (biomeCheckTimer <= 0.0F) {
            biomeCheckTimer = BIOME_CHECK_INTERVAL;
            Vec3 pos = camera.getPosition();
            BIOME_POS.set(Mth.floor(pos.x), level.getSeaLevel(), Mth.floor(pos.z));
            coldBiome = level.getBiome(BIOME_POS).value().coldEnoughToSnow(BIOME_POS);
        }
        float goal = enabled && coldBiome ? 1.0F : 0.0F;
        if (auroraFactor < 0.0F) {
            auroraFactor = goal;
        } else {
            float step = deltaSeconds / AURORA_FADE_SECONDS;
            auroraFactor = auroraFactor < goal ? Math.min(goal, auroraFactor + step) : Math.max(goal, auroraFactor - step);
        }
    }

    private static Set<ResourceLocation> allowedDimensions() {
        List<? extends String> list = StarrySkyConfig.DIMENSIONS.get();
        if (list != cachedDimensionList) {
            Set<ResourceLocation> parsed = new HashSet<>();
            for (String id : list) {
                ResourceLocation location = ResourceLocation.tryParse(id);
                if (location != null) {
                    parsed.add(location);
                }
            }
            cachedDimensions = parsed;
            cachedDimensionList = list;
        }
        return cachedDimensions;
    }

    /** Temps réel écoulé depuis l'image précédente, plafonné pour éviter les sauts après un gel. */
    private static float frameDeltaSeconds() {
        long now = System.nanoTime();
        float delta = lastFrameNanos < 0L ? 0.0F : (now - lastFrameNanos) / 1.0E9F;
        lastFrameNanos = now;
        return Mth.clamp(delta, 0.0F, 0.25F);
    }
}
