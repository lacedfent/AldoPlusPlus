package keystrokesmod.clickgui.components.impl;

import keystrokesmod.Raven;
import keystrokesmod.clickgui.animation.ScrollOffsetAnimation;
import keystrokesmod.clickgui.components.Component;
import keystrokesmod.clickgui.components.FocusableTextComponent;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.module.setting.impl.ItemListSetting;
import keystrokesmod.utility.ItemSearchIndex;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.Timer;
import keystrokesmod.utility.font.RavenFontRenderer;
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

public class ItemSearchComponent extends Component implements FocusableTextComponent {
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

    public final ItemListSetting setting;
    public final ModuleComponent moduleComponent;
    public float o;
    public float xOffset;

    private ClickGuiTextField searchField;
    private final ScrollOffsetAnimation dropdownScrollAnim = new ScrollOffsetAnimation(200);
    private final ScrollOffsetAnimation selectedScrollAnim = new ScrollOffsetAnimation(200);
    private List<ItemSearchIndex.GroupedItemResult> cachedResults = Collections.emptyList();
    private String expandedGroupId;
    private String expandedGroupLabel;
    private String expandedAllSelectionStorageId;
    private List<ItemSearchIndex.ItemEntry> expandedVariants = Collections.emptyList();

    private float lastMouseX;
    private float lastMouseY;

    private Timer dropdownAnimTimer;
    private float dropdownAnimStartH;
    private float dropdownAnimTargetH;
    private float dropdownAnimH;

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

    private static final class Layout {
        float cx;
        float cy;
        float cw;
        float left;
        float right;
        float searchTop;
        float contentTop;
    }

    private List<CachedSelectedRow> selectedRowsCache;

    public ItemSearchComponent(ItemListSetting setting, ModuleComponent moduleComponent, float o) {
        this.setting = setting;
        this.moduleComponent = moduleComponent;
        this.o = o;
    }

    private Layout layout(boolean useModuleY) {
        Layout layout = new Layout();
        layout.cx = moduleComponent.categoryComponent.getX();
        layout.cy = useModuleY ? moduleComponent.categoryComponent.getModuleY() : moduleComponent.categoryComponent.getY();
        layout.cw = moduleComponent.categoryComponent.getWidth();
        layout.left = layout.cx + 4 + (xOffset / 2);
        layout.right = layout.cx + layout.cw - 4;
        layout.searchTop = layout.cy + o + ROW_HEIGHT;
        layout.contentTop = layout.cy + o + 2 * ROW_HEIGHT;
        return layout;
    }

    private static float centeredScaledTextY(float top, float height) {
        return top + (height - Gui.getClickGuiSettingFontRenderer().getFontHeight() * STATIC_TEXT_SCALE) / 2f;
    }

    private static void drawScaledText(String text, float x, float y, int color) {
        RavenFontRenderer renderer = Gui.getClickGuiSettingFontRenderer();
        GL11.glPushMatrix();
        GL11.glScaled(STATIC_TEXT_SCALE, STATIC_TEXT_SCALE, STATIC_TEXT_SCALE);
        renderer.drawString(text, x / STATIC_TEXT_SCALE, y / STATIC_TEXT_SCALE, color, true);
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
            searchField = new ClickGuiTextField("Search items...", 128, STATIC_TEXT_SCALE);
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
        if (expandedGroupId != null) {
            return 2 + expandedVariants.size();
        }
        return cachedResults.size();
    }

    public boolean isMouseOverDropdown(float mouseX, float mouseY) {
        if (getDropdownRowCount() == 0) {
            return false;
        }

        float dropdownHeight = getAnimatedDropdownHeight();
        if (dropdownHeight <= 0) {
            return false;
        }

        float cx = moduleComponent.categoryComponent.getX();
        float cw = moduleComponent.categoryComponent.getWidth();
        float left = cx + 4 + (xOffset / 2);
        float right = cx + cw - 4;
        float top = moduleComponent.categoryComponent.getModuleY() + o + 2 * ROW_HEIGHT;
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY < top + dropdownHeight;
    }

    public boolean isMouseOverSelectedList(float mouseX, float mouseY) {
        if (setting.getItems().isEmpty()) {
            return false;
        }

        float dropdownHeight = getAnimatedDropdownHeight();
        float cx = moduleComponent.categoryComponent.getX();
        float cw = moduleComponent.categoryComponent.getWidth();
        float left = cx + 4 + (xOffset / 2);
        float right = cx + cw - 4;
        float top = moduleComponent.categoryComponent.getModuleY() + o + 2 * ROW_HEIGHT + dropdownHeight + SELECTED_LIST_GAP;
        float selectedListHeight = Math.min(MAX_VISIBLE_SELECTED, setting.getItems().size()) * ROW_HEIGHT;
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY < top + selectedListHeight;
    }

    public boolean capturesCategoryScroll(float mouseX, float mouseY) {
        if (!moduleComponent.isOpened || !moduleComponent.isVisible(this)) {
            return false;
        }

        if (isMouseOverDropdown(mouseX, mouseY) && getDropdownRowCount() > MAX_VISIBLE_RESULTS) {
            return true;
        }

        return isMouseOverSelectedList(mouseX, mouseY) && setting.getItems().size() > MAX_VISIBLE_SELECTED;
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
            }
            else {
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
        int selected = setting.getItems().size();
        float selectedHeight = selected == 0 ? 0 : SELECTED_LIST_GAP + Math.min(MAX_VISIBLE_SELECTED, selected) * ROW_HEIGHT;
        return 2 * ROW_HEIGHT + getAnimatedDropdownHeight() + selectedHeight;
    }

    @Override
    public float getHeightF() {
        return getCurrentHeight();
    }

    @Override
    public int getHeight() {
        return Math.round(getHeightF());
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
        return setting.visible;
    }

    @Override
    public void render() {
        Layout layout = layout(false);
        renderLabel(layout);
        renderSearchBox(layout);
        renderDropdown(layout);
        renderSelectedItems(layout);
    }

    private void renderLabel(Layout layout) {
        int labelColor = Theme.getGradient(Theme.descriptor[0], Theme.descriptor[1], 0);
        int count = setting.getItems().size();
        String label = setting.getName() + (count > 0 ? " (" + count + ")" : "");
        drawScaledText(label, layout.left, centeredScaledTextY(layout.cy + o, ROW_HEIGHT), labelColor);
    }

    private void renderSearchBox(Layout layout) {
        ensureSearchField();
        float boxTop = layout.searchTop + 1;
        float boxBottom = layout.searchTop + ROW_HEIGHT - 1;
        searchField.render(layout.left, boxTop, layout.right, boxBottom);
    }

    private void renderDropdown(Layout layout) {
        int rowCount = getDropdownRowCount();
        float dropdownHeight = getAnimatedDropdownHeight();
        float scrollOffset = moduleComponent.categoryComponent.moduleY - layout.cy;
        if (dropdownHeight <= 0 || rowCount == 0) {
            return;
        }

        float dropdownTopScreen = layout.contentTop + scrollOffset;
        RenderUtils.scissorPushGui(layout.left, dropdownTopScreen, layout.right - layout.left, dropdownHeight);
        float offsetPx = dropdownScrollAnim.getValue();
        int firstRow = (int) (offsetPx / ROW_HEIGHT);
        int end = Math.min(firstRow + MAX_VISIBLE_RESULTS + 1, rowCount);
        int rowUnderMouse = -1;

        if (lastMouseX >= layout.left && lastMouseX <= layout.right && lastMouseY >= dropdownTopScreen && lastMouseY < dropdownTopScreen + dropdownHeight) {
            float relY = lastMouseY - dropdownTopScreen;
            rowUnderMouse = (int) ((relY + offsetPx) / ROW_HEIGHT);
            if (rowUnderMouse < 0 || rowUnderMouse >= rowCount) {
                rowUnderMouse = -1;
            }
        }

        if (expandedGroupId != null) {
            List<ItemSearchIndex.ItemEntry> variants = expandedVariants;
            String groupName = expandedGroupLabel != null ? expandedGroupLabel : expandedGroupId;
            for (int i = firstRow; i < end; i++) {
                float rowTop = layout.contentTop - offsetPx + i * ROW_HEIGHT;
                int bg = (i == rowUnderMouse) ? 0xFF2A2A3C : ((i % 2 == 0) ? 0xFF1A1A2A : 0xFF1E1E2E);
                if (i == 0) {
                    renderBackRow(layout.left, layout.right, rowTop, bg, groupName);
                }
                else if (i == 1) {
                    ItemStack cycleIcon = getExpandedAllCyclingIcon();
                    renderItemRow(groupName + " (All)", cycleIcon, layout.left, layout.right, rowTop, bg, false);
                }
                else {
                    ItemSearchIndex.ItemEntry entry = variants.get(i - 2);
                    renderItemRow(entry.displayName, entry.toItemStack(), layout.left, layout.right, rowTop, bg, false);
                }
            }
        }
        else {
            for (int i = firstRow; i < end; i++) {
                ItemSearchIndex.GroupedItemResult result = cachedResults.get(i);
                float rowTop = layout.contentTop - offsetPx + i * ROW_HEIGHT;
                int bg = (i == rowUnderMouse) ? 0xFF2A2A3C : ((i % 2 == 0) ? 0xFF1A1A2A : 0xFF1E1E2E);
                if (result.isSingleVariant()) {
                    ItemSearchIndex.ItemEntry single = result.variants.get(0);
                    renderItemRow(single.displayName, single.toItemStack(), layout.left, layout.right, rowTop, bg, false);
                }
                else {
                    renderItemRow(result.getGroupLabel(), result.getCyclingIcon(), layout.left, layout.right, rowTop, bg, false);
                }
            }
        }

        RenderUtils.scissorPop();
    }

    private ItemStack getExpandedAllCyclingIcon() {
        if (expandedVariants.isEmpty()) {
            return null;
        }
        int idx = (int) ((System.currentTimeMillis() / 1000) % expandedVariants.size());
        return expandedVariants.get(idx).toItemStack();
    }

    private void renderBackRow(float left, float right, float rowTop, int bgColor, String groupName) {
        RenderUtils.drawRect(left, rowTop, right, rowTop + ROW_HEIGHT, bgColor);
        ensureProcessedArrowTexture();
        if (processedArrow != null) {
            float iconX = left + 2;
            float iconY = rowTop + (ROW_HEIGHT - CLOSE_SIZE) / 2f;
            boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
            boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            RenderUtils.prepareGuiTextureRenderState();
            Minecraft.getMinecraft().getTextureManager().bindTexture(processedArrow);
            GlStateManager.color(1f, 1f, 1f, 1f);
            GL11.glPushMatrix();
            GL11.glTranslatef(iconX, iconY, 0);
            net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, CLOSE_SIZE, CLOSE_SIZE, CLOSE_SIZE, CLOSE_SIZE);
            GL11.glPopMatrix();
            GlStateManager.color(1f, 1f, 1f, 1f);
            if (blendEnabled) {
                GlStateManager.enableBlend();
            } else {
                GlStateManager.disableBlend();
            }
            if (depthEnabled) {
                GlStateManager.enableDepth();
            } else {
                GlStateManager.disableDepth();
            }
            GlStateManager.depthMask(depthMask);
        }
        drawScaledText(groupName != null ? groupName : "Back", left + 13, centeredScaledTextY(rowTop, ROW_HEIGHT), 0xFFCCCCCC);
    }

    private void renderSelectedItems(Layout layout) {
        List<String> items = setting.getItems();
        if (items.isEmpty()) {
            return;
        }

        if (selectedRowsCache == null || selectedRowsCache.size() != items.size()) {
            selectedRowsCache = new ArrayList<CachedSelectedRow>();
            for (String storageId : items) {
                String displayName = ItemSearchIndex.getDisplayName(storageId);
                ItemStack stack = ItemSearchIndex.getItemStack(storageId);
                List<ItemStack> cyclingStacks = null;
                if (ItemSearchIndex.isGroupedSelection(storageId)) {
                    List<ItemSearchIndex.ItemEntry> variants = ItemSearchIndex.getSelectionVariants(storageId);
                    if (!variants.isEmpty()) {
                        cyclingStacks = new ArrayList<ItemStack>();
                        for (ItemSearchIndex.ItemEntry variant : variants) {
                            cyclingStacks.add(variant.toItemStack());
                        }
                    }
                }
                selectedRowsCache.add(new CachedSelectedRow(storageId, displayName, stack, cyclingStacks));
            }
        }

        float dropdownHeight = getAnimatedDropdownHeight();
        float selectedY = layout.contentTop + dropdownHeight + SELECTED_LIST_GAP;
        float selectedListHeight = Math.min(MAX_VISIBLE_SELECTED, selectedRowsCache.size()) * ROW_HEIGHT;
        float scrollOffset = moduleComponent.categoryComponent.moduleY - layout.cy;
        RenderUtils.scissorPushGui(layout.left, selectedY + scrollOffset, layout.right - layout.left, selectedListHeight);
        float offsetPx = selectedScrollAnim.getValue();
        int firstRow = (int) (offsetPx / ROW_HEIGHT);
        int end = Math.min(firstRow + MAX_VISIBLE_SELECTED + 1, selectedRowsCache.size());
        ensureProcessedCloseTexture();

        for (int i = firstRow; i < end; i++) {
            CachedSelectedRow row = selectedRowsCache.get(i);
            float rowTop = selectedY - offsetPx + i * ROW_HEIGHT;
            int bg = (i % 2 == 0) ? 0xFF1A1A2A : 0xFF1E1E2E;
            ItemStack icon = row.cyclingStacks != null && !row.cyclingStacks.isEmpty()
                ? row.cyclingStacks.get((int) ((System.currentTimeMillis() / 1000) % row.cyclingStacks.size()))
                : row.stack;
            renderItemRow(row.displayName, icon, layout.left, layout.right, rowTop, bg, true);
        }

        RenderUtils.scissorPop();
    }

    private void renderItemRow(String label, ItemStack stack, float left, float right, float rowTop, int bgColor, boolean showClose) {
        RenderUtils.drawRect(left, rowTop, right, rowTop + ROW_HEIGHT, bgColor);
        renderItemInRow(stack, left + 2, rowTop);
        drawScaledText(label != null ? label : "", left + 13, centeredScaledTextY(rowTop, ROW_HEIGHT), 0xFFCCCCCC);
        if (showClose && processedClose != null) {
            Color closeColor = new Color(Theme.getGradient(Theme.hiddenBind[0], Theme.hiddenBind[1], 0), true);
            float closeX = right - CLOSE_SIZE - CLOSE_PAD;
            float closeY = rowTop + (ROW_HEIGHT - CLOSE_SIZE) / 2f;
            boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
            boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            RenderUtils.prepareGuiTextureRenderState();
            Minecraft.getMinecraft().getTextureManager().bindTexture(processedClose);
            GlStateManager.color(closeColor.getRed() / 255f, closeColor.getGreen() / 255f, closeColor.getBlue() / 255f, closeColor.getAlpha() / 255f);
            GL11.glPushMatrix();
            GL11.glTranslatef(closeX, closeY, 0);
            net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, CLOSE_SIZE, CLOSE_SIZE, CLOSE_SIZE, CLOSE_SIZE);
            GL11.glPopMatrix();
            GlStateManager.color(1f, 1f, 1f, 1f);
            if (blendEnabled) {
                GlStateManager.enableBlend();
            } else {
                GlStateManager.disableBlend();
            }
            if (depthEnabled) {
                GlStateManager.enableDepth();
            } else {
                GlStateManager.disableDepth();
            }
            GlStateManager.depthMask(depthMask);
        }
    }

    private void renderItemInRow(ItemStack stack, float x, float rowTop) {
        if (stack == null) {
            return;
        }

        RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
        double scale = 0.55;
        float itemHeight = (float) (16 * scale);
        float pad = (ROW_HEIGHT - itemHeight) / 2f;
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
        if (!moduleComponent.isOpened || !moduleComponent.isVisible(this)) {
            return false;
        }

        Layout layout = layout(true);
        if (button == 0 && handleResultClick(mouseX, mouseY, layout)) {
            return true;
        }
        if (button == 0 && handleSelectedRemoveClick(mouseX, mouseY, layout)) {
            return true;
        }
        if (handleSearchFocusClick(mouseX, mouseY, layout)) {
            return true;
        }
        if (searchField != null && searchField.isFocused()) {
            searchField.setFocused(false);
            updateDropdownAnimation();
        }
        return false;
    }

    private boolean handleResultClick(int mouseX, int mouseY, Layout layout) {
        if (!isSearchFocused()) {
            return false;
        }

        int rowCount = getDropdownRowCount();
        if (rowCount == 0) {
            return false;
        }

        float offsetPx = dropdownScrollAnim.getValue();
        float relY = mouseY - layout.contentTop;
        int rowIdx = (int) ((relY + offsetPx) / ROW_HEIGHT);
        if (rowIdx < 0 || rowIdx >= rowCount || mouseX <= layout.left || mouseX >= layout.right) {
            return false;
        }

        float rowTop = layout.contentTop - offsetPx + rowIdx * ROW_HEIGHT;
        if (mouseY < rowTop || mouseY >= rowTop + ROW_HEIGHT) {
            return false;
        }

        if (expandedGroupId != null) {
            if (rowIdx == 0) {
                expandedGroupId = null;
                expandedGroupLabel = null;
                expandedAllSelectionStorageId = null;
                expandedVariants = Collections.emptyList();
                dropdownScrollAnim.reset(0);
                updateDropdownAnimation();
                moduleComponent.updateSettingPositions();
                return true;
            }
            if (rowIdx == 1) {
                setting.addItem(expandedAllSelectionStorageId != null ? expandedAllSelectionStorageId : expandedGroupId + ":*");
                closeDropdownAndClearExpansion();
                return true;
            }

            int variantIdx = rowIdx - 2;
            if (variantIdx >= 0 && variantIdx < expandedVariants.size()) {
                ItemSearchIndex.ItemEntry entry = expandedVariants.get(variantIdx);
                setting.addItem(entry.storageId);
                closeDropdownAndClearExpansion();
                return true;
            }
            return true;
        }

        ItemSearchIndex.GroupedItemResult result = cachedResults.get(rowIdx);
        if (result.isSingleVariant()) {
            setting.addItem(result.variants.get(0).storageId);
            closeDropdownAndClearExpansion();
            return true;
        }

        expandedGroupId = result.registryId;
        expandedGroupLabel = result.getGroupDisplayName();
        expandedAllSelectionStorageId = result.getAllSelectionStorageId();
        expandedVariants = new ArrayList<ItemSearchIndex.ItemEntry>();
        for (ItemSearchIndex.ItemEntry entry : result.variants) {
            if (!setting.containsItem(entry.storageId)) {
                expandedVariants.add(entry);
            }
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
        expandedGroupLabel = null;
        expandedAllSelectionStorageId = null;
        expandedVariants = Collections.emptyList();
        dropdownScrollAnim.reset(0);
        markUnsaved();
        selectedRowsCache = null;
        updateDropdownAnimation();
        moduleComponent.updateSettingPositions();
    }

    private boolean handleSelectedRemoveClick(int mouseX, int mouseY, Layout layout) {
        float dropdownHeight = getAnimatedDropdownHeight();
        float selectedY = layout.contentTop + dropdownHeight + SELECTED_LIST_GAP;
        float offsetPx = selectedScrollAnim.getValue();
        List<String> items = new ArrayList<String>(setting.getItems());
        for (int i = 0; i < items.size(); i++) {
            float rowTop = selectedY - offsetPx + i * ROW_HEIGHT;
            float closeX = layout.right - CLOSE_SIZE - CLOSE_PAD;
            float closeY = rowTop + (ROW_HEIGHT - CLOSE_SIZE) / 2f;
            if (mouseX >= closeX && mouseX <= closeX + CLOSE_SIZE && mouseY >= closeY && mouseY <= closeY + CLOSE_SIZE) {
                setting.removeItem(items.get(i));
                markUnsaved();
                selectedRowsCache = null;
                updateDropdownAnimation();
                moduleComponent.updateSettingPositions();
                return true;
            }
        }
        return false;
    }

    private boolean handleSearchFocusClick(int mouseX, int mouseY, Layout layout) {
        ensureSearchField();
        float boxTop = layout.contentTop - ROW_HEIGHT + 1f;
        float boxBottom = layout.contentTop - 1f;
        if (searchField.contains(mouseX, mouseY, layout.left, boxTop, layout.right, boxBottom)) {
            searchField.setFocused(true);
            if (!searchField.getText().isEmpty() && cachedResults.isEmpty()) {
                cachedResults = ItemSearchIndex.searchGrouped(searchField.getText(), setting);
            }
            updateDropdownAnimation();
            return true;
        }
        return false;
    }

    @Override
    public void onScroll(int scroll) {
        if (!moduleComponent.isOpened || !moduleComponent.isVisible(this)) {
            return;
        }

        float scrollSpeed = (float) Gui.scrollSpeed.getInput();
        float delta = scrollSpeed * (scroll / 120f);
        if (isMouseOverDropdown()) {
            if (delta != 0f) {
                dropdownScrollAnim.extend(-delta);
            }
            float maxScrollPx = Math.max(0f, (getDropdownRowCount() - MAX_VISIBLE_RESULTS) * ROW_HEIGHT);
            dropdownScrollAnim.clampTarget(0f, maxScrollPx);
            return;
        }
        if (isMouseOverSelectedList() && setting.getItems().size() > MAX_VISIBLE_SELECTED) {
            if (delta != 0f) {
                selectedScrollAnim.extend(-delta);
            }
            float maxScrollPx = Math.max(0f, (setting.getItems().size() - MAX_VISIBLE_SELECTED) * ROW_HEIGHT);
            selectedScrollAnim.clampTarget(0f, maxScrollPx);
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (!moduleComponent.isOpened) {
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE && isSearchFocused()) {
            if (expandedGroupId != null) {
                expandedGroupId = null;
                expandedGroupLabel = null;
                expandedAllSelectionStorageId = null;
                expandedVariants = Collections.emptyList();
                dropdownScrollAnim.reset(0);
                updateDropdownAnimation();
                moduleComponent.updateSettingPositions();
            }
            else {
                unfocusSearch();
            }
            return;
        }

        ensureSearchField();
        if (searchField.textboxKeyTyped(typedChar, keyCode)) {
            expandedGroupId = null;
            expandedGroupLabel = null;
            expandedAllSelectionStorageId = null;
            expandedVariants = Collections.emptyList();
            cachedResults = ItemSearchIndex.searchGrouped(searchField.getText(), setting);
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
        expandedGroupLabel = null;
        expandedAllSelectionStorageId = null;
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
        if (Raven.currentProfile != null) {
            Raven.currentProfile.getModule().saved = false;
        }
    }

    private static void ensureProcessedCloseTexture() {
        if (processedClose == null) {
            processedClose = RenderUtils.buildWhiteMaskedTexture("/assets/keystrokesmod/textures/gui/close.png", "raven_close_white", CLOSE_ICON);
        }
    }

    private static void ensureProcessedArrowTexture() {
        if (processedArrow == null) {
            processedArrow = RenderUtils.buildWhiteMaskedTexture("/assets/keystrokesmod/textures/gui/arrow_left.png", "raven_arrow_left_white", ARROW_ICON);
        }
    }
}
