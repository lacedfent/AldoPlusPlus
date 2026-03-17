package keystrokesmod.clickgui.components.impl;

import keystrokesmod.Raven;
import keystrokesmod.clickgui.animation.ScrollOffsetAnimation;
import keystrokesmod.clickgui.components.Component;
import keystrokesmod.clickgui.components.FocusableTextComponent;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.module.setting.impl.BlockListSetting;
import keystrokesmod.utility.BlockSearchIndex;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.Timer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BlockSearchComponent extends Component implements FocusableTextComponent {
    private static final ResourceLocation CLOSE_ICON = new ResourceLocation("keystrokesmod", "textures/gui/close.png");
    private static final ResourceLocation ARROW_ICON = new ResourceLocation("keystrokesmod", "textures/gui/arrow_left.png");
    private static ResourceLocation processedClose;
    private static ResourceLocation processedArrow;

    private static final float ANIMATION_DURATION = 250f;
    private static final float ROW_HEIGHT = 12f;
    private static final int MAX_VISIBLE_RESULTS = 7;
    private static final int MAX_VISIBLE_SELECTED = 7;
    private static final int CLOSE_SIZE = 6;
    private static final float CLOSE_PAD = 3f;
    private static final float SELECTED_LIST_GAP = 4f;

    private static final float STATIC_TEXT_SCALE = 0.5f;

    public final BlockListSetting setting;
    public final ModuleComponent moduleComponent;
    public float o;
    public float xOffset;

    private ClickGuiTextField searchField;
    private final ScrollOffsetAnimation dropdownScrollAnim = new ScrollOffsetAnimation(200);
    private final ScrollOffsetAnimation selectedScrollAnim = new ScrollOffsetAnimation(200);
    private List<BlockSearchIndex.GroupedBlockResult> cachedResults = Collections.emptyList();
    private String expandedGroupId;
    private List<BlockSearchIndex.BlockEntry> expandedVariants = Collections.emptyList();

    private float lastMouseX;
    private float lastMouseY;

    private static final class CachedSelectedRow {
        final String storageId;
        final String displayName;
        final ItemStack stack;
        final List<ItemStack> cyclingStacks;

        CachedSelectedRow(String storageId, String displayName, ItemStack stack, List<ItemStack> cyclingStacks) {
            this.storageId = storageId;
            this.displayName = displayName;
            this.stack = stack;
            this.cyclingStacks = cyclingStacks;
        }
    }

    private List<CachedSelectedRow> selectedRowsCache;

    private Timer dropdownAnimTimer;
    private float dropdownAnimStartH;
    private float dropdownAnimTargetH;
    private float dropdownAnimH;

    private static final class Layout {
        float cx, cy, cw, left, right, searchTop, contentTop;
    }

    public BlockSearchComponent(BlockListSetting setting, ModuleComponent moduleComponent, float o) {
        this.setting = setting;
        this.moduleComponent = moduleComponent;
        this.o = o;
    }

    private Layout layout(boolean useModuleY) {
        Layout L = new Layout();
        L.cx = moduleComponent.categoryComponent.getX();
        L.cy = useModuleY ? moduleComponent.categoryComponent.getModuleY() : moduleComponent.categoryComponent.getY();
        L.cw = moduleComponent.categoryComponent.getWidth();
        L.left = L.cx + 4 + (xOffset / 2);
        L.right = L.cx + L.cw - 4;
        L.searchTop = L.cy + o + ROW_HEIGHT;
        L.contentTop = L.cy + o + 2 * ROW_HEIGHT;
        return L;
    }

    private static float centeredScaledTextY(float top, float height) {
        int fontH = Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT;
        return top + (height - fontH * STATIC_TEXT_SCALE) / 2f;
    }

    private static void drawScaledText(String text, float x, float y, int color) {
        GL11.glPushMatrix();
        GL11.glScaled(STATIC_TEXT_SCALE, STATIC_TEXT_SCALE, STATIC_TEXT_SCALE);
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(text, x / STATIC_TEXT_SCALE, y / STATIC_TEXT_SCALE, color);
        GL11.glPopMatrix();
    }

    public boolean isSearchFocused() {
        return searchField != null && searchField.isFocused();
    }

    public void unfocusSearch() {
        if (searchField != null) {
            searchField.setFocused(false);
            updateDropdownAnimation();
        }
    }

    @Override
    public boolean isTextInputFocused() {
        return isSearchFocused();
    }

    @Override
    public void unfocusTextInput() {
        unfocusSearch();
    }

    private void ensureSearchField() {
        if (searchField == null) {
            searchField = new ClickGuiTextField("Search blocks...", 128, STATIC_TEXT_SCALE);
        }
    }

    private float computeDropdownTarget() {
        int rows = getDropdownRowCount();
        if (isSearchFocused() && rows > 0) {
            return Math.min(MAX_VISIBLE_RESULTS, rows) * ROW_HEIGHT;
        }
        return 0;
    }

    private void updateDropdownAnimation() {
        float newTarget = computeDropdownTarget();
        if (newTarget != dropdownAnimTargetH) {
            dropdownAnimStartH = dropdownAnimH;
            dropdownAnimTargetH = newTarget;
            (dropdownAnimTimer = new Timer(ANIMATION_DURATION)).start();
        }
    }

    private boolean isMouseOverDropdown() {
        return isMouseOverDropdown(lastMouseX, lastMouseY);
    }

    private int getDropdownRowCount() {
        if (expandedGroupId != null) return 2 + expandedVariants.size();
        return cachedResults.size();
    }

    public boolean isMouseOverDropdown(float mouseX, float mouseY) {
        if (getDropdownRowCount() == 0) return false;
        float dropdownH = getAnimatedDropdownHeight();
        if (dropdownH <= 0) return false;
        float cx = moduleComponent.categoryComponent.getX();
        float cw = moduleComponent.categoryComponent.getWidth();
        float left = cx + 4 + (xOffset / 2);
        float right = cx + cw - 4;
        float top = moduleComponent.categoryComponent.getModuleY() + o + 2 * ROW_HEIGHT;
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY < top + dropdownH;
    }

    public boolean isMouseOverSelectedList(float mouseX, float mouseY) {
        if (setting.getBlocks().isEmpty()) return false;
        float dropdownH = getAnimatedDropdownHeight();
        float cx = moduleComponent.categoryComponent.getX();
        float cw = moduleComponent.categoryComponent.getWidth();
        float left = cx + 4 + (xOffset / 2);
        float right = cx + cw - 4;
        float top = moduleComponent.categoryComponent.getModuleY() + o + 2 * ROW_HEIGHT + dropdownH + SELECTED_LIST_GAP;
        float selectedListH = MAX_VISIBLE_SELECTED * ROW_HEIGHT;
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY < top + selectedListH;
    }

    private boolean isMouseOverSelectedList() {
        return isMouseOverSelectedList(lastMouseX, lastMouseY);
    }

    private float getAnimatedDropdownHeight() {
        if (dropdownAnimTimer != null) {
            if (System.currentTimeMillis() - dropdownAnimTimer.last >= ANIMATION_DURATION + 30) {
                dropdownAnimTimer = null;
                dropdownAnimH = dropdownAnimTargetH;
                dropdownAnimStartH = dropdownAnimTargetH;
            } else {
                dropdownAnimH = dropdownAnimTimer.getValueFloat(dropdownAnimStartH, dropdownAnimTargetH, 1);
                if (dropdownAnimH == dropdownAnimTargetH) {
                    dropdownAnimTimer = null;
                    dropdownAnimStartH = dropdownAnimTargetH;
                }
            }
        }
        return dropdownAnimH;
    }

    public float getCurrentHeight() {
        int n = setting.getBlocks().size();
        float selectedH = n == 0 ? 0 : SELECTED_LIST_GAP + Math.min(MAX_VISIBLE_SELECTED, n) * ROW_HEIGHT;
        return 2 * ROW_HEIGHT + getAnimatedDropdownHeight() + selectedH;
    }

    @Override public float getHeightF() { return getCurrentHeight(); }
    @Override public int getHeight() { return Math.round(getHeightF()); }
    @Override public void updateHeight(float n) { this.o = n; }
    @Override public float getOffset() { return this.o; }
    @Override public boolean isBaseVisible() { return setting.visible; }

    @Override
    public void render() {
        Layout L = layout(false);
        renderLabel(L);
        renderSearchBox(L);
        renderDropdown(L);
        renderSelectedBlocks(L);
    }

    private void renderLabel(Layout L) {
        int labelColor = Theme.getGradient(Theme.descriptor[0], Theme.descriptor[1], 0);
        int n = setting.getBlocks().size();
        String label = setting.getName() + (n > 0 ? " (" + n + ")" : "");
        drawScaledText(label, L.left, centeredScaledTextY(L.cy + o, ROW_HEIGHT), labelColor);
    }

    private void renderSearchBox(Layout L) {
        ensureSearchField();
        float boxTop = L.searchTop + 1;
        float boxBot = L.searchTop + ROW_HEIGHT - 1;
        searchField.render(L.left, boxTop, L.right, boxBot);
    }

    private void renderDropdown(Layout L) {
        int rowCount = getDropdownRowCount();
        float dropdownH = getAnimatedDropdownHeight();
        float scrollOffset = moduleComponent.categoryComponent.moduleY - L.cy;
        if (dropdownH <= 0 || rowCount == 0) return;
        float dropdownTopScreen = L.contentTop + scrollOffset;
        RenderUtils.scissorPushGui(L.left, dropdownTopScreen, L.right - L.left, dropdownH);
        float offsetPx = dropdownScrollAnim.getValue();
        int firstRow = (int) (offsetPx / ROW_HEIGHT);
        int end = Math.min(firstRow + MAX_VISIBLE_RESULTS + 1, rowCount);
        int rowUnderMouse = -1;
        if (lastMouseX >= L.left && lastMouseX <= L.right && lastMouseY >= dropdownTopScreen && lastMouseY < dropdownTopScreen + dropdownH) {
            float relY = lastMouseY - dropdownTopScreen;
            rowUnderMouse = (int) ((relY + offsetPx) / ROW_HEIGHT);
            if (rowUnderMouse < 0 || rowUnderMouse >= rowCount) rowUnderMouse = -1;
        }
        if (expandedGroupId != null) {
            List<BlockSearchIndex.BlockEntry> variants = expandedVariants;
            String groupName = !variants.isEmpty() ? variants.get(0).displayName : expandedGroupId;
            for (int i = firstRow; i < end; i++) {
                float rowTop = L.contentTop - offsetPx + i * ROW_HEIGHT;
                int bg = (i == rowUnderMouse) ? 0xFF2A2A3C : ((i % 2 == 0) ? 0xFF1A1A2A : 0xFF1E1E2E);
                if (i == 0) {
                    renderBackRow(L.left, L.right, rowTop, bg, groupName);
                } else if (i == 1) {
                    ItemStack cycleIcon = getExpandedAllCyclingIcon();
                    renderBlockRow(groupName + " (All)", cycleIcon, L.left, L.right, rowTop, bg, false);
                } else {
                    BlockSearchIndex.BlockEntry entry = variants.get(i - 2);
                    renderBlockRow(entry.displayName, entry.toItemStack(), L.left, L.right, rowTop, bg, false);
                }
            }
        } else {
            for (int i = firstRow; i < end; i++) {
                BlockSearchIndex.GroupedBlockResult result = cachedResults.get(i);
                float rowTop = L.contentTop - offsetPx + i * ROW_HEIGHT;
                int bg = (i == rowUnderMouse) ? 0xFF2A2A3C : ((i % 2 == 0) ? 0xFF1A1A2A : 0xFF1E1E2E);
                if (result.isSingleVariant()) {
                    BlockSearchIndex.BlockEntry single = result.variants.get(0);
                    renderBlockRow(single.displayName, single.toItemStack(), L.left, L.right, rowTop, bg, false);
                } else {
                    renderBlockRow(result.getGroupLabel(), result.getCyclingIcon(), L.left, L.right, rowTop, bg, false);
                }
            }
        }
        RenderUtils.scissorPop();
    }

    private ItemStack getExpandedAllCyclingIcon() {
        if (expandedVariants.isEmpty()) return null;
        int idx = (int) ((System.currentTimeMillis() / 1000) % expandedVariants.size());
        return expandedVariants.get(idx).toItemStack();
    }

    private void renderBackRow(float left, float right, float rowTop, int bgColor, String groupName) {
        RenderUtils.drawRect(left, rowTop, right, rowTop + ROW_HEIGHT, bgColor);
        ensureProcessedArrowTexture();
        if (processedArrow != null) {
            float iconX = left + 2;
            float iconY = rowTop + (ROW_HEIGHT - CLOSE_SIZE) / 2f;
            Minecraft.getMinecraft().getTextureManager().bindTexture(processedArrow);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager.color(1f, 1f, 1f, 1f);
            GL11.glPushMatrix();
            GL11.glTranslatef(iconX, iconY, 0);
            net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, CLOSE_SIZE, CLOSE_SIZE, CLOSE_SIZE, CLOSE_SIZE);
            GL11.glPopMatrix();
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.disableBlend();
        }
        drawScaledText(groupName != null ? groupName : "Back", left + 13, centeredScaledTextY(rowTop, ROW_HEIGHT), 0xFFCCCCCC);
    }

    private void renderSelectedBlocks(Layout L) {
        List<String> blocks = setting.getBlocks();
        if (blocks.isEmpty()) return;
        if (selectedRowsCache == null || selectedRowsCache.size() != blocks.size()) {
            selectedRowsCache = new ArrayList<>();
            for (String storageId : blocks) {
                String displayName = BlockSearchIndex.getDisplayName(storageId);
                ItemStack stack = BlockSearchIndex.getItemStack(storageId);
                List<ItemStack> cyclingStacks = null;
                if (BlockSearchIndex.isWildcard(storageId)) {
                    String registryId = BlockSearchIndex.getRegistryId(storageId);
                    List<BlockSearchIndex.BlockEntry> variants = BlockSearchIndex.getVariants(registryId);
                    if (!variants.isEmpty()) {
                        cyclingStacks = new ArrayList<>();
                        for (BlockSearchIndex.BlockEntry e : variants) cyclingStacks.add(e.toItemStack());
                    }
                }
                selectedRowsCache.add(new CachedSelectedRow(storageId, displayName, stack, cyclingStacks));
            }
        }
        float dropdownH = getAnimatedDropdownHeight();
        float selY = L.contentTop + dropdownH + SELECTED_LIST_GAP;
        float selectedListH = MAX_VISIBLE_SELECTED * ROW_HEIGHT;
        RenderUtils.scissorPushGui(L.left, selY, L.right - L.left, selectedListH);
        float offsetPx = selectedScrollAnim.getValue();
        int firstRow = (int) (offsetPx / ROW_HEIGHT);
        int end = Math.min(firstRow + MAX_VISIBLE_SELECTED + 1, selectedRowsCache.size());
        ensureProcessedCloseTexture();
        for (int i = firstRow; i < end; i++) {
            CachedSelectedRow row = selectedRowsCache.get(i);
            float rowTop = selY - offsetPx + i * ROW_HEIGHT;
            int bg = (i % 2 == 0) ? 0xFF1A1A2A : 0xFF1E1E2E;
            ItemStack icon = row.cyclingStacks != null && !row.cyclingStacks.isEmpty()
                ? row.cyclingStacks.get((int) ((System.currentTimeMillis() / 1000) % row.cyclingStacks.size()))
                : row.stack;
            renderBlockRow(row.displayName, icon, L.left, L.right, rowTop, bg, true);
        }
        RenderUtils.scissorPop();
    }

    private void renderBlockRow(String label, ItemStack stack, float left, float right, float rowTop, int bgColor, boolean showClose) {
        RenderUtils.drawRect(left, rowTop, right, rowTop + ROW_HEIGHT, bgColor);
        renderItemInRow(stack, left + 2, rowTop);
        drawScaledText(label != null ? label : "", left + 13, centeredScaledTextY(rowTop, ROW_HEIGHT), 0xFFCCCCCC);
        if (showClose && processedClose != null) {
            Color closeColor = new Color(Theme.getGradient(Theme.hiddenBind[0], Theme.hiddenBind[1], 0), true);
            float closeX = right - CLOSE_SIZE - CLOSE_PAD;
            float closeY = rowTop + (ROW_HEIGHT - CLOSE_SIZE) / 2f;
            Minecraft.getMinecraft().getTextureManager().bindTexture(processedClose);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager.color(closeColor.getRed() / 255f, closeColor.getGreen() / 255f, closeColor.getBlue() / 255f, closeColor.getAlpha() / 255f);
            GL11.glPushMatrix();
            GL11.glTranslatef(closeX, closeY, 0);
            net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, CLOSE_SIZE, CLOSE_SIZE, CLOSE_SIZE, CLOSE_SIZE);
            GL11.glPopMatrix();
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.disableBlend();
        }
    }

    private void renderItemInRow(ItemStack stack, float x, float rowTop) {
        if (stack == null) return;
        RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
        double scale = 0.55;
        float itemH = (float) (16 * scale);
        float pad = (ROW_HEIGHT - itemH) / 2f;
        float itemY = rowTop + pad;
        float px = (float) (x / scale);
        float py = (float) (itemY / scale);
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, scale);
        GlStateManager.translate(px, py, 0);
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.disableBlend();
        renderItem.renderItemAndEffectIntoGUI(stack, 0, 0);
        GlStateManager.enableBlend();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (searchField != null) {
            searchField.tickCursor();
        }
        updateDropdownAnimation();
    }

    @Override
    public boolean onClick(int mouseX, int mouseY, int button) {
        if (!moduleComponent.isOpened || !moduleComponent.isVisible(this)) return false;
        Layout L = layout(true);
        if (button == 0 && handleResultClick(mouseX, mouseY, L)) return true;
        if (button == 0 && handleSelectedRemoveClick(mouseX, mouseY, L)) return true;
        if (handleSearchFocusClick(mouseX, mouseY, L)) return true;
        if (searchField != null && searchField.isFocused()) {
            searchField.setFocused(false);
            updateDropdownAnimation();
        }
        return false;
    }

    private boolean handleResultClick(int mouseX, int mouseY, Layout L) {
        if (!isSearchFocused()) return false;
        int rowCount = getDropdownRowCount();
        if (rowCount == 0) return false;
        float offsetPx = dropdownScrollAnim.getValue();
        float relY = mouseY - L.contentTop;
        int rowIdx = (int) ((relY + offsetPx) / ROW_HEIGHT);
        if (rowIdx < 0 || rowIdx >= rowCount || mouseX <= L.left || mouseX >= L.right) return false;
        float rowTop = L.contentTop - offsetPx + rowIdx * ROW_HEIGHT;
        if (mouseY < rowTop || mouseY >= rowTop + ROW_HEIGHT) return false;

        if (expandedGroupId != null) {
            if (rowIdx == 0) {
                expandedGroupId = null;
                expandedVariants = Collections.emptyList();
                dropdownScrollAnim.reset(0);
                updateDropdownAnimation();
                moduleComponent.updateSettingPositions();
                return true;
            }
            if (rowIdx == 1) {
                setting.addBlock(expandedGroupId + ":*");
                closeDropdownAndClearExpansion();
                return true;
            }
            int variantIdx = rowIdx - 2;
            if (variantIdx >= 0 && variantIdx < expandedVariants.size()) {
                BlockSearchIndex.BlockEntry entry = expandedVariants.get(variantIdx);
                setting.addBlock(entry.storageId);
                closeDropdownAndClearExpansion();
                return true;
            }
            return true;
        }

        BlockSearchIndex.GroupedBlockResult result = cachedResults.get(rowIdx);
        if (result.isSingleVariant()) {
            setting.addBlock(result.variants.get(0).storageId);
            closeDropdownAndClearExpansion();
            return true;
        }
        expandedGroupId = result.registryId;
        expandedVariants = new ArrayList<>();
        for (BlockSearchIndex.BlockEntry e : result.variants) {
            if (!setting.contains(e.storageId)) expandedVariants.add(e);
        }
        dropdownScrollAnim.reset(0);
        updateDropdownAnimation();
        moduleComponent.updateSettingPositions();
        return true;
    }

    private void closeDropdownAndClearExpansion() {
        searchField.setText("");
        searchField.setFocused(false);
        cachedResults = Collections.emptyList();
        expandedGroupId = null;
        expandedVariants = Collections.emptyList();
        dropdownScrollAnim.reset(0);
        markUnsaved();
        selectedRowsCache = null;
        updateDropdownAnimation();
        moduleComponent.updateSettingPositions();
    }

    private boolean handleSelectedRemoveClick(int mouseX, int mouseY, Layout L) {
        float dropdownH = getAnimatedDropdownHeight();
        float selY = L.contentTop + dropdownH + SELECTED_LIST_GAP;
        float offsetPx = selectedScrollAnim.getValue();
        List<String> blocks = new ArrayList<>(setting.getBlocks());
        for (int i = 0; i < blocks.size(); i++) {
            float rowTop = selY - offsetPx + i * ROW_HEIGHT;
            float closeX = L.right - CLOSE_SIZE - CLOSE_PAD;
            float closeY = rowTop + (ROW_HEIGHT - CLOSE_SIZE) / 2f;
            if (mouseX >= closeX && mouseX <= closeX + CLOSE_SIZE && mouseY >= closeY && mouseY <= closeY + CLOSE_SIZE) {
                setting.removeBlock(blocks.get(i));
                markUnsaved();
                selectedRowsCache = null;
                updateDropdownAnimation();
                moduleComponent.updateSettingPositions();
                return true;
            }
        }
        return false;
    }

    private boolean handleSearchFocusClick(int mouseX, int mouseY, Layout L) {
        ensureSearchField();
        float boxTop = L.contentTop - ROW_HEIGHT + 1f;
        float boxBottom = L.contentTop - 1f;
        if (searchField.contains(mouseX, mouseY, L.left, boxTop, L.right, boxBottom)) {
            searchField.setFocused(true);
            if (!searchField.getText().isEmpty() && cachedResults.isEmpty())
                cachedResults = BlockSearchIndex.searchGrouped(searchField.getText(), setting);
            updateDropdownAnimation();
            return true;
        }
        return false;
    }

    @Override
    public void onScroll(int scroll) {
        if (!moduleComponent.isOpened) return;
        float scrollSpeed = (float) Gui.scrollSpeed.getInput();
        float delta = scrollSpeed * (scroll / 120f);
        if (isMouseOverDropdown()) {
            if (delta != 0f) dropdownScrollAnim.extend(-delta);
            float maxScrollPx = Math.max(0f, (getDropdownRowCount() - MAX_VISIBLE_RESULTS) * ROW_HEIGHT);
            dropdownScrollAnim.clampTarget(0f, maxScrollPx);
            return;
        }
        if (isMouseOverSelectedList() && setting.getBlocks().size() > MAX_VISIBLE_SELECTED) {
            if (delta != 0f) selectedScrollAnim.extend(-delta);
            float maxScrollPx = Math.max(0f, (setting.getBlocks().size() - MAX_VISIBLE_SELECTED) * ROW_HEIGHT);
            selectedScrollAnim.clampTarget(0f, maxScrollPx);
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (!moduleComponent.isOpened) return;
        if (keyCode == Keyboard.KEY_ESCAPE && isSearchFocused()) {
            if (expandedGroupId != null) {
                expandedGroupId = null;
                expandedVariants = Collections.emptyList();
                dropdownScrollAnim.reset(0);
                updateDropdownAnimation();
                moduleComponent.updateSettingPositions();
            } else {
                unfocusSearch();
            }
            return;
        }
        ensureSearchField();
        if (searchField.textboxKeyTyped(typedChar, keyCode)) {
            expandedGroupId = null;
            expandedVariants = Collections.emptyList();
            cachedResults = BlockSearchIndex.searchGrouped(searchField.getText(), setting);
            dropdownScrollAnim.reset(0);
            updateDropdownAnimation();
            moduleComponent.updateSettingPositions();
        }
    }

    @Override
    public void onGuiClosed() {
        if (searchField != null) {
            searchField.setFocused(false);
            searchField.setText("");
        }
        cachedResults = Collections.emptyList();
        expandedGroupId = null;
        expandedVariants = Collections.emptyList();
        selectedRowsCache = null;
        dropdownScrollAnim.reset(0);
        selectedScrollAnim.reset(0);
        dropdownAnimTimer = null;
        dropdownAnimH = 0;
        dropdownAnimStartH = 0;
        dropdownAnimTargetH = 0;
    }

    private void markUnsaved() {
        if (Raven.currentProfile != null)
            Raven.currentProfile.getModule().saved = false;
    }

    private static void ensureProcessedCloseTexture() {
        if (processedClose == null)
            processedClose = RenderUtils.buildWhiteMaskedTexture("/assets/keystrokesmod/textures/gui/close.png", "raven_close_white", CLOSE_ICON);
    }

    private static void ensureProcessedArrowTexture() {
        if (processedArrow == null)
            processedArrow = RenderUtils.buildWhiteMaskedTexture("/assets/keystrokesmod/textures/gui/arrow_left.png", "raven_arrow_left_white", ARROW_ICON);
    }
}
