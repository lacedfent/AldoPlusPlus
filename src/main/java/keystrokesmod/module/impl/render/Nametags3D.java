package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
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
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.*;

public class Nametags3D extends Module {
    private static final float AUTO_SCALE_THRESHOLD = 5.0F;
    private static final Comparator<NametagRenderTarget> FAR_TO_NEAR = (a, b) -> Double.compare(b.distanceSq, a.distanceSq);

    private SliderSetting scale;
    private ButtonSetting autoScale;
    private ButtonSetting showRect;
    private ButtonSetting onlyRenderName;
    private SliderSetting bgOpacity;
    private ButtonSetting bgBorder;
    private ButtonSetting showHealth;
    private ButtonSetting textShadow;
    private ButtonSetting showDistance;
    private ButtonSetting showInvis;
    private ButtonSetting showArmor;
    private ButtonSetting showEnchants;
    private ButtonSetting showDurability;
    private ButtonSetting showYourself;
    private ButtonSetting hideVanilla;
    private final List<NametagRenderTarget> renderTargets = new ArrayList<>();
    private int renderTargetCount = 0;

    private static class NametagRenderTarget {
        EntityPlayer player;
        double x;
        double y;
        double z;
        float distance;
        double distanceSq;

        void set(EntityPlayer player, double x, double y, double z, float distance, double distanceSq) {
            this.player = player;
            this.x = x;
            this.y = y;
            this.z = z;
            this.distance = distance;
            this.distanceSq = distanceSq;
        }
    }

    public Nametags3D() {
        super("Nametags", category.render, 0);
        this.registerSetting(scale = new SliderSetting("Scale", 1.0, 0.1, 2.0, 0.1));
        this.registerSetting(autoScale = new ButtonSetting("Auto Scale", false));
        this.registerSetting(showRect = new ButtonSetting("Background", true));
        this.registerSetting(onlyRenderName = new ButtonSetting("Only render name", false));
        this.registerSetting(bgOpacity = new SliderSetting("Background Opacity", 0.5, 0.0, 1.0, 0.05));
        this.registerSetting(bgBorder = new ButtonSetting("Background Border", false));
        this.registerSetting(showHealth = new ButtonSetting("Show Health", false));
        this.registerSetting(textShadow = new ButtonSetting("Text Shadow", false));
        this.registerSetting(showDistance = new ButtonSetting("Show Distance", false));
        this.registerSetting(showInvis = new ButtonSetting("Show Invis", true));
        this.registerSetting(showArmor = new ButtonSetting("Show Armor", false));
        this.registerSetting(showEnchants = new ButtonSetting("Show Enchantments", false));
        this.registerSetting(showDurability = new ButtonSetting("Show Durability", false));
        this.registerSetting(showYourself = new ButtonSetting("Show Yourself", false));
        this.registerSetting(hideVanilla = new ButtonSetting("Hide Vanilla", true));
    }

    @SubscribeEvent
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

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (!shouldRenderNametag(player)) continue;
            if (!RenderUtils.isInViewFrustum(player)) continue;

            double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks - rm.viewerPosX;
            double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks - rm.viewerPosY;
            double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks - rm.viewerPosZ;
            double distanceSq = x * x + y * y + z * z;
            float distance = (float) Math.sqrt(distanceSq);

            if (renderTargetCount >= renderTargets.size()) {
                renderTargets.add(new NametagRenderTarget());
            }
            renderTargets.get(renderTargetCount++).set(player, x, y, z, distance, distanceSq);
        }

        renderTargets.subList(0, renderTargetCount).sort(FAR_TO_NEAR);

        for (int i = 0; i < renderTargetCount; i++) {
            NametagRenderTarget target = renderTargets.get(i);
            renderCustomName(target.player, target.x, target.y, target.z, target.distance, rm, fr);
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

    private void renderCustomName(EntityPlayer entity, double x, double y, double z, float interpolatedDist,
                                   RenderManager rm, FontRenderer fr) {
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

        if (showDistance.isToggled()) {
            int dist = (int) interpolatedDist;
            String distColor = dist <= 8 ? "\u00a7c" : (dist <= 15 ? "\u00a76" : (dist <= 25 ? "\u00a7e" : "\u00a77"));
            name = distColor + dist + "m\u00a7r " + name;
        }

        float scaleVal = (float) scale.getInput() * 0.02F;

        if (autoScale.isToggled()) {
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
        int armorCount = 0;
        if (showArmor.isToggled()) {
            heldItem = entity.getEquipmentInSlot(0);
            for (int i = 4; i >= 1; i--) {
                ItemStack stack = entity.getEquipmentInSlot(i);
                if (stack != null) armorCount++;
            }
        }

        int totalItems = armorCount + (heldItem != null ? 1 : 0);
        int itemSpacing = 14;
        int armorTotalWidth = totalItems * itemSpacing;
        int textY = 0;

        GlStateManager.translate(0.0F, -10.0F, 0.0F);

        if (showRect.isToggled() && bgOpacity.getInput() > 0.01) {
            renderBackground(stringWidth, textY, teamColor);
        }

        int nameColor = 0xFFFFFFFF;
        if (Utils.isFriended(entity)) {
            nameColor = 0xFF55FFFF;
        } else if (Utils.isEnemy(entity)) {
            nameColor = 0xFFFF5555;
        }

        fr.drawString(name, -stringWidth, textY, nameColor, textShadow.isToggled());

        if (totalItems > 0) {
            int iconX = -armorTotalWidth / 2;
            int iconY = textY - 20;

            if (heldItem != null) {
                renderItemStack(heldItem, iconX, iconY, fr);
                iconX += itemSpacing;
            }

            for (int i = 4; i >= 1; i--) {
                ItemStack armorStack = entity.getEquipmentInSlot(i);
                if (armorStack != null) {
                    renderItemStack(armorStack, iconX, iconY, fr);
                    iconX += itemSpacing;
                }
            }
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

            worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
            worldRenderer.pos(-stringWidth - 4, textY - 4, borderZ).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(-stringWidth - 4, textY - 3, borderZ).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(stringWidth + 4, textY - 3, borderZ).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(stringWidth + 4, textY - 4, borderZ).color(r, g, b, 1.0F).endVertex();

            worldRenderer.pos(-stringWidth - 4, textY + 10, borderZ).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(-stringWidth - 4, textY + 11, borderZ).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(stringWidth + 4, textY + 11, borderZ).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(stringWidth + 4, textY + 10, borderZ).color(r, g, b, 1.0F).endVertex();

            worldRenderer.pos(-stringWidth - 4, textY - 4, borderZ).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(-stringWidth - 4, textY + 11, borderZ).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(-stringWidth - 3, textY + 11, borderZ).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(-stringWidth - 3, textY - 4, borderZ).color(r, g, b, 1.0F).endVertex();

            worldRenderer.pos(stringWidth + 3, textY - 4, borderZ).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(stringWidth + 3, textY + 11, borderZ).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(stringWidth + 4, textY + 11, borderZ).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(stringWidth + 4, textY - 4, borderZ).color(r, g, b, 1.0F).endVertex();
            tessellator.draw();
        }

        GlStateManager.enableTexture2D();
    }

    private String appendHealth(String name, EntityPlayer entity) {
        float health = Math.max(0.0f, entity.getHealth());
        float maxHealth = entity.getMaxHealth();
        if (maxHealth <= 0.0f) maxHealth = 20.0f;

        float hearts = (float) Math.ceil(health / 2.0f);
        double ratio = health / maxHealth;

        String color = ratio < 0.3 ? "\u00a7c" : (ratio < 0.5 ? "\u00a76" : (ratio < 0.7 ? "\u00a7e" : "\u00a7a"));
        String heartStr = fastOneDecimal(hearts);
        name = name + " " + color + heartStr + " \u2764";

        float absorption = entity.getAbsorptionAmount();
        if (absorption > 0) {
            float absorptionHearts = absorption / 2.0f;
            String absStr = fastOneDecimal(absorptionHearts);
            name = name + " \u00a76+" + absStr + " \u2764";
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

    private void renderEnchantText(ItemStack stack, int xPos, int yPos, FontRenderer fr) {
        int newYPos = yPos - 24;
        int x = xPos * 2;

        if (stack.getItem() instanceof ItemArmor) {
            int prot = EnchantmentHelper.getEnchantmentLevel(0, stack);
            int thorns = EnchantmentHelper.getEnchantmentLevel(7, stack);
            int unbreak = EnchantmentHelper.getEnchantmentLevel(34, stack);

            if (prot > 0) { RenderUtils.drawEnchantWithColor(fr, "P", prot, x, newYPos); newYPos += 8; }
            if (thorns > 0) { RenderUtils.drawEnchantWithColor(fr, "T", thorns, x, newYPos); newYPos += 8; }
            if (unbreak > 0) { RenderUtils.drawEnchantWithColor(fr, "U", unbreak, x, newYPos); }
        } else if (stack.getItem() instanceof ItemSword) {
            int sharp = EnchantmentHelper.getEnchantmentLevel(16, stack);
            int fire = EnchantmentHelper.getEnchantmentLevel(20, stack);
            int kb = EnchantmentHelper.getEnchantmentLevel(19, stack);

            if (sharp > 0) { RenderUtils.drawEnchantWithColor(fr, "S", sharp, x, newYPos); newYPos += 8; }
            if (fire > 0) { RenderUtils.drawEnchantWithColor(fr, "F", fire, x, newYPos); newYPos += 8; }
            if (kb > 0) { RenderUtils.drawEnchantWithColor(fr, "K", kb, x, newYPos); }
        } else if (stack.getItem() instanceof ItemBow) {
            int power = EnchantmentHelper.getEnchantmentLevel(48, stack);
            int punch = EnchantmentHelper.getEnchantmentLevel(49, stack);
            int flame = EnchantmentHelper.getEnchantmentLevel(50, stack);

            if (power > 0) { RenderUtils.drawEnchantWithColor(fr, "Pw", power, x, newYPos); newYPos += 8; }
            if (punch > 0) { RenderUtils.drawEnchantWithColor(fr, "Pu", punch, x, newYPos); newYPos += 8; }
            if (flame > 0) { RenderUtils.drawEnchantWithColor(fr, "Fl", flame, x, newYPos); }
        } else if (stack.getItem() instanceof ItemTool) {
            int eff = EnchantmentHelper.getEnchantmentLevel(32, stack);
            int unbreak = EnchantmentHelper.getEnchantmentLevel(34, stack);
            int fortune = EnchantmentHelper.getEnchantmentLevel(35, stack);

            if (eff > 0) { RenderUtils.drawEnchantWithColor(fr, "E", eff, x, newYPos); newYPos += 8; }
            if (fortune > 0) { RenderUtils.drawEnchantWithColor(fr, "Fo", fortune, x, newYPos); newYPos += 8; }
            if (unbreak > 0) { RenderUtils.drawEnchantWithColor(fr, "U", unbreak, x, newYPos); }
        } else {
            int kb = EnchantmentHelper.getEnchantmentLevel(19, stack);
            if (kb > 0) { RenderUtils.drawEnchantWithColor(fr, "K", kb, x, newYPos); }
        }
    }
}
