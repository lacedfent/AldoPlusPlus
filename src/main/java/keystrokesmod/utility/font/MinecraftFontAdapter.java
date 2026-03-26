package keystrokesmod.utility.font;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;

public final class MinecraftFontAdapter implements RavenFontRenderer {
    private final FontRenderer fontRenderer;
    private final float scale;

    public MinecraftFontAdapter(FontRenderer fontRenderer) {
        this(fontRenderer, 1.0f);
    }

    public MinecraftFontAdapter(FontRenderer fontRenderer, float scale) {
        this.fontRenderer = fontRenderer;
        this.scale = Math.max(0.5f, Math.min(2.0f, scale));
    }

    @Override
    public int drawString(String text, float x, float y, int color, boolean shadow) {
        if (scale == 1.0f) {
            return fontRenderer.drawString(text, x, y, color, shadow);
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0f);
        GlStateManager.scale(scale, scale, 1.0f);
        int width = fontRenderer.drawString(text, 0.0f, 0.0f, color, shadow);
        GlStateManager.popMatrix();
        return Math.round(width * scale);
    }

    @Override
    public int getStringWidth(String text) {
        return Math.round(fontRenderer.getStringWidth(text) * scale);
    }

    @Override
    public int getFontHeight() {
        return Math.round(fontRenderer.FONT_HEIGHT * scale);
    }

    @Override
    public int getLineHeight() {
        return Math.round(fontRenderer.FONT_HEIGHT * scale);
    }

    @Override
    public int getTextTopOffset() {
        return 0;
    }

    @Override
    public int getTextBottomOffset() {
        return Math.max(1, Math.round((fontRenderer.FONT_HEIGHT - 1.0f) * scale));
    }

    public float getScale() {
        return scale;
    }
}
