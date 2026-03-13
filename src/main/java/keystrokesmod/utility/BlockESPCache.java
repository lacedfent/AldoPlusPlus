package keystrokesmod.utility;

import keystrokesmod.module.setting.impl.BlockListSetting;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-chunk cache of block positions that match a {@link BlockListSetting}.
 * Scan work is spread across ticks via a section queue, and single-block
 * updates from packets are O(1).
 */
public final class BlockESPCache {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private final BlockListSetting setting;

    private final Map<Long, Set<BlockPos>> byChunk = new ConcurrentHashMap<>();
    private final Deque<long[]> scanQueue = new ArrayDeque<>();

    private Set<String> matcherIds;
    private Map<String, Object> matcherWildcards;
    private int lastListHash;

    public BlockESPCache(BlockListSetting setting) {
        this.setting = setting;
        rebuildMatcher();
    }

    public void clear() {
        byChunk.clear();
        scanQueue.clear();
    }

    public void enqueueChunk(int chunkX, int chunkZ) {
        if (setting.getBlocks().isEmpty()) return;
        scanQueue.addLast(new long[]{chunkX, chunkZ});
    }

    public void removeChunk(int chunkX, int chunkZ) {
        byChunk.remove(key(chunkX, chunkZ));
    }

    public void enqueueLoadedChunks() {
        if (setting.getBlocks().isEmpty()) return;
        scanQueue.clear();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        int rd = mc.gameSettings.renderDistanceChunks;
        int pcx = (int) mc.thePlayer.posX >> 4;
        int pcz = (int) mc.thePlayer.posZ >> 4;
        for (int cx = pcx - rd; cx <= pcx + rd; cx++) {
            for (int cz = pcz - rd; cz <= pcz + rd; cz++) {
                Chunk chunk = mc.theWorld.getChunkFromChunkCoords(cx, cz);
                if (chunk != null && !(chunk instanceof EmptyChunk)) {
                    enqueueChunk(cx, cz);
                }
            }
        }
    }

    public void tickScan(int maxSections) {
        if (mc.theWorld == null) return;
        refreshMatcher();
        if (matcherIds.isEmpty() && matcherWildcards.isEmpty()) return;
        int remaining = maxSections;
        while (remaining > 0 && !scanQueue.isEmpty()) {
            long[] cpos = scanQueue.pollFirst();
            int cx = (int) cpos[0], cz = (int) cpos[1];
            Chunk chunk = mc.theWorld.getChunkFromChunkCoords(cx, cz);
            if (chunk == null || chunk instanceof EmptyChunk) continue;
            remaining -= scanChunk(chunk);
        }
    }

    public void onBlockChange(BlockPos pos, IBlockState newState) {
        refreshMatcher();
        long ck = key(pos.getX() >> 4, pos.getZ() >> 4);
        if (matches(newState)) {
            byChunk.computeIfAbsent(ck, k -> ConcurrentHashMap.newKeySet())
                    .add(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
        } else {
            Set<BlockPos> set = byChunk.get(ck);
            if (set != null) set.remove(pos);
        }
    }

    public void onSettingsChanged() {
        rebuildMatcher();
        rescanAll();
    }

    public void rescanAll() {
        byChunk.clear();
        scanQueue.clear();
        if (setting.getBlocks().isEmpty()) return;
        enqueueLoadedChunks();
    }

    public Iterable<Map.Entry<Long, Set<BlockPos>>> entries() {
        return byChunk.entrySet();
    }

    public int totalCached() {
        int n = 0;
        for (Set<BlockPos> s : byChunk.values()) n += s.size();
        return n;
    }

    public boolean hasPendingScans() {
        return !scanQueue.isEmpty();
    }

    public boolean contains(BlockPos pos) {
        if (pos == null) return false;
        long ck = key(pos.getX() >> 4, pos.getZ() >> 4);
        Set<BlockPos> set = byChunk.get(ck);
        return set != null && set.contains(pos);
    }

    private int scanChunk(Chunk chunk) {
        int scanned = 0;
        long ck = key(chunk.xPosition, chunk.zPosition);
        Set<BlockPos> found = ConcurrentHashMap.newKeySet();
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        int baseX = chunk.xPosition << 4;
        int baseZ = chunk.zPosition << 4;
        for (int si = 0; si < sections.length; si++) {
            ExtendedBlockStorage section = sections[si];
            if (section == null) continue;
            scanned++;
            int baseY = si << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        IBlockState state = section.get(x, y, z);
                        if (matches(state)) {
                            found.add(new BlockPos(baseX + x, baseY + y, baseZ + z));
                        }
                    }
                }
            }
        }
        if (!found.isEmpty()) {
            byChunk.put(ck, found);
        } else {
            byChunk.remove(ck);
        }
        return Math.max(scanned, 1);
    }

    private boolean matches(IBlockState state) {
        if (state == null) return false;
        Block block = state.getBlock();
        if (block == null) return false;
        Object nameObj = Block.blockRegistry.getNameForObject(block);
        if (nameObj == null) return false;
        String registryId = nameObj.toString();
        if (matcherWildcards.containsKey(registryId)) return true;
        int meta = block.getMetaFromState(state);
        if (meta != 0) {
            if (matcherIds.contains(registryId + ":" + meta)) return true;
        }
        return matcherIds.contains(registryId);
    }

    private void refreshMatcher() {
        int h = setting.getBlocks().hashCode();
        if (h != lastListHash) rebuildMatcher();
    }

    private void rebuildMatcher() {
        List<String> blocks = setting.getBlocks();
        lastListHash = blocks.hashCode();
        matcherIds = new HashSet<>();
        matcherWildcards = new HashMap<>();
        for (String id : blocks) {
            if (id.endsWith(":*")) {
                String base = id.substring(0, id.length() - 2);
                matcherWildcards.put(base, null);
            } else {
                matcherIds.add(id);
            }
        }
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}
