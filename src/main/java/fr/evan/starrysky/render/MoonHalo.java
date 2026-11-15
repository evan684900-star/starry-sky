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
 * Halo de lune : une lueur bleutée et douce autour de la lune, plus forte à la pleine lune.
 * <p>
 * Dans Minecraft, la lune est fixe dans le repère du ciel (direction (0, -1, 0), à l'opposé du soleil). Le halo est
 * donc un petit maillage statique de cercles concentriques, construit une fois, dont l'intensité est portée par la
 * couleur des sommets. Il est dessiné avec le shader de la Voie lactée (simple interpolation de couleurs).
 */
public final class MoonHalo {
    private static final int RINGS = 12;
    private static final int SEGMENTS = 40;
    /** Rayon du halo, en degrés. */
    private static final double RADIUS_DEG = 30.0;

    @Nullable
    private VertexBuffer buffer;

    public void buildIfNeeded() {
        if (buffer != null) {
            return;
        }
        VertexFormat format = DefaultVertexFormat.POSITION_COLOR;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(RINGS * SEGMENTS * 4 * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, format);
            for (int r = 0; r < RINGS; r++) {
                for (int s = 0; s < SEGMENTS; s++) {
                    vertex(builder, r, s);
                    vertex(builder, r, s + 1);
                    vertex(builder, r + 1, s + 1);
                    vertex(builder, r + 1, s);
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

    private static void vertex(BufferBuilder builder, int ring, int segment) {
        double radiusDeg = RADIUS_DEG * ring / RINGS;
        double r = Math.toRadians(radiusDeg);
        double phi = segment * Math.PI * 2.0 / SEGMENTS;
        // Autour de (0, -1, 0) : axes (1, 0, 0) et (0, 0, 1).
        float x = (float) (Math.sin(r) * Math.cos(phi));
        float y = (float) -Math.cos(r);
        float z = (float) (Math.sin(r) * Math.sin(phi));

        // Profil : forte près de la lune, longue décroissance, nulle au bord du maillage.
        double fade = 1.0 - smoothstep(RADIUS_DEG * 0.65, RADIUS_DEG, radiusDeg);
        double density = (0.75 * Math.exp(-radiusDeg / 5.0) + 0.25 * Math.exp(-radiusDeg / 14.0)) * fade;
        int alpha = (int) Math.round(Math.min(1.0, density) * 255.0);

        builder.addVertex(x * StarField.RADIUS, y * StarField.RADIUS, z * StarField.RADIUS)
                .setColor(200, 216, 255, alpha);
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = Math.min(1.0, Math.max(0.0, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }
}
