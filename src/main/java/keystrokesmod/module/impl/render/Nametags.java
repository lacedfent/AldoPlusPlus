package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.*;

public class Nametags extends Module {
    private static final float AUTO_SCALE_THRESHOLD = 5.0F;
    private static final Comparator<NametagRenderTarget> FAR_TO_NEAR = (a, b) -> Double.compare(b.distanceSq, a.distanceSq);
    private static final String[] HEALTH_DISPLAY_MODES = {"Hearts", "Health"};

    private SliderSetting scale;
    private ButtonSetting autoScale;
    private ButtonSetting showRect;
    private ButtonSetting onlyRenderName;
    private SliderSetting bgOpacity;
    private ButtonSetting bgBorder;
    private ButtonSetting showHealth;
    private SliderSetting healthDisplayMode;
    private ButtonSetting showHeartSymbol;
    private ButtonSetting textShadow;
    private ButtonSetting showDistance;
    private ButtonSetting showInvis;
    private ButtonSetting showArmor;
    private ButtonSetting showEnchants;
    private ButtonSetting showDurability;
    private ButtonSetting showYourself;
    private ButtonSetting hideVanilla;
    private ColorSetting friendColor;
    private ColorSetting enemyColor;
    private final List<NametagRenderTarget> renderTargets = new ArrayList<>();
    private int renderTargetCount = 0;

    private static class NametagRenderTarget {
        EntityPlayer player;
        double x;
        double y;
        double z;
        double distanceSq;

        void set(EntityPlayer player, double x, double y, double z, double distanceSq) {
            this.player = player;
            this.x = x;
            this.y = y;
            this.z = z;
            this.distanceSq = distanceSq;
        }
    }

    public Nametags() {
        super("Nametags", category.render, 0);
        this.registerSetting(scale = new SliderSetting("Scale", 1.0, 0.1, 2.0, 0.1));
        this.registerSetting(autoScale = new ButtonSetting("Auto Scale", false));
        this.registerSetting(showRect = new ButtonSetting("Background", true));
        this.registerSetting(onlyRenderName = new ButtonSetting("Only render name", false));
        this.registerSetting(bgOpacity = new SliderSetting("Background Opacity", 0.5, 0.0, 1.0, 0.05));
        this.registerSetting(bgBorder = new ButtonSetting("Background Border", false));
        this.registerSetting(showHealth = new ButtonSetting("Show Health", false));
        this.registerSetting(healthDisplayMode = new SliderSetting("Health display", 0, HEALTH_DISPLAY_MODES));
        this.registerSetting(showHeartSymbol = new ButtonSetting("Show Heart Symbol", true));
        this.registerSetting(textShadow = new ButtonSetting("Text Shadow", false));
        this.registerSetting(showDistance = new ButtonSetting("Show Distance", false));
        this.registerSetting(showInvis = new ButtonSetting("Show Invis", true));
        this.registerSetting(showArmor = new ButtonSetting("Show Armor", false));
        this.registerSetting(showEnchants = new ButtonSetting("Show Enchantments", false));
        this.registerSetting(showDurability = new ButtonSetting("Show Durability", false));
        this.registerSetting(showYourself = new ButtonSetting("Show Yourself", false));
        this.registerSetting(hideVanilla = new ButtonSetting("Hide Vanilla", true));
        this.registerSetting(friendColor = new ColorSetting("Friend color", 85, 255, 255));
        this.registerSetting(enemyColor = new ColorSetting("Enemy color", 255, 85, 85));
    }

    @Override
    public void guiUpdate() {
        boolean healthOn = showHealth.isToggled();
        healthDisplayMode.setVisible(healthOn, this);
        showHeartSymbol.setVisible(healthOn && (int) healthDisplayMode.getInput() == 0, this);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderWorldLast(RenderWorldLastEvent e) {
        if (!Utils.nullCheck()) return;
        renderNametags(e.partialTicks);
    }

    @SubscribeEvent
    public void onRenderLiving(RenderLivingEvent.Specials.Pre e) {
        if (!hideVanilla.isToggled()) return;
        if (e.entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) e.entity;
            if (shouldRenderNametag(player)) {
                e.setCanceled(true);
            }
        }
    }

    private void renderNametags(float partialTicks) {
        RenderManager rm = mc.getRenderManager();
        if (rm == null) return;
        FontRenderer fr = mc.fontRendererObj;
        if (fr == null) return;
        renderTargetCount = 0;

        double vx = rm.viewerPosX;
        double vy = rm.viewerPosY;
        double vz = rm.viewerPosZ;

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (!RenderUtils.isInViewFrustum(player)) continue;
            if (!shouldRenderNametag(player)) continue;

            double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks - vx;
            double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks - vy;
            double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks - vz;
            double distanceSq = x * x + y * y + z * z;

            if (renderTargetCount >= renderTargets.size()) {
                renderTargets.add(new NametagRenderTarget());
            }
            NametagRenderTarget t = renderTargets.get(renderTargetCount++);
            t.set(player, x, y, z, distanceSq);
        }

        if (renderTargetCount == 0) {
            return;
        }

        renderTargets.subList(0, renderTargetCount).sort(FAR_TO_NEAR);

        boolean needDistance = showDistance.isToggled();
        boolean needScaleByDistance = autoScale.isToggled();

        for (int i = 0; i < renderTargetCount; i++) {
            NametagRenderTarget target = renderTargets.get(i);
            renderCustomName(target.player, target.x, target.y, target.z, target.distanceSq,
                    needDistance, needScaleByDistance, rm, fr);
        }

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private boolean shouldRenderNametag(EntityPlayer player) {
        if (player == null) return false;
        if (player == mc.thePlayer) {
            return showYourself.isToggled() && mc.gameSettings.thirdPersonView != 0;
        }
        if (player.isDead || player.deathTime > 0) return false;
        if (!showInvis.isToggled() && player.isInvisible()) return false;
        return !AntiBot.isBot(player);
    }

    private void renderCustomName(EntityPlayer entity, double x, double y, double z, double distanceSq,
                                   boolean showDist, boolean scaleByDist, RenderManager rm, FontRenderer fr) {
        float interpolatedDist = (showDist || scaleByDist) ? (float) Math.sqrt(distanceSq) : 0.0F;

        String name;
        int teamColor = Utils.getColorFromEntity(entity);

        if (onlyRenderName.isToggled()) {
            String formatted = Utils.getFirstColorCode(entity.getDisplayName().getFormattedText());
            String color = (formatted.length() >= 2 && formatted.startsWith("§")) ? formatted : "";
            name = color + entity.getName();
        } else {
            name = entity.getDisplayName().getFormattedText();
        }

        if (showHealth.isToggled()) {
            name = appendHealth(name, entity);
        }

        if (showDist) {
            int dist = (int) interpolatedDist;
            String distColor = dist <= 8 ? "\u00a7c" : (dist <= 15 ? "\u00a76" : (dist <= 25 ? "\u00a7e" : "\u00a77"));
            name = distColor + dist + "m\u00a7r " + name;
        }

        float scaleVal = (float) scale.getInput() * 0.02F;

        if (scaleByDist) {
            float effectiveDistance = Math.max(1.0F, interpolatedDist);
            float scaledVal = scaleVal * (effectiveDistance / AUTO_SCALE_THRESHOLD);
            scaleVal = Math.max(scaleVal, scaledVal);
        }

        float yOff = (entity.isSneaking() ? (entity.height - 0.3F) : entity.height) + 0.3F;

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y + yOff, (float) z);
        GlStateManager.rotate(-rm.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(rm.playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-scaleVal, -scaleVal, scaleVal);

        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        int stringWidth = fr.getStringWidth(name) / 2;

        ItemStack heldItem = null;
        ItemStack slot1 = null, slot2 = null, slot3 = null, slot4 = null;
        int totalItems = 0;
        if (showArmor.isToggled()) {
            heldItem = entity.getEquipmentInSlot(0);
            if (heldItem != null) totalItems++;
            slot1 = entity.getEquipmentInSlot(1);
            if (slot1 != null) totalItems++;
            slot2 = entity.getEquipmentInSlot(2);
            if (slot2 != null) totalItems++;
            slot3 = entity.getEquipmentInSlot(3);
            if (slot3 != null) totalItems++;
            slot4 = entity.getEquipmentInSlot(4);
            if (slot4 != null) totalItems++;
        }

        final int itemSpacing = 14;
        int armorTotalWidth = totalItems * itemSpacing;
        int textY = 0;

        GlStateManager.translate(0.0F, -10.0F, 0.0F);

        if (showRect.isToggled() && bgOpacity.getInput() > 0.01) {
            renderBackground(stringWidth, textY, teamColor);
        }

        int nameColor = 0xFFFFFFFF;
        if (Utils.isFriended(entity)) {
            nameColor = friendColor.getColor();
        } else if (Utils.isEnemy(entity)) {
            nameColor = enemyColor.getColor();
        }

        fr.drawString(name, -stringWidth, textY, nameColor, textShadow.isToggled());

        if (totalItems > 0) {
            int iconX = -armorTotalWidth / 2;
            int iconY = textY - 20;

            if (heldItem != null) {
                renderItemStack(heldItem, iconX, iconY, fr);
                iconX += itemSpacing;
            }
            if (slot4 != null) { renderItemStack(slot4, iconX, iconY, fr); iconX += itemSpacing; }
            if (slot3 != null) { renderItemStack(slot3, iconX, iconY, fr); iconX += itemSpacing; }
            if (slot2 != null) { renderItemStack(slot2, iconX, iconY, fr); iconX += itemSpacing; }
            if (slot1 != null) { renderItemStack(slot1, iconX, iconY, fr); }
        }

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        GlStateManager.popMatrix();
    }

    private void renderBackground(int stringWidth, int textY, int teamColor) {
        GlStateManager.disableTexture2D();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        float alpha = (float) bgOpacity.getInput();

        worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        worldRenderer.pos(-stringWidth - 3, textY - 3, 0).color(0.0F, 0.0F, 0.0F, alpha).endVertex();
        worldRenderer.pos(-stringWidth - 3, textY + 10, 0).color(0.0F, 0.0F, 0.0F, alpha).endVertex();
        worldRenderer.pos(stringWidth + 3, textY + 10, 0).color(0.0F, 0.0F, 0.0F, alpha).endVertex();
        worldRenderer.pos(stringWidth + 3, textY - 3, 0).color(0.0F, 0.0F, 0.0F, alpha).endVertex();
        tessellator.draw();

        if (bgBorder.isToggled()) {
            float r, g, b;
            if (teamColor != -1) {
                r = ((teamColor >> 16) & 255) / 255.0F;
                g = ((teamColor >> 8) & 255) / 255.0F;
                b = (teamColor & 255) / 255.0F;
            } else {
                r = 0.6F;
                g = 0.6F;
                b = 0.6F;
            }

            float borderZ = -0.001F;
            float l = -stringWidth - 4;
            float r_ = stringWidth + 4;
            float t = textY - 4;
            float b_ = textY + 11;
            float l2 = -stringWidth - 3;
            float r2 = stringWidth + 3;

            worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
            worldRenderer.pos(l, textY - 3, borderZ).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(l, t, borderZ).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(r_, t, borderZ).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(r_, textY - 3, borderZ).color(r, g, b, 1.0F).endVertex();

            worldRenderer.pos(l, b_, borderZ).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(l, textY + 10, borderZ).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(r_, textY + 10, borderZ).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(r_, b_, borderZ).color(r, g, b, 1.0F).endVertex();

            worldRenderer.pos(l2, b_, borderZ).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(l, b_, borderZ).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(l, t, borderZ).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(l2, t, borderZ).color(r, g, b, 1.0F).endVertex();

            worldRenderer.pos(r2, t, borderZ).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(r_, t, borderZ).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(r_, b_, borderZ).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(r2, b_, borderZ).color(r, g, b, 1.0F).endVertex();
            tessellator.draw();
        }

        GlStateManager.enableTexture2D();
    }

    private String appendHealth(String name, EntityPlayer entity) {
        float health = Math.max(0.0f, entity.getHealth());
        float maxHealth = entity.getMaxHealth();
        if (maxHealth <= 0.0f) maxHealth = 20.0f;

        boolean heartsMode = (int) healthDisplayMode.getInput() == 0;
        double ratio = health / maxHealth;

        String color = ratio < 0.3 ? "\u00a7c" : (ratio < 0.5 ? "\u00a76" : (ratio < 0.7 ? "\u00a7e" : "\u00a7a"));
        float displayValue = heartsMode ? health / 2.0f : health;
        String valueStr = fastOneDecimal(displayValue);
        String heartSuffix = heartsMode && showHeartSymbol.isToggled() ? " \u2764" : "";
        name = name + " " + color + valueStr + heartSuffix;

        float absorption = entity.getAbsorptionAmount();
        if (absorption > 0) {
            float absDisplay = heartsMode ? absorption / 2.0f : absorption;
            String absStr = fastOneDecimal(absDisplay);
            String absSuffix = heartsMode && showHeartSymbol.isToggled() ? " \u2764" : "";
            name = name + " \u00a76+" + absStr + absSuffix;
        }
        name = name + "\u00a7r";
        return name;
    }

    private String fastOneDecimal(float value) {
        int whole = (int) value;
        if (value == whole) {
            return String.valueOf(whole);
        }
        int tenths = Math.round(value * 10.0F);
        int intPart = tenths / 10;
        int fracPart = Math.abs(tenths % 10);
        return intPart + "." + fracPart;
    }

    private void renderItemStack(ItemStack stack, int xPos, int yPos, FontRenderer fr) {
        if (stack == null) return;

        RenderUtils.renderItemAndEffectIntoGui3D(stack, xPos, yPos);

        if (showEnchants.isToggled()) {
            GlStateManager.pushMatrix();
            GlStateManager.scale(0.5, 0.5, 0.5);
            GlStateManager.translate(0, -10, 0);
            renderEnchantText(stack, xPos, yPos, fr);
            GlStateManager.popMatrix();
        }

        GlStateManager.disableDepth();

        if (stack.stackSize > 1) {
            String countStr = String.valueOf(stack.stackSize);
            fr.drawStringWithShadow(countStr, xPos + 17 - fr.getStringWidth(countStr), yPos + 9, 0xFFFFFF);
        }

        if (showDurability.isToggled() && stack.isItemStackDamageable() && stack.getItemDamage() > 0) {
            int maxDamage = stack.getMaxDamage();
            int currentDamage = stack.getItemDamage();
            float durabilityRatio = 1.0F - (float) currentDamage / (float) maxDamage;
            RenderUtils.drawDurabilityBar(xPos, yPos, durabilityRatio);
        }

        GlStateManager.enableDepth();
    }

    private static final int ENCHANT_LINE_HEIGHT = 8;
    private static final int ENCHANT_Y_OFFSET = 24;

    private static final int[] ARMOR_ENCHANT_IDS = { 0, 7, 34 };
    private static final String[] ARMOR_ENCHANT_ABBR = { "P", "T", "U" };

    private static final int[] SWORD_ENCHANT_IDS = { 16, 20, 19 };
    private static final String[] SWORD_ENCHANT_ABBR = { "S", "F", "K" };

    private static final int[] BOW_ENCHANT_IDS = { 48, 49, 50 };
    private static final String[] BOW_ENCHANT_ABBR = { "Pw", "Pu", "Fl" };

    private static final int[] TOOL_ENCHANT_IDS = { 32, 35, 34 };
    private static final String[] TOOL_ENCHANT_ABBR = { "E", "Fo", "U" };

    private static final int[] MISC_ENCHANT_IDS = { 19 };
    private static final String[] MISC_ENCHANT_ABBR = { "K" };

    private void renderEnchantText(ItemStack stack, int xPos, int yPos, FontRenderer fr) {
        int[] ids;
        String[] abbrs;
        Item item = stack.getItem();
        if (item instanceof ItemArmor) {
            ids = ARMOR_ENCHANT_IDS;
            abbrs = ARMOR_ENCHANT_ABBR;
        } else if (item instanceof ItemSword) {
            ids = SWORD_ENCHANT_IDS;
            abbrs = SWORD_ENCHANT_ABBR;
        } else if (item instanceof ItemBow) {
            ids = BOW_ENCHANT_IDS;
            abbrs = BOW_ENCHANT_ABBR;
        } else if (item instanceof ItemTool) {
            ids = TOOL_ENCHANT_IDS;
            abbrs = TOOL_ENCHANT_ABBR;
        } else {
            ids = MISC_ENCHANT_IDS;
            abbrs = MISC_ENCHANT_ABBR;
        }

        int drawX = xPos * 2;
        int drawY = yPos - ENCHANT_Y_OFFSET;

        for (int i = 0; i < ids.length; i++) {
            int level = EnchantmentHelper.getEnchantmentLevel(ids[i], stack);
            if (level <= 0) continue;
            drawEnchantLine(fr, abbrs[i], level, drawX, drawY);
            drawY += ENCHANT_LINE_HEIGHT;
        }
    }

    private void drawEnchantLine(FontRenderer fr, String abbreviation, int level, int x, int y) {
        fr.drawStringWithShadow(abbreviation, x, y, 0xFFFFFF);
        int advance = fr.getStringWidth(abbreviation);
        fr.drawStringWithShadow(String.valueOf(level), x + advance, y, colorForEnchantLevel(level));
    }

    private int colorForEnchantLevel(int level) {
        if (level <= 5) {
            if (level == 1) return 0xFFFFFF;
            if (level == 2) return 0x55FFFF;
            if (level == 3) return 0x00AAAA;
            if (level == 4) return 0xAA00AA;
            if (level == 5) return 0xFFAA00;
        }
        return 0xFF55FF;
    }
}
