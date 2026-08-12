package keystrokesmod.module.impl.fun;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class SlotMachine extends Module {
    private static final String[] SYMBOLS = new String[]{"7", "BAR", "CHERRY", "LEMON", "BELL", "DIAMOND"};
    private static final int SYMBOL_COUNT = SYMBOLS.length;
    private static final Map<String, Integer> TEXTURES = new HashMap<>();

    private final ButtonSetting autoSpin;
    private final SliderSetting spinDelay;
    private final ButtonSetting rightClickSpin;
    private final SliderSetting scale;

    private boolean placed;
    private double machineX;
    private double machineY;
    private double machineZ;
    private double machineYaw;

    private final Reel[] reels = new Reel[3];
    private boolean spinActive;
    private boolean wonLast;
    private String resultText;
    private long resultTime;
    private long nextAutoSpin;
    private long lastFrame;
    private boolean prevMouseDown;
    private float knobY;

    public SlotMachine() {
        super("Slot Machine", category.fun);
        this.registerSetting(autoSpin = new ButtonSetting("Auto spin", false));
        this.registerSetting(spinDelay = new SliderSetting("Spin delay", 4.0, 1.0, 15.0, 0.5));
        this.registerSetting(rightClickSpin = new ButtonSetting("Right click to spin", true));
        this.registerSetting(scale = new SliderSetting("Scale", 1.0, 0.5, 2.0, 0.1));
        for (int i = 0; i < 3; i++) {
            reels[i] = new Reel();
        }
    }

    @Override
    public void onEnable() {
        place();
    }

    @Override
    public void onDisable() {
        placed = false;
        spinActive = false;
        resultText = "";
        resultTime = 0;
        lastFrame = 0;
        for (Reel r : reels) {
            r.spinning = false;
        }
    }

    private void place() {
        if (!Utils.nullCheck()) return;
        machineY = Math.floor(mc.thePlayer.posY);
        double lookX = -Math.sin(Math.toRadians(mc.thePlayer.rotationYaw));
        double lookZ = Math.cos(Math.toRadians(mc.thePlayer.rotationYaw));
        double bestX = 0;
        double bestZ = 0;
        for (double dist = 2.2; dist <= 5.0; dist += 0.6) {
            double tx = mc.thePlayer.posX + lookX * dist;
            double tz = mc.thePlayer.posZ + lookZ * dist;
            if (spotFree(tx, tz)) {
                bestX = tx;
                bestZ = tz;
                break;
            }
        }
        if (bestX == 0 && bestZ == 0) {
            bestX = mc.thePlayer.posX + lookX * 3.0;
            bestZ = mc.thePlayer.posZ + lookZ * 3.0;
        }
        machineX = bestX;
        machineZ = bestZ;
        machineYaw = Math.toDegrees(Math.atan2(mc.thePlayer.posX - machineX, mc.thePlayer.posZ - machineZ));
        placed = true;
        knobY = 0.78f;
        lastFrame = 0;
    }

    private boolean spotFree(double x, double z) {
        int baseY = (int) Math.floor(mc.thePlayer.posY);
        double[] xs = new double[]{-0.7, 0.0, 0.7};
        double[] zs = new double[]{-0.4, 0.0, 0.4};
        for (int y = 1; y <= 3; y++) {
            for (double ix : xs) {
                for (double iz : zs) {
                    BlockPos pos = new BlockPos(Math.floor(x + ix), baseY + y, Math.floor(z + iz));
                    Block block = mc.theWorld.getBlockState(pos).getBlock();
                    if (block.isFullBlock()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void onUpdate() {
        if (!placed || !Utils.nullCheck()) {
            return;
        }
        if (spinActive) {
            boolean allStopped = true;
            for (Reel r : reels) {
                if (r.spinning) {
                    allStopped = false;
                    break;
                }
            }
            if (allStopped) {
                spinActive = false;
                evaluateResult();
            }
        }
        if (mc.currentScreen == null) {
            if (rightClickSpin.isToggled()) {
                boolean down = Mouse.isButtonDown(1);
                if (down && !prevMouseDown) {
                    if (rayHitsMachine()) {
                        spin();
                    }
                }
                prevMouseDown = down;
            }
            if (autoSpin.isToggled()) {
                long now = System.currentTimeMillis();
                if (nextAutoSpin == 0 || now >= nextAutoSpin) {
                    spin();
                    nextAutoSpin = now + (long) (spinDelay.getInput() * 1000.0);
                }
            }
        }
    }

    @Override
    public String getInfo() {
        if (wonLast && resultText != null && System.currentTimeMillis() - resultTime < 5000) {
            return "Jackpot!";
        }
        return "";
    }

    private void spin() {
        if (spinActive || !Utils.nullCheck()) return;
        spinActive = true;
        wonLast = false;
        resultText = "";
        Random rand = new Random();
        boolean forceWin = rand.nextDouble() < 0.15;
        int winSymbol = rand.nextInt(SYMBOL_COUNT);
        long now = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            Reel r = reels[i];
            r.target = forceWin ? winSymbol : rand.nextInt(SYMBOL_COUNT);
            r.spinning = true;
            r.stopAt = now + 1200L + i * 600L;
        }
        mc.thePlayer.playSound("random.click", 1.0f, 1.3f);
    }

    private void evaluateResult() {
        int a = reels[0].target;
        int b = reels[1].target;
        int c = reels[2].target;
        String symA = SYMBOLS[a];
        String symB = SYMBOLS[b];
        String symC = SYMBOLS[c];
        if (a == b && b == c) {
            wonLast = true;
            resultText = "JACKPOT " + symA + "!";
            resultTime = System.currentTimeMillis();
            mc.thePlayer.playSound("random.levelup", 1.0f, 1.0f);
            Utils.sendMessage("&7[&dSlot&7] &aJACKPOT! &b" + symA.toLowerCase() + " &7x3&a! fortune is yours!");
        } else {
            wonLast = false;
            resultText = "no win";
            resultTime = System.currentTimeMillis();
            mc.thePlayer.playSound("random.click", 1.0f, 0.5f);
            Utils.sendMessage("&7[&dSlot&7] &8" + symA.toLowerCase() + " | " + symB.toLowerCase() + " | " + symC.toLowerCase() + " &7- no win, try again");
        }
    }

    private boolean rayHitsMachine() {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = mc.thePlayer.getLook(1.0F);
        double ox = eye.xCoord - machineX;
        double oy = eye.yCoord - machineY;
        double oz = eye.zCoord - machineZ;
        double c = Math.cos(-Math.toRadians(machineYaw));
        double s = Math.sin(-Math.toRadians(machineYaw));
        double rx = ox * c + oz * s;
        double rz = -ox * s + oz * c;
        double dx = look.xCoord * c + look.zCoord * s;
        double dz = -look.xCoord * s + look.zCoord * c;
        return slabHit(rx, oy, rz, dx, look.yCoord, dz, -0.72, 0.72, 0.0, 2.1, -0.42, 0.42);
    }

    private boolean slabHit(double ox, double oy, double oz, double dx, double dy, double dz, double minX, double maxX, double minY, double maxY, double minZ, double maxZ) {
        double tMin = 0.0;
        double tMax = Double.MAX_VALUE;
        if (Math.abs(dx) < 1.0E-9) {
            if (ox < minX || ox > maxX) return false;
        } else {
            double t1 = (minX - ox) / dx;
            double t2 = (maxX - ox) / dx;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
        }
        if (Math.abs(dy) < 1.0E-9) {
            if (oy < minY || oy > maxY) return false;
        } else {
            double t1 = (minY - oy) / dy;
            double t2 = (maxY - oy) / dy;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
        }
        if (Math.abs(dz) < 1.0E-9) {
            if (oz < minZ || oz > maxZ) return false;
        } else {
            double t1 = (minZ - oz) / dz;
            double t2 = (maxZ - oz) / dz;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
        }
        return tMax >= tMin;
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!placed || !Utils.nullCheck()) return;
        double px = mc.thePlayer.posX;
        double py = mc.thePlayer.posY;
        double pz = mc.thePlayer.posZ;
        double ddx = machineX - px;
        double ddy = machineY - py;
        double ddz = machineZ - pz;
        if (ddx * ddx + ddy * ddy + ddz * ddz > 4096.0) return;

        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0.016F : Math.min((now - lastFrame) / 1000.0F, 0.05F);
        lastFrame = now;

        boolean anySpinning = false;
        for (Reel r : reels) {
            if (!r.spinning) continue;
            anySpinning = true;
            if (now >= r.stopAt) {
                float m = r.scroll % SYMBOL_COUNT;
                if (m < 0) m += SYMBOL_COUNT;
                float remaining = (m - r.target + SYMBOL_COUNT) % SYMBOL_COUNT;
                if (remaining <= 0.001F) {
                    r.scroll = r.scroll - m + r.target;
                    r.spinning = false;
                } else {
                    r.scroll -= Math.min(12.0F * dt, remaining);
                }
            } else {
                r.scroll -= 12.0F * dt;
            }
            if (r.scroll < -100.0F) r.scroll += 100.0F;
        }

        float targetKnob = anySpinning ? 0.55f : 0.78f;
        knobY += (targetKnob - knobY) * Math.min(dt * 14.0F, 1.0F);

        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean texture2d = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);

        float s = (float) scale.getInput();
        GlStateManager.pushMatrix();
        GlStateManager.translate(machineX - mc.getRenderManager().viewerPosX, machineY - mc.getRenderManager().viewerPosY, machineZ - mc.getRenderManager().viewerPosZ);
        GlStateManager.rotate((float) machineYaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(s, s, s);

        drawMachine(now, anySpinning);
        drawReels(now);

        GlStateManager.enableBlend();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawGlass();
        GlStateManager.disableBlend();

        GlStateManager.popMatrix();

        if (lighting) GL11.glEnable(GL11.GL_LIGHTING);
        if (texture2d) GL11.glEnable(GL11.GL_TEXTURE_2D);
        if (blend) GL11.glEnable(GL11.GL_BLEND);
        if (!depthTest) GL11.glDisable(GL11.GL_DEPTH_TEST);
        if (cull) GL11.glEnable(GL11.GL_CULL_FACE);
    }

    private void drawMachine(long now, boolean spinning) {
        int plinth = 0x37474F;
        int gold = 0xFFC107;
        int cabinet = 0x16161F;
        int frontPanel = 0x1E1E2E;
        int head = 0x0F1026;
        int dark = 0x0A0A12;
        int neon = (now / 500L) % 2L == 0L ? 0x00E5FF : 0x007C8C;
        int crownColor = (now / 300L) % 2L == 0L ? (wonLast ? 0x4CAF50 : 0xE53935) : 0x111111;

        box(-0.7, 0.002, -0.38, 0.7, 0.14, 0.38, plinth);
        box(-0.72, 0.145, -0.4, 0.72, 0.185, 0.4, gold);

        box(-0.6, 0.19, -0.32, 0.6, 1.62, 0.32, cabinet);
        box(-0.597, 0.195, 0.28, 0.597, 1.618, 0.325, frontPanel);
        box(-0.598, 0.197, -0.318, -0.56, 1.616, 0.318, neon);
        box(0.56, 0.197, -0.318, 0.598, 1.616, 0.318, neon);

        box(-0.52, 0.5, 0.33, 0.52, 1.3, 0.37, gold);
        box(-0.46, 0.56, 0.375, 0.46, 1.24, 0.395, dark);
        box(-0.52, 1.245, 0.37, 0.52, 1.3, 0.41, gold);
        box(-0.52, 0.5, 0.37, 0.52, 0.555, 0.41, gold);

        box(-0.14, 0.2, 0.3, 0.14, 0.3, 0.36, gold);
        box(-0.06, 0.22, 0.363, 0.06, 0.26, 0.383, dark);
        box(-0.2, 0.36, 0.3, -0.06, 0.46, 0.356, 0xE53935);
        box(0.06, 0.36, 0.3, 0.2, 0.46, 0.356, 0x4FC3F7);
        box(-0.42, 0.195, 0.3, 0.42, 0.24, 0.34, 0x263238);

        box(0.602, 0.4, -0.06, 0.66, 0.85, 0.06, 0x9E9E9E);
        box(0.58, knobY - 0.06, -0.12, 0.68, knobY + 0.06, 0.12, 0xE53935);
        box(0.59, 0.34, -0.02, 0.61, 0.398, 0.02, 0x616161);

        box(-0.66, 1.625, -0.38, 0.66, 1.9, 0.38, head);
        double[] lampX = new double[]{-0.52, -0.26, 0.0, 0.26, 0.52};
        for (int i = 0; i < 5; i++) {
            boolean on = ((now + i * 130L) / 240L) % 2L == 0L;
            box(lampX[i] - 0.06, 1.84, 0.385, lampX[i] + 0.06, 1.9, 0.425, on ? 0xFFEB3B : 0x101018);
        }
        box(-0.18, 1.905, -0.18, 0.18, 2.0, 0.18, crownColor);
    }

    private void drawReels(long now) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        double winTop = 1.24;
        double winBottom = 0.56;
        double cellH = (winTop - winBottom) / 3.0;
        double winCenter = (winTop + winBottom) / 2.0;
        double cellZ = 0.385;
        for (int i = 0; i < 3; i++) {
            double cx = -0.29 + i * 0.29;
            double cw = 0.2;
            Reel r = reels[i];
            float base = (float) Math.floor(r.scroll);
            float frac = r.scroll - base;
            for (int row = -1; row <= 3; row++) {
                double centerY = winCenter - (row - frac) * cellH;
                quad(cx - cw / 2, centerY - cellH / 2, cellZ, cx + cw / 2, centerY + cellH / 2, 0x2E2E38);
                quad(cx - cw / 2 + 0.014, centerY - cellH / 2 + 0.014, cellZ + 0.001, cx + cw / 2 - 0.014, centerY + cellH / 2 - 0.014, 0xFAFAFA);
            }
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            for (int row = -1; row <= 3; row++) {
                int idx = ((int) base + row) % SYMBOL_COUNT;
                if (idx < 0) idx += SYMBOL_COUNT;
                double centerY = winCenter - (row - frac) * cellH;
                texturedQuad(cx - 0.07, centerY - 0.07, cellZ + 0.002, cx + 0.07, centerY + 0.07, symbolTexture(SYMBOLS[idx]));
            }
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    private void drawGlass() {
        Tessellator ts = Tessellator.getInstance();
        WorldRenderer w = ts.getWorldRenderer();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        w.begin(7, DefaultVertexFormats.POSITION_COLOR);
        w.pos(-0.46, 0.56, 0.403).color(255, 255, 255, 26).endVertex();
        w.pos(-0.10, 0.56, 0.403).color(255, 255, 255, 26).endVertex();
        w.pos(0.10, 1.24, 0.403).color(255, 255, 255, 26).endVertex();
        w.pos(-0.26, 1.24, 0.403).color(255, 255, 255, 26).endVertex();
        w.pos(-0.06, 0.56, 0.403).color(255, 255, 255, 64).endVertex();
        w.pos(0.14, 0.56, 0.403).color(255, 255, 255, 64).endVertex();
        w.pos(0.34, 1.24, 0.403).color(255, 255, 255, 64).endVertex();
        w.pos(0.14, 1.24, 0.403).color(255, 255, 255, 64).endVertex();
        ts.draw();
    }

    private static int symbolTexture(String symbol) {
        Integer cached = TEXTURES.get(symbol);
        if (cached != null) return cached;
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillOval(8, 8, 112, 112);
        g.setColor(new Color(0xECEFF1));
        g.setStroke(new BasicStroke(6.0f));
        g.drawOval(8, 8, 112, 112);
        g.setFont(new Font("Arial", Font.BOLD, 80));
        if (symbol.equals("7")) {
            g.setColor(new Color(0xE53935));
            FontMetrics fm = g.getFontMetrics();
            g.drawString("7", (128 - fm.stringWidth("7")) / 2, 90);
        } else if (symbol.equals("BAR")) {
            g.setColor(new Color(0x111111));
            g.fillRoundRect(20, 34, 88, 60, 20, 20);
            g.setColor(Color.WHITE);
            Font barFont = new Font("Arial", Font.BOLD, 36);
            g.setFont(barFont);
            FontMetrics fm = g.getFontMetrics();
            g.drawString("BAR", (128 - fm.stringWidth("BAR")) / 2, 76);
        } else if (symbol.equals("CHERRY")) {
            g.setColor(new Color(0x2E7D32));
            g.setStroke(new BasicStroke(6.0f));
            g.draw(new Line2D.Double(40, 60, 36, 24));
            g.draw(new Line2D.Double(88, 60, 92, 24));
            g.setColor(new Color(0xE53935));
            g.fillOval(24, 48, 38, 38);
            g.fillOval(68, 48, 38, 38);
            g.setColor(new Color(0x2E7D32));
            g.fillOval(16, 8, 26, 16);
            g.fillOval(86, 8, 26, 16);
        } else if (symbol.equals("LEMON")) {
            g.setColor(new Color(0xFDD835));
            g.fill(new Ellipse2D.Double(20, 24, 88, 80));
            g.setColor(new Color(0xF9A825));
            g.setStroke(new BasicStroke(7.0f));
            g.draw(new Ellipse2D.Double(20, 24, 88, 80));
        } else if (symbol.equals("BELL")) {
            g.setColor(new Color(0xFFB300));
            g.fillOval(16, 12, 96, 80);
            g.setColor(new Color(0xE65100));
            g.fillRect(56, 88, 16, 16);
            g.setColor(new Color(0xFFE082));
            g.fillOval(32, 24, 26, 24);
        } else {
            int[] xs = new int[]{64, 116, 64, 12};
            int[] ys = new int[]{12, 64, 116, 64};
            g.setColor(new Color(0x8E24AA));
            g.fillPolygon(xs, ys, 4);
            g.setColor(new Color(0xE1BEE7));
            g.fillPolygon(new int[]{64, 90, 64, 38}, new int[]{22, 62, 96, 62}, 4);
        }
        g.dispose();
        DynamicTexture dt = new DynamicTexture(img);
        dt.updateDynamicTexture();
        int id = dt.getGlTextureId();
        TEXTURES.put(symbol, id);
        return id;
    }

    private void box(double x0, double y0, double z0, double x1, double y1, double z1, int color) {
        float r = (color >> 16 & 0xFF) / 255.0f;
        float g = (color >> 8 & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        Tessellator ts = Tessellator.getInstance();
        WorldRenderer w = ts.getWorldRenderer();
        w.begin(7, DefaultVertexFormats.POSITION_COLOR);
        w.pos(x0, y1, z0).color(r, g, b, 1.0f).endVertex();
        w.pos(x0, y1, z1).color(r, g, b, 1.0f).endVertex();
        w.pos(x1, y1, z1).color(r, g, b, 1.0f).endVertex();
        w.pos(x1, y1, z0).color(r, g, b, 1.0f).endVertex();
        float rb = r * 0.55f;
        float gb = g * 0.55f;
        float bb = b * 0.55f;
        w.pos(x0, y0, z0).color(rb, gb, bb, 1.0f).endVertex();
        w.pos(x1, y0, z0).color(rb, gb, bb, 1.0f).endVertex();
        w.pos(x1, y0, z1).color(rb, gb, bb, 1.0f).endVertex();
        w.pos(x0, y0, z1).color(rb, gb, bb, 1.0f).endVertex();
        float rf = r * 0.95f;
        float gf = g * 0.95f;
        float bf = b * 0.95f;
        w.pos(x0, y0, z1).color(rf, gf, bf, 1.0f).endVertex();
        w.pos(x0, y1, z1).color(rf, gf, bf, 1.0f).endVertex();
        w.pos(x1, y1, z1).color(rf, gf, bf, 1.0f).endVertex();
        w.pos(x1, y0, z1).color(rf, gf, bf, 1.0f).endVertex();
        float rk = r * 0.7f;
        float gk = g * 0.7f;
        float bk = b * 0.7f;
        w.pos(x0, y0, z0).color(rk, gk, bk, 1.0f).endVertex();
        w.pos(x0, y1, z0).color(rk, gk, bk, 1.0f).endVertex();
        w.pos(x1, y1, z0).color(rk, gk, bk, 1.0f).endVertex();
        w.pos(x1, y0, z0).color(rk, gk, bk, 1.0f).endVertex();
        float re = r * 0.75f;
        float ge = g * 0.75f;
        float be = b * 0.75f;
        w.pos(x1, y0, z0).color(re, ge, be, 1.0f).endVertex();
        w.pos(x1, y1, z0).color(re, ge, be, 1.0f).endVertex();
        w.pos(x1, y1, z1).color(re, ge, be, 1.0f).endVertex();
        w.pos(x1, y0, z1).color(re, ge, be, 1.0f).endVertex();
        float rw = r * 0.75f;
        float gw = g * 0.75f;
        float bw = b * 0.75f;
        w.pos(x0, y0, z0).color(rw, gw, bw, 1.0f).endVertex();
        w.pos(x0, y0, z1).color(rw, gw, bw, 1.0f).endVertex();
        w.pos(x0, y1, z1).color(rw, gw, bw, 1.0f).endVertex();
        w.pos(x0, y1, z0).color(rw, gw, bw, 1.0f).endVertex();
        ts.draw();
    }

    private void quad(double x0, double y0, double z, double x1, double y1, int color) {
        float r = (color >> 16 & 0xFF) / 255.0f;
        float g = (color >> 8 & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        Tessellator ts = Tessellator.getInstance();
        WorldRenderer w = ts.getWorldRenderer();
        w.begin(7, DefaultVertexFormats.POSITION_COLOR);
        w.pos(x0, y0, z).color(r, g, b, 1.0f).endVertex();
        w.pos(x0, y1, z).color(r, g, b, 1.0f).endVertex();
        w.pos(x1, y1, z).color(r, g, b, 1.0f).endVertex();
        w.pos(x1, y0, z).color(r, g, b, 1.0f).endVertex();
        ts.draw();
    }

    private void texturedQuad(double x0, double y0, double z, double x1, double y1, int texId) {
        GlStateManager.bindTexture(texId);
        Tessellator ts = Tessellator.getInstance();
        WorldRenderer w = ts.getWorldRenderer();
        w.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        w.pos(x0, y0, z).tex(0.0, 1.0).color(255, 255, 255, 255).endVertex();
        w.pos(x0, y1, z).tex(0.0, 0.0).color(255, 255, 255, 255).endVertex();
        w.pos(x1, y1, z).tex(1.0, 0.0).color(255, 255, 255, 255).endVertex();
        w.pos(x1, y0, z).tex(1.0, 1.0).color(255, 255, 255, 255).endVertex();
        ts.draw();
    }

    private static class Reel {
        float scroll;
        int target;
        boolean spinning;
        long stopAt;
    }
}