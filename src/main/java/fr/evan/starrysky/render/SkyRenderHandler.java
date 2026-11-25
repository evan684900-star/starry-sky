package fr.evan.starrysky.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.evan.starrysky.config.StarrySkyConfig;
import fr.evan.starrysky.telescope.Achievement;
import fr.evan.starrysky.telescope.ObservationJournal;
import fr.evan.starrysky.telescope.StarCatalog;
import fr.evan.starrysky.telescope.TelescopeTargeting;
import net.minecraft.network.chat.Component;
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
 * sur des maillages déjà stockés dans la carte graphique (deux de plus les nuits où une comète est visible). Le jour, sous la pluie ou sous terre, rien n'est dessiné.
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
    private static final Planets PLANETS = new Planets();
    private static final MoonHalo MOON_HALO = new MoonHalo();
    private static final Comet COMET = new Comet();

    /** Avancement de l'éclipse de lune en cours (0 = aucune, 1 = totalité), lu par le mixin du ciel. */
    private static volatile float eclipse;
    private static final float[] MOON_TINT = new float[3];
    /** Pendant la totalité, regarder la lune à moins de 25° débloque le succès « Lune de sang ». */
    private static final float ECLIPSE_LOOK_COS = (float) Math.cos(Math.toRadians(25.0));
    /** Les succès liés au ciel (aurores, pluie d'étoiles filantes, éclipse) sont vérifiés une fois par seconde. */
    private static final float EVENT_CHECK_INTERVAL = 1.0F;
    private static float eventCheckTimer;

    /** Intensité maximale du halo de lune (à la pleine lune). */
    private static final float MOON_HALO_PEAK = 0.22F;
    /** Une nuit sur SHOWER_ONE_IN connaît une pluie d'étoiles filantes. */
    private static final int SHOWER_ONE_IN = 10;
    /** Opacité des nuages (1 = normale, 0 = masqués), lue par les mixins de nuages. */
    private static volatile float cloudFactor = 1.0F;

    /** Opacité à appliquer aux nuages : 1 le jour ou sous la pluie, 0 en pleine nuit claire. */
    public static float cloudFactor() {
        return cloudFactor;
    }

    /**
     * Teinte à appliquer à la lune de base pendant une éclipse ({r, g, b}), ou null s'il n'y a pas d'éclipse.
     * Appelé par {@link fr.evan.starrysky.mixin.LevelRendererMixin} juste avant le dessin de la lune.
     */
    @Nullable
    public static float[] moonTint() {
        float e = eclipse;
        if (e <= 0.0F) {
            return null;
        }
        LunarEclipse.moonTint(e, MOON_TINT);
        return MOON_TINT;
    }

    /** Dernière nuit pour laquelle un événement (éclipse, comète, pluie d'étoiles filantes) a été annoncé. */
    private static long announcedNight = Long.MIN_VALUE;

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
            COMET.clear();
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
            cloudFactor = 1.0F;
            eclipse = 0.0F;
            return;
        }

        // Maillages : construits une fois (dès la première image dans le monde, pour ne pas provoquer de
        // à-coup à la tombée de la nuit), puis reconstruits seulement si un paramètre qui les concerne change.
        boolean milkyWay = StarrySkyConfig.MILKY_WAY.get();
        STAR_FIELD.rebuildIfNeeded(StarrySkyConfig.STAR_COUNT.get(), StarrySkyConfig.COLORED_STARS.get(), milkyWay);
        if (milkyWay) {
            MILKY_WAY.buildIfNeeded();
        }
        boolean planets = StarrySkyConfig.PLANETS.get();
        if (planets) {
            PLANETS.update(level.getDayTime(), StarrySkyConfig.COLORED_STARS.get());
        }
        boolean comets = StarrySkyConfig.COMETS.get();
        if (comets) {
            COMET.update(level.getDayTime());
        } else {
            COMET.clear();
        }
        boolean moonHalo = StarrySkyConfig.MOON_HALO.get();
        if (moonHalo) {
            MOON_HALO.buildIfNeeded();
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float visibility = starVisibility(level, partialTick);
        eclipse = StarrySkyConfig.ECLIPSES.get()
                ? LunarEclipse.factor(level.getDayTime(), partialTick, level.getMoonPhase()) : 0.0F;
        // Nuages : ils s'effacent à mesure que la nuit tombe (et reviennent à l'aube), mais restent quand il pleut.
        cloudFactor = StarrySkyConfig.HIDE_CLOUDS_AT_NIGHT.get() ? 1.0F - visibility : 1.0F;
        // Une nuit va du coucher du soleil (12000) au lever (24000) : même numéro pendant toute la nuit.
        long night = Math.floorDiv(level.getDayTime(), 24000L);
        int nightHash = StarCatalog.mix((int) night * 0x2C1B3C6D + 0x297A2D39);

        // Horloges : le scintillement tourne en temps réel ; les étoiles filantes s'arrêtent quand le jeu est en pause.
        boolean twinkle = StarrySkyConfig.TWINKLE.get();
        if (twinkle) {
            float speed = StarrySkyConfig.TWINKLE_SPEED.get().floatValue();
            twinkleClock = (twinkleClock + deltaSeconds * speed) % StarField.TWINKLE_PERIOD;
        }
        // Pluie d'étoiles filantes : environ une nuit sur dix, six fois plus d'étoiles filantes (au moins 15 par minute).
        boolean shootingStars = StarrySkyConfig.SHOOTING_STARS.get();
        int frequency = StarrySkyConfig.SHOOTING_STAR_FREQUENCY.get();
        boolean shower = shootingStars && frequency > 0 && StarrySkyConfig.METEOR_SHOWERS.get()
                && Integer.remainderUnsigned(nightHash, SHOWER_ONE_IN) == 0;
        if (shower) {
            SHOOTING_STARS.update(minecraft.isPaused() ? 0.0F : deltaSeconds, visibility, true,
                    Math.max(15.0F, frequency * 6.0F), 6);
        } else {
            SHOOTING_STARS.update(minecraft.isPaused() ? 0.0F : deltaSeconds, visibility, shootingStars, frequency);
        }

        // Aurores : uniquement dans les biomes froids, avec un fondu quand on entre dans le biome ou qu'on en sort.
        boolean auroras = StarrySkyConfig.AURORAS.get() && StarShaders.aurora() != null;
        updateAuroraFactor(level, event.getCamera(), auroras, deltaSeconds);
        if (auroras && auroraFactor > 0.0F) {
            AURORA.buildIfNeeded();
            auroraClock = (auroraClock + deltaSeconds) % AuroraBorealis.TIME_PERIOD;
        }

        // Longue-vue : n'est utile que si le ciel est assez sombre.
        boolean skyVisible = isSkyVisible(minecraft, level, event.getCamera());

        // Annonce au crépuscule (une seule par nuit) : éclipse de lune, puis comète, puis pluie d'étoiles filantes.
        boolean eclipseNight = StarrySkyConfig.ECLIPSES.get() && LunarEclipse.isEclipseNight(level.getDayTime(), level.getMoonPhase());
        if (visibility > 0.3F && announcedNight != night) {
            announcedNight = night;
            Component announce = null;
            if (eclipseNight) {
                announce = Component.translatable("starrysky.eclipse.announce");
            } else if (comets && !Comet.catalog().isEmpty()) {
                announce = Component.translatable("starrysky.comet.announce", Comet.catalog().get(0).name());
            } else if (shower) {
                announce = Component.translatable("starrysky.shower.announce");
            }
            if (announce != null) {
                minecraft.gui.setOverlayMessage(announce, false);
            }
        }
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
        // La lune éclaire le ciel : à la pleine lune, les étoiles pâlissent un peu et la Voie lactée davantage.
        // Pendant une éclipse, la lune s'éteint presque : le ciel redevient bien noir.
        float moonBrightness = level.getMoonBrightness() * (1.0F - 0.85F * eclipse);
        float starIntensity = intensity * (1.0F - 0.25F * moonBrightness);

        // Même rotation que le soleil, la lune et les étoiles de base (voir LevelRenderer#renderSky) :
        // -90° autour de Y, puis l'heure du jour autour de X.
        SKY_ROTATION.rotationY((float) (-Math.PI / 2.0))
                .rotateX(level.getTimeOfDay(partialTick) * (float) (Math.PI * 2.0));
        Matrix4f cameraView = event.getModelViewMatrix();
        STAR_VIEW.set(cameraView).mul(SKY_ROTATION);
        Matrix4f projection = event.getProjectionMatrix();

        if (telescope) {
            TelescopeTargeting.update(SKY_ROTATION, STAR_VIEW, projection, event.getCamera().getLookVector(), planets, comets);
        }

        // Succès liés au ciel : vérifiés une fois par seconde seulement (la lecture du carnet n'est pas gratuite).
        eventCheckTimer -= deltaSeconds;
        if (eventCheckTimer <= 0.0F) {
            eventCheckTimer = EVENT_CHECK_INTERVAL;
            checkSkyEvents(event.getCamera(), visibility, shower, auroras);
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
            float glow = intensity * visibility * MilkyWay.GLOW_PEAK * (1.0F - 0.5F * moonBrightness);
            if (glow >= MIN_GLOW) {
                RenderSystem.setShaderColor(glow, glow, glow, 1.0F);
                milkyWayShader.safeGetUniform("SkyMat").set(SKY_ROTATION);
                MILKY_WAY.draw(STAR_VIEW, projection, milkyWayShader);
            }
        }

        // 1 bis) Comète : chevelure et queues (même shader que la Voie lactée).
        if (comets && COMET.envelope() > 0.0F) {
            float glow = intensity * visibility * Comet.GLOW_PEAK * COMET.envelope() * (1.0F - 0.3F * moonBrightness);
            if (glow >= MIN_GLOW) {
                RenderSystem.setShaderColor(glow, glow, glow, 1.0F);
                milkyWayShader.safeGetUniform("SkyMat").set(SKY_ROTATION);
                COMET.drawGlow(STAR_VIEW, projection, milkyWayShader);
            }
        }

        // 1 bis) Halo de lune (même shader que la Voie lactée), plus fort quand la lune est pleine.
        // Pendant une éclipse, il faiblit et prend la teinte cuivrée de la lune.
        if (moonHalo) {
            float halo = MOON_HALO_PEAK * level.getMoonBrightness() * visibility * brightness * (1.0F - 0.6F * eclipse);
            if (halo >= MIN_GLOW) {
                LunarEclipse.moonTint(eclipse, MOON_TINT);
                RenderSystem.setShaderColor(halo * MOON_TINT[0], halo * MOON_TINT[1], halo * MOON_TINT[2], 1.0F);
                milkyWayShader.safeGetUniform("SkyMat").set(SKY_ROTATION);
                MOON_HALO.draw(STAR_VIEW, projection, milkyWayShader);
            }
        }

        // 1 bis) Aurores boréales : dans le repère du monde (vers le nord), sans la rotation du ciel.
        ShaderInstance auroraShader = StarShaders.aurora();
        if (auroras && auroraShader != null && auroraFactor > 0.0F) {
            // Elles demandent un ciel bien noir : visibilité au carré, comme la Voie lactée.
            // Chaque nuit est différente : faible, normale ou intense, et parfois un orage (rouge et rose).
            int roll = Integer.remainderUnsigned(nightHash >>> 8, 100);
            float nightStrength = roll < 20 ? 0.35F : roll < 70 ? 0.8F : roll < 92 ? 1.0F : 1.25F;
            float storm = roll >= 92 ? 1.0F : 0.0F;
            float strength = AURORA_PEAK * nightStrength * auroraFactor * visibility * visibility;
            if (strength >= MIN_GLOW) {
                RenderSystem.setShaderColor(strength, strength, strength, 1.0F);
                auroraShader.safeGetUniform("AuroraTime").set(auroraClock);
                auroraShader.safeGetUniform("AuroraStorm").set(storm);
                AURORA.draw(cameraView, projection, auroraShader);
            }
        }

        // 2) Étoiles.
        Window window = minecraft.getWindow();
        RenderSystem.setShaderColor(starIntensity, starIntensity, starIntensity, 1.0F);
        starShader.safeGetUniform("SkyMat").set(SKY_ROTATION);
        starShader.safeGetUniform("ScreenSize").set((float) window.getWidth(), (float) window.getHeight());
        starShader.safeGetUniform("StarSize").set(StarrySkyConfig.STAR_SIZE.get().floatValue());
        starShader.safeGetUniform("StarTime").set(twinkleClock);
        starShader.safeGetUniform("TwinkleAmount").set(twinkle ? TWINKLE_STRENGTH : 0.0F);
        STAR_FIELD.draw(STAR_VIEW, projection, starShader);

        // 2 bis) Planètes : même shader, mais elles ne scintillent pas (et la lune ne les fait pas pâlir).
        if (planets) {
            RenderSystem.setShaderColor(intensity, intensity, intensity, 1.0F);
            starShader.safeGetUniform("TwinkleAmount").set(0.0F);
            PLANETS.draw(STAR_VIEW, projection, starShader);
        }
        // 2 ter) Noyau de la comète : même shader, sans scintillement non plus.
        if (comets && COMET.envelope() > 0.0F) {
            RenderSystem.setShaderColor(intensity, intensity, intensity, 1.0F);
            starShader.safeGetUniform("TwinkleAmount").set(0.0F);
            COMET.drawNucleus(STAR_VIEW, projection, starShader);
        }

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
     * Débloque les succès d'événements du ciel : pluie d'étoiles filantes, aurores bien visibles, et éclipse totale
     * (en regardant la lune). Appelé une fois par seconde, seulement quand le ciel est visible.
     */
    private static void checkSkyEvents(Camera camera, float visibility, boolean shower, boolean auroras) {
        if (shower && visibility > 0.5F) {
            ObservationJournal.unlock(Achievement.SHOWER);
        }
        if (auroras && auroraFactor * visibility * visibility > 0.5F) {
            ObservationJournal.unlock(Achievement.AURORA);
        }
        if (eclipse >= 0.99F) {
            // Direction de la lune dans le monde : la rotation du ciel appliquée à (0, -1, 0).
            float mx = -SKY_ROTATION.m10();
            float my = -SKY_ROTATION.m11();
            float mz = -SKY_ROTATION.m12();
            org.joml.Vector3f look = camera.getLookVector();
            if (my > 0.0F && mx * look.x + my * look.y + mz * look.z > ECLIPSE_LOOK_COS) {
                ObservationJournal.unlock(Achievement.ECLIPSE);
            }
        }
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
