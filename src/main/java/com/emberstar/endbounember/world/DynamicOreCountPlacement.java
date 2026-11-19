package com.emberstar.endbounember.world;

import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.RepeatingPlacement;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class DynamicOreCountPlacement extends RepeatingPlacement {
    /** 内置矿脉扩张整体上调约 10%，保持稀疏补充生成，不直接大幅提高矿团尺寸。 */
    private static final float ORE_VEIN_EXPANSION_SCALE = 1.1F;

    private final OreGenerationSettings.Ore ore;

    public DynamicOreCountPlacement(OreGenerationSettings.Ore ore) {
        this.ore = ore;
    }

    public static final MapCodec<DynamicOreCountPlacement> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(ResourceLocation.CODEC.fieldOf("ore").xmap(
                    id -> OreGenerationSettings.Ore.byKey(id.getPath()),
                    value -> ResourceLocation.fromNamespaceAndPath("endbound_ember", value.key()))
                    .forGetter(placement -> placement.ore))
                    .apply(instance, DynamicOreCountPlacement::new));

    private static final PlacementModifierType<DynamicOreCountPlacement> TYPE = () -> CODEC;

    public static void register(RegisterEvent event) {
        event.register(Registries.PLACEMENT_MODIFIER_TYPE,
                ResourceLocation.fromNamespaceAndPath("endbound_ember", "dynamic_ore_count"),
                () -> TYPE);
    }

    @Override
    protected int count(RandomSource random, BlockPos pos) {
        int baseAttempts = Math.max(0, Math.min(64, OreGenerationSettings.attempts(this.ore)));
        float scaledAttempts = baseAttempts * ORE_VEIN_EXPANSION_SCALE;
        int attempts = (int) scaledAttempts;
        // 放置次数必须为整数：小数部分按概率进位，使长期平均生成量准确提高约 10%。
        if (attempts < 64 && random.nextFloat() < scaledAttempts - attempts) attempts++;
        return Math.min(64, attempts);
    }

    @Override
    public PlacementModifierType<?> type() {
        return TYPE;
    }
}
