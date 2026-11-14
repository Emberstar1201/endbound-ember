package com.emberstar.endbounember.world;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class OreGenerationSettings extends SavedData {
    public enum Ore {
        COAL("coal", 8), IRON("iron", 4), COPPER("copper", 6), GOLD("gold", 2),
        REDSTONE("redstone", 2), LAPIS("lapis", 1), DIAMOND("diamond", 2),
        SHALLOW_DIAMOND("shallow_diamond", 2), EMERALD("emerald", 20),
        ANCIENT_DEBRIS("ancient_debris", 1), ANCIENT_DEBRIS_SMALL("ancient_debris_small", 1),
        NETHER_GOLD("nether_gold", 4), QUARTZ("quartz", 8);

        private final String key;
        private final int defaultAttempts;

        Ore(String key, int defaultAttempts) {
            this.key = key;
            this.defaultAttempts = defaultAttempts;
        }

        public String key() { return key; }
        public int defaultAttempts() { return defaultAttempts; }
        public static Ore byKey(String key) {
            for (Ore ore : values()) if (ore.key.equals(key)) return ore;
            return null;
        }
    }

    private static final String NAME = "endbound_ember_ore_generation";
    private static final AtomicReference<Map<Ore, Integer>> ACTIVE = new AtomicReference<>(defaults());
    private final EnumMap<Ore, Integer> attempts = new EnumMap<>(Ore.class);

    private OreGenerationSettings() {
        attempts.putAll(defaults());
        publish();
    }

    public static OreGenerationSettings get(ServerLevel level) {
        OreGenerationSettings data = level.getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(OreGenerationSettings::new, OreGenerationSettings::load, null), NAME);
        data.publish();
        return data;
    }

    public static int attempts(Ore ore) {
        return ACTIVE.get().get(ore);
    }

    public int getAttempts(Ore ore) {
        return attempts.get(ore);
    }

    public void setAttempts(Ore ore, int value) {
        attempts.put(ore, value);
        setDirty();
        publish();
    }

    private void publish() {
        ACTIVE.set(Map.copyOf(attempts));
    }

    private static EnumMap<Ore, Integer> defaults() {
        EnumMap<Ore, Integer> values = new EnumMap<>(Ore.class);
        for (Ore ore : Ore.values()) values.put(ore, ore.defaultAttempts());
        return values;
    }

    private static OreGenerationSettings load(CompoundTag tag, HolderLookup.Provider provider) {
        OreGenerationSettings data = new OreGenerationSettings();
        for (Ore ore : Ore.values()) {
            if (tag.contains(ore.key)) data.attempts.put(ore, Math.max(0, Math.min(64, tag.getInt(ore.key))));
        }
        data.publish();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        attempts.forEach((ore, value) -> tag.putInt(ore.key, value));
        return tag;
    }
}
