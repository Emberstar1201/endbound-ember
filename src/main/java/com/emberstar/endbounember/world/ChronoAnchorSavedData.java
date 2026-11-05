package com.emberstar.endbounember.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 记录某个时间乱流维度是否已经放置过时之锚。
 * <p>
 * 数据存在各自维度目录下的 {@code data/} 里，因此每个玩家的乱流维度各自独立，
 * 保证「一个乱流维度里只存在一座时之锚」。
 */
public final class ChronoAnchorSavedData extends SavedData {
    private static final String NAME = "endbound_ember_chrono_anchor";

    private boolean placed;

    public static ChronoAnchorSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ChronoAnchorSavedData::new, ChronoAnchorSavedData::load, null),
                NAME);
    }

    private static ChronoAnchorSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        ChronoAnchorSavedData data = new ChronoAnchorSavedData();
        data.placed = tag.getBoolean("Placed");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean("Placed", placed);
        return tag;
    }

    public boolean isPlaced() {
        return placed;
    }

    public void markPlaced() {
        placed = true;
        setDirty();
    }
}
