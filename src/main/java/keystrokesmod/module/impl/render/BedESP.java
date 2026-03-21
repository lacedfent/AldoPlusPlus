package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.SharedBlockHighlightCache;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.Utils;

import java.awt.Color;
import net.minecraft.block.BlockBed;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BedESP extends Module {

    private static final String[] COLOR_MODES = {"Static", "Gradient", "Rainbow"};

    private SliderSetting colorMode;
    private ColorSetting color;
    private ColorSetting color2;
    private SliderSetting gradientSpeed;
    private SliderSetting range;
    private SliderSetting scanSpeed;
    private ButtonSetting firstBed;
    private ButtonSetting renderFullBlock;

    private final List<BlockPos[]> lastRenderedBedPairs = new ArrayList<>();

    public BedESP() {
        super("BedESP", category.render);
        this.registerSetting(colorMode = new SliderSetting("Color mode", 0, COLOR_MODES));
        this.registerSetting(color = new ColorSetting("Color", 255, 85, 85, 64));
        this.registerSetting(color2 = new ColorSetting("Color 2", 85, 85, 255, 64));
        this.registerSetting(gradientSpeed = new SliderSetting("Gradient speed", 1.0, 0.1, 8.0, 0.1));
        this.registerSetting(range = new SliderSetting("Range", 10.0, 2.0, 200.0, 2.0));
        this.registerSetting(scanSpeed = new SliderSetting("Scan speed", 8.0, 1.0, 32.0, 1.0));
        this.registerSetting(firstBed = new ButtonSetting("Only render first bed", false));
        this.registerSetting(renderFullBlock = new ButtonSetting("Render full block", false));
    }

    @Override
    public void guiUpdate() {
        int mode = (int) colorMode.getInput();
        color.setVisible(true, this);
        color2.setVisible(mode == 1, this);
        gradientSpeed.setVisible(mode == 1, this);
    }

    @Override
    public void onEnable() {
        SharedBlockHighlightCache cache = SharedBlockHighlightCache.get();
        cache.attachBed();
        cache.enqueueLoadedChunks();
    }

    @Override
    public void onDisable() {
        SharedBlockHighlightCache.get().detachBed();
        lastRenderedBedPairs.clear();
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinWorldEvent e) {
        if (e.entity == mc.thePlayer) {
            lastRenderedBedPairs.clear();
        }
    }

    public int getScanSpeedBudget() {
        return isEnabled() ? (int) scanSpeed.getInput() : 0;
    }

    @Override
    public String getInfo() {
        int n = SharedBlockHighlightCache.get().totalBedFeet();
        return n > 0 ? String.valueOf(n) : "";
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }
        SharedBlockHighlightCache cache = SharedBlockHighlightCache.get();
        if (!cache.anyConsumerActive()) {
            return;
        }
        float blockHeight = getBlockHeight();
        double rangeSq = range.getInput() * range.getInput();
        double px = mc.thePlayer.posX;
        double py = mc.thePlayer.posY;
        double pz = mc.thePlayer.posZ;

        Set<BlockPos> currentFeet = new HashSet<>();
        for (Map.Entry<Long, Set<BlockPos>> chunk : cache.entriesBedFeet()) {
            for (BlockPos foot : chunk.getValue()) {
                double dx = foot.getX() + 0.5 - px;
                double dy = foot.getY() + 0.5 - py;
                double dz = foot.getZ() + 0.5 - pz;
                if (dx * dx + dy * dy + dz * dz > rangeSq) {
                    continue;
                }
                BlockPos[] pair = footAndHead(foot);
                if (pair == null) {
                    continue;
                }
                AxisAlignedBB bedBb = bedWorldBounds(pair[0], pair[1], blockHeight);
                if (!RenderUtils.isInViewFrustum(bedBb)) {
                    continue;
                }
                currentFeet.add(foot);
            }
        }

        if (firstBed.isToggled() && !currentFeet.isEmpty()) {
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (BlockPos foot : currentFeet) {
                double dx = foot.getX() + 0.5 - px;
                double dy = foot.getY() + 0.5 - py;
                double dz = foot.getZ() + 0.5 - pz;
                double d = dx * dx + dy * dy + dz * dz;
                if (d < bestD) {
                    bestD = d;
                    best = foot;
                }
            }
            currentFeet.clear();
            if (best != null) {
                currentFeet.add(best);
            }
        }

        List<BlockPos[]> pairsToRender = new ArrayList<>();
        Set<BlockPos> addedFeet = new HashSet<>();

        for (BlockPos foot : currentFeet) {
            BlockPos[] pair = footAndHead(foot);
            if (pair == null) {
                continue;
            }
            AxisAlignedBB bb = bedWorldBounds(pair[0], pair[1], blockHeight);
            if (!RenderUtils.isInViewFrustum(bb)) {
                continue;
            }
            if (addedFeet.add(foot)) {
                pairsToRender.add(copyBedPair(pair));
            }
        }

        for (BlockPos[] prev : new ArrayList<>(lastRenderedBedPairs)) {
            if (prev == null || prev.length < 2) {
                continue;
            }
            BlockPos foot = prev[0];
            BlockPos head = prev[1];
            if (addedFeet.contains(foot)) {
                continue;
            }
            IBlockState footSt = mc.theWorld.getBlockState(foot);
            IBlockState headSt = mc.theWorld.getBlockState(head);
            if (!(headSt.getBlock() instanceof BlockBed)) {
                continue;
            }
            if (isBedFoot(footSt)) {
                continue;
            }
            double dx = foot.getX() + 0.5 - px;
            double dy = foot.getY() + 0.5 - py;
            double dz = foot.getZ() + 0.5 - pz;
            if (dx * dx + dy * dy + dz * dz > rangeSq) {
                continue;
            }
            AxisAlignedBB bb = bedWorldBounds(foot, head, blockHeight);
            if (!RenderUtils.isInViewFrustum(bb)) {
                continue;
            }
            pairsToRender.add(copyBedPair(prev));
            addedFeet.add(foot);
        }

        for (BlockPos[] pair : pairsToRender) {
            renderBed(pair, blockHeight);
        }

        lastRenderedBedPairs.clear();
        for (BlockPos[] pair : pairsToRender) {
            lastRenderedBedPairs.add(copyBedPair(pair));
        }
    }

    private static BlockPos[] copyBedPair(BlockPos[] pair) {
        return new BlockPos[]{new BlockPos(pair[0]), new BlockPos(pair[1])};
    }

    private static boolean isBedFoot(IBlockState st) {
        return st != null && st.getBlock() instanceof BlockBed
                && st.getValue((IProperty) BlockBed.PART) == BlockBed.EnumPartType.FOOT;
    }

    /** World-space bed outline bounds; matches {@link #renderBed} geometry. */
    private static AxisAlignedBB bedWorldBounds(BlockPos foot, BlockPos head, float height) {
        int fx = foot.getX(), fy = foot.getY(), fz = foot.getZ();
        double h = fy + height;
        if (foot.getX() != head.getX()) {
            if (foot.getX() > head.getX()) {
                return new AxisAlignedBB(fx - 1.0, fy, fz, fx + 1.0, h, fz + 1.0);
            }
            return new AxisAlignedBB(fx, fy, fz, fx + 2.0, h, fz + 1.0);
        }
        if (foot.getZ() > head.getZ()) {
            return new AxisAlignedBB(fx, fy, fz - 1.0, fx + 1.0, h, fz + 1.0);
        }
        return new AxisAlignedBB(fx, fy, fz, fx + 1.0, h, fz + 2.0);
    }

    private BlockPos[] footAndHead(BlockPos foot) {
        IBlockState st = mc.theWorld.getBlockState(foot);
        if (!(st.getBlock() instanceof BlockBed)) {
            return null;
        }
        EnumFacing facing = (EnumFacing) st.getValue((IProperty) BlockBed.FACING);
        return new BlockPos[]{foot, foot.offset(facing)};
    }

    private void renderBed(BlockPos[] blocks, float height) {
        double x = blocks[0].getX() - mc.getRenderManager().viewerPosX;
        double y = blocks[0].getY() - mc.getRenderManager().viewerPosY;
        double z = blocks[0].getZ() - mc.getRenderManager().viewerPosZ;
        GL11.glBlendFunc(770, 771);
        GL11.glEnable(3042);
        GL11.glLineWidth(2.0f);
        GL11.glDisable(3553);
        GL11.glDisable(2929);
        GL11.glDepthMask(false);
        int col = getCurrentColor();
        float drawA = (col >> 24 & 0xFF) / 255.0f;
        float r = (col >> 16 & 0xFF) / 255.0f;
        float g = (col >> 8 & 0xFF) / 255.0f;
        float b = (col & 0xFF) / 255.0f;
        GL11.glColor4d(r, g, b, drawA);
        AxisAlignedBB axisAlignedBB;
        if (blocks[0].getX() != blocks[1].getX()) {
            if (blocks[0].getX() > blocks[1].getX()) {
                axisAlignedBB = new AxisAlignedBB(x - 1.0, y, z, x + 1.0, y + height, z + 1.0);
            } else {
                axisAlignedBB = new AxisAlignedBB(x, y, z, x + 2.0, y + height, z + 1.0);
            }
        } else if (blocks[0].getZ() > blocks[1].getZ()) {
            axisAlignedBB = new AxisAlignedBB(x, y, z - 1.0, x + 1.0, y + height, z + 1.0);
        } else {
            axisAlignedBB = new AxisAlignedBB(x, y, z, x + 1.0, y + height, z + 2.0);
        }
        RenderUtils.drawBoundingBox(axisAlignedBB, r, g, b, drawA);
        GL11.glEnable(3553);
        GL11.glEnable(2929);
        GL11.glDepthMask(true);
        GL11.glDisable(3042);
    }

    private float getBlockHeight() {
        return renderFullBlock.isToggled() ? 1 : 0.5625F;
    }

    /** Used by BedESP rendering and BedAura outline. */
    public int getCurrentColor() {
        int mode = (int) colorMode.getInput();
        switch (mode) {
            case 0: // Static
                return color.getColor();
            case 1: { // Gradient — higher speed = faster blend between color and color 2
                double pct = Math.sin(System.currentTimeMillis() * gradientSpeed.getInput() / 1000.0) * 0.5 + 0.5;
                Color c1 = new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
                Color c2 = new Color(color2.getRed(), color2.getGreen(), color2.getBlue(), color2.getAlpha());
                Color blended = Theme.convert(c1, c2, pct);
                int a = (int) (color.getAlpha() * pct + color2.getAlpha() * (1 - pct));
                return Utils.mergeAlpha(blended.getRGB(), a);
            }
            case 2: // Rainbow
                return Utils.mergeAlpha(Utils.getChroma(2L, 0L), color.getAlpha());
            default:
                return color.getColor();
        }
    }
}
