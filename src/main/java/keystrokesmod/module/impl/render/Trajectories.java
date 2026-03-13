package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class Trajectories extends Module {
    private static final int HIT_NONE = 0;
    private static final int HIT_ENTITY = 1;
    private static final int HIT_WALL = 2;
    private static final int HIT_GROUND = 3;

    private ButtonSetting disableUnchargedBow;
    private ButtonSetting highlightEntities;
    private ButtonSetting shortenLine;
    private SliderSetting lineThickness;

    private ColorSetting defaultColor;
    private ColorSetting enemyColor;
    private ColorSetting wallColor;
    private ColorSetting groundColor;
    private SliderSetting maxTicks;
    private ButtonSetting showLanding;
    private ButtonSetting onlyWhenHolding;

    public Trajectories() {
        super("Trajectories", category.render);
        this.registerSetting(disableUnchargedBow = new ButtonSetting("Disable uncharged bow", true));
        this.registerSetting(highlightEntities = new ButtonSetting("Highlight on entity", true));
        this.registerSetting(shortenLine = new ButtonSetting("Shorten line", false));
        this.registerSetting(lineThickness = new SliderSetting("Line thickness", 2.0, 1.0, 5.0, 0.1));

        this.registerSetting(defaultColor = new ColorSetting("Default", 170, 0, 255));
        this.registerSetting(enemyColor = new ColorSetting("Enemy Hit", 255, 50, 50));
        this.registerSetting(wallColor = new ColorSetting("Wall Hit", 50, 255, 50));
        this.registerSetting(groundColor = new ColorSetting("Ground Hit", 85, 255, 255));

        this.registerSetting(maxTicks = new SliderSetting("Max ticks", 100.0, 30.0, 200.0, 10.0));
        this.registerSetting(showLanding = new ButtonSetting("Show landing", true));
        this.registerSetting(onlyWhenHolding = new ButtonSetting("Only when holding", true));
    }

    private float getBowVelocity(float partialTicks) {
        int timeLeft = mc.thePlayer.getItemInUseCount();
        float drawTicks = (72000 - timeLeft) + partialTicks;
        float f = drawTicks / 20.0f;
        f = (f * f + f * 2.0f) / 3.0f;
        if (f > 1.0f) f = 1.0f;
        return f * 2.0f * 1.5f;
    }

    private float[] getProjectileProperties(Item item, EntityPlayer player, float partialTicks) {
        if (item == Items.bow) {
            return new float[]{getBowVelocity(partialTicks), 0.05f, 0.5f};
        }
        if (item == Items.ender_pearl) {
            return new float[]{1.5f, 0.03f, 0.25f};
        }
        if (item == Items.snowball || item == Items.egg) {
            return new float[]{1.5f, 0.03f, 0.25f};
        }
        if (item == Items.experience_bottle) {
            return new float[]{0.7f, 0.07f, 0.25f};
        }
        if (item == Items.potionitem) {
            return new float[]{0.5f, 0.05f, 0.25f};
        }
        if (item == Items.fishing_rod) {
            return new float[]{1.5f, 0.04f, 0.25f};
        }
        return null;
    }

    private ItemStack getHeldProjectile(EntityPlayer player) {
        ItemStack held = player.getHeldItem();
        if (held == null) return null;
        Item item = held.getItem();
        if (item == Items.ender_pearl || item == Items.snowball || item == Items.egg || item == Items.experience_bottle) {
            return held;
        }
        if (item == Items.potionitem) {
            if (ItemPotion.isSplash(held.getMetadata())) {
                return held;
            }
            return null;
        }
        if (item instanceof ItemBow) {
            return held;
        }
        if (item == Items.fishing_rod) {
            return held;
        }
        return null;
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!Utils.nullCheck() || mc.theWorld == null) {
            return;
        }
        EntityPlayer player = mc.thePlayer;
        float partialTicks = e.partialTicks;
        ItemStack heldStack = getHeldProjectile(player);
        if (onlyWhenHolding.isToggled() && heldStack == null) return;
        if (heldStack == null) return;

        Item item = heldStack.getItem();
        float[] props = getProjectileProperties(item, player, partialTicks);
        if (props == null) return;

        float velocity = props[0];
        float gravity = props[1];
        float size = props[2];

        if (item == Items.bow && disableUnchargedBow.isToggled() && (!player.isUsingItem() || velocity < 0.1f)) return;
        float yaw = (float) Math.toRadians(player.rotationYaw);
        float pitch = (float) Math.toRadians(player.rotationPitch);
        double posX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks - MathHelper.cos(yaw) * 0.16f;
        double posY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks + player.getEyeHeight() - 0.10;
        double posZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks - MathHelper.sin(yaw) * 0.16f;

        if (mc.gameSettings.thirdPersonView == 0) {
            double forwardOffset = 0.5;
            double dirX = -MathHelper.sin(yaw) * MathHelper.cos(pitch);
            double dirY = -MathHelper.sin(pitch);
            double dirZ = MathHelper.cos(yaw) * MathHelper.cos(pitch);
            posX += dirX * forwardOffset;
            posY += dirY * forwardOffset;
            posZ += dirZ * forwardOffset;
        }

        double motX = -MathHelper.sin(yaw) * MathHelper.cos(pitch);
        double motY = -MathHelper.sin(pitch);
        double motZ = MathHelper.cos(yaw) * MathHelper.cos(pitch);
        double len = Math.sqrt(motX * motX + motY * motY + motZ * motZ);
        motX /= len;
        motY /= len;
        motZ /= len;
        motX *= velocity;
        motY *= velocity;
        motZ *= velocity;

        List<double[]> renderPoints = new ArrayList<>();
        MovingObjectPosition hitBlock = null;
        Entity hitEntity = null;
        AxisAlignedBB hitEntityBox = null;
        int hitType = HIT_NONE;
        Vec3 hitPos = null;
        RenderManager rm = mc.getRenderManager();
        final int maxSteps = (int) maxTicks.getInput();
        final int SUB = 4;
        final double hw = size;

        outer:
        for (int i = 0; i < maxSteps; i++) {
            double nextX = posX + motX;
            double nextY = posY + motY;
            double nextZ = posZ + motZ;

            Vec3 start = new Vec3(posX, posY, posZ);
            Vec3 end = new Vec3(nextX, nextY, nextZ);
            MovingObjectPosition blockMop = mc.theWorld.rayTraceBlocks(start, end);
            Vec3 clampedEnd = end;
            if (blockMop != null) {
                clampedEnd = new Vec3(blockMop.hitVec.xCoord, blockMop.hitVec.yCoord, blockMop.hitVec.zCoord);
            }

            AxisAlignedBB broadBox = new AxisAlignedBB(posX - hw, posY - hw, posZ - hw, posX + hw, posY + hw, posZ + hw).addCoord(motX, motY, motZ).expand(1.0, 1.0, 1.0);
            List<Entity> candidates = mc.theWorld.getEntitiesWithinAABBExcludingEntity(mc.getRenderViewEntity(), broadBox);
            Entity bestEntity = null;
            Vec3 bestHitVec = null;
            AxisAlignedBB bestBox = null;
            double bestDistSq = Double.MAX_VALUE;
            for (Entity en : candidates) {
                if (!(en instanceof EntityLivingBase)) continue;
                if (en instanceof EntityArmorStand) continue;
                if (!en.canBeCollidedWith()) continue;
                if (((EntityLivingBase) en).deathTime != 0) continue;
                if (en instanceof EntityPlayer && AntiBot.isBot(en)) continue;

                AxisAlignedBB testBox = en.getEntityBoundingBox().expand(0.3, 0.3, 0.3);
                MovingObjectPosition mop = testBox.calculateIntercept(start, clampedEnd);
                if (mop == null) continue;

                double dSq = start.squareDistanceTo(mop.hitVec);
                if (dSq < bestDistSq) {
                    bestDistSq = dSq;
                    bestEntity = en;
                    bestHitVec = mop.hitVec;
                    bestBox = testBox;
                }
            }
            if (bestEntity != null) {
                double hitT = Math.sqrt(bestDistSq) / Math.sqrt(motX * motX + motY * motY + motZ * motZ);
                hitT = Math.max(0, Math.min(1, hitT));
                int subCount = (int) Math.ceil(hitT * SUB);
                for (int s = 0; s < subCount; s++) {
                    double t = (double) s / SUB;
                    renderPoints.add(new double[]{posX + motX * t - rm.viewerPosX, posY + motY * t - rm.viewerPosY, posZ + motZ * t - rm.viewerPosZ});
                }
                renderPoints.add(new double[]{bestHitVec.xCoord - rm.viewerPosX, bestHitVec.yCoord - rm.viewerPosY, bestHitVec.zCoord - rm.viewerPosZ});
                hitEntity = bestEntity;
                hitEntityBox = bestBox;
                hitType = HIT_ENTITY;
                hitPos = bestHitVec;
                break outer;
            }
            if (blockMop != null) {
                Vec3 hitVec = blockMop.hitVec;
                int side = blockMop.sideHit.getIndex();
                hitType = (side == 0 || side == 1) ? HIT_GROUND : HIT_WALL;
                hitPos = hitVec;
                double segLenSq = motX * motX + motY * motY + motZ * motZ;
                double hitDx = hitVec.xCoord - posX;
                double hitDy = hitVec.yCoord - posY;
                double hitDz = hitVec.zCoord - posZ;
                double hitT = segLenSq > 0 ? Math.sqrt((hitDx * hitDx + hitDy * hitDy + hitDz * hitDz) / segLenSq) : 0;
                hitT = Math.max(0, Math.min(1, hitT));
                int subCount = (int) Math.ceil(hitT * SUB);
                for (int s = 0; s < subCount; s++) {
                    double t = (double) s / SUB;
                    renderPoints.add(new double[]{posX + motX * t - rm.viewerPosX, posY + motY * t - rm.viewerPosY, posZ + motZ * t - rm.viewerPosZ});
                }
                renderPoints.add(new double[]{hitVec.xCoord - rm.viewerPosX, hitVec.yCoord - rm.viewerPosY, hitVec.zCoord - rm.viewerPosZ});
                hitBlock = blockMop;
                break outer;
            }
            for (int s = 0; s < SUB; s++) {
                double t = (double) s / SUB;
                renderPoints.add(new double[]{posX + motX * t - rm.viewerPosX, posY + motY * t - rm.viewerPosY, posZ + motZ * t - rm.viewerPosZ});
            }
            posX = nextX;
            posY = nextY;
            posZ = nextZ;
            BlockPos currentBlockPos = new BlockPos(posX, posY, posZ);
            Block block = mc.theWorld.getBlockState(currentBlockPos).getBlock();
            if (block.getMaterial() == Material.water) {
                motX *= 0.8;
                motY *= 0.8;
                motZ *= 0.8;
            } else {
                motX *= 0.99;
                motY *= 0.99;
                motZ *= 0.99;
                motY -= gravity;
            }

            if (posY < -64) break;
        }
        int color;
        switch (hitType) {
            case HIT_ENTITY: color = enemyColor.getColor(); break;
            case HIT_WALL: color = wallColor.getColor(); break;
            case HIT_GROUND: color = groundColor.getColor(); break;
            default: color = defaultColor.getColor(); break;
        }
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        float lineW = (float) lineThickness.getInput();
        GL11.glColor4f(r, g, b, 1.0f);
        GL11.glLineWidth(lineW);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        boolean first = true;
        for (double[] pt : renderPoints) {
            if (first && shortenLine.isToggled()) {
                first = false;
                continue;
            }
            first = false;
            GL11.glVertex3d(pt[0], pt[1], pt[2]);
        }
        GL11.glEnd();
        if (hitEntity != null && highlightEntities.isToggled() && hitEntityBox != null) {
            double ex = hitEntity.lastTickPosX + (hitEntity.posX - hitEntity.lastTickPosX) * partialTicks;
            double ey = hitEntity.lastTickPosY + (hitEntity.posY - hitEntity.lastTickPosY) * partialTicks;
            double ez = hitEntity.lastTickPosZ + (hitEntity.posZ - hitEntity.lastTickPosZ) * partialTicks;
            AxisAlignedBB renderBox = new AxisAlignedBB(hitEntityBox.minX - hitEntity.posX + ex, hitEntityBox.minY - hitEntity.posY + ey, hitEntityBox.minZ - hitEntity.posZ + ez, hitEntityBox.maxX - hitEntity.posX + ex, hitEntityBox.maxY - hitEntity.posY + ey, hitEntityBox.maxZ - hitEntity.posZ + ez);
            GL11.glColor4f(r, g, b, 1.0f);
            RenderUtils.drawOutlinedBox(renderBox, rm.viewerPosX, rm.viewerPosY, rm.viewerPosZ);
        } else if (hitBlock != null && !showLanding.isToggled()) {
            BlockPos bpos = hitBlock.getBlockPos();
            AxisAlignedBB selBox = BlockUtils.getBlockSelectionBox(bpos);
            if (selBox != null) {
                GL11.glColor4f(r, g, b, 1.0f);
                RenderUtils.drawOutlinedBox(selBox, rm.viewerPosX, rm.viewerPosY, rm.viewerPosZ);
            }
        }
        if (showLanding.isToggled() && hitPos != null) {
            renderLandingIndicator(hitPos, rm.viewerPosX, rm.viewerPosY, rm.viewerPosZ, r, g, b, hitType);
        }
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glPopMatrix();
    }

    private void renderLandingIndicator(Vec3 hitPos, double camX, double camY, double camZ, float r, float g, float b, int hitType) {
        double boxSize = hitType == HIT_ENTITY ? 0.4 : 0.2;
        AxisAlignedBB worldBox = new AxisAlignedBB(hitPos.xCoord - boxSize, hitPos.yCoord - boxSize, hitPos.zCoord - boxSize, hitPos.xCoord + boxSize, hitPos.yCoord + boxSize, hitPos.zCoord + boxSize);
        GL11.glLineWidth(2.0f);
        GL11.glColor4f(r, g, b, 1.0f);
        RenderUtils.drawOutlinedBox(worldBox, camX, camY, camZ);
        AxisAlignedBB renderBox = worldBox.offset(-camX, -camY, -camZ);
        GL11.glColor4f(r, g, b, 0.3f);
        RenderUtils.drawBoundingBox(renderBox, r, g, b, 0.3f);
        double x = hitPos.xCoord - camX;
        double y = hitPos.yCoord - camY;
        double z = hitPos.zCoord - camZ;
        double crossSize = hitType == HIT_ENTITY ? 0.5 : 0.3;
        GL11.glLineWidth(1.5f);
        GL11.glColor4f(r, g, b, 1.0f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(x - crossSize, y, z);
        GL11.glVertex3d(x + crossSize, y, z);
        GL11.glVertex3d(x, y - crossSize, z);
        GL11.glVertex3d(x, y + crossSize, z);
        GL11.glVertex3d(x, y, z - crossSize);
        GL11.glVertex3d(x, y, z + crossSize);
        GL11.glEnd();
    }
}
