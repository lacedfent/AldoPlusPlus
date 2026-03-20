package keystrokesmod.module.impl.combat;

import keystrokesmod.Raven;
import keystrokesmod.event.AttackEvent;
import keystrokesmod.event.PrePlayerInteractEvent;
import keystrokesmod.lag.api.EnumLagDirection;
import keystrokesmod.lag.api.LagRequest;
import keystrokesmod.lag.timeout.ModuleBackedTimeout;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.CombatTargeting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.Deque;

public class LagRange extends Module {
    private static final double MINIMUM_DISTANCE_SQ = 3.0 * 3.0;

    private final SliderSetting range;
    private final SliderSetting maximumDelay;
    private final ButtonSetting sprintReset;
    private final ButtonSetting usedSplashPotion;
    private final ButtonSetting holdingWeapon;
    private final ButtonSetting realPositionIndicator;
    private final ColorSetting indicatorColor;
    private final SliderSetting indicatorLineWidth;
    private final ButtonSetting indicatorFilled;

    private EntityPlayer currentTarget;
    private double lastDistSq = -1;
    private boolean isLagging;
    private int lastSelfHurtTime;
    private boolean lastSprintState;
    private double realPosX, realPosY, realPosZ;
    private final Deque<PosSample> delayedPosSamples = new ArrayDeque<>();
    private LagRequest outboundLag;

    public LagRange() {
        super("Lag Range", category.combat);
        this.registerSetting(range = new SliderSetting("Range", 6.0, 3.0, 10.0, 0.1));
        this.registerSetting(maximumDelay = new SliderSetting("Maximum delay", "ms", 200, 50, 1000, 10));
        this.registerSetting(new DescriptionSetting("Flush conditions"));
        this.registerSetting(sprintReset = new ButtonSetting("Sprint reset", true));
        this.registerSetting(usedSplashPotion = new ButtonSetting("Used splash potion", true));
        this.registerSetting(new DescriptionSetting("Indicator"));
        this.registerSetting(realPositionIndicator = new ButtonSetting("Real position indicator", true));
        this.registerSetting(indicatorColor = new ColorSetting("Indicator color", 255, 0, 0, 100));
        this.registerSetting(indicatorLineWidth = new SliderSetting("Indicator line width", 2.0, 1.0, 5.0, 0.5));
        this.registerSetting(indicatorFilled = new ButtonSetting("Indicator filled", false));
        this.registerSetting(new DescriptionSetting("Conditions"));
        this.registerSetting(holdingWeapon = new ButtonSetting("Holding a weapon", true));
        this.closetModule = true;
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        flushLag();
        resetState();
    }

    @SubscribeEvent
    public void onPrePlayerInteract(PrePlayerInteractEvent e) {
        if (!Utils.nullCheck() || mc.thePlayer.isDead || mc.theWorld == null) {
            if (isLagging) flushLag();
            resetState();
            return;
        }

        double rangeSq = range.getInput() * range.getInput();
        boolean moving = isMoving();

        EntityPlayer nextTarget = CombatTargeting.findTarget(rangeSq);
        if (!sameTarget(nextTarget)) {
            if (isLagging) flushLag();
            lastDistSq = -1;
        }
        currentTarget = nextTarget;

        if (currentTarget != null) {
            double distSq = RotationUtils.distanceSqFromEyeToClosestOnAABB(currentTarget);

            if (isLagging) {
                if (!moving) {
                    flushLag();
                    lastDistSq = distSq;
                    return;
                }

                if (distSq > rangeSq) {
                    flushLag();
                    lastDistSq = distSq;
                    return;
                }

                if (lastDistSq >= 0 && distSq >= lastDistSq) {
                    flushLag();
                    lastDistSq = distSq;
                    return;
                }

                int hurtTime = mc.thePlayer.hurtTime;
                if (hurtTime > lastSelfHurtTime) {
                    flushLag();
                    lastSelfHurtTime = hurtTime;
                    lastDistSq = distSq;
                    return;
                }
                lastSelfHurtTime = hurtTime;

                Raven.lagHandler.releaseExpiredPackets(EnumLagDirection.OUTBOUND, (long) maximumDelay.getInput());
                updateDelayedRealPos((long) maximumDelay.getInput());

                if (holdingWeapon.isToggled() && !Utils.holdingWeapon()) {
                    flushLag();
                    lastDistSq = distSq;
                    return;
                }

                if (sprintReset.isToggled()) {
                    boolean sprintingNow = mc.thePlayer.isSprinting();
                    if (sprintingNow && !lastSprintState) {
                        flushLag();
                        lastSprintState = sprintingNow;
                        lastDistSq = distSq;
                        return;
                    }
                    lastSprintState = sprintingNow;
                }

                if (usedSplashPotion.isToggled() && mc.thePlayer.isUsingItem()) {
                    ItemStack held = mc.thePlayer.getHeldItem();
                    if (held != null && held.getItem() instanceof ItemPotion && ItemPotion.isSplash(held.getMetadata())) {
                        flushLag();
                        lastDistSq = distSq;
                        return;
                    }
                }

                lastDistSq = distSq;
                return;
            }

            int hurtTime = mc.thePlayer.hurtTime;
            lastSelfHurtTime = hurtTime;
            lastSprintState = mc.thePlayer.isSprinting();

            boolean closing = lastDistSq >= 0 && distSq < lastDistSq;
            boolean outsideMinDist = distSq > MINIMUM_DISTANCE_SQ;
            boolean weaponOk = !holdingWeapon.isToggled() || Utils.holdingWeapon();

            lastDistSq = distSq;

            if (closing && moving && outsideMinDist && hurtTime == 0 && weaponOk) {
                startLag();
            }
        } else {
            if (isLagging) flushLag();
            lastDistSq = -1;
        }
    }

    @SubscribeEvent
    public void onAttackEvent(AttackEvent e) {
        if (isLagging && e.attacker == mc.thePlayer) {
            flushLag();
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!isLagging || !realPositionIndicator.isToggled()) return;
        if (!Utils.nullCheck()) return;
        if (mc.gameSettings.thirdPersonView == 0) return;

        double viewX = mc.getRenderManager().viewerPosX;
        double viewY = mc.getRenderManager().viewerPosY;
        double viewZ = mc.getRenderManager().viewerPosZ;

        float halfW = mc.thePlayer.width / 2.0f;
        float height = mc.thePlayer.height;
        AxisAlignedBB box = new AxisAlignedBB(
                realPosX - halfW, realPosY, realPosZ - halfW,
                realPosX + halfW, realPosY + height, realPosZ + halfW
        ).offset(-viewX, -viewY, -viewZ);

        float r = indicatorColor.getRed() / 255.0f;
        float g = indicatorColor.getGreen() / 255.0f;
        float b = indicatorColor.getBlue() / 255.0f;
        float a = indicatorColor.getAlpha() / 255.0f;

        GL11.glPushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        if (indicatorFilled.isToggled()) {
            RenderUtils.drawBoundingBox(box, r, g, b, a);
        }

        GL11.glLineWidth((float) indicatorLineWidth.getInput());
        GL11.glColor4f(r, g, b, a);
        RenderGlobal.drawSelectionBoundingBox(box);

        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GL11.glPopMatrix();
    }

    private void startLag() {
        realPosX = mc.thePlayer.posX;
        realPosY = mc.thePlayer.posY;
        realPosZ = mc.thePlayer.posZ;
        delayedPosSamples.clear();
        delayedPosSamples.addLast(new PosSample(System.currentTimeMillis(), realPosX, realPosY, realPosZ));
        outboundLag = new LagRequest(EnumLagDirection.ONLY_OUTBOUND, new ModuleBackedTimeout(this));
        Raven.lagHandler.requestLag(outboundLag);
        isLagging = true;
    }

    private void flushLag() {
        if (!isLagging) return;
        if (outboundLag != null) {
            outboundLag.getTimeout().forceTimeOut();
            outboundLag = null;
        }
        delayedPosSamples.clear();
        isLagging = false;
    }

    private void resetState() {
        currentTarget = null;
        lastDistSq = -1;
        isLagging = false;
        lastSelfHurtTime = 0;
        lastSprintState = false;
        delayedPosSamples.clear();
        outboundLag = null;
    }

    private boolean sameTarget(EntityPlayer nextTarget) {
        if (currentTarget == null || nextTarget == null) {
            return currentTarget == nextTarget;
        }
        return currentTarget.getEntityId() == nextTarget.getEntityId();
    }

    private boolean isMoving() {
        return mc.thePlayer.moveForward != 0.0f || mc.thePlayer.moveStrafing != 0.0f;
    }

    private void updateDelayedRealPos(long delayMs) {
        long now = System.currentTimeMillis();
        delayedPosSamples.addLast(new PosSample(now, mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ));
        long cutoff = now - delayMs;

        while (delayedPosSamples.size() > 1) {
            PosSample next = delayedPosSamples.peekFirst();
            if (next == null || next.timeMs > cutoff) {
                break;
            }
            realPosX = next.x;
            realPosY = next.y;
            realPosZ = next.z;
            delayedPosSamples.removeFirst();
        }
    }

    private static final class PosSample {
        private final long timeMs;
        private final double x, y, z;

        private PosSample(long timeMs, double x, double y, double z) {
            this.timeMs = timeMs;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
