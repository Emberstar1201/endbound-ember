package com.emberstar.endbounember.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ZombieGirlCampSavedData extends SavedData {
    private static final String NAME = "endbound_ember_zombie_girl_camps";
    private final List<BlockPos> camps = new ArrayList<>();
    private final Set<Long> generatedChunks = new HashSet<>();

    public static ZombieGirlCampSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        ZombieGirlCampSavedData::new,
                        ZombieGirlCampSavedData::load,
                        null),
                NAME);
    }

    private static ZombieGirlCampSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        ZombieGirlCampSavedData data = new ZombieGirlCampSavedData();
        ListTag positions = tag.getList("Positions", Tag.TAG_COMPOUND);
        for (int i = 0; i < positions.size(); i++) {
            CompoundTag entry = positions.getCompound(i);
            BlockPos pos = new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z"));
            data.camps.add(pos);
            data.generatedChunks.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        }
        for (long key : tag.getLongArray("GeneratedChunks")) {
            data.generatedChunks.add(key);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag positions = new ListTag();
        for (BlockPos pos : camps) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("X", pos.getX());
            entry.putInt("Y", pos.getY());
            entry.putInt("Z", pos.getZ());
            positions.add(entry);
        }
        tag.put("Positions", positions);
        tag.putLongArray("GeneratedChunks", generatedChunks.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    public boolean hasGenerated(ChunkPos chunk) {
        return generatedChunks.contains(ChunkPos.asLong(chunk.x, chunk.z));
    }

    public void addCamp(BlockPos pos, ChunkPos chunk) {
        long key = ChunkPos.asLong(chunk.x, chunk.z);
        if (generatedChunks.add(key)) {
            camps.add(pos.immutable());
            setDirty();
        }
    }

    public BlockPos findNearest(BlockPos from) {
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (BlockPos camp : camps) {
            double distance = camp.distSqr(from);
            if (distance < best) {
                best = distance;
                nearest = camp;
            }
        }
        return nearest;
    }
}
