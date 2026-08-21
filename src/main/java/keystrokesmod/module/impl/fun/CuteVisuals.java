package keystrokesmod.module.impl.fun;

import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.Random;

public class CuteVisuals extends Module {
    private static final int MAX_HEARTS = 50;
    private static final int MAX_DOTS = 100;
    private static final int MAX_BED_PARTICLES = 200;
    private static final int MAX_RAINBOWS = 5;

    private static final int TRAIL_HEART_SEGMENTS = 30;
    private static final int BED_HEART_SEGMENTS = 20;
    private static final int DOT_SEGMENTS = 8;
    private static final int DOT_FILL_SEGMENTS = 6;
    private static final int RAINBOW_SEGMENTS = 30;

    private static final double TWO_PI = Math.PI * 2.0;

    private static final double HEART_RADIUS = 1.5;
    private static final double HEART_SIZE = 0.15;
    private static final double HEART_FLOAT_SPEED = 1.0;
    private static final float HEART_LINE_WIDTH = 1.5f;

    private static final double DOT_SIZE = 0.04;
    private static final double DOT_SPREAD = 0.9;
    private static final double DOT_DRIFT_SPEED = 0.3;
    private static final float DOT_LINE_WIDTH = 2.0f;

    private static final double RAINBOW_SIZE = 3.0;
    private static final double RAINBOW_BAND_WIDTH = 0.15;

    private final Random random = new Random();

    private final ButtonSetting bedSound;
    private final ButtonSetting bedBurst;
    private final SliderSetting bedBurstCount;
    private final SliderSetting bedBurstSize;
    private final SliderSetting bedBurstSpeed;
    private final SliderSetting bedBurstLifetime;
    private final ButtonSetting rainbow;
    private final SliderSetting rainbowLineWidth;
    private final SliderSetting rainbowDuration;
    private final ButtonSetting onlyWhileMoving;
    private final SliderSetting opacity;
    private final ButtonSetting hearts;
    private final SliderSetting heartsSpawnRate;
    private final SliderSetting heartsLifetime;
    private final ButtonSetting dots;
    private final SliderSetting dotsSpawnRate;
    private final SliderSetting dotsLifetime;
    private final ButtonSetting pulse;

    private final double[] heartX = new double[MAX_HEARTS];
    private final double[] heartY = new double[MAX_HEARTS];
    private final double[] heartZ = new double[MAX_HEARTS];
    private final long[] heartTime = new long[MAX_HEARTS];
    private final float[] heartRotY = new float[MAX_HEARTS];
    private final float[] heartRotZ = new float[MAX_HEARTS];
    private final float[] heartScale = new float[MAX_HEARTS];
    private final int[] heartType = new int[MAX_HEARTS];
    private final boolean[] heartActive = new boolean[MAX_HEARTS];
    private int activeHeartCount = 0;
    private long lastHeartSpawn = 0;

    private final double[] dotX = new double[MAX_DOTS];
    private final double[] dotY = new double[MAX_DOTS];
    private final double[] dotZ = new double[MAX_DOTS];
    private final double[] dotDriftX = new double[MAX_DOTS];
    private final double[] dotDriftY = new double[MAX_DOTS];
    private final double[] dotDriftZ = new double[MAX_DOTS];
    private final long[] dotTime = new long[MAX_DOTS];
    private final float[] dotScale = new float[MAX_DOTS];
    private final int[] dotType = new int[MAX_DOTS];
    private final boolean[] dotActive = new boolean[MAX_DOTS];
    private int activeDotCount = 0;
    private long lastDotSpawn = 0;
    private double lastDotPosX = 0;
    private double lastDotPosY = 0;
    private double lastDotPosZ = 0;
    private boolean hasLastDotPos = false;

    private final double[] bedParticleX = new double[MAX_BED_PARTICLES];
    private final double[] bedParticleY = new double[MAX_BED_PARTICLES];
    private final double[] bedParticleZ = new double[MAX_BED_PARTICLES];
    private final double[] bedParticleVX = new double[MAX_BED_PARTICLES];
    private final double[] bedParticleVY = new double[MAX_BED_PARTICLES];
    private final double[] bedParticleVZ = new double[MAX_BED_PARTICLES];
    private final float[] bedParticleScale = new float[MAX_BED_PARTICLES];
    private final int[] bedParticleType = new int[MAX_BED_PARTICLES];
    private final long[] bedParticleTime = new long[MAX_BED_PARTICLES];
    private final boolean[] bedParticleActive = new boolean[MAX_BED_PARTICLES];
    private int activeBedParticleCount = 0;

    private final double[] rainbowX = new double[MAX_RAINBOWS];
    private final double[] rainbowY = new double[MAX_RAINBOWS];
    private final double[] rainbowZ = new double[MAX_RAINBOWS];
    private final float[] rainbowYaw = new float[MAX_RAINBOWS];
    private final long[] rainbowTime = new long[MAX_RAINBOWS];
    private final boolean[] rainbowActive = new boolean[MAX_RAINBOWS];
    private int activeRainbowCount = 0;

    private boolean diggingBed = false;
    private double bedX = 0;
    private double bedY = 0;
    private double bedZ = 0;

    private final double[] trailHeartShapeX = new double[TRAIL_HEART_SEGMENTS + 1];
    private final double[] trailHeartShapeY = new double[TRAIL_HEART_SEGMENTS + 1];
    private final double[] bedHeartShapeX = new double[BED_HEART_SEGMENTS + 1];
    private final double[] bedHeartShapeY = new double[BED_HEART_SEGMENTS + 1];
    private final double[] dotCircleX = new double[DOT_SEGMENTS + 1];
    private final double[] dotCircleY = new double[DOT_SEGMENTS + 1];
    private final double[] dotFillX = new double[DOT_FILL_SEGMENTS + 1];
    private final double[] dotFillY = new double[DOT_FILL_SEGMENTS + 1];
    private final double[] starShapeX = new double[9];
    private final double[] starShapeY = new double[9];
    private final double[] rainbowArcCos = new double[RAINBOW_SEGMENTS + 1];
    private final double[] rainbowArcSin = new double[RAINBOW_SEGMENTS + 1];

    private final double[] rainbowRed = {0.85, 0.60, 0.50, 0.50, 1.00, 1.00, 1.00};
    private final double[] rainbowGreen = {0.50, 0.50, 0.75, 1.00, 0.90, 0.60, 0.40};
    private final double[] rainbowBlue = {1.00, 1.00, 1.00, 0.65, 0.50, 0.40, 0.50};

    private final double[] bedParticleRed = {1.00, 1.00, 1.00, 0.50, 0.50, 0.60, 0.85};
    private final double[] bedParticleGreen = {0.40, 0.60, 0.90, 1.00, 0.75, 0.50, 0.50};
    private final double[] bedParticleBlue = {0.50, 0.40, 0.50, 0.65, 1.00, 1.00, 1.00};

    public CuteVisuals() {
        super("Cute Visuals", category.fun);

        this.registerSetting(new DescriptionSetting("Bed break"));
        this.registerSetting(bedSound = new ButtonSetting("Bed Sound", true));
        this.registerSetting(bedBurst = new ButtonSetting("Bed Burst", true));
        this.registerSetting(bedBurstCount = new SliderSetting("Bed Burst Count", "", 20, 5, 40, 1));
        this.registerSetting(bedBurstSize = new SliderSetting("Bed Burst Size", "", 0.20, 0.05, 0.6, 0.01));
        this.registerSetting(bedBurstSpeed = new SliderSetting("Bed Burst Speed", "", 2.5, 0.5, 7.0, 0.1));
        this.registerSetting(bedBurstLifetime = new SliderSetting("Bed Burst Lifetime", "ms", 1500, 500, 3000, 100));
        this.registerSetting(rainbow = new ButtonSetting("Rainbow", true));
        this.registerSetting(rainbowLineWidth = new SliderSetting("Rainbow Line Width", "", 5.0, 1.0, 12.0, 0.5));
        this.registerSetting(rainbowDuration = new SliderSetting("Rainbow Duration", "ms", 3000, 1000, 6000, 200));

        this.registerSetting(new DescriptionSetting("Trail"));
        this.registerSetting(onlyWhileMoving = new ButtonSetting("Only While Moving", true));
        this.registerSetting(opacity = new SliderSetting("Opacity", "%", 85, 20, 100, 5));
        this.registerSetting(hearts = new ButtonSetting("Hearts", true));
        this.registerSetting(heartsSpawnRate = new SliderSetting("Hearts Spawn Rate", "ms", 200, 50, 500, 10));
        this.registerSetting(heartsLifetime = new SliderSetting("Hearts Lifetime", "ms", 1500, 500, 4000, 100));
        this.registerSetting(dots = new ButtonSetting("Dots", true));
        this.registerSetting(dotsSpawnRate = new SliderSetting("Dots Spawn Rate", "ms", 100, 20, 200, 10));
        this.registerSetting(dotsLifetime = new SliderSetting("Dots Lifetime", "ms", 1500, 500, 5000, 100));
        this.registerSetting(pulse = new ButtonSetting("Pulse", false));

        initializeGeometry();
    }

    @Override
    public void onEnable() {
        clearHearts();
        clearDots();
        clearBedParticles();
        clearRainbows();

        lastHeartSpawn = 0;
        lastDotSpawn = 0;
        hasLastDotPos = false;
        diggingBed = false;
    }

    private void initializeGeometry() {
        for (int i = 0; i <= TRAIL_HEART_SEGMENTS; i++) {
            double t = (double) i / TRAIL_HEART_SEGMENTS * TWO_PI;
            double sinT = Math.sin(t);
            trailHeartShapeX[i] = 16.0 * sinT * sinT * sinT;
            trailHeartShapeY[i] = 13.0 * Math.cos(t)
                    - 5.0 * Math.cos(2.0 * t)
                    - 2.0 * Math.cos(3.0 * t)
                    - Math.cos(4.0 * t);
        }

        for (int i = 0; i <= BED_HEART_SEGMENTS; i++) {
            double t = (double) i / BED_HEART_SEGMENTS * TWO_PI;
            double sinT = Math.sin(t);
            bedHeartShapeX[i] = 16.0 * sinT * sinT * sinT;
            bedHeartShapeY[i] = 13.0 * Math.cos(t)
                    - 5.0 * Math.cos(2.0 * t)
                    - 2.0 * Math.cos(3.0 * t)
                    - Math.cos(4.0 * t);
        }

        for (int i = 0; i <= DOT_SEGMENTS; i++) {
            double angle = (double) i / DOT_SEGMENTS * TWO_PI;
            dotCircleX[i] = Math.cos(angle);
            dotCircleY[i] = Math.sin(angle);
        }

        for (int i = 0; i <= DOT_FILL_SEGMENTS; i++) {
            double angle = (double) i / DOT_FILL_SEGMENTS * TWO_PI;
            dotFillX[i] = Math.cos(angle);
            dotFillY[i] = Math.sin(angle);
        }

        for (int i = 0; i <= 8; i++) {
            double angle = i * Math.PI / 4.0 - Math.PI / 2.0;
            double radius = (i % 2 == 0) ? 12.0 : 5.0;
            starShapeX[i] = Math.cos(angle) * radius;
            starShapeY[i] = Math.sin(angle) * radius;
        }
    }

    private void clearHearts() {
        for (int i = 0; i < MAX_HEARTS; i++) heartActive[i] = false;
        activeHeartCount = 0;
    }

    private void clearDots() {
        for (int i = 0; i < MAX_DOTS; i++) dotActive[i] = false;
        activeDotCount = 0;
    }

    private void clearBedParticles() {
        for (int i = 0; i < MAX_BED_PARTICLES; i++) bedParticleActive[i] = false;
        activeBedParticleCount = 0;
    }

    private void clearRainbows() {
        for (int i = 0; i < MAX_RAINBOWS; i++) rainbowActive[i] = false;
        activeRainbowCount = 0;
    }

    @Override
    public void onUpdate() {
        if (!Utils.nullCheck()) {
            return;
        }

        double posX = mc.thePlayer.posX;
        double posY = mc.thePlayer.posY;
        double posZ = mc.thePlayer.posZ;

        boolean heartsEnabled = hearts.isToggled();
        boolean dotsEnabled = dots.isToggled();

        if (!heartsEnabled && activeHeartCount > 0) clearHearts();
        if (!dotsEnabled && activeDotCount > 0) clearDots();

        boolean canSpawnDots = hasLastDotPos;
        if (!hasLastDotPos) {
            lastDotPosX = posX;
            lastDotPosY = posY;
            lastDotPosZ = posZ;
            hasLastDotPos = true;
        }

        if (!heartsEnabled && !dotsEnabled) {
            lastDotPosX = posX;
            lastDotPosY = posY;
            lastDotPosZ = posZ;
            return;
        }

        if (onlyWhileMoving.isToggled() && !Utils.isMoving()) {
            return;
        }

        long now = System.currentTimeMillis();

        if (heartsEnabled) {
            long heartRate = (long) heartsSpawnRate.getInput();
            if (now - lastHeartSpawn >= heartRate) {
                lastHeartSpawn = now;
                spawnHeart(posX, posY + 0.5, posZ, now);
            }
        }

        if (dotsEnabled && canSpawnDots) {
            long dotRate = (long) dotsSpawnRate.getInput();
            if (now - lastDotSpawn >= dotRate) {
                lastDotSpawn = now;
                spawnDot(posY, now);
            }
        }

        lastDotPosX = posX;
        lastDotPosY = posY;
        lastDotPosZ = posZ;
    }

    private void spawnHeart(double x, double y, double z, long now) {
        int slot = findFreeHeartSlot();
        boolean wasActive = heartActive[slot];

        double angle = random.nextDouble() * TWO_PI;
        double distance = random.nextDouble() * HEART_RADIUS;

        heartX[slot] = x + Math.cos(angle) * distance;
        heartZ[slot] = z + Math.sin(angle) * distance;
        heartY[slot] = y + random.nextDouble() * 0.5;
        heartTime[slot] = now;
        heartRotY[slot] = (float) (random.nextDouble() * 360.0);
        heartRotZ[slot] = (float) (random.nextDouble() * 30.0 - 15.0);
        heartScale[slot] = (float) (HEART_SIZE * (0.6 + random.nextDouble() * 0.8));
        heartType[slot] = random.nextInt(3);
        heartActive[slot] = true;

        if (!wasActive) activeHeartCount++;
    }

    private void spawnDot(double posY, long now) {
        int slot = findFreeDotSlot();
        boolean wasActive = dotActive[slot];

        dotX[slot] = lastDotPosX + (random.nextDouble() - 0.5) * DOT_SPREAD;
        dotY[slot] = posY + 0.3 + random.nextDouble() * 1.2;
        dotZ[slot] = lastDotPosZ + (random.nextDouble() - 0.5) * DOT_SPREAD;

        dotDriftX[slot] = (random.nextDouble() - 0.5) * DOT_DRIFT_SPEED;
        dotDriftY[slot] = (0.3 + random.nextDouble() * 0.7) * DOT_DRIFT_SPEED;
        dotDriftZ[slot] = (random.nextDouble() - 0.5) * DOT_DRIFT_SPEED;

        dotTime[slot] = now;
        dotScale[slot] = (float) (DOT_SIZE * (0.5 + random.nextDouble()));
        dotType[slot] = random.nextInt(4);
        dotActive[slot] = true;

        if (!wasActive) activeDotCount++;
    }

    private int findFreeHeartSlot() {
        for (int i = 0; i < MAX_HEARTS; i++) {
            if (!heartActive[i]) return i;
        }
        long oldest = Long.MAX_VALUE;
        int oldestIndex = 0;
        for (int i = 0; i < MAX_HEARTS; i++) {
            if (heartTime[i] < oldest) {
                oldest = heartTime[i];
                oldestIndex = i;
            }
        }
        return oldestIndex;
    }

    private int findFreeDotSlot() {
        for (int i = 0; i < MAX_DOTS; i++) {
            if (!dotActive[i]) return i;
        }
        long oldest = Long.MAX_VALUE;
        int oldestIndex = 0;
        for (int i = 0; i < MAX_DOTS; i++) {
            if (dotTime[i] < oldest) {
                oldest = dotTime[i];
                oldestIndex = i;
            }
        }
        return oldestIndex;
    }

    private int findFreeBedParticleSlot() {
        for (int i = 0; i < MAX_BED_PARTICLES; i++) {
            if (!bedParticleActive[i]) return i;
        }
        long oldest = Long.MAX_VALUE;
        int oldestIndex = 0;
        for (int i = 0; i < MAX_BED_PARTICLES; i++) {
            if (bedParticleTime[i] < oldest) {
                oldest = bedParticleTime[i];
                oldestIndex = i;
            }
        }
        return oldestIndex;
    }

    private int findFreeRainbowSlot() {
        for (int i = 0; i < MAX_RAINBOWS; i++) {
            if (!rainbowActive[i]) return i;
        }
        long oldest = Long.MAX_VALUE;
        int oldestIndex = 0;
        for (int i = 0; i < MAX_RAINBOWS; i++) {
            if (rainbowTime[i] < oldest) {
                oldest = rainbowTime[i];
                oldestIndex = i;
            }
        }
        return oldestIndex;
    }

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent event) {
        if (!(event.getPacket() instanceof C07PacketPlayerDigging)) {
            return;
        }
        C07PacketPlayerDigging packet = (C07PacketPlayerDigging) event.getPacket();
        C07PacketPlayerDigging.Action status = packet.getStatus();
        BlockPos position = packet.getPosition();
        if (status == null || position == null || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        if (status == C07PacketPlayerDigging.Action.START_DESTROY_BLOCK) {
            Block block = mc.theWorld.getBlockState(position).getBlock();
            String name = String.valueOf(block.getRegistryName()).replace("minecraft:", "");

            double x = position.getX() + 0.5;
            double y = position.getY() + 0.5;
            double z = position.getZ() + 0.5;

            if (!name.toLowerCase().contains("bed")) {
                diggingBed = false;
                return;
            }

            if (mc.thePlayer.capabilities.isCreativeMode) {
                diggingBed = false;
                spawnBedBreak(x, y, z);
            } else {
                diggingBed = true;
                bedX = x;
                bedY = y;
                bedZ = z;
            }
        } else if (status == C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK) {
            if (diggingBed) {
                spawnBedBreak(bedX, bedY, bedZ);
                diggingBed = false;
            }
        } else if (status == C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK) {
            diggingBed = false;
        }
    }

    private void spawnBedBreak(double x, double y, double z) {
        long now = System.currentTimeMillis();

        if (rainbow.isToggled()) {
            int slot = findFreeRainbowSlot();
            boolean wasActive = rainbowActive[slot];

            rainbowX[slot] = x;
            rainbowY[slot] = y;
            rainbowZ[slot] = z;
            rainbowTime[slot] = now;
            rainbowActive[slot] = true;

            rainbowYaw[slot] = (float) Math.toDegrees(
                    Math.atan2(mc.thePlayer.posX - x, mc.thePlayer.posZ - z));

            if (!wasActive) activeRainbowCount++;
        }

        if (bedBurst.isToggled()) {
            double speed = bedBurstSpeed.getInput();
            double baseSize = bedBurstSize.getInput();
            int count = (int) bedBurstCount.getInput();

            for (int i = 0; i < count; i++) {
                int slot = findFreeBedParticleSlot();
                boolean wasActive = bedParticleActive[slot];

                bedParticleX[slot] = x;
                bedParticleY[slot] = y;
                bedParticleZ[slot] = z;

                double theta = random.nextDouble() * TWO_PI;
                double phi = random.nextDouble() * Math.PI * 0.67 - Math.PI / 6.0;
                double particleSpeed = (0.8 + random.nextDouble() * 1.2) * speed;
                double cosPhi = Math.cos(phi);

                bedParticleVX[slot] = cosPhi * Math.cos(theta) * particleSpeed;
                bedParticleVY[slot] = Math.sin(phi) * particleSpeed + 1.0;
                bedParticleVZ[slot] = cosPhi * Math.sin(theta) * particleSpeed;

                int typeRoll = random.nextInt(5);
                bedParticleType[slot] = typeRoll < 2 ? 0 : typeRoll - 1;
                bedParticleScale[slot] = (float) (baseSize * (0.6 + random.nextDouble() * 0.8));
                bedParticleTime[slot] = now;
                bedParticleActive[slot] = true;

                if (!wasActive) activeBedParticleCount++;
            }
        }

        if (bedSound.isToggled()) {
            mc.thePlayer.playSound("random.orb", 1.0f, 1.5f);
            mc.thePlayer.playSound("random.levelup", 0.5f, 2.0f);
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (activeHeartCount <= 0 && activeDotCount <= 0
                && activeBedParticleCount <= 0 && activeRainbowCount <= 0) {
            return;
        }
        if (!Utils.nullCheck()) {
            return;
        }

        double cameraX = mc.getRenderManager().viewerPosX;
        double cameraY = mc.getRenderManager().viewerPosY;
        double cameraZ = mc.getRenderManager().viewerPosZ;
        long now = System.currentTimeMillis();

        renderTrail(cameraX, cameraY, cameraZ, now);
        renderBedVisuals(cameraX, cameraY, cameraZ, now);
    }

    private void renderTrail(double camX, double camY, double camZ, long now) {
        boolean renderHearts = activeHeartCount > 0 && hearts.isToggled();
        boolean renderDots = activeDotCount > 0 && dots.isToggled();
        if (!renderHearts && !renderDots) {
            return;
        }

        double opacityValue = opacity.getInput() / 100.0;

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_CULL_FACE);

        if (renderHearts) {
            renderHearts(camX, camY, camZ, now, opacityValue);
        }
        if (renderDots) {
            renderDots(camX, camY, camZ, now, opacityValue);
        }

        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glPopMatrix();
    }

    private void renderHearts(double camX, double camY, double camZ, long now, double opacityValue) {
        long lifetime = (long) heartsLifetime.getInput();
        float effectiveLineWidth = Math.max(HEART_LINE_WIDTH * 0.6f, 1.0f);

        for (int i = 0; i < MAX_HEARTS; i++) {
            if (!heartActive[i]) continue;

            long age = now - heartTime[i];
            if (age > lifetime) {
                heartActive[i] = false;
                activeHeartCount--;
                continue;
            }

            double progress = (double) age / (double) lifetime;
            double floatY = HEART_FLOAT_SPEED * progress * 1.5;

            double alpha;
            if (progress < 0.1) alpha = progress / 0.1;
            else if (progress > 0.6) alpha = (1.0 - progress) / 0.4;
            else alpha = 1.0;
            alpha *= opacityValue;

            double scaleAnimation;
            if (progress < 0.1) scaleAnimation = progress / 0.1;
            else if (progress > 0.8) scaleAnimation = (1.0 - progress) / 0.2;
            else scaleAnimation = 1.0;

            double drawX = heartX[i] + Math.sin(age * 0.002 + i * 1.7) * 0.1 - camX;
            double drawY = heartY[i] + floatY - camY;
            double drawZ = heartZ[i] + Math.cos(age * 0.0015 + i * 2.3) * 0.1 - camZ;

            double red;
            double green;
            double blue;
            int type = heartType[i];
            if (type == 0) {
                red = 1.0; green = 0.5; blue = 0.8;
            } else if (type == 1) {
                red = 1.0; green = 0.3; blue = 0.6;
            } else {
                red = 0.9; green = 0.4; blue = 0.9;
            }

            double size = heartScale[i] * scaleAnimation;
            double billboardYaw = Math.toDegrees(Math.atan2(-drawX, -drawZ));
            double spinAngle = (age * 0.1 + heartRotY[i]) % 360.0;

            GL11.glPushMatrix();
            GL11.glTranslated(drawX, drawY, drawZ);
            GL11.glRotated(billboardYaw, 0, 1, 0);
            GL11.glRotated(spinAngle, 0, 1, 0);
            GL11.glRotated(heartRotZ[i], 0, 0, 1);
            GL11.glLineWidth(effectiveLineWidth + 3.0f);
            drawTrailHeart(size, alpha, red, green, blue);
            GL11.glLineWidth(effectiveLineWidth);
            GL11.glPopMatrix();
        }
        GL11.glLineWidth(1.0f);
    }

    private void drawTrailHeart(double size, double baseAlpha, double red, double green, double blue) {
        double scale = size / 16.0;

        for (int layer = 3; layer >= 0; layer--) {
            double glowScale = scale * (1.0 + layer * 0.08);
            double layerAlpha = layer == 0 ? baseAlpha * 0.9 : baseAlpha * (0.3 / layer);

            GL11.glColor4d(red, green, blue, layerAlpha);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int i = 0; i <= TRAIL_HEART_SEGMENTS; i++) {
                GL11.glVertex3d(trailHeartShapeX[i] * glowScale, trailHeartShapeY[i] * glowScale, 0);
            }
            GL11.glEnd();
        }
    }

    private void renderDots(double camX, double camY, double camZ, long now, double opacityValue) {
        long lifetime = (long) dotsLifetime.getInput();
        boolean pulseEnabled = pulse.isToggled();

        for (int i = 0; i < MAX_DOTS; i++) {
            if (!dotActive[i]) continue;

            long age = now - dotTime[i];
            if (age > lifetime) {
                dotActive[i] = false;
                activeDotCount--;
                continue;
            }

            double progress = (double) age / (double) lifetime;

            double fade;
            if (progress < 0.1) fade = progress / 0.1;
            else if (progress > 0.5) fade = (1.0 - progress) / 0.5;
            else fade = 1.0;

            double pulseFactor = 1.0;
            if (pulseEnabled) {
                double flickerSpeed = 3.0 + dotType[i] * 1.5;
                pulseFactor = 0.5 + 0.5 * Math.sin(age * 0.01 * flickerSpeed + i * 2.7);
            }

            double ageSeconds = age / 1000.0;
            double drawX = dotX[i]
                    + dotDriftX[i] * ageSeconds
                    + Math.sin(ageSeconds * 1.5 + i * 1.3) * 0.15 - camX;
            double drawY = dotY[i] + dotDriftY[i] * ageSeconds - camY;
            double drawZ = dotZ[i]
                    + dotDriftZ[i] * ageSeconds
                    + Math.cos(ageSeconds * 1.2 + i * 2.1) * 0.15 - camZ;

            double red;
            double green;
            double blue;
            int type = dotType[i];
            if (type == 0) {
                red = 1.0; green = 0.45; blue = 0.7;
            } else if (type == 1) {
                red = 1.0; green = 0.6; blue = 0.85;
            } else if (type == 2) {
                red = 1.0; green = 0.3; blue = 0.55;
            } else {
                red = 1.0; green = 0.75; blue = 0.95;
            }

            double alpha = fade * pulseFactor * opacityValue;
            if (alpha < 0.02) continue;

            double size = dotScale[i];
            double billboardYaw = Math.toDegrees(Math.atan2(-drawX, -drawZ));

            GL11.glPushMatrix();
            GL11.glTranslated(drawX, drawY, drawZ);
            GL11.glRotated(billboardYaw, 0, 1, 0);
            GL11.glColor4d(red, green, blue, alpha);

            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex3d(0, 0, 0);
            for (int j = 0; j <= DOT_FILL_SEGMENTS; j++) {
                GL11.glVertex3d(dotFillX[j] * size, dotFillY[j] * size, 0);
            }
            GL11.glEnd();

            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int j = 0; j <= DOT_SEGMENTS; j++) {
                GL11.glVertex3d(dotCircleX[j] * size, dotCircleY[j] * size, 0);
            }
            GL11.glEnd();

            GL11.glPopMatrix();
        }
        GL11.glLineWidth(1.0f);
    }

    private void renderBedVisuals(double camX, double camY, double camZ, long now) {
        if (activeBedParticleCount <= 0 && activeRainbowCount <= 0) {
            return;
        }

        long bedLifetime = activeBedParticleCount > 0 ? (long) bedBurstLifetime.getInput() : 0;
        long rainbowDurationValue = activeRainbowCount > 0 ? (long) rainbowDuration.getInput() : 0;
        float lineWidth = (float) rainbowLineWidth.getInput();

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_CULL_FACE);

        if (activeRainbowCount > 0) {
            renderRainbows(camX, camY, camZ, now, rainbowDurationValue, lineWidth);
        }

        if (activeBedParticleCount > 0) {
            renderBedParticles(camX, camY, camZ, now, bedLifetime, lineWidth);
        }

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glPopMatrix();
    }

    private void renderRainbows(double camX, double camY, double camZ, long now, long duration, float lineWidth) {
        for (int rainbowIndex = 0; rainbowIndex < MAX_RAINBOWS; rainbowIndex++) {
            if (!rainbowActive[rainbowIndex]) continue;

            long age = now - rainbowTime[rainbowIndex];
            if (age > duration) {
                rainbowActive[rainbowIndex] = false;
                activeRainbowCount--;
                continue;
            }

            double progress = (double) age / (double) duration;

            double alpha;
            if (progress < 0.15) alpha = progress / 0.15;
            else if (progress > 0.6) alpha = (1.0 - progress) / 0.4;
            else alpha = 1.0;

            double arcProgress = progress < 0.2 ? Math.pow(progress / 0.2, 2.0) : 1.0;

            for (int segment = 0; segment <= RAINBOW_SEGMENTS; segment++) {
                double angle = (double) segment / RAINBOW_SEGMENTS * Math.PI * arcProgress;
                rainbowArcCos[segment] = Math.cos(angle);
                rainbowArcSin[segment] = Math.sin(angle);
            }

            double drawX = rainbowX[rainbowIndex] - camX;
            double drawY = rainbowY[rainbowIndex] - camY;
            double drawZ = rainbowZ[rainbowIndex] - camZ;

            GL11.glPushMatrix();
            GL11.glTranslated(drawX, drawY, drawZ);
            GL11.glRotated(rainbowYaw[rainbowIndex], 0, 1, 0);

            for (int band = 0; band < 7; band++) {
                double radius = RAINBOW_SIZE + (band - 3) * RAINBOW_BAND_WIDTH;
                if (radius < 0.1) continue;

                double red = rainbowRed[band];
                double green = rainbowGreen[band];
                double blue = rainbowBlue[band];

                drawRainbowArc(radius, red, green, blue, alpha * 0.15, lineWidth + 3.0f);
                drawRainbowArc(radius, red, green, blue, alpha * 0.30, lineWidth + 1.5f);
                drawRainbowArc(radius, red, green, blue, alpha * 0.85, lineWidth);
            }

            if (arcProgress > 0.5) {
                drawRainbowSparkles(now, arcProgress, alpha);
            }

            GL11.glPopMatrix();
        }
        GL11.glLineWidth(1.0f);
    }

    private void drawRainbowArc(double radius, double red, double green, double blue, double alpha, float lineWidth) {
        GL11.glLineWidth(lineWidth);
        GL11.glColor4d(red, green, blue, alpha);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int segment = 0; segment <= RAINBOW_SEGMENTS; segment++) {
            GL11.glVertex3d(rainbowArcCos[segment] * radius, rainbowArcSin[segment] * radius, 0);
        }
        GL11.glEnd();
    }

    private void drawRainbowSparkles(long now, double arcProgress, double alpha) {
        double sparkleAlpha = alpha * 0.7;
        double sparkleSize = 0.15;
        double rotation = now * 0.003;
        double cosRotation = Math.cos(rotation);
        double sinRotation = Math.sin(rotation);

        GL11.glColor4d(1.0, 1.0, 0.8, sparkleAlpha);

        for (int end = 0; end < 2; end++) {
            double endAngle = end == 0 ? 0.0 : Math.PI * arcProgress;
            double endX = Math.cos(endAngle) * RAINBOW_SIZE;
            double endY = Math.sin(endAngle) * RAINBOW_SIZE;

            drawSparkleRay(endX, endY, cosRotation, sinRotation, sparkleSize);
            drawSparkleRay(endX, endY, -sinRotation, cosRotation, sparkleSize);
            drawSparkleRay(endX, endY, -cosRotation, -sinRotation, sparkleSize);
            drawSparkleRay(endX, endY, sinRotation, -cosRotation, sparkleSize);
        }
    }

    private void drawSparkleRay(double x, double y, double directionX, double directionY, double size) {
        GL11.glBegin(GL11.GL_LINE_STRIP);
        GL11.glVertex3d(x, y, 0);
        GL11.glVertex3d(x + directionX * size, y + directionY * size, 0);
        GL11.glEnd();
    }

    private void renderBedParticles(double camX, double camY, double camZ, long now, long lifetime, float lineWidth) {
        GL11.glLineWidth(lineWidth);

        for (int i = 0; i < MAX_BED_PARTICLES; i++) {
            if (!bedParticleActive[i]) continue;

            long age = now - bedParticleTime[i];
            if (age > lifetime) {
                bedParticleActive[i] = false;
                activeBedParticleCount--;
                continue;
            }

            double progress = (double) age / (double) lifetime;
            double ageSeconds = age / 1000.0;

            double particleX = bedParticleX[i] + bedParticleVX[i] * ageSeconds;
            double particleY = bedParticleY[i] + bedParticleVY[i] * ageSeconds
                    - 1.5 * ageSeconds * ageSeconds;
            double particleZ = bedParticleZ[i] + bedParticleVZ[i] * ageSeconds;

            particleX += Math.sin(age * 0.002 + i * 1.7) * 0.05;
            particleZ += Math.cos(age * 0.0015 + i * 2.3) * 0.05;

            double alpha;
            if (progress < 0.1) alpha = progress / 0.1;
            else if (progress > 0.7) alpha = (1.0 - progress) / 0.3;
            else alpha = 1.0;

            double scaleAnimation;
            if (progress < 0.1) scaleAnimation = progress / 0.1;
            else if (progress > 0.7) scaleAnimation = (1.0 - progress) / 0.3;
            else scaleAnimation = 1.0;

            double drawX = particleX - camX;
            double drawY = particleY - camY;
            double drawZ = particleZ - camZ;

            int colorIndex = i % 7;
            double red = bedParticleRed[colorIndex];
            double green = bedParticleGreen[colorIndex];
            double blue = bedParticleBlue[colorIndex];

            double size = bedParticleScale[i] * scaleAnimation;
            double billboardYaw = Math.toDegrees(Math.atan2(drawX, drawZ));

            GL11.glPushMatrix();
            GL11.glTranslated(drawX, drawY, drawZ);
            GL11.glRotated(billboardYaw, 0, 1, 0);
            GL11.glRotated(ageSeconds * 40.0 + i * 60.0, 0, 0, 1);

            int type = bedParticleType[i];
            if (type == 0) {
                drawBedHeart(size, alpha, red, green, blue);
            } else if (type == 1) {
                drawBedStar(size, alpha, red, green, blue);
            } else if (type == 2) {
                drawBedDot(size, alpha, red, green, blue);
            } else {
                drawBedDiamond(size, alpha, red, green, blue);
            }

            GL11.glPopMatrix();
        }
        GL11.glLineWidth(1.0f);
    }

    private void drawBedHeart(double size, double baseAlpha, double red, double green, double blue) {
        double scale = size / 16.0;
        for (int layer = 2; layer >= 0; layer--) {
            double glowScale = scale * (1.0 + layer * 0.1);
            double layerAlpha = layer == 0 ? baseAlpha * 0.9 : baseAlpha * (0.25 / layer);

            GL11.glColor4d(red, green, blue, layerAlpha);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int i = 0; i <= BED_HEART_SEGMENTS; i++) {
                GL11.glVertex3d(bedHeartShapeX[i] * glowScale, bedHeartShapeY[i] * glowScale, 0);
            }
            GL11.glEnd();
        }
    }

    private void drawBedStar(double size, double baseAlpha, double red, double green, double blue) {
        double scale = size / 16.0;
        for (int layer = 2; layer >= 0; layer--) {
            double glowScale = scale * (1.0 + layer * 0.1);
            double layerAlpha = layer == 0 ? baseAlpha * 0.9 : baseAlpha * (0.25 / layer);

            GL11.glColor4d(red, green, blue, layerAlpha);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int i = 0; i <= 8; i++) {
                GL11.glVertex3d(starShapeX[i] * glowScale, starShapeY[i] * glowScale, 0);
            }
            GL11.glEnd();
        }
    }

    private void drawBedDot(double size, double baseAlpha, double red, double green, double blue) {
        double scale = size / 2.0;
        for (int layer = 2; layer >= 0; layer--) {
            double glowScale = scale * (1.0 + layer * 0.15);
            double layerAlpha = layer == 0 ? baseAlpha * 0.9 : baseAlpha * (0.25 / layer);

            GL11.glColor4d(red, green, blue, layerAlpha);
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex3d(0, 0, 0);
            for (int i = 0; i <= DOT_SEGMENTS; i++) {
                GL11.glVertex3d(dotCircleX[i] * glowScale, dotCircleY[i] * glowScale, 0);
            }
            GL11.glEnd();
        }
    }

    private void drawBedDiamond(double size, double baseAlpha, double red, double green, double blue) {
        double scale = size / 16.0;
        for (int layer = 2; layer >= 0; layer--) {
            double glowScale = scale * (1.0 + layer * 0.1);
            double layerAlpha = layer == 0 ? baseAlpha * 0.9 : baseAlpha * (0.25 / layer);

            GL11.glColor4d(red, green, blue, layerAlpha);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            GL11.glVertex3d(0, 14.0 * glowScale, 0);
            GL11.glVertex3d(8.0 * glowScale, 0, 0);
            GL11.glVertex3d(0, -14.0 * glowScale, 0);
            GL11.glVertex3d(-8.0 * glowScale, 0, 0);
            GL11.glVertex3d(0, 14.0 * glowScale, 0);
            GL11.glEnd();
        }
    }
}