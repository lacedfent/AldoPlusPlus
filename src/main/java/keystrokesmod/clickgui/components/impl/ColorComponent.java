package keystrokesmod.clickgui.components.impl;

import keystrokesmod.Raven;
import keystrokesmod.clickgui.components.Component;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Timer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.nio.IntBuffer;

public class ColorComponent extends Component {
    public ColorSetting colorSetting;
    private ModuleComponent moduleComponent;
    public float o;
    public float x;
    private float y;
    public float xOffset;
    public boolean expanded;
    private int dragMode;
    private float cachedHue;
    private float cachedSat;
    private float cachedBri;

    private Timer smoothTimer;
    private float animationProgress;
    private float animationStartProgress;
    private float animationTargetProgress;
    private static final float ANIMATION_DURATION = 250f;

    private static final float LABEL_HEIGHT = 12f;
    private static final float SQUARE_SIZE = 50f;
    private static final float HUE_BAR_WIDTH = 10f;
    private static final float HUE_GAP = 4f;
    private static final float SQUARE_TOP_PAD = 2f;
    private static final float ALPHA_BAR_HEIGHT = 8f;
    private static final float ALPHA_TOP_PAD = 4f;
    private static final float BOTTOM_PAD = 2f;
    private static final int HUE_STEPS = 20;
    private static final float PREVIEW_BOX_SIZE = 5f;
    private static final IntBuffer SCISSOR_BUF = BufferUtils.createIntBuffer(16);

    public ColorComponent(ColorSetting colorSetting, ModuleComponent moduleComponent, float o) {
        this.colorSetting = colorSetting;
        this.moduleComponent = moduleComponent;
        this.o = o;
        this.animationProgress = 0f;
        this.animationStartProgress = 0f;
        this.animationTargetProgress = 0f;
    }

    public float getExpandedHeight() {
        float h = LABEL_HEIGHT + SQUARE_TOP_PAD + SQUARE_SIZE + BOTTOM_PAD;
        if (colorSetting.hasAlpha()) {
            h += ALPHA_TOP_PAD + ALPHA_BAR_HEIGHT;
        }
        return h;
    }

    public float getAnimationProgress() {
        if (smoothTimer != null) {
            if (System.currentTimeMillis() - smoothTimer.last >= ANIMATION_DURATION + 30) {
                smoothTimer = null;
                animationProgress = animationTargetProgress;
                animationStartProgress = animationTargetProgress;
            } else {
                animationProgress = smoothTimer.getValueFloat(animationStartProgress, animationTargetProgress, 1);
                if (animationProgress == animationTargetProgress) {
                    smoothTimer = null;
                    animationStartProgress = animationTargetProgress;
                }
            }
        }
        return animationProgress;
    }

    @Override
    public void render() {
        float cx = moduleComponent.categoryComponent.getX();
        float cy = moduleComponent.categoryComponent.getY();
        float cw = moduleComponent.categoryComponent.getWidth();

        float boxX = cx + 4 + (xOffset / 2);
        float boxY = cy + o + 3f;
        RenderUtils.drawRect(boxX - 0.5, boxY - 0.5,
                boxX + PREVIEW_BOX_SIZE + 0.5, boxY + PREVIEW_BOX_SIZE + 0.5, 0xFF3C3C46);
        RenderUtils.drawRect(boxX, boxY,
                boxX + PREVIEW_BOX_SIZE, boxY + PREVIEW_BOX_SIZE,
                colorSetting.getColor() | 0xFF000000);

        GL11.glPushMatrix();
        GL11.glScaled(0.5, 0.5, 0.5);
        float textOffset = Minecraft.getMinecraft().fontRendererObj.getStringWidth("[+]  ");
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(
                colorSetting.getName(),
                (cx + 4) * 2 + xOffset + textOffset,
                (cy + o + 4) * 2,
                -1
        );
        GL11.glPopMatrix();

        float progress = getAnimationProgress();
        if (progress <= 0f) return;

        float scrollOffset = moduleComponent.categoryComponent.moduleY - cy;
        float contentTopScreen = cy + o + LABEL_HEIGHT + scrollOffset;
        float revealH = (getExpandedHeight() - LABEL_HEIGHT) * progress;

        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        int sf = sr.getScaleFactor();
        double screenH = sr.getScaledHeight();

        int newLeft = (int) Math.floor(cx * sf);
        int newRight = (int) Math.ceil((cx + cw) * sf);
        int newW = Math.max(0, newRight - newLeft);
        int newGlBottom = (int) Math.floor((screenH - (contentTopScreen + revealH)) * sf);
        int newGlTop = (int) Math.ceil((screenH - contentTopScreen) * sf);
        int newH = Math.max(0, newGlTop - newGlBottom);

        SCISSOR_BUF.clear();
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, SCISSOR_BUF);
        int px = SCISSOR_BUF.get(0), py = SCISSOR_BUF.get(1);
        int pw = SCISSOR_BUF.get(2), ph = SCISSOR_BUF.get(3);

        int ix = Math.max(px, newLeft);
        int iy = Math.max(py, newGlBottom);
        int iw = Math.max(0, Math.min(px + pw, newLeft + newW) - ix);
        int ih = Math.max(0, Math.min(py + ph, newGlBottom + newH) - iy);
        GL11.glScissor(ix, iy, iw, ih);

        renderPickerContent(cx, cy);

        GL11.glScissor(px, py, pw, ph);
    }

    private void renderPickerContent(float cx, float cy) {
        float areaLeft = cx + 4 + (xOffset / 2);
        float sqTop = cy + o + LABEL_HEIGHT + SQUARE_TOP_PAD;
        float sqRight = areaLeft + SQUARE_SIZE;
        float sqBottom = sqTop + SQUARE_SIZE;

        float hue = (dragMode != 0) ? cachedHue / 360f : colorSetting.getHue() / 360f;
        float sat = (dragMode != 0) ? cachedSat : colorSetting.getSaturation();
        float bri = (dragMode != 0) ? cachedBri : colorSetting.getBrightness();

        int hueRGB = Color.HSBtoRGB(hue, 1f, 1f) | 0xFF000000;
        RenderUtils.drawRect(areaLeft, sqTop, sqRight, sqBottom, hueRGB);
        RenderUtils.drawHorizontalGradientRect(areaLeft, sqTop, sqRight, sqBottom,
                0xFFFFFFFF, 0x00FFFFFF);
        RenderUtils.drawVerticalGradientRect(areaLeft, sqTop, sqRight, sqBottom,
                0x00000000, 0xFF000000);

        float indX = areaLeft + sat * SQUARE_SIZE;
        float indY = sqTop + (1f - bri) * SQUARE_SIZE;
        RenderUtils.drawRect(indX - 2, indY, indX + 3, indY + 1, 0xFFFFFFFF);
        RenderUtils.drawRect(indX, indY - 2, indX + 1, indY + 3, 0xFFFFFFFF);

        RenderUtils.drawOutline(areaLeft - 1, sqTop - 1, sqRight + 1, sqBottom + 1,
                1f, 0xFF3C3C46);

        float hueLeft = sqRight + HUE_GAP;
        float hueRight = hueLeft + HUE_BAR_WIDTH;
        float stepH = SQUARE_SIZE / HUE_STEPS;
        for (int i = 0; i < HUE_STEPS; i++) {
            float h1 = (float) i / HUE_STEPS;
            float h2 = (float) (i + 1) / HUE_STEPS;
            int c1 = Color.HSBtoRGB(h1, 1f, 1f) | 0xFF000000;
            int c2 = Color.HSBtoRGB(h2, 1f, 1f) | 0xFF000000;
            RenderUtils.drawVerticalGradientRect(hueLeft, sqTop + i * stepH,
                    hueRight, sqTop + (i + 1) * stepH, c1, c2);
        }

        float hueIndY = sqTop + Math.max(0, Math.min(1, hue)) * SQUARE_SIZE;
        RenderUtils.drawRect(hueLeft - 1, hueIndY - 1,
                hueRight + 1, hueIndY + 2, 0xFFFFFFFF);

        RenderUtils.drawOutline(hueLeft - 1, sqTop - 1, hueRight + 1, sqBottom + 1,
                1f, 0xFF3C3C46);

        if (colorSetting.hasAlpha()) {
            float alphaTop = sqBottom + ALPHA_TOP_PAD;
            float alphaBottom = alphaTop + ALPHA_BAR_HEIGHT;
            float alphaRight = hueRight;

            int checkSize = 4;
            for (float ax = areaLeft; ax < alphaRight; ax += checkSize) {
                for (float ay = alphaTop; ay < alphaBottom; ay += checkSize) {
                    int col = ((int) ((ax - areaLeft) / checkSize)
                            + (int) ((ay - alphaTop) / checkSize)) % 2 == 0
                            ? 0xFF666666 : 0xFF999999;
                    RenderUtils.drawRect(ax, ay,
                            Math.min(ax + checkSize, alphaRight),
                            Math.min(ay + checkSize, alphaBottom), col);
                }
            }

            int rgb = colorSetting.getRGB();
            RenderUtils.drawHorizontalGradientRect(areaLeft, alphaTop, alphaRight, alphaBottom,
                    rgb & 0x00FFFFFF, rgb | 0xFF000000);

            float alphaFrac = colorSetting.getAlpha() / 255f;
            float alphaIndX = areaLeft + alphaFrac * (alphaRight - areaLeft);
            RenderUtils.drawRect(alphaIndX - 1, alphaTop - 1,
                    alphaIndX + 2, alphaBottom + 1, 0xFFFFFFFF);

            RenderUtils.drawOutline(areaLeft - 1, alphaTop - 1, alphaRight + 1, alphaBottom + 1,
                    1f, 0xFF3C3C46);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        this.y = moduleComponent.categoryComponent.getModuleY() + this.o;
        this.x = moduleComponent.categoryComponent.getX();

        if (dragMode == 0 || getAnimationProgress() < 1f) return;

        float areaLeft = this.x + 4 + (xOffset / 2);
        float sqTop = this.y + LABEL_HEIGHT + SQUARE_TOP_PAD;
        float sqRight = areaLeft + SQUARE_SIZE;
        float sqBottom = sqTop + SQUARE_SIZE;
        float hueLeft = sqRight + HUE_GAP;
        float hueRight = hueLeft + HUE_BAR_WIDTH;

        if (dragMode == 1) {
            cachedSat = Math.max(0, Math.min(1, (mouseX - areaLeft) / SQUARE_SIZE));
            cachedBri = Math.max(0, Math.min(1, 1f - (mouseY - sqTop) / SQUARE_SIZE));
            colorSetting.setFromHSB(cachedHue, cachedSat, cachedBri);
            markUnsaved();
        } else if (dragMode == 2) {
            cachedHue = Math.max(0, Math.min(360, (mouseY - sqTop) / SQUARE_SIZE * 360f));
            colorSetting.setFromHSB(cachedHue, cachedSat, cachedBri);
            markUnsaved();
        } else if (dragMode == 3 && colorSetting.hasAlpha()) {
            float alphaW = hueRight - areaLeft;
            float a = Math.max(0, Math.min(1, (mouseX - areaLeft) / alphaW));
            colorSetting.setAlpha((int) (a * 255));
            markUnsaved();
        }
    }

    @Override
    public boolean onClick(int mouseX, int mouseY, int button) {
        if (button != 0 || !moduleComponent.isOpened || !moduleComponent.isVisible(this)) {
            return false;
        }

        float cw = moduleComponent.categoryComponent.getWidth();

        if (mouseX > this.x && mouseX < this.x + cw
                && mouseY > this.y && mouseY < this.y + LABEL_HEIGHT) {
            float currentProgress = getAnimationProgress();
            this.animationStartProgress = currentProgress;
            this.expanded = !this.expanded;
            this.animationTargetProgress = this.expanded ? 1f : 0f;
            (this.smoothTimer = new Timer(ANIMATION_DURATION)).start();
            moduleComponent.updateSettingPositions();
            return true;
        }

        if (getAnimationProgress() < 1f) return false;

        float areaLeft = this.x + 4 + (xOffset / 2);
        float sqTop = this.y + LABEL_HEIGHT + SQUARE_TOP_PAD;
        float sqRight = areaLeft + SQUARE_SIZE;
        float sqBottom = sqTop + SQUARE_SIZE;
        float hueLeft = sqRight + HUE_GAP;
        float hueRight = hueLeft + HUE_BAR_WIDTH;

        if (mouseX >= areaLeft && mouseX <= sqRight
                && mouseY >= sqTop && mouseY <= sqBottom) {
            cacheHSB();
            dragMode = 1;
            return false;
        }

        if (mouseX >= hueLeft - 2 && mouseX <= hueRight + 2
                && mouseY >= sqTop && mouseY <= sqBottom) {
            cacheHSB();
            dragMode = 2;
            return false;
        }

        if (colorSetting.hasAlpha()) {
            float alphaTop = sqBottom + ALPHA_TOP_PAD;
            float alphaBottom = alphaTop + ALPHA_BAR_HEIGHT;
            if (mouseX >= areaLeft && mouseX <= hueRight
                    && mouseY >= alphaTop - 2 && mouseY <= alphaBottom + 2) {
                cacheHSB();
                dragMode = 3;
                return false;
            }
        }

        return false;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        dragMode = 0;
    }

    @Override
    public void onGuiClosed() {
        dragMode = 0;
        smoothTimer = null;
        animationProgress = expanded ? 1f : 0f;
        animationStartProgress = animationProgress;
        animationTargetProgress = animationProgress;
    }

    @Override
    public void updateHeight(float n) {
        this.o = n;
    }

    @Override
    public float getOffset() {
        return this.o;
    }

    @Override
    public boolean isBaseVisible() {
        return colorSetting.visible;
    }

    private void cacheHSB() {
        cachedHue = colorSetting.getHue();
        cachedSat = colorSetting.getSaturation();
        cachedBri = colorSetting.getBrightness();
    }

    private void markUnsaved() {
        if (Raven.currentProfile != null) {
            Raven.currentProfile.getModule().saved = false;
        }
    }
}
