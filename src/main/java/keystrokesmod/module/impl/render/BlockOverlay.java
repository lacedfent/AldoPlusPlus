package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.StairsUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockDeadBush;
import net.minecraft.block.BlockFlower;
import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class BlockOverlay extends Module {
    private static final String[] RENDER_MODES = {"Hidden", "Vanilla", "Side", "Full"};
    private static final String[] COLOR_MODES = {"Static", "Gradient", "Fade", "Chroma"};
    private static final double PADDING = 0.002;

    private final SliderSetting renderMode;
    private final GroupSetting overlayGroup;
    private final ButtonSetting overlayVisible;
    private final SliderSetting overlayColorMode;
    private final ColorSetting overlayColor;
    private final ColorSetting overlayColor2;
    private final SliderSetting overlayFadeSpeed;
    private final SliderSetting overlayChromaSpeed;
    private final GroupSetting outlineGroup;
    private final ButtonSetting outlineVisible;
    private final SliderSetting outlineColorMode;
    private final ColorSetting outlineColor;
    private final ColorSetting outlineColor2;
    private final SliderSetting outlineFadeSpeed;
    private final SliderSetting outlineChromaSpeed;
    private final GroupSetting optionsGroup;
    private final SliderSetting thickness;
    private final ButtonSetting persistence;
    private final ButtonSetting depthless;
    private final ButtonSetting barriers;
    private final ButtonSetting hidePlants;

    public BlockOverlay() {
        super("Block Overlay", category.render);
        this.registerSetting(renderMode = new SliderSetting("Mode", 2, RENDER_MODES));
        this.registerSetting(overlayGroup = new GroupSetting("Overlay"));
        this.registerSetting(overlayVisible = new ButtonSetting(overlayGroup, "Visible", true));
        this.registerSetting(overlayColorMode = new SliderSetting(overlayGroup, "Color mode", 0, COLOR_MODES));
        this.registerSetting(overlayColor = new ColorSetting(overlayGroup, "Color", 0, 0, 0, 100));
        this.registerSetting(overlayColor2 = new ColorSetting(overlayGroup, "Color 2", 255, 255, 255, 100));
        this.registerSetting(overlayFadeSpeed = new SliderSetting(overlayGroup, "Fade speed", 5.5, 1.0, 10.0, 0.5));
        this.registerSetting(overlayChromaSpeed = new SliderSetting(overlayGroup, "Chroma speed", 5.5, 1.0, 10.0, 0.5));
        this.registerSetting(outlineGroup = new GroupSetting("Outline"));
        this.registerSetting(outlineVisible = new ButtonSetting(outlineGroup, "Visible", true));
        this.registerSetting(outlineColorMode = new SliderSetting(outlineGroup, "Color mode", 0, COLOR_MODES));
        this.registerSetting(outlineColor = new ColorSetting(outlineGroup, "Color", 0, 0, 0, 255));
        this.registerSetting(outlineColor2 = new ColorSetting(outlineGroup, "Color 2", 255, 255, 255, 255));
        this.registerSetting(outlineFadeSpeed = new SliderSetting(outlineGroup, "Fade speed", 5.5, 1.0, 10.0, 0.5));
        this.registerSetting(outlineChromaSpeed = new SliderSetting(outlineGroup, "Chroma speed", 5.5, 1.0, 10.0, 0.5));
        this.registerSetting(optionsGroup = new GroupSetting("Options"));
        this.registerSetting(thickness = new SliderSetting(optionsGroup, "Thickness", 2.0, 1.0, 10.0, 0.5));
        this.registerSetting(persistence = new ButtonSetting(optionsGroup, "Persistence", false));
        this.registerSetting(depthless = new ButtonSetting(optionsGroup, "Depthless", false));
        this.registerSetting(barriers = new ButtonSetting(optionsGroup, "Barriers", false));
        this.registerSetting(hidePlants = new ButtonSetting(optionsGroup, "Hide plants", false));
    }

    @Override
    public void guiUpdate() {
        int oMode = (int) overlayColorMode.getInput();
        overlayColor2.setVisible(oMode == 1 || oMode == 2, this);
        overlayFadeSpeed.setVisible(oMode == 2, this);
        overlayChromaSpeed.setVisible(oMode == 3, this);
        int olMode = (int) outlineColorMode.getInput();
        outlineColor2.setVisible(olMode == 1 || olMode == 2, this);
        outlineFadeSpeed.setVisible(olMode == 2, this);
        outlineChromaSpeed.setVisible(olMode == 3, this);
    }

    @Override
    public String getInfo() {
        return RENDER_MODES[(int) renderMode.getInput()];
    }

    @SubscribeEvent
    public void onDrawBlockHighlight(DrawBlockHighlightEvent e) {
        if ((int) renderMode.getInput() != 1) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        int mode = (int) renderMode.getInput();
        if (mode < 2) return;
        if (!Utils.nullCheck()) return;
        if (!persistence.isToggled() && mc.thePlayer.isSpectator()) return;
        BlockPos pos = getFocusedBlock();
        if (pos == null) return;
        boolean showOverlay = overlayVisible.isToggled();
        boolean showOutline = outlineVisible.isToggled();
        if (!showOverlay && !showOutline) return;

        AxisAlignedBB box = BlockUtils.getBlockSelectionBox(pos);
        if (box == null) return;
        box = box.expand(PADDING, PADDING, PADDING);
        double vx = mc.getRenderManager().viewerPosX, vy = mc.getRenderManager().viewerPosY, vz = mc.getRenderManager().viewerPosZ;
        AxisAlignedBB renderBox = box.offset(-vx, -vy, -vz);
        EnumFacing side = (mode == 2) ? mc.objectMouseOver.sideHit : null;

        int overlayStart = 0, overlayEnd = 0, outlineStart = 0, outlineEnd = 0;
        if (showOverlay) {
            overlayStart = computeStart((int) overlayColorMode.getInput(), overlayColor, overlayColor2, overlayFadeSpeed.getInput(), overlayChromaSpeed.getInput());
            overlayEnd = computeEnd((int) overlayColorMode.getInput(), overlayColor, overlayColor2, overlayFadeSpeed.getInput(), overlayChromaSpeed.getInput());
        }
        if (showOutline) {
            outlineStart = computeStart((int) outlineColorMode.getInput(), outlineColor, outlineColor2, outlineFadeSpeed.getInput(), outlineChromaSpeed.getInput());
            outlineEnd = computeEnd((int) outlineColorMode.getInput(), outlineColor, outlineColor2, outlineFadeSpeed.getInput(), outlineChromaSpeed.getInput());
        }

        GL11.glPushMatrix();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);
        if (depthless.isToggled()) GlStateManager.disableDepth();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        if (showOutline) GL11.glLineWidth((float) thickness.getInput());
        GL11.glShadeModel(GL11.GL_SMOOTH);

        IBlockState state = mc.theWorld.getBlockState(pos);
        if (state.getBlock() instanceof BlockStairs) {
            StairsUtils.drawStairs(pos, state, box, side, vx, vy, vz, overlayStart, overlayEnd, outlineStart, outlineEnd, showOverlay, showOutline, BlockOverlay::drawFace);
        } else {
            if (side != null) {
                drawFace(renderBox, side, overlayStart, overlayEnd, outlineStart, outlineEnd, showOverlay, showOutline);
            } else {
                for (EnumFacing face : EnumFacing.values()) {
                    drawFace(renderBox, face, overlayStart, overlayEnd, outlineStart, outlineEnd, showOverlay, showOutline);
                }
            }
        }

        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glLineWidth(2.0f);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GL11.glPopMatrix();
    }

    private BlockPos getFocusedBlock() {
        if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null;
        BlockPos pos = mc.objectMouseOver.getBlockPos();
        if (pos == null) return null;
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        if (block == Blocks.air) return null;
        if (block == Blocks.barrier && !barriers.isToggled()) return null;
        if (hidePlants.isToggled() && (block instanceof BlockTallGrass || block instanceof BlockFlower || block instanceof BlockDeadBush)) return null;
        return pos;
    }

    private static int interpolate(int c1, int c2, double pct) {
        Color a = new Color(c1, true), b = new Color(c2, true);
        double inv = 1.0 - pct;
        return new Color((int)(a.getRed() * pct + b.getRed() * inv), (int)(a.getGreen() * pct + b.getGreen() * inv), (int)(a.getBlue() * pct + b.getBlue() * inv), (int)(a.getAlpha() * pct + b.getAlpha() * inv)).getRGB();
    }

    private static int computeStart(int colorMode, ColorSetting color1, ColorSetting color2, double fadeSpeed, double chromaSpeed) {
        switch (colorMode) {
            case 1: return color1.getColor();
            case 2:
                double pct = Math.sin(System.currentTimeMillis() / (1100.0 - fadeSpeed * 100.0)) * 0.5 + 0.5;
                return interpolate(color1.getColor(), color2.getColor(), pct);
            case 3:
                int alpha = (color1.getColor() >> 24) & 0xFF;
                return Utils.mergeAlpha(Utils.getChroma((long) chromaSpeed), alpha);
            default: return color1.getColor();
        }
    }

    private static int computeEnd(int colorMode, ColorSetting color1, ColorSetting color2, double fadeSpeed, double chromaSpeed) {
        switch (colorMode) {
            case 1: return color2.getColor();
            case 2:
                double pct = Math.sin((System.currentTimeMillis() + 500L) / (1100.0 - fadeSpeed * 100.0)) * 0.5 + 0.5;
                return interpolate(color1.getColor(), color2.getColor(), pct);
            default: return computeStart(colorMode, color1, color2, fadeSpeed, chromaSpeed);
        }
    }

    private static void drawFace(AxisAlignedBB box, EnumFacing face, int os, int oe, int ls, int le, boolean overlay, boolean outline) {
        Tessellator ts = Tessellator.getInstance();
        WorldRenderer wr = ts.getWorldRenderer();
        if (overlay) {
            wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
            addFaceVertices(wr, face, box, os, oe);
            ts.draw();
        }
        if (outline) {
            wr.begin(2, DefaultVertexFormats.POSITION_COLOR);
            addFaceVertices(wr, face, box, ls, le);
            ts.draw();
        }
    }

    private static void v(WorldRenderer wr, double x, double y, double z, int color) {
        wr.pos(x, y, z).color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF).endVertex();
    }

    private static void addFaceVertices(WorldRenderer wr, EnumFacing face, AxisAlignedBB box, int start, int end) {
        switch (face) {
            case UP:
                v(wr, box.minX, box.maxY, box.maxZ, start);
                v(wr, box.maxX, box.maxY, box.maxZ, end);
                v(wr, box.maxX, box.maxY, box.minZ, start);
                v(wr, box.minX, box.maxY, box.minZ, end);
                break;
            case DOWN:
                v(wr, box.maxX, box.minY, box.maxZ, start);
                v(wr, box.minX, box.minY, box.maxZ, end);
                v(wr, box.minX, box.minY, box.minZ, start);
                v(wr, box.maxX, box.minY, box.minZ, end);
                break;
            case NORTH:
                v(wr, box.maxX, box.maxY, box.minZ, start);
                v(wr, box.maxX, box.minY, box.minZ, end);
                v(wr, box.minX, box.minY, box.minZ, start);
                v(wr, box.minX, box.maxY, box.minZ, end);
                break;
            case SOUTH:
                v(wr, box.minX, box.maxY, box.maxZ, start);
                v(wr, box.minX, box.minY, box.maxZ, end);
                v(wr, box.maxX, box.minY, box.maxZ, start);
                v(wr, box.maxX, box.maxY, box.maxZ, end);
                break;
            case EAST:
                v(wr, box.maxX, box.maxY, box.minZ, start);
                v(wr, box.maxX, box.maxY, box.maxZ, end);
                v(wr, box.maxX, box.minY, box.maxZ, start);
                v(wr, box.maxX, box.minY, box.minZ, end);
                break;
            case WEST:
                v(wr, box.minX, box.maxY, box.maxZ, start);
                v(wr, box.minX, box.maxY, box.minZ, end);
                v(wr, box.minX, box.minY, box.minZ, start);
                v(wr, box.minX, box.minY, box.maxZ, end);
                break;
        }
    }
}
