package keystrokesmod.clickgui.components.impl;

import keystrokesmod.clickgui.components.Component;
import keystrokesmod.clickgui.components.FocusableTextComponent;
import keystrokesmod.module.setting.impl.TextSetting;
import keystrokesmod.utility.Theme;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public class TextFieldComponent extends Component implements FocusableTextComponent {
    private static final float ROW_HEIGHT = 12f;
    private static final float TEXT_SCALE = 0.5f;

    public final TextSetting textSetting;
    public final ModuleComponent moduleComponent;
    public float o;
    public float xOffset;

    private final ClickGuiTextField textField;

    public TextFieldComponent(TextSetting textSetting, ModuleComponent moduleComponent, float o) {
        this.textSetting = textSetting;
        this.moduleComponent = moduleComponent;
        this.o = o;
        this.textField = new ClickGuiTextField(textSetting.getPlaceholder(), textSetting.getMaxLength(), TEXT_SCALE);
        this.textField.setText(textSetting.getText());
    }

    @Override
    public void render() {
        if (!textField.isFocused() && !textField.getText().equals(textSetting.getText())) {
            textField.setText(textSetting.getText());
        }

        float left = moduleComponent.categoryComponent.getX() + 4f + (xOffset / 2f);
        float right = moduleComponent.categoryComponent.getX() + moduleComponent.categoryComponent.getWidth() - 4f;
        float labelTop = moduleComponent.categoryComponent.getY() + o;
        float boxTop = moduleComponent.categoryComponent.getY() + o + ROW_HEIGHT + 1f;
        float boxBottom = moduleComponent.categoryComponent.getY() + o + (2f * ROW_HEIGHT) - 1f;

        drawScaledText(
            textSetting.getName(),
            left,
            centeredScaledTextY(labelTop, ROW_HEIGHT),
            Theme.getGradient(Theme.descriptor[0], Theme.descriptor[1], 0)
        );
        textField.render(left, boxTop, right, boxBottom);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        textField.tickCursor();
    }

    @Override
    public boolean onClick(int mouseX, int mouseY, int button) {
        if (!moduleComponent.isOpened || !moduleComponent.isVisible(this) || button != 0) {
            return false;
        }

        float left = moduleComponent.categoryComponent.getX() + 4f + (xOffset / 2f);
        float right = moduleComponent.categoryComponent.getX() + moduleComponent.categoryComponent.getWidth() - 4f;
        float boxTop = moduleComponent.categoryComponent.getModuleY() + o + ROW_HEIGHT + 1f;
        float boxBottom = moduleComponent.categoryComponent.getModuleY() + o + (2f * ROW_HEIGHT) - 1f;

        if (textField.contains(mouseX, mouseY, left, boxTop, right, boxBottom)) {
            textField.setFocused(true);
            return true;
        }

        if (textField.isFocused()) {
            textField.setFocused(false);
        }
        return false;
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (!moduleComponent.isOpened || !textField.isFocused()) {
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            textField.setFocused(false);
            return;
        }

        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            textSetting.submit();
            textField.setText(textSetting.getText());
            return;
        }

        if (textField.textboxKeyTyped(typedChar, keyCode)) {
            textSetting.setText(textField.getText());
        }
    }

    @Override
    public void onGuiClosed() {
        textField.setFocused(false);
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
    public float getHeightF() {
        return ROW_HEIGHT * 2f;
    }

    @Override
    public boolean isBaseVisible() {
        return textSetting.visible;
    }

    @Override
    public boolean isTextInputFocused() {
        return textField.isFocused();
    }

    @Override
    public void unfocusTextInput() {
        textField.setFocused(false);
    }

    private static float centeredScaledTextY(float top, float height) {
        int fontHeight = Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT;
        return top + (height - fontHeight * TEXT_SCALE) / 2f;
    }

    private static void drawScaledText(String text, float x, float y, int color) {
        GL11.glPushMatrix();
        GL11.glScaled(TEXT_SCALE, TEXT_SCALE, TEXT_SCALE);
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(text, x / TEXT_SCALE, y / TEXT_SCALE, color);
        GL11.glPopMatrix();
    }
}
