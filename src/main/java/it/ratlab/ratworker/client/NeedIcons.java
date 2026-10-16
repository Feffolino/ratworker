package it.ratlab.ratworker.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Classic HUD icons for a 0..20 need: 10 icons, full = 2 points, half = 1.
 * Food uses vanilla gui/icons.png; thirst uses Thirst Was Taken's icons (thirst is only shown when that mod is installed).
 */
public final class NeedIcons {
    public static final int ROW_WIDTH = 9 * 8 + 9;
    public static final int ICON = 9;

    private static final ResourceLocation VANILLA = ResourceLocation.withDefaultNamespace("textures/gui/icons.png");
    private static final ResourceLocation TWT = ResourceLocation.fromNamespaceAndPath("thirst", "textures/gui/thirst_icons.png");

    private NeedIcons() {}

    public enum Kind {
        HEART(256, 256, 16, 0, 52, 61),
        FOOD(256, 256, 16, 27, 52, 61),
        WATER(25, 9, 0, 0, 16, 8);

        final int texW, texH, bgU, v, fullU, halfU;

        Kind(int texW, int texH, int bgU, int v, int fullU, int halfU) {
            this.texW = texW;
            this.texH = texH;
            this.bgU = bgU;
            this.v = v;
            this.fullU = fullU;
            this.halfU = halfU;
        }

        ResourceLocation texture() {
            return this == WATER ? TWT : VANILLA;
        }
    }

    /** Health as one row of up to 10 hearts; above 20 max HP each heart stands for max/10. */
    public static void drawHealth(GuiGraphics g, int x, int y, float health, float max) {
        int hearts = Math.max(1, Math.min(10, (int) Math.ceil(max / 2f)));
        float value = max > 20 ? health * 20f / max : health;
        int level = (int) Math.ceil(value);
        for (int i = 0; i < hearts; i++) {
            int ix = x + i * 8, idx = i * 2 + 1;
            g.blit(VANILLA, ix, y, Kind.HEART.bgU, 0, ICON, ICON, 256, 256);
            if (idx < level) g.blit(VANILLA, ix, y, Kind.HEART.fullU, 0, ICON, ICON, 256, 256);
            else if (idx == level) g.blit(VANILLA, ix, y, Kind.HEART.halfU, 0, ICON, ICON, 256, 256);
        }
    }

    /** GUI row, left to right. */
    public static void draw(GuiGraphics g, Kind k, int x, int y, float value) {
        int level = (int) Math.ceil(value);
        ResourceLocation tex = k.texture();
        for (int i = 0; i < 10; i++) {
            int ix = x + i * 8, idx = i * 2 + 1;
            g.blit(tex, ix, y, k.bgU, k.v, ICON, ICON, k.texW, k.texH);
            if (idx < level) g.blit(tex, ix, y, k.fullU, k.v, ICON, ICON, k.texW, k.texH);
            else if (idx == level) g.blit(tex, ix, y, k.halfU, k.v, ICON, ICON, k.texW, k.texH);
        }
    }

    /** World-space row (already oriented and scaled like a name tag), centered on x = 0. */
    public static void drawInWorld(Matrix4f m, MultiBufferSource buffers, Kind k, float y, float value, int light) {
        int level = (int) Math.ceil(value);
        VertexConsumer vc = buffers.getBuffer(RenderType.text(k.texture()));
        float x0 = -ROW_WIDTH / 2f;
        for (int i = 0; i < 10; i++) {
            float ix = x0 + i * 8;
            int idx = i * 2 + 1;
            quad(vc, m, k, ix, y, k.bgU, light, 0f);
            if (idx < level) quad(vc, m, k, ix, y, k.fullU, light, 0f);
            else if (idx == level) quad(vc, m, k, ix, y, k.halfU, light, 0f);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Kind k, float x, float y, int u, int light, float z) {
        float u0 = u / (float) k.texW, u1 = (u + ICON) / (float) k.texW;
        float v0 = k.v / (float) k.texH, v1 = (k.v + ICON) / (float) k.texH;
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u0, v0).uv2(light).endVertex();
        vc.vertex(m, x, y + ICON, z).color(255, 255, 255, 255).uv(u0, v1).uv2(light).endVertex();
        vc.vertex(m, x + ICON, y + ICON, z).color(255, 255, 255, 255).uv(u1, v1).uv2(light).endVertex();
        vc.vertex(m, x + ICON, y, z).color(255, 255, 255, 255).uv(u1, v0).uv2(light).endVertex();
    }
}
