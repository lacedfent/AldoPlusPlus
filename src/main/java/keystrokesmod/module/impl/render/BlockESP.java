package keystrokesmod.module.impl.render;

import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.BlockListSetting;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.BlockESPCache;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class BlockESP extends Module {
    private final BlockListSetting blockList;
    private final ButtonSetting outline;
    private final ButtonSetting shade;
    private final SliderSetting range;
    private final SliderSetting maxRenders;
    private final SliderSetting scanSpeed;

    private BlockESPCache cache;
    private int prevListHash;

    public BlockESP() {
        super("BlockESP", category.render);
        this.registerSetting(blockList = new BlockListSetting("Blocks"));
        this.registerSetting(outline = new ButtonSetting("Outline", true));
        this.registerSetting(shade = new ButtonSetting("Shade", false));
        this.registerSetting(range = new SliderSetting("Range", 64, 8, 256, 8));
        this.registerSetting(maxRenders = new SliderSetting("Max renders", 2048, 128, 8192, 128));
        this.registerSetting(scanSpeed = new SliderSetting("Scan speed", 8, 1, 32, 1));
    }

    @Override
    public void onEnable() {
        cache = new BlockESPCache(blockList);
        prevListHash = blockList.getBlocks().hashCode();
        if (!blockList.getBlocks().isEmpty()) {
            cache.enqueueLoadedChunks();
        }
    }

    @Override
    public void onDisable() {
        if (cache != null) {
            cache.clear();
            cache = null;
        }
    }

    @Override
    public void onUpdate() {
        if (cache == null || !Utils.nullCheck()) return;
        int h = blockList.getBlocks().hashCode();
        if (h != prevListHash) {
            prevListHash = h;
            cache.onSettingsChanged();
        }
        cache.tickScan((int) scanSpeed.getInput());
    }

    @Override
    public String getInfo() {
        if (cache == null) return "";
        int total = cache.totalCached();
        return total > 0 ? String.valueOf(total) : "";
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent ev) {
        if (cache == null || !Utils.nullCheck()) return;
        double rangeSq = range.getInput() * range.getInput();
        double px = mc.thePlayer.posX;
        double py = mc.thePlayer.posY;
        double pz = mc.thePlayer.posZ;
        boolean doOutline = outline.isToggled();
        boolean doShade = shade.isToggled();
        int max = (int) maxRenders.getInput();
        int rendered = 0;

        for (Map.Entry<Long, Set<BlockPos>> entry : cache.entries()) {
            if (rendered >= max) break;
            for (BlockPos pos : entry.getValue()) {
                if (rendered >= max) break;
                double dx = pos.getX() + 0.5 - px;
                double dy = pos.getY() + 0.5 - py;
                double dz = pos.getZ() + 0.5 - pz;
                if (dx * dx + dy * dy + dz * dz > rangeSq) continue;
                AxisAlignedBB box = BlockUtils.getBlockSelectionBox(pos);
                if (box != null && !RenderUtils.isInViewFrustum(box)) continue;
                EnumSet<EnumFacing> visibleFaces = EnumSet.noneOf(EnumFacing.class);
                for (EnumFacing f : EnumFacing.VALUES) {
                    if (!cache.contains(pos.offset(f))) visibleFaces.add(f);
                }
                if (visibleFaces.isEmpty()) continue;
                IBlockState state = mc.theWorld.getBlockState(pos);
                MapColor mapColor = state.getBlock().getMapColor(state);
                int rgb = mapColor != null ? (0xFF << 24 | mapColor.colorValue) : 0xFFFF0000;
                RenderUtils.renderBlockShape(pos, state, rgb, doOutline, doShade, visibleFaces);
                rendered++;
            }
        }
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinWorldEvent e) {
        if (e.entity == mc.thePlayer && cache != null) {
            cache.clear();
            cache.enqueueLoadedChunks();
        }
    }

    @SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        if (cache == null) return;

        if (e.getPacket() instanceof S23PacketBlockChange) {
            S23PacketBlockChange pkt = (S23PacketBlockChange) e.getPacket();
            cache.onBlockChange(pkt.getBlockPosition(), pkt.getBlockState());
        }
        else if (e.getPacket() instanceof S22PacketMultiBlockChange) {
            S22PacketMultiBlockChange pkt = (S22PacketMultiBlockChange) e.getPacket();
            for (S22PacketMultiBlockChange.BlockUpdateData data : pkt.getChangedBlocks()) {
                cache.onBlockChange(data.getPos(), data.getBlockState());
            }
        }
        else if (e.getPacket() instanceof S21PacketChunkData) {
            S21PacketChunkData pkt = (S21PacketChunkData) e.getPacket();
            if (pkt.getExtractedSize() == 0) {
                cache.removeChunk(pkt.getChunkX(), pkt.getChunkZ());
            } else {
                cache.enqueueChunk(pkt.getChunkX(), pkt.getChunkZ());
            }
        }
        else if (e.getPacket() instanceof S26PacketMapChunkBulk) {
            S26PacketMapChunkBulk pkt = (S26PacketMapChunkBulk) e.getPacket();
            for (int i = 0; i < pkt.getChunkCount(); i++) {
                cache.enqueueChunk(pkt.getChunkX(i), pkt.getChunkZ(i));
            }
        }
    }
}
