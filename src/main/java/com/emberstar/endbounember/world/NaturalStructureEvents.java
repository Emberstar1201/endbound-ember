package com.emberstar.endbounember.world;

import com.emberstar.endbounember.EndboundEmber;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.LinkedHashSet;
import java.util.Set;

/** 在服务端主线程收尾已完整加载的原版模板结构，不请求加载区块。 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class NaturalStructureEvents {
    private static final ResourceLocation CAMP = ResourceLocation.fromNamespaceAndPath(
            EndboundEmber.MODID, "zombie_girl_camp");
    private static final ResourceLocation CEMETERY = ResourceLocation.fromNamespaceAndPath(
            EndboundEmber.MODID, "cemetery");
    private static final int SCANS_PER_TICK = 16;
    private static final int STARTS_PER_TICK = 8;
    private static final int FINISHES_PER_TICK = 1;
    private static final Set<ChunkKey> PENDING_CHUNKS = new LinkedHashSet<>();
    private static final Set<StartKey> PENDING_STARTS = new LinkedHashSet<>();

    private record ChunkKey(ResourceKey<Level> dimension, long pos) {}
    private record StartKey(ResourceKey<Level> dimension, long pos, ResourceLocation structure) {}

    private NaturalStructureEvents() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            // NeoForge 在 FULL 转换内部触发 Load；此处只记录坐标。
            PENDING_CHUNKS.add(new ChunkKey(level.dimension(), chunk.getPos().toLong()));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        int scans = Math.min(SCANS_PER_TICK, PENDING_CHUNKS.size());
        while (scans-- > 0) {
            ChunkKey key = takeFirst(PENDING_CHUNKS);
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) continue;
            ChunkPos pos = new ChunkPos(key.pos());
            LevelChunk chunk = fullChunkNow(level, pos.x, pos.z);
            if (chunk == null) {
                // 如果尚未提升到可访问 FULL，轮到下个 tick 再检查；Unload 会移除该坐标。
                PENDING_CHUNKS.add(key);
            } else {
                scan(level, chunk);
            }
        }

        int checks = Math.min(STARTS_PER_TICK, PENDING_STARTS.size());
        int finishes = FINISHES_PER_TICK;
        while (checks-- > 0 && finishes > 0) {
            StartKey key = takeFirst(PENDING_STARTS);
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) continue;
            ChunkPos pos = new ChunkPos(key.pos());
            LevelChunk chunk = fullChunkNow(level, pos.x, pos.z);
            if (chunk == null) continue;
            Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(key.structure());
            if (structure == null) continue;
            StructureStart start = chunk.getStartForStructure(structure);
            if (start == null) continue;
            switch (finishStart(level, start, key.structure())) {
                case WAITING -> PENDING_STARTS.add(key);
                case FINISHED -> finishes--;
                case NOT_NEEDED -> { }
            }
        }
    }

    private static <T> T takeFirst(Set<T> pending) {
        var it = pending.iterator();
        T value = it.next();
        it.remove();
        return value;
    }

    private static void scan(ServerLevel level, LevelChunk chunk) {
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (var entry : chunk.getAllStarts().entrySet()) {
            ResourceLocation id = registry.getKey(entry.getKey());
            if (isTarget(id) && entry.getValue().isValid()) {
                PENDING_STARTS.add(new StartKey(level.dimension(), entry.getValue().getChunkPos().toLong(), id));
            }
        }
        for (var entry : chunk.getAllReferences().entrySet()) {
            ResourceLocation id = registry.getKey(entry.getKey());
            if (!isTarget(id)) continue;
            for (long pos : entry.getValue()) PENDING_STARTS.add(new StartKey(level.dimension(), pos, id));
        }
    }

    private static boolean isTarget(ResourceLocation id) {
        return CAMP.equals(id) || CEMETERY.equals(id);
    }

    private enum FinishResult { NOT_NEEDED, WAITING, FINISHED }

    private static FinishResult finishStart(ServerLevel level, StructureStart start, ResourceLocation id) {
        if (!start.isValid()) return FinishResult.NOT_NEEDED;
        boolean camp = CAMP.equals(id);
        boolean finishedAny = false;
        for (var piece : start.getPieces()) {
            if (!(piece instanceof FixedTemplateStructurePiece fixed)) continue;
            if (camp ? ZombieGirlCampEvents.hasFinishedNatural(level, fixed)
                    : CemeterySavedData.get(level).cemeteries().stream().anyMatch(c ->
                            c.dimension().equals(level.dimension()) && c.origin().equals(fixed.templateOrigin())
                                    && c.lootInitialized())) continue;
            // 等所有区块都完整可用再收尾；仅有 start 所在区块不够。
            if (!allChunksLoaded(level, start.getBoundingBox())) return FinishResult.WAITING;
            if (camp) {
                ZombieGirlCampEvents.finishNatural(level, start.getChunkPos(), fixed);
            } else {
                CemeteryEvents.finishNatural(level, start.getChunkPos(), fixed);
            }
            finishedAny = true;
        }
        return finishedAny ? FinishResult.FINISHED : FinishResult.NOT_NEEDED;
    }

    private static LevelChunk fullChunkNow(ServerLevel level, int x, int z) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
        return chunk != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL) ? chunk : null;
    }

    static boolean allChunksLoaded(ServerLevel level, BoundingBox box) {
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                if (fullChunkNow(level, x, z) == null) return false;
            }
        }
        return true;
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PENDING_CHUNKS.remove(new ChunkKey(level.dimension(), event.getChunk().getPos().toLong()));
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PENDING_CHUNKS.removeIf(key -> key.dimension().equals(level.dimension()));
            PENDING_STARTS.removeIf(key -> key.dimension().equals(level.dimension()));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING_CHUNKS.clear();
        PENDING_STARTS.clear();
    }
}
