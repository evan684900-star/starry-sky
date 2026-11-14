package fr.evan.starrysky.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;

/**
 * Étoiles filantes occasionnelles.
 * <p>
 * Contrairement aux étoiles, elles ne tournent pas avec le ciel : ce sont des phénomènes atmosphériques,
 * placées dans le repère du monde, au-dessus de l'horizon. Chacune suit un arc de grand cercle sur la sphère
 * du ciel, avec une traînée qui s'affine vers l'arrière. Le shader {@code shooting_star} adoucit les bords
 * de la traînée et fait briller la tête.
 * <p>
 * Il n'y en a que quelques-unes à la fois (quelques dizaines de sommets), et rien n'est dessiné ni calculé quand
 * il n'y en a aucune : leur maillage est simplement reconstruit à chaque image où une étoile filante est visible.
 */
public final class ShootingStars {
    private static final float RADIUS = StarField.RADIUS;
    private static final int MAX_ACTIVE = 3;
    private static final int SEGMENTS = 16;
    /** En dessous de cette visibilité des étoiles (crépuscule, pluie), aucune étoile filante n'apparaît. */
    private static final float MIN_VISIBILITY = 0.3F;

    private final List<Meteor> meteors = new ArrayList<>();
    private final RandomSource random = RandomSource.create();
    @Nullable
    private VertexBuffer buffer;

    /** Supprime toutes les étoiles filantes en cours (changement de monde, option désactivée...). */
    public void clear() {
        meteors.clear();
    }

    /**
     * Fait avancer les étoiles filantes et en fait éventuellement apparaître une nouvelle.
     *
     * @param deltaSeconds temps écoulé depuis l'image précédente (0 si le jeu est en pause)
     * @param visibility   visibilité des étoiles, de 0 (jour/pluie) à 1 (pleine nuit claire)
     * @param perMinute    fréquence moyenne d'apparition, par minute
     */
    public void update(float deltaSeconds, float visibility, boolean enabled, int perMinute) {
        if (!enabled) {
            meteors.clear();
            return;
        }
        if (deltaSeconds <= 0.0F) {
            return;
        }

        for (int i = meteors.size() - 1; i >= 0; i--) {
            Meteor meteor = meteors.get(i);
            meteor.age += deltaSeconds;
            if (meteor.age >= meteor.duration) {
                meteors.remove(i);
            }
        }

        if (perMinute > 0 && visibility >= MIN_VISIBILITY && meteors.size() < MAX_ACTIVE) {
            // Processus de Poisson : probabilité d'au moins une apparition pendant deltaSeconds.
            double chance = 1.0 - Math.exp(-perMinute / 60.0 * deltaSeconds);
            if (random.nextDouble() < chance) {
                meteors.add(spawn());
            }
        }
    }

    /**
     * Dessine les étoiles filantes. L'état de rendu (blending, depth mask...) est préparé par l'appelant.
     *
     * @param modelView matrice de vue de la caméra (rotation seule, sans la rotation du ciel)
     * @param intensity luminosité globale (visibilité × luminosité de la config)
     */
    public void render(Matrix4f modelView, Matrix4f projection, ShaderInstance shader, float intensity) {
        if (meteors.isEmpty() || intensity <= 0.0F) {
            return;
        }

        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < meteors.size(); i++) {
            emit(builder, meteors.get(i));
        }
        MeshData mesh = builder.build();
        if (mesh == null) {
            return;
        }

        if (buffer == null) {
            buffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        }
        RenderSystem.setShaderColor(intensity, intensity, intensity, 1.0F);
        buffer.bind();
        buffer.upload(mesh);
        buffer.drawWithShader(modelView, projection, shader);
        VertexBuffer.unbind();
    }

    private Meteor spawn() {
        // Point de départ : entre 25° et 75° au-dessus de l'horizon, dans une direction quelconque.
        double azimuth = random.nextFloat() * Math.PI * 2.0;
        double elevation = Math.toRadians(25.0 + random.nextFloat() * 50.0);
        float ax = (float) (Math.cos(elevation) * Math.cos(azimuth));
        float ay = (float) Math.sin(elevation);
        float az = (float) (Math.cos(elevation) * Math.sin(azimuth));

        // Direction de déplacement : plutôt vers le bas, projetée sur le plan tangent au point de départ.
        float tx;
        float ty;
        float tz;
        float lengthSquared;
        do {
            double heading = random.nextFloat() * Math.PI * 2.0;
            float wx = (float) Math.cos(heading);
            float wy = -(0.4F + random.nextFloat() * 0.8F);
            float wz = (float) Math.sin(heading);
            float along = wx * ax + wy * ay + wz * az;
            tx = wx - ax * along;
            ty = wy - ay * along;
            tz = wz - az * along;
            lengthSquared = tx * tx + ty * ty + tz * tz;
        } while (lengthSquared < 1.0E-4F);
        float inverse = 1.0F / (float) Math.sqrt(lengthSquared);
        tx *= inverse;
        ty *= inverse;
        tz *= inverse;

        Meteor meteor = new Meteor();
        meteor.ax = ax;
        meteor.ay = ay;
        meteor.az = az;
        meteor.tx = tx;
        meteor.ty = ty;
        meteor.tz = tz;
        // Sur un grand cercle, la normale au plan de l'arc est constante : c'est la direction de la largeur.
        meteor.sx = ay * tz - az * ty;
        meteor.sy = az * tx - ax * tz;
        meteor.sz = ax * ty - ay * tx;
        meteor.duration = 0.6F + random.nextFloat() * 0.8F;                          // secondes
        meteor.arc = (float) Math.toRadians(12.0 + random.nextFloat() * 22.0);       // distance parcourue
        meteor.trail = (float) Math.toRadians(5.0 + random.nextFloat() * 7.0);       // longueur de la traînée
        meteor.halfWidth = 0.22F + random.nextFloat() * 0.20F;                       // demi-largeur de la tête
        meteor.brightness = 0.65F + random.nextFloat() * 0.35F;
        return meteor;
    }

    /**
     * Ajoute au maillage la traînée d'une étoile filante : une bande de quads le long de l'arc.
     * UV.x = position en travers (-1 / +1), UV.y = position le long de la traînée (0 = queue, 1 = tête).
     */
    private static void emit(BufferBuilder builder, Meteor m) {
        float progress = m.age / m.duration;
        // Apparition rapide, disparition plus lente.
        float opacity = Math.min(1.0F, progress / 0.15F) * Math.min(1.0F, (1.0F - progress) / 0.4F) * m.brightness;
        if (opacity <= 0.0F) {
            return;
        }
        float head = m.arc * progress;
        float tail = Math.max(0.0F, head - m.trail);
        if (head - tail < 1.0E-4F) {
            return;
        }

        // Rangées 0..SEGMENTS : de la queue à la tête. Rangée SEGMENTS + 1 : petite pointe devant la tête.
        for (int i = 0; i <= SEGMENTS; i++) {
            float f0 = fraction(i);
            float f1 = fraction(i + 1);
            float angle0 = angle(m, tail, head, i);
            float angle1 = angle(m, tail, head, i + 1);
            float width0 = width(m, i);
            float width1 = width(m, i + 1);
            float opacity0 = i > SEGMENTS ? 0.0F : opacity;
            float opacity1 = i + 1 > SEGMENTS ? 0.0F : opacity;

            vertex(builder, m, angle0, width0, -1.0F, f0, opacity0);
            vertex(builder, m, angle0, width0, 1.0F, f0, opacity0);
            vertex(builder, m, angle1, width1, 1.0F, f1, opacity1);
            vertex(builder, m, angle1, width1, -1.0F, f1, opacity1);
        }
    }

    /** Position le long de la traînée : 0 = queue, 1 = tête (la pointe reste à 1). */
    private static float fraction(int row) {
        return Math.min(1.0F, row / (float) SEGMENTS);
    }

    private static float angle(Meteor m, float tail, float head, int row) {
        if (row > SEGMENTS) {
            return head + 1.5F * m.halfWidth / RADIUS;
        }
        return tail + (head - tail) * (row / (float) SEGMENTS);
    }

    private static float width(Meteor m, int row) {
        if (row > SEGMENTS) {
            return 0.0F;
        }
        return m.halfWidth * (0.25F + 0.75F * (row / (float) SEGMENTS));
    }

    private static void vertex(BufferBuilder builder, Meteor m, float angle, float width, float sideSign,
                               float along, float opacity) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float offset = width * sideSign;
        builder.addVertex(
                        (m.ax * cos + m.tx * sin) * RADIUS + m.sx * offset,
                        (m.ay * cos + m.ty * sin) * RADIUS + m.sy * offset,
                        (m.az * cos + m.tz * sin) * RADIUS + m.sz * offset)
                .setUv(sideSign, along)
                // Bleuté vers la queue, presque blanc vers la tête.
                .setColor(0.55F + 0.40F * along, 0.75F + 0.22F * along, 1.0F, opacity);
    }

    /** Une étoile filante en cours. (a = point de départ, t = direction de déplacement, s = direction de la largeur) */
    private static final class Meteor {
        float ax, ay, az;
        float tx, ty, tz;
        float sx, sy, sz;
        float duration;
        float arc;
        float trail;
        float halfWidth;
        float brightness;
        float age;
    }
}
