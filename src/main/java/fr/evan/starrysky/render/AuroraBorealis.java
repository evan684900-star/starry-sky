package fr.evan.starrysky.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

/**
 * Aurores boréales : quelques rideaux de lumière suspendus haut dans le ciel, vers le nord.
 * <p>
 * Ce sont des phénomènes atmosphériques : ils sont placés dans le repère du monde et ne tournent pas avec les étoiles.
 * Le maillage (3 rideaux × 96 segments, soit 288 quads) est construit une seule fois ; l'ondulation, les rayons et
 * les vagues de lumière sont animés par le shader {@code aurora} à partir d'une simple horloge.
 */
public final class AuroraBorealis {
    /** Période de l'horloge des aurores, en secondes (l'animation saute très légèrement une fois par heure). */
    public static final float TIME_PERIOD = 3600.0F;

    private static final int SEGMENTS = 96;
    private static final float RADIUS = StarField.RADIUS;

    // Rideaux : azimut du centre (radians, 0 = nord), étendue en azimut, intensité.
    private static final float[][] CURTAINS = {
            {-0.55F, 1.90F, 1.00F},
            {0.15F, 2.20F, 0.75F},
            {0.70F, 1.60F, 0.55F}};

    @Nullable
    private VertexBuffer buffer;

    public void buildIfNeeded() {
        if (buffer != null) {
            return;
        }
        VertexFormat format = DefaultVertexFormat.POSITION_TEX_COLOR;
        int quads = CURTAINS.length * SEGMENTS;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(quads * 4 * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, format);
            for (int k = 0; k < CURTAINS.length; k++) {
                for (int i = 0; i < SEGMENTS; i++) {
                    float u0 = i / (float) SEGMENTS;
                    float u1 = (i + 1) / (float) SEGMENTS;
                    vertex(builder, k, u0, false);
                    vertex(builder, k, u1, false);
                    vertex(builder, k, u1, true);
                    vertex(builder, k, u0, true);
                }
            }
            buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(builder.buildOrThrow());
            VertexBuffer.unbind();
        }
    }

    public void draw(Matrix4f modelView, Matrix4f projection, ShaderInstance shader) {
        if (buffer == null) {
            return;
        }
        buffer.bind();
        buffer.drawWithShader(modelView, projection, shader);
        VertexBuffer.unbind();
    }

    /** Un sommet du rideau k, à la position u (0..1) le long du rideau, en bas ou en haut. */
    private static void vertex(BufferBuilder builder, int k, float u, boolean top) {
        float[] c = CURTAINS[k];
        double azimuth = c[0] + (u - 0.5) * c[1];
        // Bord inférieur ondulé (entre ~10° et ~23° au-dessus de l'horizon) et hauteur variable du rideau.
        double base = Math.toRadians(14.0 + 6.0 * Math.sin(u * Math.PI * 1.3 + k * 2.0) + 3.0 * Math.sin(u * 9.0 + k));
        double height = Math.toRadians(26.0 + 10.0 * Math.sin(u * Math.PI * 2.1 + k * 1.7));
        double elevation = top ? base + height : base;

        // Nord = -Z dans Minecraft.
        float x = (float) (Math.cos(elevation) * Math.sin(azimuth)) * RADIUS;
        float y = (float) Math.sin(elevation) * RADIUS;
        float z = (float) (-Math.cos(elevation) * Math.cos(azimuth)) * RADIUS;
        float seed = 0.1F + k * 0.3F;
        builder.addVertex(x, y, z)
                .setUv(u, top ? 1.0F : 0.0F)
                .setColor(seed, 0.0F, 0.0F, c[2]);
    }
}
