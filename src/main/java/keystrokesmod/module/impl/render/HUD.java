package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.AntiKnockback;
import keystrokesmod.module.impl.combat.Velocity;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.font.FontManager;
import keystrokesmod.utility.font.RavenFontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.fml.client.config.GuiButtonExt;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent;

import java.awt.Color;
import java.io.IOException;

public class HUD extends Module {
    public static SliderSetting theme;
    public static SliderSetting font;
    public static SliderSetting fontSize;
    private static SliderSetting outline;
    public static ButtonSetting alphabeticalSort;
    private static ButtonSetting drawBackground;
    private static ButtonSetting textShadow;
    private static ButtonSetting alignRight;
    private static ButtonSetting lowercase;
    private static ButtonSetting removeCloset;
    private static ButtonSetting removeRender;
    private static ButtonSetting removeScripts;
    public static ButtonSetting showInfo;
    public static float posX = 5.0f;
    public static float posY = 70.0f;

    private static final String[] OUTLINE_MODES = new String[] { "None", "Full", "Side" };
    private static final String[] HUD_FONT_OPTIONS = FontManager.getHudFontOptions();
    private static final int BACKGROUND_COLOR = new Color(0, 0, 0, 110).getRGB();

    private boolean isAlphabeticalSort;
    private boolean canShowInfo;
    private String lastHudFontName = "";
    private float lastHudFontScale = -1.0f;

    public HUD() {
        super("HUD", Module.category.render);
        this.registerSetting(new DescriptionSetting("Right click bind to hide modules."));
        this.registerSetting(theme = new SliderSetting("Theme", 0, Theme.themes));
        this.registerSetting(font = new SliderSetting("Font", 0, HUD_FONT_OPTIONS));
        this.registerSetting(fontSize = new SliderSetting("Scale", 1.0, 0.5, 2.0, 0.1));
        this.registerSetting(outline = new SliderSetting("Outline", 0, OUTLINE_MODES));
        this.registerSetting(new ButtonSetting("Edit position", () -> mc.displayGuiScreen(new EditScreen())));
        this.registerSetting(alignRight = new ButtonSetting("Align right", false));
        this.registerSetting(alphabeticalSort = new ButtonSetting("Alphabetical sort", false));
        this.registerSetting(drawBackground = new ButtonSetting("Draw background", false));
        this.registerSetting(textShadow = new ButtonSetting("Text shadow", true));
        this.registerSetting(lowercase = new ButtonSetting("Lowercase", false));
        this.registerSetting(removeCloset = new ButtonSetting("Remove closet modules", false));
        this.registerSetting(removeRender = new ButtonSetting("Remove render modules", false));
        this.registerSetting(removeScripts = new ButtonSetting("Remove scripts", false));
        this.registerSetting(showInfo = new ButtonSetting("Show module info", true));
    }

    @Override
    public void onEnable() {
        ModuleManager.sort();
    }

    @Override
    public void guiButtonToggled(ButtonSetting buttonSetting) {
        if (buttonSetting == alphabeticalSort || buttonSetting == showInfo) {
            ModuleManager.sort();
        }
    }

    @SubscribeEvent
    public void onRenderTick(RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Utils.nullCheck()) {
            return;
        }

        if (isAlphabeticalSort != alphabeticalSort.isToggled()) {
            isAlphabeticalSort = alphabeticalSort.isToggled();
            ModuleManager.sort();
        }

        if (canShowInfo != showInfo.isToggled()) {
            canShowInfo = showInfo.isToggled();
            ModuleManager.sort();
        }

        String currentFontName = getSelectedFontName();
        float currentFontScale = getSelectedFontScale();
        if (!currentFontName.equals(lastHudFontName) || Float.compare(currentFontScale, lastHudFontScale) != 0) {
            lastHudFontName = currentFontName;
            lastHudFontScale = currentFontScale;
            ModuleManager.sort();
        }

        if (mc.currentScreen != null || mc.gameSettings.showDebugInfo) {
            return;
        }

        for (Module module : ModuleManager.organizedModules) {
            module.getInfoUpdate();
            if (Module.sort) {
                break;
            }
        }

        if (Module.sort) {
            ModuleManager.sort();
        }
        Module.sort = false;

        RavenFontRenderer hudFont = getHudFontRenderer();
        int textTopOffset = hudFont.getTextTopOffset();
        int textBottomOffset = hudFont.getTextBottomOffset();
        int textPadding = getHudTextPadding();
        int outlineThickness = getHudOutlineThickness();
        int rowHeight = getHudRowHeight(textTopOffset, textBottomOffset, textPadding);
        float yPos = posY;
        double gradientOffset = 0.0;
        String previousModule = "";
        double lastOutlineLeft = 0.0;
        double lastOutlineRight = 0.0;
        double lastBackgroundBottom = 0.0;
        boolean removeVelocity = ModuleManager.antiKnockback.isEnabled();

        try {
            for (Module module : ModuleManager.organizedModules) {
                if (!module.isEnabled() || module == this || shouldSkipModule(module, removeVelocity)) {
                    continue;
                }

                String moduleName = getHudRenderText(module);
                int moduleWidth = hudFont.getStringWidth(moduleName);
                int color = Theme.getGradient((int) theme.getInput(), gradientOffset);
                float xPos = posX;
                float textY = getHudTextY(yPos, rowHeight, textTopOffset, textBottomOffset);
                double backgroundLeft = xPos - textPadding;
                double backgroundRight = xPos + moduleWidth + textPadding;
                double backgroundTop = yPos;
                double backgroundBottom = yPos + rowHeight;
                double outlineLeft = backgroundLeft - outlineThickness;
                double outlineRight = backgroundRight + outlineThickness;
                double outlineTop = backgroundTop - outlineThickness;

                if (alignRight.isToggled()) {
                    xPos -= moduleWidth;
                    backgroundLeft = xPos - textPadding;
                    backgroundRight = xPos + moduleWidth + textPadding;
                    outlineLeft = backgroundLeft - outlineThickness;
                    outlineRight = backgroundRight + outlineThickness;
                }

                if (drawBackground.isToggled()) {
                    RenderUtils.drawRect(backgroundLeft, backgroundTop, backgroundRight, backgroundBottom, BACKGROUND_COLOR);
                }

                if (outline.getInput() == 1 && gradientOffset == 0.0) {
                    RenderUtils.drawRect(outlineLeft, outlineTop, outlineRight, backgroundTop, color);
                }

                gradientOffset -= theme.getInput() == 0 ? 120 : 12;

                if (outline.getInput() == 1 && !previousModule.isEmpty()) {
                    double difference = hudFont.getStringWidth(previousModule) - moduleWidth;
                    if (alphabeticalSort.isToggled() && difference < 0) {
                        RenderUtils.drawRect(outlineLeft, outlineTop, xPos - difference + textPadding + outlineThickness, backgroundTop, color);
                    }
                    else if (alignRight.isToggled()) {
                        RenderUtils.drawRect(xPos - difference - textPadding - outlineThickness, outlineTop, backgroundLeft, backgroundTop, color);
                    }
                    else {
                        RenderUtils.drawRect(backgroundRight, outlineTop, xPos + difference + moduleWidth + textPadding + outlineThickness, backgroundTop, color);
                    }
                }

                if (outline.getInput() > 0) {
                    if (alignRight.isToggled()) {
                        RenderUtils.drawRect(backgroundRight, backgroundTop, outlineRight, backgroundBottom, color);
                    }
                    else {
                        RenderUtils.drawRect(outlineLeft, backgroundTop, backgroundLeft, backgroundBottom, color);
                    }
                }

                if (outline.getInput() == 1) {
                    if (alignRight.isToggled()) {
                        RenderUtils.drawRect(outlineLeft, backgroundTop, backgroundLeft, backgroundBottom, color);
                    }
                    else {
                        RenderUtils.drawRect(backgroundRight, backgroundTop, outlineRight, backgroundBottom, color);
                    }
                }

                hudFont.drawString(moduleName, xPos, textY, color, shouldDrawTextShadow());
                previousModule = moduleName;
                lastOutlineLeft = outlineLeft;
                lastOutlineRight = outlineRight;
                lastBackgroundBottom = backgroundBottom;
                yPos += rowHeight;
            }
        }
        catch (Exception exception) {
            Utils.sendMessage("&cAn error occurred rendering HUD. check your logs");
            exception.printStackTrace();
        }

        if (outline.getInput() == 1 && !previousModule.isEmpty()) {
            RenderUtils.drawRect(lastOutlineLeft, lastBackgroundBottom, lastOutlineRight, lastBackgroundBottom + outlineThickness, Theme.getGradient((int) theme.getInput(), gradientOffset));
        }
    }

    public static int getLongestModule() {
        RavenFontRenderer hudFont = getHudFontRenderer();
        int length = 0;

        for (Module module : ModuleManager.organizedModules) {
            if (module.isEnabled()) {
                length = Math.max(length, hudFont.getStringWidth(getHudRenderText(module)));
            }
        }

        return length;
    }

    private static boolean shouldSkipModule(Module module, boolean removeVelocity) {
        if (module.isHidden()) {
            return true;
        }
        if (module == ModuleManager.commandLine) {
            return true;
        }
        if (removeRender.isToggled() && module.moduleCategory() == category.render) {
            return true;
        }
        if (removeScripts.isToggled() && module.moduleCategory() == category.scripts) {
            return true;
        }
        if (removeCloset.isToggled() && module.closetModule) {
            return true;
        }
        return module instanceof Velocity && removeVelocity;
    }

    private static boolean isLastVisibleModule(Module currentModule, boolean removeVelocity) {
        boolean foundCurrent = false;

        for (Module module : ModuleManager.organizedModules) {
            if (!foundCurrent) {
                if (module == currentModule) {
                    foundCurrent = true;
                }
                continue;
            }

            if (module.isEnabled() && !(module instanceof HUD) && !shouldSkipModule(module, removeVelocity)) {
                return false;
            }
        }

        return true;
    }

    static class EditScreen extends GuiScreen {
        private static final String EXAMPLE = "This is an-Example-HUD";

        private GuiButtonExt resetPosition;
        private boolean dragging = false;
        private float minX = 0.0f;
        private float minY = 0.0f;
        private float maxX = 0.0f;
        private float maxY = 0.0f;
        private float actualX = 5.0f;
        private float actualY = 70.0f;
        private float lastActualX = 0.0f;
        private float lastActualY = 0.0f;
        private int lastMouseX = 0;
        private int lastMouseY = 0;
        private float clickMinX = 0.0f;

        @Override
        public void initGui() {
            super.initGui();
            this.buttonList.add(this.resetPosition = new GuiButtonExt(1, this.width - 90, this.height - 25, 85, 20, "Reset position"));
            this.actualX = HUD.posX;
            this.actualY = HUD.posY;
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawRect(0, 0, this.width, this.height, -1308622848);
            float previewX = this.actualX;
            float previewY = this.actualY;
            float previewMaxX = previewX + 50.0f;
            float previewMaxY = previewY + 32.0f;
            float[] clickPos = this.getPreviewBounds(EXAMPLE);

            this.minX = previewX;
            this.minY = previewY;

            if (clickPos == null) {
                this.maxX = previewMaxX;
                this.maxY = previewMaxY;
                this.clickMinX = previewX;
            }
            else {
                this.maxX = clickPos[0];
                this.maxY = clickPos[1];
                this.clickMinX = clickPos[2];
            }

            HUD.posX = previewX;
            HUD.posY = previewY;

            ScaledResolution resolution = new ScaledResolution(this.mc);
            int textX = resolution.getScaledWidth() / 2 - 84;
            int textY = resolution.getScaledHeight() / 2 - 20;
            RenderUtils.drawColoredString("Edit the HUD position by dragging.", '-', textX, textY, 2L, 0L, true, this.mc.fontRendererObj);

            try {
                this.handleInput();
            }
            catch (IOException ignored) {
            }

            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        private float[] getPreviewBounds(String text) {
            RavenFontRenderer hudFont = HUD.getHudFontRenderer();

            if (empty()) {
                float x = this.minX;
                float y = this.minY;
                String[] lines = text.split("-");
                int localTextPadding = getHudTextPadding();
                int localRowHeight = getHudRowHeight(hudFont.getTextTopOffset(), hudFont.getTextBottomOffset(), localTextPadding);

                for (String line : lines) {
                    if (HUD.alignRight.isToggled()) {
                        x += hudFont.getStringWidth(lines[0]) - hudFont.getStringWidth(line);
                    }
                    float textY = getHudTextY(y, localRowHeight, hudFont.getTextTopOffset(), hudFont.getTextBottomOffset());
                    hudFont.drawString(line, x, textY, Color.white.getRGB(), shouldDrawTextShadow());
                    y += localRowHeight;
                }
                return null;
            }

            int longestModule = getLongestModule();
            float y = this.minY;
            double gradientOffset = 0.0;
            String previousModule = "";
            double lastOutlineLeft = 0.0;
            double lastOutlineRight = 0.0;
            double lastBackgroundBottom = 0.0;
            boolean removeVelocity = ModuleManager.antiKnockback.isEnabled();
            int textTopOffset = hudFont.getTextTopOffset();
            int textBottomOffset = hudFont.getTextBottomOffset();
            int textPadding = getHudTextPadding();
            int outlineThickness = getHudOutlineThickness();
            int rowHeight = getHudRowHeight(textTopOffset, textBottomOffset, textPadding);

            try {
                for (Module module : ModuleManager.organizedModules) {
                    if (!module.isEnabled() || module instanceof HUD || shouldSkipModule(module, removeVelocity)) {
                        continue;
                    }

                    String moduleName = getHudRenderText(module);
                    int moduleWidth = hudFont.getStringWidth(moduleName);
                    int color = Theme.getGradient((int) theme.getInput(), gradientOffset);
                    float xPos = posX;
                    float textY = getHudTextY(y, rowHeight, textTopOffset, textBottomOffset);
                    double backgroundLeft = xPos - textPadding;
                    double backgroundRight = xPos + moduleWidth + textPadding;
                    double backgroundTop = y;
                    double backgroundBottom = y + rowHeight;
                    double outlineLeft = backgroundLeft - outlineThickness;
                    double outlineRight = backgroundRight + outlineThickness;
                    double outlineTop = backgroundTop - outlineThickness;

                    if (alignRight.isToggled()) {
                        xPos -= moduleWidth;
                        backgroundLeft = xPos - textPadding;
                        backgroundRight = xPos + moduleWidth + textPadding;
                        outlineLeft = backgroundLeft - outlineThickness;
                        outlineRight = backgroundRight + outlineThickness;
                    }

                    if (outline.getInput() == 1 && gradientOffset == 0.0) {
                        RenderUtils.drawRect(outlineLeft, outlineTop, outlineRight, backgroundTop, color);
                    }

                    gradientOffset -= theme.getInput() == 0 ? 120 : 12;

                    if (drawBackground.isToggled()) {
                        RenderUtils.drawRect(backgroundLeft, backgroundTop, backgroundRight, backgroundBottom, BACKGROUND_COLOR);
                    }

                    if (outline.getInput() == 1 && !previousModule.isEmpty()) {
                        double difference = hudFont.getStringWidth(previousModule) - moduleWidth;
                        if (alphabeticalSort.isToggled() && difference < 0) {
                            RenderUtils.drawRect(outlineLeft, outlineTop, xPos - difference + textPadding + outlineThickness, backgroundTop, color);
                        }
                        else if (alignRight.isToggled()) {
                            RenderUtils.drawRect(xPos - difference - textPadding - outlineThickness, outlineTop, backgroundLeft, backgroundTop, color);
                        }
                        else {
                            RenderUtils.drawRect(backgroundRight, outlineTop, xPos + difference + moduleWidth + textPadding + outlineThickness, backgroundTop, color);
                        }
                    }

                    if (outline.getInput() > 0) {
                        if (alignRight.isToggled()) {
                            RenderUtils.drawRect(backgroundRight, backgroundTop, outlineRight, backgroundBottom, color);
                        }
                        else {
                            RenderUtils.drawRect(outlineLeft, backgroundTop, backgroundLeft, backgroundBottom, color);
                        }
                    }

                    if (outline.getInput() == 1) {
                        if (alignRight.isToggled()) {
                            RenderUtils.drawRect(outlineLeft, backgroundTop, backgroundLeft, backgroundBottom, color);
                        }
                        else {
                            RenderUtils.drawRect(backgroundRight, backgroundTop, outlineRight, backgroundBottom, color);
                        }
                    }

                    hudFont.drawString(moduleName, xPos, textY, color, shouldDrawTextShadow());
                    previousModule = moduleName;
                    lastOutlineLeft = outlineLeft;
                    lastOutlineRight = outlineRight;
                    lastBackgroundBottom = backgroundBottom;
                    y += rowHeight;
                }
            }
            catch (Exception exception) {
                Utils.sendMessage("&cAn error occurred rendering HUD. check your logs");
                exception.printStackTrace();
            }

            if (outline.getInput() == 1 && !previousModule.isEmpty()) {
                RenderUtils.drawRect(lastOutlineLeft, lastBackgroundBottom, lastOutlineRight, lastBackgroundBottom + outlineThickness, Theme.getGradient((int) theme.getInput(), gradientOffset));
            }

            return new float[]{this.minX + longestModule, (float) Math.ceil(Math.max(y, lastBackgroundBottom)), this.minX - longestModule};
        }

        @Override
        protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
            super.mouseClickMove(mouseX, mouseY, button, timeSinceLastClick);

            if (button != 0) {
                return;
            }

            if (this.dragging) {
                this.actualX = this.lastActualX + (mouseX - this.lastMouseX);
                this.actualY = this.lastActualY + (mouseY - this.lastMouseY);
            }
            else if (mouseX > this.clickMinX && mouseX < this.maxX && mouseY > this.minY && mouseY < this.maxY) {
                this.dragging = true;
                this.lastMouseX = mouseX;
                this.lastMouseY = mouseY;
                this.lastActualX = this.actualX;
                this.lastActualY = this.actualY;
            }
        }

        @Override
        protected void mouseReleased(int mouseX, int mouseY, int state) {
            super.mouseReleased(mouseX, mouseY, state);
            if (state == 0) {
                this.dragging = false;
            }
        }

        @Override
        public void actionPerformed(GuiButton button) {
            if (button == this.resetPosition) {
                this.actualX = HUD.posX = 5.0f;
                this.actualY = HUD.posY = 70.0f;
            }
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }

        private boolean empty() {
            for (Module module : ModuleManager.organizedModules) {
                if (module.isEnabled() && !module.getName().equals("HUD")) {
                    if (module.isHidden()) {
                        continue;
                    }
                    if (module == ModuleManager.commandLine) {
                        continue;
                    }
                    return false;
                }
            }
            return true;
        }
    }

    public static RavenFontRenderer getHudFontRenderer() {
        return FontManager.getHudRenderer(getSelectedFontName(), getSelectedFontScale());
    }

    public static String getHudText(Module module) {
        String moduleName = module instanceof AntiKnockback ? "Velocity" : module.getNameInHud();
        if (lowercase != null && lowercase.isToggled()) {
            moduleName = moduleName.toLowerCase();
        }
        return moduleName;
    }

    public static String getHudRenderText(Module module) {
        String moduleName = getHudText(module);
        if (showInfo != null && showInfo.isToggled() && !module.getInfo().isEmpty()) {
            moduleName += " \u00a77" + module.getInfo();
        }
        if (lowercase != null && lowercase.isToggled()) {
            moduleName = moduleName.toLowerCase();
        }
        return moduleName;
    }

    public static String getSelectedFontName() {
        if (font == null) {
            return HUD_FONT_OPTIONS[0];
        }
        int index = (int) Math.max(0, Math.min(font.getOptions().length - 1, font.getInput()));
        return font.getOptions()[index];
    }

    public static float getSelectedFontScale() {
        if (fontSize == null) {
            return 1.0f;
        }
        return (float) fontSize.getInput();
    }

    private static int getHudTextPadding() {
        return getScaledHudPixels(2.0f);
    }

    private static int getHudOutlineThickness() {
        return getScaledHudPixels(1.0f);
    }

    private static int getHudRowHeight(int textTopOffset, int textBottomOffset, int textPadding) {
        int textBoxHeight = Math.max(1, textBottomOffset - textTopOffset);
        return Math.max(1, textBoxHeight + textPadding * 2);
    }

    private static float getHudTextY(float rowTop, int rowHeight, int textTopOffset, int textBottomOffset) {
        int textBoxHeight = Math.max(1, textBottomOffset - textTopOffset);
        return rowTop + (rowHeight - textBoxHeight) / 2.0f - textTopOffset;
    }

    private static int getScaledHudPixels(float basePixels) {
        return Math.max(1, Math.round(basePixels * getSelectedFontScale()));
    }

    private static boolean shouldDrawTextShadow() {
        return textShadow == null || textShadow.isToggled();
    }
}
