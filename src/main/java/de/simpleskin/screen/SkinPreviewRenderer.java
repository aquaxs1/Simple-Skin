package de.simpleskin.screen;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import de.simpleskin.data.SkinModel;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public final class SkinPreviewRenderer {
    private static final RenderPipeline PIPELINE = RenderPipelines.GUI_TEXTURED;

    private SkinPreviewRenderer() {
    }

    public static void draw(DrawContext context, Identifier texture, SkinModel model, int centerX, int top, int scale) {
        int arm = model == SkinModel.SLIM ? 3 : 4;
        int bodyX = centerX - 4 * scale;
        part(context, texture, centerX - 4 * scale, top, 8, 8, 8, 8, scale);
        part(context, texture, centerX - 4 * scale, top, 40, 8, 8, 8, scale);
        int bodyTop = top + 8 * scale;
        part(context, texture, bodyX, bodyTop, 20, 20, 8, 12, scale);
        part(context, texture, bodyX, bodyTop, 20, 36, 8, 12, scale);
        part(context, texture, bodyX - arm * scale, bodyTop, 44, 20, arm, 12, scale);
        part(context, texture, bodyX - arm * scale, bodyTop, 44, 36, arm, 12, scale);
        part(context, texture, bodyX + 8 * scale, bodyTop, 36, 52, arm, 12, scale);
        part(context, texture, bodyX + 8 * scale, bodyTop, 52, 52, arm, 12, scale);
        int legTop = bodyTop + 12 * scale;
        part(context, texture, bodyX, legTop, 4, 20, 4, 12, scale);
        part(context, texture, bodyX, legTop, 4, 36, 4, 12, scale);
        part(context, texture, centerX, legTop, 20, 52, 4, 12, scale);
        part(context, texture, centerX, legTop, 4, 52, 4, 12, scale);
    }

    public static void drawHead(DrawContext context, Identifier texture, int x, int y, int size) {
        context.drawTexture(PIPELINE, texture, x, y, 8, 8, size, size, 8, 8, 64, 64);
        context.drawTexture(PIPELINE, texture, x, y, 40, 8, size, size, 8, 8, 64, 64);
    }

    private static void part(DrawContext context, Identifier texture, int x, int y, int u, int v,
            int width, int height, int scale) {
        context.drawTexture(PIPELINE, texture, x, y, u, v, width * scale, height * scale,
                width, height, 64, 64);
    }
}
