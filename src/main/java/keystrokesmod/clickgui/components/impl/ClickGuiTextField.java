package keystrokesmod.clickgui.components.impl;

import keystrokesmod.utility.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.opengl.GL11;

public class ClickGuiTextField {
    private static final float DEFAULT_TEXT_SCALE = 0.5f;
    private static final int BACKGROUND_COLOR = 0xFF222230;
    private static final int FOCUSED_BACKGROUND_COLOR = 0xFF2A2A3A;
    private static final int OUTLINE_COLOR = 0xFF3A3A50;
    private static final int FOCUSED_OUTLINE_COLOR = 0xFF5563A0;
    private static int nextId;

    private final GuiTextField textField;
    private final String placeholder;
    private final float textScale;
    private long lastCursorTick;

    public ClickGuiTextField(String placeholder, int maxLength) {
        this(placeholder, maxLength, DEFAULT_TEXT_SCALE);
    }

    public ClickGuiTextField(String placeholder, int maxLength, float textScale) {
        this.placeholder = placeholder == null ? "" : placeholder;
        this.textScale = textScale;
        this.textField = new GuiTextField(nextId++, Minecraft.getMinecraft().fontRendererObj, 0, 0, 100, 20);
        this.textField.setMaxStringLength(maxLength);
        this.textField.setEnableBackgroundDrawing(false);
        this.textField.setCanLoseFocus(true);
    }

    public void render(float left, float top, float right, float bottom) {
        float innerLeft = left + 2f;
        float innerWidth = Math.max(0f, right - left - 4f);
        float boxHeight = Math.max(0f, bottom - top);

        RenderUtils.drawRect(left, top, right, bottom, isFocused() ? FOCUSED_BACKGROUND_COLOR : BACKGROUND_COLOR);
        RenderUtils.drawOutline(left, top, right, bottom, 1f, isFocused() ? FOCUSED_OUTLINE_COLOR : OUTLINE_COLOR);

        int fontHeight = Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT;
        float scaledFontHeight = fontHeight * textScale;
        float boxCenterY = top + boxHeight / 2f;

        GL11.glPushMatrix();
        GL11.glScaled(textScale, textScale, 1f);
        textField.xPosition = Math.round(innerLeft / textScale);
        textField.yPosition = Math.round((boxCenterY - scaledFontHeight / 2f) / textScale);
        textField.width = Math.max(0, Math.round(innerWidth / textScale));
        textField.height = Math.max(0, Math.round(boxHeight / textScale));
        textField.drawTextBox();
        GL11.glPopMatrix();

        if (!textField.isFocused() && textField.getText().isEmpty() && !placeholder.isEmpty()) {
            drawScaledText("\u00a77" + placeholder, innerLeft, centeredScaledTextY(top, boxHeight), 0xFFAAAAAA);
        }
    }

    public void tickCursor() {
        if (System.currentTimeMillis() - lastCursorTick >= 50L) {
            lastCursorTick = System.currentTimeMillis();
            textField.updateCursorCounter();
        }
    }

    public boolean contains(int mouseX, int mouseY, float left, float top, float right, float bottom) {
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }

    public boolean textboxKeyTyped(char typedChar, int keyCode) {
        return textField.textboxKeyTyped(typedChar, keyCode);
    }

    public String getText() {
        return textField.getText();
    }

    public void setText(String text) {
        textField.setText(text == null ? "" : text);
    }

    public boolean isFocused() {
        return textField.isFocused();
    }

    public void setFocused(boolean focused) {
        textField.setFocused(focused);
    }

    private float centeredScaledTextY(float top, float height) {
        int fontHeight = Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT;
        return top + (height - fontHeight * textScale) / 2f;
    }

    private void drawScaledText(String text, float x, float y, int color) {
        GL11.glPushMatrix();
        GL11.glScaled(textScale, textScale, textScale);
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(text, x / textScale, y / textScale, color);
        GL11.glPopMatrix();
    }
}
