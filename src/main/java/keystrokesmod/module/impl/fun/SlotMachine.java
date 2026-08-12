package keystrokesmod.module.impl.fun;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.AxisAlignedBB;
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
        lastFrame = 0;
        knobY = 0;
        for (Reel r : reels) {
            r.spinning = false;
        }
    }

    private void place() {
        if (!Utils.nullCheck()) return;
        machineY = Math.floor(mc.thePlayer.posY);
        double dx = -Math.sin(Math.toRadians(mc.thePlayer.rotationYaw));
        double dz = Math.cos(Math.toRadians(mc.thePlayer.rotationYaw));
        machineX = mc.thePlayer.posX + dx * 3.0;
        machineZ = mc.thePlayer.posZ + dz * 3.0;
        machineYaw = Math.toDegrees(Math.atan2(mc.thePlayer.posX - machineX, mc.thePlayer.posZ - machineZ));
        placed = true;
        knobY = 1.7f;
        lastFrame = 0;
    }

    @Override
    public void onUpdate() {
        if (!placed || !Utils.nullCheck()) {
            if (!Utils.nullCheck() && placed) {
                placed = false;
            }
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
        if (resultText != null && !resultText.isEmpty() && System.currentTimeMillis() - resultTime < 5000) {
            return resultText;
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
            r.stopAt = now + 1300L + i * 650L;
        }
        mc.thePlayer.playSound("random.click", 1.0f, 1.3f);
    }

    private void evaluateResult() {
        int a = reels[0].target;
        int b = reels[1].target;
        int c = reels[2].target;
        if (a == b && b == c) {
            wonLast = true;
            resultText = "JACKPOT " + SYMBOLS[a] + "!";
            resultTime = System.currentTimeMillis();
            mc.thePlayer.playSound("random.levelup", 1.0f, 1.0f);
        } else {
            wonLast = false;
            resultText = "no win";
            resultTime = System.currentTimeMillis();
            mc.thePlayer.playSound("random.click", 1.0f, 0.5f);
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
        return slabHit(rx, oy, rz, dx, look.yCoord, dz, -1.35, 1.35, 0.0, 3.1, -0.62, 0.62);
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
                    r.scroll -= Math.min(13.0F * dt, remaining);
                }
            } else {
                r.scroll -= 13.0F * dt;
            }
            if (r.scroll < -100.0F) r.scroll += 100.0F;
        }

        float targetKnob = anySpinning ? 1.35f : 1.7f;
        knobY += (targetKnob - knobY) * Math.min(dt * 12.0F, 1.0F);

        float s = (float) scale.getInput();
        GlStateManager.pushMatrix();
        GlStateManager.translate(machineX - mc.getRenderManager().viewerPosX, machineY - mc.getRenderManager().viewerPosY, machineZ - mc.getRenderManager().viewerPosZ);
        GlStateManager.rotate((float) machineYaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(s, s, s);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);

        drawMachine(now, anySpinning);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawReels(now);

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glLineWidth(2.0F);
        drawFloatingText("ALDO SLOT", 3.55, 0xFFD54F);
        if (resultText != null && !resultText.isEmpty() && now - resultTime < 5000) {
            drawFloatingText(resultText, 3.9, wonLast ? 0x4CAF50 : 0xE53935);
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GlStateManager.popMatrix();
    }

    private void drawMachine(long now, boolean spinning) {
        int base = 0x37474F;
        int baseTrim = 0xFFC107;
        int cabinet = 0x8E2430;
        int cabinetDark = 0x5D161E;
        int head = 0x1A1A2E;
        int dark = 0x111111;
        int gold = 0xFFC107;
        int lampColor = (now / 250L) % 2L == 0L ? 0xFFEB3B : 0x8E2430;
        int crownColor = (now / 300L) % 2L == 0L ? (wonLast ? 0x4CAF50 : 0xE53935) : 0x111111;

        box(-1.3, 0.0, -0.6, 1.3, 0.45, 0.6, base);
        box(-1.35, 0.45, -0.62, 1.35, 0.52, 0.62, baseTrim);
        box(-1.2, 0.52, -0.5, 1.2, 2.55, 0.5, cabinet);
        box(-1.24, 0.52, -0.5, -1.18, 2.55, 0.5, cabinetDark);
        box(1.18, 0.52, -0.5, 1.24, 2.55, 0.5, cabinetDark);
        box(-1.35, 2.55, -0.62, 1.35, 3.1, 0.62, head);
        box(-0.3, 3.1, -0.3, 0.3, 3.32, 0.3, crownColor);

        box(-0.95, 0.85, 0.5, 0.95, 2.05, 0.56, gold);
        box(-0.85, 0.9, 0.56, 0.85, 2.0, 0.58, dark);
        box(-0.95, 2.0, 0.56, 0.95, 2.05, 0.59, gold);
        box(-0.95, 0.85, 0.56, 0.95, 0.9, 0.59, gold);

        box(-0.2, 0.56, 0.5, 0.2, 0.72, 0.6, gold);
        box(-0.1, 0.6, 0.6, 0.1, 0.68, 0.62, dark);
        box(-0.9, 0.45, 0.48, 0.9, 0.56, 0.52, 0x263238);

        box(1.2, 0.9, -0.08, 1.28, 1.8, 0.08, 0x9E9E9E);
        box(1.17, knobY - 0.07, -0.15, 1.31, knobY + 0.07, 0.15, 0xE53935);

        double[] lampX = new double[]{-0.9, -0.3, 0.3, 0.9};
        for (int i = 0; i < 4; i++) {
            boolean on = ((now + i * 120L) / 250L) % 2L == 0L;
            box(lampX[i] - 0.07, 2.82, 0.62, lampX[i] + 0.07, 2.96, 0.68, on ? lampColor : 0x101018);
        }

        GL11.glColor4f(1.0f, 0.76f, 0.0f, 0.45f);
        RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(-1.2, 0.52, -0.5, 1.2, 2.55, 0.5));
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void drawReels(long now) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        double winTop = 2.0;
        double winBottom = 0.9;
        double cellH = (winTop - winBottom) / 3.0;
        double cellZ = 0.585;
        for (int i = 0; i < 3; i++) {
            double cx = -0.55 + i * 0.55;
            double cw = 0.46;
            Reel r = reels[i];
            float base = (float) Math.floor(r.scroll);
            float frac = r.scroll - base;
            for (int row = -1; row <= 3; row++) {
                double centerY = winTop - (row - frac) * cellH;
                quad(cx - cw / 2, centerY - cellH / 2, cellZ, cx + cw / 2, centerY + cellH / 2, 0xFAFAFA, true);
            }
            for (int row = -1; row <= 3; row++) {
                int idx = ((int) base + row) % SYMBOL_COUNT;
                if (idx < 0) idx += SYMBOL_COUNT;
                double centerY = winTop - (row - frac) * cellH;
                texturedQuad(cx - cw / 2 + 0.05, centerY - cellH / 2 + 0.05, cellZ + 0.002, cx + cw / 2 - 0.05, centerY + cellH / 2 - 0.05, symbolTexture(SYMBOLS[idx]));
            }
        }
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    private static int symbolTexture(String symbol) {
        Integer cached = TEXTURES.get(symbol);
        if (cached != null) return cached;
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillOval(4, 4, 56, 56);
        Font font = new Font("Arial", Font.BOLD, 42);
        g.setFont(font);
        if (symbol.equals("7")) {
            g.setColor(new Color(0xE53935));
            FontMetrics fm = g.getFontMetrics();
            g.drawString("7", (64 - fm.stringWidth("7")) / 2, 46);
        } else if (symbol.equals("BAR")) {
            g.setColor(new Color(0x111111));
            g.fillRoundRect(10, 18, 44, 28, 14, 14);
            g.setColor(Color.WHITE);
            Font barFont = new Font("Arial", Font.BOLD, 18);
            g.setFont(barFont);
            FontMetrics fm = g.getFontMetrics();
            g.drawString("BAR", (64 - fm.stringWidth("BAR")) / 2, 38);
        } else if (symbol.equals("CHERRY")) {
            g.setColor(new Color(0x2E7D32));
            g.setStroke(new BasicStroke(3.0f));
            g.draw(new Line2D.Double(20, 30, 18, 12));
            g.draw(new Line2D.Double(44, 30, 46, 12));
            g.setColor(new Color(0xE53935));
            g.fillOval(12, 24, 18, 18);
            g.fillOval(34, 24, 18, 18);
            g.setColor(new Color(0x2E7D32));
            g.fillOval(8, 8, 12, 7);
            g.fillOval(42, 8, 12, 7);
        } else if (symbol.equals("LEMON")) {
            g.setColor(new Color(0xFDD835));
            g.fill(new Ellipse2D.Double(10, 12, 44, 40));
            g.setColor(new Color(0xF9A825));
            g.setStroke(new BasicStroke(3.0f));
            g.draw(new Ellipse2D.Double(10, 12, 44, 40));
        } else if (symbol.equals("BELL")) {
            g.setColor(new Color(0xFFB300));
            g.fillOval(8, 8, 48, 40);
            g.setColor(new Color(0xE65100));
            g.fillRect(28, 44, 8, 8);
            g.setColor(new Color(0xFFE082));
            g.fillOval(16, 14, 10, 10);
        } else {
            int[] xs = new int[]{32, 58, 32, 6};
            int[] ys = new int[]{6, 32, 58, 32};
            g.setColor(new Color(0x8E24AA));
            g.fillPolygon(xs, ys, 4);
            g.setColor(new Color(0xE1BEE7));
            g.fillPolygon(new int[]{32, 44, 32, 20}, new int[]{10, 30, 44, 30}, 4);
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
        float rf = r * 0.9f;
        float gf = g * 0.9f;
        float bf = b * 0.9f;
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

    private void quad(double x0, double y0, double z, double x1, double y1, int color, boolean solid) {
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
        w.pos(x0, y0, z).tex(0.0, 0.0).color(255, 255, 255, 255).endVertex();
        w.pos(x0, y1, z).tex(0.0, 1.0).color(255, 255, 255, 255).endVertex();
        w.pos(x1, y1, z).tex(1.0, 1.0).color(255, 255, 255, 255).endVertex();
        w.pos(x1, y0, z).tex(1.0, 0.0).color(255, 255, 255, 255).endVertex();
        ts.draw();
    }

    private void drawFloatingText(String text, double y, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0, y, 0.0);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-0.025F, -0.025F, 0.025F);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        mc.fontRendererObj.drawStringWithShadow(text, -mc.fontRendererObj.getStringWidth(text) / 2.0f, 0, color);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GlStateManager.popMatrix();
    }

    private static class Reel {
        float scroll;
        int target;
        boolean spinning;
        long stopAt;
    }
}
