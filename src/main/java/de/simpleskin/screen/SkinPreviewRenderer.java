package de.simpleskin.screen;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import de.simpleskin.data.SkinModel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Draws a flat front view of a skin PNG straight from its 64x64 texture atlas layout. */
public final class SkinPreviewRenderer {
    private static final RenderPipeline PIPELINE = RenderPipelines.GUI_TEXTURED;

    private SkinPreviewRenderer() {
    }

    public static void draw(GuiGraphicsExtractor graphics, Identifier texture, SkinModel model,
            int centerX, int top, int scale) {
        int arm = model == SkinModel.SLIM ? 3 : 4;
        int bodyX = centerX - 4 * scale;
        part(graphics, texture, bodyX, top, 8, 8, 8, 8, scale);
        part(graphics, texture, bodyX, top, 40, 8, 8, 8, scale);
        int bodyTop = top + 8 * scale;
        part(graphics, texture, bodyX, bodyTop, 20, 20, 8, 12, scale);
        part(graphics, texture, bodyX, bodyTop, 20, 36, 8, 12, scale);
        part(graphics, texture, bodyX - arm * scale, bodyTop, 44, 20, arm, 12, scale);
        part(graphics, texture, bodyX - arm * scale, bodyTop, 44, 36, arm, 12, scale);
        part(graphics, texture, bodyX + 8 * scale, bodyTop, 36, 52, arm, 12, scale);
        part(graphics, texture, bodyX + 8 * scale, bodyTop, 52, 52, arm, 12, scale);
        int legTop = bodyTop + 12 * scale;
        part(graphics, texture, bodyX, legTop, 4, 20, 4, 12, scale);
        part(graphics, texture, bodyX, legTop, 4, 36, 4, 12, scale);
        part(graphics, texture, centerX, legTop, 20, 52, 4, 12, scale);
        part(graphics, texture, centerX, legTop, 4, 52, 4, 12, scale);
    }

    public static void drawHead(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int size) {
        graphics.blit(PIPELINE, texture, x, y, 8.0f, 8.0f, size, size, 8, 8, 64, 64);
        graphics.blit(PIPELINE, texture, x, y, 40.0f, 8.0f, size, size, 8, 8, 64, 64);
    }

    private static void part(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int u, int v,
            int width, int height, int scale) {
        graphics.blit(PIPELINE, texture, x, y, (float) u, (float) v, width * scale, height * scale,
                width, height, 64, 64);
    }
}
