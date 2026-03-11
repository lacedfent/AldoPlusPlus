package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemSnowball;
import net.minecraft.util.*;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class Trajectories extends Module {
    private ButtonSetting autoScale;
    private ButtonSetting disableUnchargedBow;
    private ButtonSetting highlightEntities;
    private ButtonSetting shortenLine;
    private SliderSetting lineThickness;

    public Trajectories() {
        super("Trajectories", category.render);
        this.registerSetting(autoScale = new ButtonSetting("Auto-scale", true));
        this.registerSetting(disableUnchargedBow = new ButtonSetting("Disable uncharged bow", true));
        this.registerSetting(highlightEntities = new ButtonSetting("Highlight on entity", true));
        this.registerSetting(shortenLine = new ButtonSetting("Shorten line", false));
        this.registerSetting(lineThickness = new SliderSetting("Line thickness", 2.0, 1.0, 5.0, 0.1));
    }

    private float getBowVelocity(float partialTicks) {
        int timeLeft = mc.thePlayer.getItemInUseCount();
        float drawTicks = (72000 - timeLeft) + partialTicks;
        float f = drawTicks / 20.0f;
        f = (f * f + f * 2.0f) / 3.0f;
        if (f > 1.0f) f = 1.0f;
        return f * 2.0f * 1.5f;
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!Utils.nullCheck() || mc.thePlayer.getHeldItem() == null) {
            return;
        }
        Item item = mc.thePlayer.getHeldItem().getItem();
        boolean usingBow = item instanceof ItemBow;
        if (!usingBow && !(item instanceof ItemSnowball) && !(item instanceof ItemEgg) && !(item instanceof ItemEnderPearl)) {
            return;
        }
        if (usingBow && disableUnchargedBow.isToggled() && !mc.thePlayer.isUsingItem()) {
            return;
        }

        float partialTicks = e.partialTicks;
        float yaw   = (float) Math.toRadians(mc.thePlayer.rotationYaw);
        float pitch = (float) Math.toRadians(mc.thePlayer.rotationPitch);

        double posX = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * partialTicks
                - MathHelper.cos(yaw) * 0.16f;
        double posY = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * partialTicks
                + mc.thePlayer.getEyeHeight() - 0.10;
        double posZ = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * partialTicks
                - MathHelper.sin(yaw) * 0.16f;

        double motX = -MathHelper.sin(yaw) * MathHelper.cos(pitch);
        double motY = -MathHelper.sin(pitch);
        double motZ =  MathHelper.cos(yaw) * MathHelper.cos(pitch);
        double len  = Math.sqrt(motX * motX + motY * motY + motZ * motZ);
        motX /= len;
        motY /= len;
        motZ /= len;

        double velocity;
        if (usingBow) {
            velocity = getBowVelocity(partialTicks);
        } else {
            velocity = 1.5;
        }
        motX *= velocity;
        motY *= velocity;
        motZ *= velocity;

        double gravity = usingBow ? 0.05 : 0.03;

        List<double[]> renderPoints = new ArrayList<>();
        MovingObjectPosition hitBlock  = null;
        Entity               hitEntity = null;
        AxisAlignedBB        hitEntityBox = null;

        RenderManager rm = mc.getRenderManager();
        final int maxSteps = 750;
        final int SUB = 4;

        outer:
        for (int i = 0; i < maxSteps; i++) {
            double nextX = posX + motX;
            double nextY = posY + motY;
            double nextZ = posZ + motZ;

            Vec3 start = new Vec3(posX, posY, posZ);
            Vec3 end   = new Vec3(nextX, nextY, nextZ);

            MovingObjectPosition blockMop = mc.theWorld.rayTraceBlocks(start, end);
            Vec3 clampedEnd = end;
            if (blockMop != null) {
                clampedEnd = new Vec3(blockMop.hitVec.xCoord, blockMop.hitVec.yCoord, blockMop.hitVec.zCoord);
            }

            double hw = 0.25;
            AxisAlignedBB broadBox = new AxisAlignedBB(
                    posX - hw, posY - hw, posZ - hw,
                    posX + hw, posY + hw, posZ + hw)
                    .addCoord(motX, motY, motZ)
                    .expand(1.0, 1.0, 1.0);

            List<Entity> candidates = mc.theWorld.getEntitiesWithinAABBExcludingEntity(mc.getRenderViewEntity(), broadBox);

            Entity         bestEntity  = null;
            Vec3           bestHitVec  = null;
            AxisAlignedBB  bestBox     = null;
            double         bestDistSq  = Double.MAX_VALUE;

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
                    bestBox    = testBox;
                }
            }

            if (bestEntity != null) {
                double hitT = Math.sqrt(bestDistSq) / Math.sqrt(motX * motX + motY * motY + motZ * motZ);
                hitT = Math.max(0, Math.min(1, hitT));
                int subCount = (int) Math.ceil(hitT * SUB);
                for (int s = 0; s < subCount; s++) {
                    double t = (double) s / SUB;
                    renderPoints.add(new double[]{
                            posX + motX * t - rm.viewerPosX,
                            posY + motY * t - rm.viewerPosY,
                            posZ + motZ * t - rm.viewerPosZ
                    });
                }
                renderPoints.add(new double[]{
                        bestHitVec.xCoord - rm.viewerPosX,
                        bestHitVec.yCoord - rm.viewerPosY,
                        bestHitVec.zCoord - rm.viewerPosZ
                });
                hitEntity = bestEntity;
                hitEntityBox = bestBox;
                break outer;
            }

            if (blockMop != null) {
                Vec3 hitVec = blockMop.hitVec;
                double segLenSq = motX * motX + motY * motY + motZ * motZ;
                double hitDx = hitVec.xCoord - posX;
                double hitDy = hitVec.yCoord - posY;
                double hitDz = hitVec.zCoord - posZ;
                double hitT = segLenSq > 0 ? Math.sqrt((hitDx * hitDx + hitDy * hitDy + hitDz * hitDz) / segLenSq) : 0;
                hitT = Math.max(0, Math.min(1, hitT));
                int subCount = (int) Math.ceil(hitT * SUB);
                for (int s = 0; s < subCount; s++) {
                    double t = (double) s / SUB;
                    renderPoints.add(new double[]{
                            posX + motX * t - rm.viewerPosX,
                            posY + motY * t - rm.viewerPosY,
                            posZ + motZ * t - rm.viewerPosZ
                    });
                }
                renderPoints.add(new double[]{
                        hitVec.xCoord - rm.viewerPosX,
                        hitVec.yCoord - rm.viewerPosY,
                        hitVec.zCoord - rm.viewerPosZ
                });
                hitBlock = blockMop;
                break outer;
            }

            for (int s = 0; s < SUB; s++) {
                double t = (double) s / SUB;
                renderPoints.add(new double[]{
                        posX + motX * t - rm.viewerPosX,
                        posY + motY * t - rm.viewerPosY,
                        posZ + motZ * t - rm.viewerPosZ
                });
            }

            posX = nextX;
            posY = nextY;
            posZ = nextZ;
            motX *= 0.99;
            motY *= 0.99;
            motZ *= 0.99;
            motY -= gravity;
        }

        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        float lineW = (float) lineThickness.getInput();

        if (hitEntity != null && highlightEntities.isToggled()) {
            GL11.glColor3f(1.0f, 0.0f, 0.0f);
        } else {
            GL11.glColor3f(1.0f, 1.0f, 1.0f);
        }
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

        GL11.glLineWidth(lineW);

        if (hitEntity != null && highlightEntities.isToggled() && hitEntityBox != null) {
            double ex = hitEntity.lastTickPosX + (hitEntity.posX - hitEntity.lastTickPosX) * partialTicks;
            double ey = hitEntity.lastTickPosY + (hitEntity.posY - hitEntity.lastTickPosY) * partialTicks;
            double ez = hitEntity.lastTickPosZ + (hitEntity.posZ - hitEntity.lastTickPosZ) * partialTicks;
            AxisAlignedBB renderBox = new AxisAlignedBB(
                    hitEntityBox.minX - hitEntity.posX + ex,
                    hitEntityBox.minY - hitEntity.posY + ey,
                    hitEntityBox.minZ - hitEntity.posZ + ez,
                    hitEntityBox.maxX - hitEntity.posX + ex,
                    hitEntityBox.maxY - hitEntity.posY + ey,
                    hitEntityBox.maxZ - hitEntity.posZ + ez
            );
            GL11.glColor3f(1.0f, 0.0f, 0.0f);
            RenderUtils.drawOutlinedBox(renderBox, rm.viewerPosX, rm.viewerPosY, rm.viewerPosZ);
        } else if (hitBlock != null) {
            BlockPos bpos = hitBlock.getBlockPos();
            AxisAlignedBB selBox = BlockUtils.getBlockSelectionBox(bpos);
            if (selBox != null) {
                GL11.glColor3f(0.2f, 0.8f, 0.2f);
                RenderUtils.drawOutlinedBox(selBox, rm.viewerPosX, rm.viewerPosY, rm.viewerPosZ);
            }
        }

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glPopMatrix();
    }
}
