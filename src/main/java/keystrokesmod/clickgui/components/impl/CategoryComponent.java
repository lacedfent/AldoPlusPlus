package keystrokesmod.clickgui.components.impl;

import keystrokesmod.Raven;
import keystrokesmod.clickgui.animation.ScrollOffsetAnimation;
import keystrokesmod.clickgui.components.Component;
import keystrokesmod.clickgui.components.impl.BlockSearchComponent;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.Timer;
import keystrokesmod.utility.profile.Manager;
import keystrokesmod.utility.profile.Profile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class CategoryComponent {
    public List<ModuleComponent> modules = new CopyOnWriteArrayList<>();
    public Module.category category;
    public boolean opened;
    public float width;
    public float y;
    public float x;
    public float titleHeight;
    public boolean dragging;
    public float xx;
    public float yy;
    public boolean hovering = false;
    public boolean hoveringOverCategory = false;
    public Timer smoothTimer;
    private Timer textTimer;
    /** Visible content height (float). Used for overCategory hit-testing. */
    public float big;

    private static final int TRANSLUCENT_BACKGROUND = new Color(0, 0, 0, 110).getRGB();
    private static final int REGULAR_OUTLINE = new Color(81, 99, 149).getRGB();
    private static final int REGULAR_OUTLINE2 = new Color(97, 67, 133).getRGB();
    private static final int CATEGORY_NAME_COLOR = new Color(220, 220, 220).getRGB();

    private float lastHeight;
    private float lastNamePos;
    private float animationStartNamePos;
    /** Current animated scroll position (float). Driven by scrollAnim each frame. */
    public float moduleY;
    private float screenHeight;
    private float screenWidth;
    private float animationStartHeight;

    /** Persistent Expo-Out scroll animation — mirrors OneConfig/PolyUI behaviour. */
    private final ScrollOffsetAnimation scrollAnim = new ScrollOffsetAnimation(200);

    /** Time of last user interaction (drag, click, scroll). Used for render/input order. */
    public long lastInteractedTime = 0L;

    public CategoryComponent(Module.category category) {
        this.category = category;
        this.width = 92;
        this.x = 5;
        this.moduleY = this.y = 5;
        this.titleHeight = 13;
        float moduleRenderY = this.titleHeight + 3;
        scrollAnim.reset(this.moduleY);

        this.lastHeight = this.y + this.titleHeight + 4;
        this.animationStartHeight = this.lastHeight;

        for (Module mod : Raven.getModuleManager().inCategory(this.category)) {
            ModuleComponent b = new ModuleComponent(mod, this, moduleRenderY);
            this.modules.add(b);
            moduleRenderY += 16;
        }
    }

    public List<ModuleComponent> getModules() {
        return this.modules;
    }

    public void reloadModules(boolean isProfile) {
        this.modules.clear();
        this.titleHeight = 13;
        float moduleRenderY = this.titleHeight + 3;

        if ((this.category == Module.category.profiles && isProfile) || (this.category == Module.category.scripts && !isProfile)) {
            ModuleComponent manager = new ModuleComponent(isProfile ? new Manager() : new keystrokesmod.script.Manager(), this, moduleRenderY);
            this.modules.add(manager);

            if ((Raven.profileManager == null && isProfile) || (Raven.scriptManager == null && !isProfile)) {
                return;
            }

            if (isProfile) {
                for (Profile profile : Raven.profileManager.profiles) {
                    moduleRenderY += 16;
                    ModuleComponent b = new ModuleComponent(profile.getModule(), this, moduleRenderY);
                    this.modules.add(b);
                }
            }
            else {
                Collection<Module> modulesCollection = Raven.scriptManager.scripts.values();
                List<Module> sortedModules = modulesCollection.stream().sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());
                for (Module module : sortedModules) {
                    moduleRenderY += 16;
                    ModuleComponent b = new ModuleComponent(module, this, moduleRenderY);
                    this.modules.add(b);
                }
            }
        }
    }

    public void setX(float newX, boolean limit) {
        if (limit) {
            newX = Math.max(newX, 2);
            newX = Math.min(newX, screenWidth - this.width - 4);
        }
        this.x = newX;
    }

    public void setY(float y, boolean limit) {
        if (limit) {
            y = Math.max(y, 1);
            float maxY = screenHeight - this.titleHeight - 5;
            y = Math.min(y, maxY);
        }

        float scrollOffset = scrollAnim.getTarget() - this.y;
        this.y = y;
        float newTarget = y + scrollOffset;
        this.moduleY = newTarget;
        scrollAnim.reset(newTarget);
    }

    public void overTitle(boolean d) {
        this.dragging = d;
    }

    public boolean isOpened() {
        return this.opened;
    }

    public void mouseClicked(boolean on) {
        this.animationStartHeight = getCurrentAnimatedCategoryHeight();
        this.animationStartNamePos = getCurrentAnimatedNamePos();

        float animationDuration = 250.0f;

        this.opened = on;
        (this.smoothTimer = new Timer(animationDuration)).start();
        (this.textTimer = new Timer(animationDuration)).start();
    }

    public void onScroll(int mouseScrollInput) {
        onScroll(mouseScrollInput, Float.NaN, Float.NaN);
    }

    public void onScroll(int mouseScrollInput, float mouseX, float mouseY) {
        for (ModuleComponent mod : this.modules) {
            mod.onScroll(mouseScrollInput);
        }
        if (!hoveringOverCategory || !this.opened) {
            return;
        }
        if (!Float.isNaN(mouseX) && !Float.isNaN(mouseY)) {
            for (ModuleComponent mod : this.modules) {
                for (Component comp : mod.settings) {
                    if (comp instanceof BlockSearchComponent) {
                        BlockSearchComponent bsc = (BlockSearchComponent) comp;
                        if (bsc.isMouseOverDropdown(mouseX, mouseY) || bsc.isMouseOverSelectedList(mouseX, mouseY))
                            return;
                    }
                }
            }
        }
        this.lastInteractedTime = System.currentTimeMillis();
        float scrollSpeed = (float) Gui.scrollSpeed.getInput();
        float minScrollY = computeMinScrollY();
        float maxScrollY = this.y;
        float delta = scrollSpeed * (mouseScrollInput / 120f);
        if (delta != 0f) {
            scrollAnim.extend(delta);
        }
        scrollAnim.clampTarget(minScrollY, maxScrollY);
    }

    private float getTotalScrollExtentHeightF() {
        float total = 0f;
        for (ModuleComponent c : this.modules) {
            total += c.getScrollExtentHeightF();
        }
        return total;
    }

    private float computeMinScrollY() {
        if (this.modules.isEmpty() || (!this.opened && smoothTimer == null)) {
            return this.y;
        }
        float total = getTotalScrollExtentHeightF();
        float maxModulesHeight = (this.screenHeight * 0.9f) - this.titleHeight - 4;
        float viewport = Math.min(maxModulesHeight, total);
        float overflow = total - viewport;
        if (overflow > 0f) {
            return this.y - overflow;
        }
        return this.y;
    }

    public void render(FontRenderer renderer) {
        this.width = 92;

        if (smoothTimer != null && System.currentTimeMillis() - smoothTimer.last >= 280) {
            smoothTimer = null;
        }
        if (textTimer != null && System.currentTimeMillis() - textTimer.last >= 280) {
            textTimer = null;
        }

        for (ModuleComponent c : this.modules) {
            c.updateAnimationState();
        }

        if (!this.modules.isEmpty() && (this.opened || smoothTimer != null)) {
            float maxModulesHeight = (this.screenHeight * 0.9f) - this.titleHeight - 4;
            float accumulated = 0f;
            for (ModuleComponent c : this.modules) {
                float moduleHeight = c.getHeightF();
                if (accumulated + moduleHeight > maxModulesHeight) {
                    float remaining = maxModulesHeight - accumulated;
                    if (remaining > 0f) {
                        accumulated += remaining;
                    }
                    break;
                }
                accumulated += moduleHeight;
            }
            big = accumulated;
        }
        else if (!this.opened && smoothTimer == null) {
            big = 0f;
        }

        float maxScrollY = this.y;
        float minScrollY = computeMinScrollY();

        scrollAnim.clampTarget(minScrollY, maxScrollY);

        moduleY = scrollAnim.getValue();
        moduleY = Math.max(minScrollY, Math.min(maxScrollY, moduleY));

        if (smoothTimer != null || this.opened) {
            this.updateHeight();
        }

        float middlePos = this.x + this.width / 2 - Minecraft.getMinecraft().fontRendererObj.getStringWidth(this.category.name()) / 2;

        float contentBottom = getCurrentCategoryBottomFromContent();

        float extra;
        if (smoothTimer != null) {
            float targetHeight = this.opened ? contentBottom : (this.y + this.titleHeight + 4);
            extra = smoothTimer.getValueFloat(animationStartHeight, targetHeight, 1);
            if ((this.opened && extra > targetHeight) || (!this.opened && extra < targetHeight)) {
                extra = targetHeight;
            }
        } else {
            extra = contentBottom;
        }

        float targetNamePos = this.opened ? middlePos : (this.x + 12);
        float namePos;
        if (textTimer == null) {
            namePos = targetNamePos;
        } else {
            namePos = textTimer.getValueFloat(animationStartNamePos, targetNamePos, 1);
        }
        this.lastNamePos = namePos;
        this.lastHeight = extra;

        GL11.glPushMatrix();

        RenderUtils.drawRoundedGradientOutlinedRectangle(this.x - 2, this.y, this.x + this.width + 2, extra, 10, TRANSLUCENT_BACKGROUND,
                ((opened || hovering) && Gui.rainBowOutlines.isToggled()) ? RenderUtils.setAlpha(Utils.getChroma(2, 0), 0.5) : REGULAR_OUTLINE, ((opened || hovering) && Gui.rainBowOutlines.isToggled()) ? RenderUtils.setAlpha(Utils.getChroma(2, 700), 0.5) : REGULAR_OUTLINE2);
        renderItemForCategory(this.category, (int) (this.x + 1), (int) (this.y + 4), opened || hovering);
        renderer.drawString(this.category.name(), namePos, this.y + 4, CATEGORY_NAME_COLOR, false);

        float moduleAreaTop = this.y + this.titleHeight + 3;
        float scissorBottom = extra - 2f;
        float moduleAreaHeight = Math.max(0f, scissorBottom - moduleAreaTop);

        if (this.opened || smoothTimer != null) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            RenderUtils.scissor(0, moduleAreaTop, this.x + this.width + 4, moduleAreaHeight);

            float scrollOffset = moduleY - this.y;
            GL11.glPushMatrix();
            GL11.glTranslatef(0f, scrollOffset, 0f);
            for (Component c2 : this.modules) {
                c2.render();
            }
            GL11.glPopMatrix();

            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }

        GL11.glPopMatrix();
    }

    public void updateHeight() {
        float y = this.titleHeight + 3;
        for (Component component : this.modules) {
            component.updateHeight(y);
            y += component.getHeightF();
        }
    }

    public float getX() {
        return this.x;
    }

    public float getY() {
        return this.y;
    }

    public float getModuleY() {
        return this.moduleY;
    }

    public float getWidth() {
        return this.width;
    }

    public void mousePosition(int mouseX, int mouseY, boolean isTopmostUnderCursor) {
        if (this.dragging) {
            float newX = mouseX - this.xx;
            float newY = mouseY - this.yy;

            if (Gui.limitToScreen.isToggled()) {
                newX = Math.max(newX, 2);
                newX = Math.min(newX, screenWidth - this.width - 4);

                newY = Math.max(newY, 1);
                int maxY = (int) (screenHeight - this.titleHeight - 5);
                newY = Math.min(newY, maxY);
            }

            this.setX(newX, false);
            this.setY(newY, false);
        }

        hoveringOverCategory = isTopmostUnderCursor && overCategory(mouseX, mouseY);
        hovering = isTopmostUnderCursor && overTitle(mouseX, mouseY);
    }

    public boolean overTitle(int x, int y) {
        return x >= this.x && x <= this.x + this.width && (float) y >= (float) this.y + 2.0F && y <= this.y + this.titleHeight + 1;
    }

    public boolean overCategory(int x, int y) {
        return x >= this.x - 2 && x <= this.x + this.width + 2 && (float) y >= (float) this.y + 2.0F && y <= this.y + this.titleHeight + big + 1;
    }

    public boolean draggable(int x, int y) {
        return x >= this.x && x <= this.x + this.width && y >= this.y && y <= this.y + this.titleHeight;
    }

    public boolean overRect(int x, int y) {
        return x >= this.x - 2 && x <= this.x + this.width + 2 && y >= this.y && y <= lastHeight;
    }

    private void renderItemForCategory(Module.category category, int x, int y, boolean enchant) {
        RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
        double scale = 0.55;
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, scale);
        ItemStack itemStack = null;
        switch (category) {
            case combat:
                itemStack = new ItemStack(Items.diamond_sword);
                break;
            case movement:
                itemStack = new ItemStack(Items.diamond_boots);
                break;
            case player:
                itemStack = new ItemStack(Items.golden_apple);
                break;
            case world:
                itemStack = new ItemStack(Items.filled_map);
                break;
            case render:
                itemStack = new ItemStack(Items.ender_eye);
                break;
            case minigames:
                itemStack = new ItemStack(Items.gold_ingot);
                break;
            case fun:
                itemStack = new ItemStack(Items.slime_ball);
                break;
            case other:
                itemStack = new ItemStack(Items.clock);
                break;
            case client:
                itemStack = new ItemStack(Items.compass);
                break;
            case profiles:
                itemStack = new ItemStack(Items.book);
                break;
            case scripts:
                itemStack = new ItemStack(Items.redstone);
                break;
        }
        if (itemStack != null) {
            if (enchant) {
                if (category != Module.category.player) {
                    itemStack.addEnchantment(Enchantment.unbreaking, 2);
                } else {
                    itemStack.setItemDamage(1);
                }
            }
            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.disableBlend();
            GlStateManager.translate((float) (x / scale), (float) (y / scale), 0);
            renderItem.renderItemAndEffectIntoGUI(itemStack, 0, 0);
            GlStateManager.enableBlend();
            RenderHelper.disableStandardItemLighting();
        }
        GlStateManager.scale(1, 1, 1);
        GlStateManager.popMatrix();
    }

    private float getCurrentCategoryBottomFromContent() {
        if (!this.modules.isEmpty() && (this.opened || smoothTimer != null)) {
            float maxBottom = this.y + (this.screenHeight * 0.9f);
            return Math.min(this.y + this.titleHeight + big + 4, maxBottom);
        }
        return this.y + this.titleHeight + 4;
    }

    private float getCurrentAnimatedNamePos() {
        if (textTimer != null) {
            return lastNamePos;
        }
        float middlePos = this.x + this.width / 2 - Minecraft.getMinecraft().fontRendererObj.getStringWidth(this.category.name()) / 2;
        return this.opened ? middlePos : (this.x + 12);
    }

    private float getCurrentAnimatedCategoryHeight() {
        if (this.lastHeight > 0) {
            return this.lastHeight;
        }
        if (!this.modules.isEmpty() && (this.opened || this.smoothTimer != null)) {
            float modulesHeight = 0f;
            for (ModuleComponent c : this.modules) {
                modulesHeight += c.getHeightF();
            }
            return this.y + this.titleHeight + modulesHeight + 4;
        }
        return this.y + this.titleHeight + 4;
    }

    public void setScreenHeight(int screenHeight) {
        this.screenHeight = screenHeight;
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        this.screenWidth = sr.getScaledWidth();
    }

    public void limitPositions() {
        setX(this.x, true);
        setY(this.y, true);
    }

    public void applySavedState(float x, float y, boolean opened, boolean clampToScreen) {
        if (clampToScreen) {
            setX(x, true);
            setY(y, true);
        } else {
            float scrollOffset = scrollAnim.getTarget() - this.y;
            this.x = x;
            this.y = y;
            float newTarget = y + scrollOffset;
            this.moduleY = newTarget;
            scrollAnim.reset(newTarget);
        }
        this.opened = opened;
        smoothTimer = null;
        textTimer = null;
        if (opened && !this.modules.isEmpty()) {
            updateHeight();
            float maxModulesHeight = (this.screenHeight * 0.9f) - this.titleHeight - 4;
            float accumulated = 0f;
            for (Component c : this.modules) {
                float h = c.getHeightF();
                if (accumulated + h > maxModulesHeight) {
                    float remaining = maxModulesHeight - accumulated;
                    if (remaining > 0f) accumulated += remaining;
                    break;
                }
                accumulated += h;
            }
            this.lastHeight = Math.min(this.y + this.titleHeight + accumulated + 4, this.y + this.screenHeight * 0.9f);
        } else {
            this.lastHeight = this.y + this.titleHeight + 4;
        }
        this.moduleY = this.y;
        scrollAnim.reset(this.y);
    }

    public void onGuiClosed() {
        if (smoothTimer != null || textTimer != null) {
            float finalHeight = this.y + this.titleHeight;
            if (this.opened && !this.modules.isEmpty()) {
                float modulesHeight = 0f;
                for (ModuleComponent c : this.modules) {
                    modulesHeight += c.getHeightF();
                }
                finalHeight += modulesHeight + 4;
            } else {
                finalHeight += 4;
            }
            this.lastHeight = finalHeight;
        }

        smoothTimer = null;
        textTimer = null;
        moduleY = scrollAnim.getTarget();
        scrollAnim.reset(moduleY);
    }
}
