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

    /** C 键放大：按住放大键时缩小视野，键位可在按键绑定里修改。 */
    public static final ModConfigSpec.BooleanValue ZOOM_ENABLED = BUILDER
            .comment("放大：按住放大键（默认 C，可在按键绑定里修改）时拉近视野。")
            .define("zoomEnabled", true);

    /** 玩家移动机制总开关：关闭后下面三项全部不生效。 */
    public static final ModConfigSpec.BooleanValue MOVEMENT_TWEAKS_ENABLED = BUILDER
            .comment("玩家移动机制总开关，关闭后惯性滑行、落地连跳与空中控制增强全部失效。")
            .define("movementTweaksEnabled", true);

    /** 惯性滑行：松开方向键或落地后保留水平速度，缓慢减速。 */
    public static final ModConfigSpec.BooleanValue MOVEMENT_MOMENTUM_GLIDE = BUILDER
            .comment("惯性滑行：疾跑中松开方向键或跳跃落地后，保留水平速度继续滑行并缓慢减速。")
            .define("movementMomentumGlide", true);

    /** 落地连跳：即将落地时按下跳跃键立刻再次起跳，保留水平速度。 */
    public static final ModConfigSpec.BooleanValue MOVEMENT_BUNNY_HOP = BUILDER
            .comment("落地连跳：滞空下落且即将接触地面时按下跳跃键，可立刻再次起跳并保留大部分水平速度。")
            .define("movementBunnyHop", true);

    /** 空中控制增强：空中方向输入转向更顺滑。 */
    public static final ModConfigSpec.BooleanValue MOVEMENT_AIR_CONTROL = BUILDER
            .comment("空中控制增强：滞空时按方向键可以更顺滑地改变移动方向。")
            .define("movementAirControl", true);

    /** 移动光源：手持会发光的方块时脚下自动亮起一圈光。 */
    public static final ModConfigSpec.BooleanValue MOVING_LIGHT_ENABLED = BUILDER
            .comment("移动光源：手持火把、荧石等会发光的方块时，脚下会自动亮起一圈光，走开后熄灭。")
            .define("movingLightEnabled", true);

    /** 剑右键格挡：手持剑按住右键进入格挡，正面伤害减半。 */
    public static final ModConfigSpec.BooleanValue SWORD_BLOCKING_ENABLED = BUILDER
            .comment("剑右键格挡：手持剑按住右键进入格挡姿态，正面受到的伤害减半。")
            .define("swordBlockingEnabled", true);

    /** 更好的村民交易总开关。 */
    public static final ModConfigSpec.BooleanValue BETTER_VILLAGER_TRADING = BUILDER
            .comment("启用劫掠与僵尸围城胜利后的永久村庄折扣、稀有愚者村民（每 1~2 游戏日自动补货）、加快补货与职业方块丢失回退机制。")
            .define("betterVillagerTrading", true);

    /** 更好的铁砧机制总开关。 */
    public static final ModConfigSpec.BooleanValue ANVIL_MECHANICS_ENABLED = BUILDER
            .comment("启用免费改名、移除铁砧过于昂贵限制并大幅降低铁砧损耗概率。")
            .define("anvilMechanicsEnabled", true);

    /** 砂轮去魔附魔书总开关。 */
    public static final ModConfigSpec.BooleanValue GRINDSTONE_ENCHANTED_BOOK_ENABLED = BUILDER
            .comment("单件附魔物品使用砂轮去魔时，额外生成一本包含原有完整附魔的附魔书。")
            .define("grindstoneEnchantedBookEnabled", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }
}
