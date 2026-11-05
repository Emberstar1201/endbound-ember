package com.emberstar.endbounember;

import java.util.List;
import java.util.Locale;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** 时光光环时钟的时间来源，配置界面里以按钮形式循环切换 */
    public enum TimeHaloMode implements TranslatableEnum {
        /** 系统时：跟随电脑时钟 */
        REAL,
        /** 游戏时：跟随世界昼夜 */
        GAME;

        @Override
        public Component getTranslatedName() {
            return Component.translatable(
                    "endbound_ember.configuration.timeHaloMode." + name().toLowerCase(Locale.ROOT));
        }
    }

    public static final ModConfigSpec.EnumValue<TimeHaloMode> TIME_HALO_MODE = BUILDER
            .comment("Clock display time source: REAL (system clock) or GAME (world time).")
            .defineEnum("timeHaloMode", TimeHaloMode.REAL);

    // 时光光环技能的作用范围（区块半径）：变慢与暂停都以玩家所在区块为中心
    public static final ModConfigSpec.IntValue TIME_HALO_SKILL_CHUNK_RADIUS = BUILDER
            .comment("Maximum radius in chunks for the slow and stop skills (1-8).",
                    "The radius is measured from the wearer. Smaller values reduce server load.")
            .defineInRange("timeHaloSkillChunkRadius", 3, 1, 8);

    // 时间变慢时实体的速度倍率（0.05-0.95）
    public static final ModConfigSpec.DoubleValue TIME_HALO_SLOW_FACTOR = BUILDER
            .comment("Movement speed multiplier inside the slowed area (0.05-0.95). Lower values mean slower movement.")
            .defineInRange("timeHaloSlowFactor", 0.25D, 0.05D, 0.95D);

    // 万灵之主夺走玩家装备时，这些物品 ID 会被放过（精妙背包模组的背包始终安全）
    public static final ModConfigSpec.ConfigValue<List<? extends String>> HEROBRINE_PLUNDER_PROTECTED = BUILDER
            .comment("Item IDs that Herobrine's Myriad Lord title will never plunder.",
                    "Items from the sophisticatedbackpacks mod are always protected.")
            .defineListAllowEmpty("herobrinePlunderProtected", List.of(), () -> "", Config::validateItemName);

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }
}
