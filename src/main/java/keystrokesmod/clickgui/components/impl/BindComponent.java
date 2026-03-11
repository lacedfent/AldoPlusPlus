package keystrokesmod.clickgui.components.impl;

import keystrokesmod.Raven;
import keystrokesmod.clickgui.components.Component;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.profile.ProfileModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;

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

    @Override
    public float getOffset() {
        return this.o;
    }

    @Override
    public boolean isBaseVisible() {
        return this.keySetting == null || this.keySetting.visible;
    }

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

        if (keySetting == null && this.moduleComponent.mod.moduleCategory() != Module.category.profiles) {
            ensureProcessedTextures();

            int iconSize = getEyeIconSize();
            float iconX = getEyeIconX(iconSize);
            float textHeight = Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT * 0.5f;
            float iconY = getRenderTextY() + (textHeight - iconSize) / 2f;

            int themeColor = !this.moduleComponent.mod.hidden
                    ? Theme.getGradient(Theme.descriptor[0], Theme.descriptor[1], 0)
                    : Theme.getGradient(Theme.hiddenBind[0], Theme.hiddenBind[1], 0);
            Color c = new Color(themeColor, true);

            ResourceLocation tex = this.moduleComponent.mod.isHidden() ? processedEyeOff : processedEye;
            Minecraft.getMinecraft().getTextureManager().bindTexture(tex);

            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager.color(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, c.getAlpha() / 255f);

            net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture(
                    (int) iconX, (int) iconY, 0, 0,
                    iconSize, iconSize, iconSize, iconSize);

            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.disableBlend();
        }
    }

    public void drawScreen(int x, int y) {
        this.y = this.moduleComponent.categoryComponent.getModuleY() + this.o;
        this.x = this.moduleComponent.categoryComponent.getX();
    }

    public boolean onClick(int x, int y, int button) {
        if (!this.overSetting(x, y) || !this.moduleComponent.isOpened || !this.moduleComponent.isVisible(this)) {
            return false;
        }

        if (button == 0 && this.moduleComponent.mod.moduleCategory() != Module.category.profiles && this.overEyeIcon(x, y)) {
            this.moduleComponent.mod.setHidden(!this.moduleComponent.mod.isHidden());
            if (Raven.currentProfile != null) {
                Raven.currentProfile.getModule().saved = false;
            }
            return true;
        }

        if (this.moduleComponent.mod.canBeEnabled() && button == 0 && this.overBindText(x, y)) {
            this.isBinding = !this.isBinding;
            return true;
        }

        if (this.moduleComponent.mod.canBeEnabled() && button > 1 && this.isBinding) {
            if (this.keySetting != null) {
                this.keySetting.setKey(button + 1000);
            } else {
                this.moduleComponent.mod.setBind(button + 1000);
            }
            if (Raven.currentProfile != null) {
                Raven.currentProfile.getModule().saved = false;
            }
            this.isBinding = false;
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
        return this.moduleComponent.categoryComponent.getX() + 4f + (this.xOffset * 0.5f);
    }

    /** Screen-space Y for click hitboxes (no GL translate applied). */
    private float getBindTextY() {
        return this.moduleComponent.categoryComponent.getModuleY() + this.o + (this.keySetting == null ? 3f : 4f);
    }

    /** Render-space Y for drawing inside the parent GL translate. */
    private float getRenderTextY() {
        return this.moduleComponent.categoryComponent.getY() + this.o + (this.keySetting == null ? 3f : 4f);
    }

    private String getBindDisplayString() {
        if (this.keySetting == null) {
            return !this.moduleComponent.mod.canBeEnabled() && this.moduleComponent.mod.script == null
                    ? "Module cannot be bound."
                    : this.isBinding
                        ? "Press a key..."
                        : "Current bind: '\u00a7e" + getKeyAsStr(false) + "\u00a7r'";
        }
        return this.isBinding
                ? "Press a key..."
                : this.keySetting.getName() + ": '\u00a7e" + getKeyAsStr(true) + "\u00a7r'";
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
        return this.moduleComponent.categoryComponent.getX()
                + this.moduleComponent.categoryComponent.getWidth()
                - iconSize - EYE_ICON_PADDING;
    }

    private float getEyeIconY(int iconSize) {
        float textY = getBindTextY();
        float textHeight = Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT * 0.5f;
        return textY + (textHeight - iconSize) / 2f;
    }

    public void onScroll(int scroll) {
        if (this.isBinding && scroll != 0) {
            if (this.keySetting != null) {
                this.keySetting.setKey(scroll > 0 ? 1069 : 1070);
            }
            else {
                this.moduleComponent.mod.setBind(scroll > 0 ? 1069 : 1070);
            }
            if (Raven.currentProfile != null) {
                Raven.currentProfile.getModule().saved = false;
            }
            this.isBinding = false;
        }
    }

    public void keyTyped(char t, int keybind) {
        if (this.isBinding) {
            if (keybind == Keyboard.KEY_0 || keybind == Keyboard.KEY_ESCAPE) {
                if (this.moduleComponent.mod instanceof Gui) {
                    this.moduleComponent.mod.setBind(54);
                }
                else {
                    if (this.keySetting != null) {
                        this.keySetting.setKey(0);
                    }
                    else {
                        this.moduleComponent.mod.setBind(0);
                    }
                }
                if (Raven.currentProfile != null) {
                    Raven.currentProfile.getModule().saved = false;
                }
            }
            else {
                if (Raven.currentProfile != null) {
                    Raven.currentProfile.getModule().saved = false;
                }
                if (this.keySetting != null) {
                    this.keySetting.setKey(keybind);
                }
                else {
                    this.moduleComponent.mod.setBind(keybind);
                }
            }

            this.isBinding = false;
        }
    }

    public boolean overSetting(int mouseX, int mouseY) {
        float rowX = this.moduleComponent.categoryComponent.getX();
        float rowY = this.moduleComponent.categoryComponent.getModuleY() + this.o;
        float rowW = this.moduleComponent.categoryComponent.getWidth();
        return mouseX > rowX && mouseX < rowX + rowW && mouseY > rowY - 1 && mouseY < rowY + 12;
    }

    public String getKeyAsStr(boolean isKey) {
        int key = isKey ? this.keySetting.getKey() : this.moduleComponent.mod.getKeycode();
        return (key >= 1000 ? ((key == 1069 || key == 1070) ? getScroll(key) : "M" + (key - 1000)) : Keyboard.getKeyName(key));
    }

    public String getScroll(int key) {
        if (key == 1069) {
            return "MScrollUp";
        }
        else if (key == 1070) {
            return "MScrollDown";
        }
        return "&cERROR";
    }

    @Override
    public float getHeightF() {
        if (this.keySetting != null) {
            return 0f;
        }
        return 16f;
    }

    @Override
    public int getHeight() {
        return Math.round(getHeightF());
    }

    private void drawString(String s) {
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(s, (float) ((this.moduleComponent.categoryComponent.getX() + 4) * 2) + xOffset, (float) ((this.moduleComponent.categoryComponent.getY() + this.o + (this.keySetting == null ? 3 : 4)) * 2), Theme.getGradient(Theme.descriptor[0], Theme.descriptor[1], 0));
    }

    public void onGuiClosed() {
        this.isBinding = false;
    }

    private static void ensureProcessedTextures() {
        if (processedEye == null) {
            processedEye = buildWhiteMaskedTexture(
                    "/assets/keystrokesmod/textures/gui/eye.png",
                    "raven_eye_white",
                    EYE_ICON
            );
        }
        if (processedEyeOff == null) {
            processedEyeOff = buildWhiteMaskedTexture(
                    "/assets/keystrokesmod/textures/gui/eye_off.png",
                    "raven_eye_off_white",
                    EYE_OFF_ICON
            );
        }
    }

    private static ResourceLocation buildWhiteMaskedTexture(String resourcePath, String registryName, ResourceLocation fallback) {
        try (InputStream stream = Raven.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return fallback;
            }
            BufferedImage src = ImageIO.read(stream);
            int w = src.getWidth();
            int h = src.getHeight();
            BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int py = 0; py < h; py++) {
                for (int px = 0; px < w; px++) {
                    int argb = src.getRGB(px, py);
                    int alpha = (argb >>> 24) & 0xFF;
                    if (alpha > 0) {
                        dst.setRGB(px, py, (alpha << 24) | 0x00FFFFFF);
                    }
                }
            }
            DynamicTexture tex = new DynamicTexture(dst);
            return Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation(registryName, tex);
        } catch (Exception e) {
            e.printStackTrace();
            return fallback;
        }
    }
}
