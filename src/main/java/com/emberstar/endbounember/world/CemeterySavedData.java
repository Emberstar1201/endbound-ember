package com.emberstar.endbounember.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

public final class CemeterySavedData extends SavedData {
    private static final String NAME = "endbound_ember_cemeteries";
    private final List<Cemetery> cemeteries = new ArrayList<>();

    public static CemeterySavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CemeterySavedData::new, CemeterySavedData::load, null), NAME);
    }

    private static CemeterySavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        CemeterySavedData data = new CemeterySavedData();
        ListTag entries = tag.getList("Cemeteries", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            List<BlockPos> candles = new ArrayList<>();
            ListTag candleTags = entry.getList("Candles", Tag.TAG_COMPOUND);
            for (int j = 0; j < candleTags.size(); j++) {
                CompoundTag pos = candleTags.getCompound(j);
                candles.add(new BlockPos(pos.getInt("X"), pos.getInt("Y"), pos.getInt("Z")));
            }
            data.cemeteries.add(new Cemetery(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.parse(entry.getString("Dimension"))),
                    new BlockPos(entry.getInt("VillageX"), entry.getInt("VillageY"), entry.getInt("VillageZ")),
                    new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z")),
                    entry.getInt("LootVersion") >= 1 && entry.getBoolean("LootInitialized"),
                    entry.getBoolean("Awarded"), candles));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entries = new ListTag();
        for (Cemetery cemetery : cemeteries) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Dimension", cemetery.dimension().location().toString());
            entry.putInt("VillageX", cemetery.village().getX());
            entry.putInt("VillageY", cemetery.village().getY());
            entry.putInt("VillageZ", cemetery.village().getZ());
            entry.putInt("X", cemetery.origin().getX());
            entry.putInt("Y", cemetery.origin().getY());
            entry.putInt("Z", cemetery.origin().getZ());
            entry.putBoolean("LootInitialized", cemetery.lootInitialized());
            entry.putInt("LootVersion", 1);
            entry.putBoolean("Awarded", cemetery.awarded());
            ListTag candles = new ListTag();
            for (BlockPos pos : cemetery.candles()) {
                CompoundTag candle = new CompoundTag();
                candle.putInt("X", pos.getX());
                candle.putInt("Y", pos.getY());
                candle.putInt("Z", pos.getZ());
                candles.add(candle);
            }
            entry.put("Candles", candles);
            entries.add(entry);
        }
        tag.put("Cemeteries", entries);
        return tag;
    }

    public List<Cemetery> cemeteries() { return cemeteries; }

    public boolean hasVillage(BlockPos village) {
        return cemeteries.stream().anyMatch(c -> {
            long dx = (long) c.village().getX() - village.getX();
            long dz = (long) c.village().getZ() - village.getZ();
            return dx * dx + dz * dz <= 64L * 64L;
        });
    }

    public void add(Cemetery cemetery) {
        cemeteries.add(cemetery);
        setDirty();
    }

    public void changed() { setDirty(); }

    public record Cemetery(ResourceKey<Level> dimension, BlockPos village, BlockPos origin,
                           boolean lootInitialized, boolean awarded, List<BlockPos> candles) {
        public Cemetery {
            village = village.immutable();
            origin = origin.immutable();
            candles = candles.stream().map(BlockPos::immutable).toList();
        }
        public Cemetery withLootInitialized() {
            return new Cemetery(dimension, village, origin, true, awarded, candles);
        }
        public Cemetery withAwarded() {
            return new Cemetery(dimension, village, origin, lootInitialized, true, candles);
        }
        public boolean contains(BlockPos pos) {
            return pos.getX() >= origin.getX() && pos.getX() < origin.getX() + 51
                    && pos.getY() >= origin.getY() && pos.getY() < origin.getY() + 9
                    && pos.getZ() >= origin.getZ() && pos.getZ() < origin.getZ() + 51;
        }
    }
}
