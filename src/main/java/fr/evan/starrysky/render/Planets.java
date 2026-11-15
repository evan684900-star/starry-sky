package fr.evan.starrysky.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.evan.starrysky.telescope.CatalogStar;
import fr.evan.starrysky.telescope.StarCatalog;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

/**
 * Planètes : Vénus, Mars, Jupiter et Saturne.
 * <p>
 * Elles suivent le même grand cercle que le soleil et la lune (le plan x = 0 du repère du ciel), et se déplacent
 * lentement d'une nuit à l'autre par rapport aux étoiles : Vénus reste toujours près du soleil (on la voit au
 * crépuscule ou à l'aube), les autres font le tour du ciel en quelques dizaines à quelques centaines de jours.
 * <p>
 * Elles ne scintillent pas (comme les vraies planètes) et sont dessinées avec le shader des étoiles. Leurs 4 sprites
 * sont recalculés environ une fois par minute de jeu : le coût est nul.
 */
public final class Planets {
    /** Une planète : nom, type, mouvement dans le ciel et caractéristiques. */
    private record Def(String name, String type, float baseLongitude, float degreesPerDay, float inclination,
                       float magnitude, float distanceAu, int temperatureK, int color, float size) {
    }

    // Longitude (degrés) sur le grand cercle du soleil : 90 = soleil, -90 = lune (minuit).
    private static final Def VENUS = new Def("Venus", "terrestrial", 0.0F, 0.0F, 2.0F, -4.2F, 0.7F, 737, 0xFFF6E0, 1.25F);
    private static final Def[] OTHERS = {
            new Def("Mars", "terrestrial", -60.0F, -0.55F, -1.5F, 0.4F, 1.5F, 210, 0xFF9A72, 0.95F),
            new Def("Jupiter", "gas_giant", -125.0F, 0.083F, 1.2F, -2.3F, 5.2F, 165, 0xFFEFD2, 1.15F),
            new Def("Saturn", "gas_giant", -95.0F, -0.034F, -2.2F, 0.6F, 9.5F, 134, 0xFBE2A8, 0.95F),
    };

    @Nullable
    private VertexBuffer buffer;
    private long builtStep = Long.MIN_VALUE;
    private boolean builtColored;
    private static volatile List<CatalogStar> catalog = List.of();

    /** Les planètes, comme astres identifiables à la longue-vue (positions de la dernière mise à jour). */
    public static List<CatalogStar> catalog() {
        return catalog;
    }

    /** Met à jour positions et maillage si le temps du jeu a avancé d'au moins une minute (1200 ticks). */
    public void update(long dayTime, boolean colored) {
        long step = Math.floorDiv(dayTime, 1200L);
        if (buffer != null && step == builtStep && colored == builtColored) {
            return;
        }
        builtStep = step;
        builtColored = colored;
        double days = dayTime / 24000.0;

        VertexFormat format = DefaultVertexFormat.POSITION_TEX_COLOR;
        List<CatalogStar> list = new ArrayList<>();
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(8 * 4 * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, format);
            // Vénus oscille de part et d'autre du soleil (élongation maximale d'environ 45°).
            double venusLongitude = 90.0 + 44.0 * Math.sin(days * 2.0 * Math.PI / 58.4 + 1.0);
            add(builder, list, VENUS, venusLongitude, colored);
            for (Def def : OTHERS) {
                add(builder, list, def, def.baseLongitude() + def.degreesPerDay() * days, colored);
            }
            if (buffer == null) {
                buffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            }
            buffer.bind();
            buffer.upload(builder.buildOrThrow());
            VertexBuffer.unbind();
        }
        catalog = List.copyOf(list);
    }

    /** Dessine les planètes (shader des étoiles, sans scintillement : l'appelant met TwinkleAmount à 0). */
    public void draw(Matrix4f modelView, Matrix4f projection, ShaderInstance shader) {
        if (buffer == null) {
            return;
        }
        buffer.bind();
        buffer.drawWithShader(modelView, projection, shader);
        VertexBuffer.unbind();
    }

    private static void add(BufferBuilder builder, List<CatalogStar> list, Def def, double longitudeDeg, boolean colored) {
        double lon = Math.toRadians(longitudeDeg);
        double inc = Math.toRadians(def.inclination());
        float x = (float) Math.sin(inc);
        float y = (float) (Math.sin(lon) * Math.cos(inc));
        float z = (float) (Math.cos(lon) * Math.cos(inc));

        // Plus brillantes que la plupart des étoiles : intensité selon la magnitude, couleur propre à chaque planète.
        float intensity = Math.max(0.75F, Math.min(1.0F, (2.5F - def.magnitude()) / 4.0F));
        int rgb = colored ? def.color() : 0xFFFFFF;
        StarField.sprite(builder, x, y, z, def.size(), 0.0F,
                ((rgb >> 16) & 0xFF) / 255.0F * intensity,
                ((rgb >> 8) & 0xFF) / 255.0F * intensity,
                (rgb & 0xFF) / 255.0F * intensity, 0.5F);

        list.add(new CatalogStar(x, y, z, def.name(), true, StarCatalog.constellationAt(x, y, z), ' ', 0,
                def.magnitude(), 0, def.temperatureK(), def.color(), null, def.type(), def.distanceAu()));
    }
}
