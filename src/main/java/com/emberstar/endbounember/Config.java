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

    // 附魔台候选等级增强开关；关闭时不修改任何原版附魔候选，也不再显示三档预览结果。
    public static final ModConfigSpec.BooleanValue BETTER_LUCK_ENCHANTMENT = BUILDER
            .comment("Improve all three enchanting table candidates more often as more bookshelves are placed.",
                    "Bare tables keep a small chance, and every row stays within its vanilla maximum level.",
                    "When enabled, the enchanting table also shows the exact result of every row.")
            .define("betterLuckEnchantment", true);

    /** 附魔台单件装备允许持有的最多不同附魔条目数 */
    public static final ModConfigSpec.IntValue ENCHANTING_TABLE_MAX_ENCHANTMENTS = BUILDER
            .comment("Maximum number of distinct enchantments an item can have after enchanting at a table (1-255).")
            .defineInRange("enchantingTableMaxEnchantments", 8, 1, 255);

    // 万灵之主夺走玩家装备时，这些物品 ID 会被放过（精妙背包模组的背包始终安全）
    public static final ModConfigSpec.ConfigValue<List<? extends String>> HEROBRINE_PLUNDER_PROTECTED = BUILDER
            .comment("Item IDs that Herobrine's Myriad Lord title will never plunder.",
                    "Items from the sophisticatedbackpacks mod are always protected.")
            .defineListAllowEmpty("herobrinePlunderProtected", List.of(), () -> "", Config::validateItemName);

    /** Him Boss 战背景音乐总开关 */
    public static final ModConfigSpec.BooleanValue HEROBRINE_BOSS_MUSIC_ENABLED = BUILDER
            .comment("Play the boss battle music while players stay inside the music radius.")
            .define("herobrineBossMusicEnabled", true);

    /** 保持背景音乐生效的半径（格）；玩家逃离该范围就停止播放 */
    public static final ModConfigSpec.DoubleValue HEROBRINE_BOSS_MUSIC_RANGE = BUILDER
            .comment("Radius in blocks that keeps the boss music playing (8-256).",
                    "Leaving this radius stops the music, and coming back restarts it.")
            .defineInRange("herobrineBossMusicRange", 64.0D, 8.0D, 256.0D);

    /** 背景音乐音量 */
    public static final ModConfigSpec.DoubleValue HEROBRINE_BOSS_MUSIC_VOLUME = BUILDER
            .comment("Volume of the boss music (0.0-1.0).")
            .defineInRange("herobrineBossMusicVolume", 1.0D, 0.0D, 1.0D);

    /**
     * 兜底循环时长（秒）。
     * <p>正常情况下会自动解析导入的 OGG 时长来循环；只有解析失败（文件缺失或格式不对）时才使用该值。</p>
     */
    public static final ModConfigSpec.IntValue HEROBRINE_BOSS_MUSIC_LOOP_SECONDS = BUILDER
            .comment("Fallback loop length in seconds (10-3600).",
                    "The track duration is normally read from the OGG file itself.",
                    "This value is only used when the file is missing or cannot be parsed.")
            .defineInRange("herobrineBossMusicLoopSeconds", 180, 10, 3600);

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }
}
