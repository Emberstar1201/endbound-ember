package com.emberstar.endbounember.world;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * 原版矿脉扩张。
 * <p>JSON 里定义要追加的 placed_feature，本类只负责在生物群落加载时把它们加进去；
 * 真正追加多少矿脉由 {@code data/endbound_ember/worldgen/placed_feature} 下的文件决定，
 * 是否生效则受配置项 {@link Config#ORE_VEIN_EXPANSION_ENABLED} 控制。</p>
 */
public final class OreVeinExpansion {
    private OreVeinExpansion() {
    }

    /** 在模组总线上注册自定义 BiomeModifier 序列化器。 */
    public static void register(RegisterEvent event) {
        event.register(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, helper -> helper.register(
                ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "ore_expansion"),
                ConfigGatedAddFeaturesModifier.CODEC));
    }

    /**
     * 与 {@code neoforge:add_features} 行为一致，但额外检查配置开关。
     * <p>把配置判断放在这里而不是 JSON 里，才能在整合包中随时关闭本功能而无需改数据包。</p>
     */
    public record ConfigGatedAddFeaturesModifier(HolderSet<Biome> biomes,
            HolderSet<PlacedFeature> features,
            GenerationStep.Decoration step) implements BiomeModifier {
        public static final MapCodec<ConfigGatedAddFeaturesModifier> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        Biome.LIST_CODEC.fieldOf("biomes").forGetter(ConfigGatedAddFeaturesModifier::biomes),
                        PlacedFeature.LIST_CODEC.fieldOf("features").forGetter(ConfigGatedAddFeaturesModifier::features),
                        GenerationStep.Decoration.CODEC.fieldOf("step").forGetter(ConfigGatedAddFeaturesModifier::step))
                        .apply(instance, ConfigGatedAddFeaturesModifier::new));

        @Override
        public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
            if (phase != Phase.ADD || !Config.ORE_VEIN_EXPANSION_ENABLED.get() || !this.biomes.contains(biome)) {
                return;
            }
            this.features.forEach(feature -> builder.getGenerationSettings().addFeature(this.step, feature));
        }

        @Override
        public MapCodec<? extends BiomeModifier> codec() {
            return CODEC;
        }
    }
}
