package keystrokesmod.clickgui.components.impl;

import keystrokesmod.Raven;
import keystrokesmod.clickgui.components.Component;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.profile.ProfileModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class BindComponent extends Component {
    private static final ResourceLocation EYE_ICON = new ResourceLocation("keystrokesmod", "textures/gui/eye.png");
    private static final ResourceLocation EYE_OFF_ICON = new ResourceLocation("keystrokesmod", "textures/gui/eye_off.png");
    private static final int EYE_ICON_PADDING = 2;
    private static ResourceLocation processedEye;
    private static ResourceLocation processedEyeOff;

    public boolean isBinding;
    public ModuleComponent moduleComponent;
    public float o;
    public float x;
    private float y;
    public KeySetting keySetting;
    public float xOffset;

    public BindComponent(ModuleComponent moduleComponent, float o) {
        this.moduleComponent = moduleComponent;
        this.x = moduleComponent.categoryComponent.getX() + moduleComponent.categoryComponent.getWidth();
        this.y = moduleComponent.categoryComponent.getY() + moduleComponent.yPos;
        this.o = o;
    }

    public BindComponent(ModuleComponent moduleComponent, KeySetting keySetting, float o) {
        this.moduleComponent = moduleComponent;
        this.x = moduleComponent.categoryComponent.getX() + moduleComponent.categoryComponent.getWidth();
        this.y = moduleComponent.categoryComponent.getY() + moduleComponent.yPos;
        this.keySetting = keySetting;
        this.o = o;
    }

    public void updateHeight(float n) {
        this.o = n;
    }

    @Override public float getOffset() { return o; }
    @Override public boolean isBaseVisible() { return keySetting == null || keySetting.visible; }

    public void render() {
        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        if (keySetting == null) {
            this.drawString(!this.moduleComponent.mod.canBeEnabled() && this.moduleComponent.mod.script == null ? "Module cannot be bound." : this.isBinding ? "Press a key..." : "Current bind: '\u00a7e" + getKeyAsStr(false) + "\u00a7r'");
        }
        else {
            Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(this.isBinding ? "Press a key..." : this.keySetting.getName() + ": '\u00a7e" + getKeyAsStr(true) + "\u00a7r'", (float) ((this.moduleComponent.categoryComponent.getX() + 4) * 2) + xOffset, (float) ((this.moduleComponent.categoryComponent.getY() + this.o + (this.keySetting == null ? 3 : 4)) * 2), Theme.getGradient(Theme.descriptor[0], Theme.descriptor[1], 0));
        }
        GL11.glPopMatrix();

        if (keySetting == null && moduleComponent.mod.moduleCategory() != Module.category.profiles) {
            ensureProcessedTextures();
            int iconSize = getEyeIconSize();
            float iconX = getEyeIconX(iconSize);
            float textHeight = Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT * 0.5f;
            float iconY = getRenderTextY() + (textHeight - iconSize) / 2f;

            int themeColor = !moduleComponent.mod.hidden
                    ? Theme.getGradient(Theme.descriptor[0], Theme.descriptor[1], 0)
                    : Theme.getGradient(Theme.hiddenBind[0], Theme.hiddenBind[1], 0);
            Color c = new Color(themeColor, true);
            Minecraft.getMinecraft().getTextureManager().bindTexture(moduleComponent.mod.isHidden() ? processedEyeOff : processedEye);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager.color(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, c.getAlpha() / 255f);

            GL11.glPushMatrix();
            GL11.glTranslatef(iconX, iconY, 0);
            net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, iconSize, iconSize, iconSize, iconSize);
            GL11.glPopMatrix();

            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.disableBlend();
        }
    }

    public void drawScreen(int x, int y) {
        this.y = moduleComponent.categoryComponent.getModuleY() + o;
        this.x = moduleComponent.categoryComponent.getX();
    }

    public boolean onClick(int x, int y, int button) {
        if (!overSetting(x, y) || !moduleComponent.isOpened || !moduleComponent.isVisible(this)) return false;
        if (button == 0 && moduleComponent.mod.moduleCategory() != Module.category.profiles && overEyeIcon(x, y)) {
            moduleComponent.mod.setHidden(!moduleComponent.mod.isHidden());
            if (Raven.currentProfile != null) Raven.currentProfile.getModule().saved = false;
            return true;
        }
        if (moduleComponent.mod.canBeEnabled() && button == 0 && overBindText(x, y)) {
            isBinding = !isBinding;
            return true;
        }
        if (moduleComponent.mod.canBeEnabled() && button > 1 && isBinding) {
            if (keySetting != null) keySetting.setKey(button + 1000);
            else moduleComponent.mod.setBind(button + 1000);
            if (Raven.currentProfile != null) Raven.currentProfile.getModule().saved = false;
            isBinding = false;
            return true;
        }
        return false;
    }

    private boolean overEyeIcon(int x, int y) {
        int iconSize = getEyeIconSize();
        float iconX = getEyeIconX(iconSize);
        float iconY = getEyeIconY(iconSize);
        return x >= iconX && x < iconX + iconSize && y >= iconY && y < iconY + iconSize;
    }

    private float getBindTextX() {
        return moduleComponent.categoryComponent.getX() + 4f + (xOffset * 0.5f);
    }

    private float getBindTextY() {
        return moduleComponent.categoryComponent.getModuleY() + o + (keySetting == null ? 3f : 4f);
    }

    private float getRenderTextY() {
        return moduleComponent.categoryComponent.getY() + o + (keySetting == null ? 3f : 4f);
    }

    private String getBindDisplayString() {
        if (keySetting == null)
            return !moduleComponent.mod.canBeEnabled() && moduleComponent.mod.script == null ? "Module cannot be bound."
                    : isBinding ? "Press a key..." : "Current bind: '\u00a7e" + getKeyAsStr(false) + "\u00a7r'";
        return isBinding ? "Press a key..." : keySetting.getName() + ": '\u00a7e" + getKeyAsStr(true) + "\u00a7r'";
    }

    private boolean overBindText(int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getMinecraft();
        String text = getBindDisplayString();

        float left = getBindTextX();
        float top = getBindTextY();
        float width = mc.fontRendererObj.getStringWidth(text) * 0.5f;
        float height = mc.fontRendererObj.FONT_HEIGHT * 0.5f;

        return mouseX >= left && mouseX < left + width
                && mouseY >= top && mouseY < top + height;
    }

    private int getEyeIconSize() {
        int fontH = Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT;
        return Math.max(6, fontH - 1);
    }

    private float getEyeIconX(int iconSize) {
        return moduleComponent.categoryComponent.getX() + moduleComponent.categoryComponent.getWidth() - iconSize - EYE_ICON_PADDING;
    }

    private float getEyeIconY(int iconSize) {
        float textY = getBindTextY();
        float textHeight = Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT * 0.5f;
        return textY + (textHeight - iconSize) / 2f;
    }

    public void onScroll(int scroll) {
        if (!isBinding || scroll == 0) return;
        if (keySetting != null) keySetting.setKey(scroll > 0 ? 1069 : 1070);
        else moduleComponent.mod.setBind(scroll > 0 ? 1069 : 1070);
        if (Raven.currentProfile != null) Raven.currentProfile.getModule().saved = false;
        isBinding = false;
    }

    public void keyTyped(char t, int keybind) {
        if (!isBinding) return;
        if (keybind == Keyboard.KEY_0 || keybind == Keyboard.KEY_ESCAPE) {
            if (moduleComponent.mod instanceof Gui) moduleComponent.mod.setBind(54);
            else if (keySetting != null) keySetting.setKey(0);
            else moduleComponent.mod.setBind(0);
        } else {
            if (keySetting != null) keySetting.setKey(keybind);
            else moduleComponent.mod.setBind(keybind);
        }
        if (Raven.currentProfile != null) Raven.currentProfile.getModule().saved = false;
        isBinding = false;
    }

    public boolean overSetting(int mouseX, int mouseY) {
        float rowX = moduleComponent.categoryComponent.getX();
        float rowY = moduleComponent.categoryComponent.getModuleY() + o;
        float rowW = moduleComponent.categoryComponent.getWidth();
        return mouseX > rowX && mouseX < rowX + rowW && mouseY > rowY - 1 && mouseY < rowY + 12;
    }

    public String getKeyAsStr(boolean isKey) {
        int key = isKey ? keySetting.getKey() : moduleComponent.mod.getKeycode();
        return key >= 1000 ? ((key == 1069 || key == 1070) ? getScroll(key) : "M" + (key - 1000)) : Keyboard.getKeyName(key);
    }

    public String getScroll(int key) {
        if (key == 1069) return "MScrollUp";
        if (key == 1070) return "MScrollDown";
        return "&cERROR";
    }

    @Override public float getHeightF() { return keySetting != null ? 0f : 16f; }
    @Override public int getHeight() { return Math.round(getHeightF()); }

    private void drawString(String s) {
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(s, (float) ((this.moduleComponent.categoryComponent.getX() + 4) * 2) + xOffset, (float) ((this.moduleComponent.categoryComponent.getY() + this.o + (this.keySetting == null ? 3 : 4)) * 2), Theme.getGradient(Theme.descriptor[0], Theme.descriptor[1], 0));
    }

    public void onGuiClosed() { isBinding = false; }

    private static void ensureProcessedTextures() {
        if (processedEye == null)
            processedEye = RenderUtils.buildWhiteMaskedTexture("/assets/keystrokesmod/textures/gui/eye.png", "raven_eye_white", EYE_ICON);
        if (processedEyeOff == null)
            processedEyeOff = RenderUtils.buildWhiteMaskedTexture("/assets/keystrokesmod/textures/gui/eye_off.png", "raven_eye_off_white", EYE_OFF_ICON);
    }
}
