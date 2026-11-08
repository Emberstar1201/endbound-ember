package com.emberstar.endbounember.enchantment;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.enchanting.EnchantmentLevelSetEvent;

/**
 * 按书架数量逐步改善三个附魔档位的候选等级。
 *
 * <p>这里只抬高原版用于筛选候选附魔的等级，不直接写入任何附魔，
 * 因此附魔池、兼容性、单附魔最大等级仍完全由原版控制；
 * 等级提高后，原版自带的“多附魔”判定也会自然多给附魔数量。</p>
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class BetterLuckEnchantmentEvents {
    /*
     * 每个档位允许达到的最高候选等级：
     * 分别对应原版 15 书架时三档的理论上限，第三档保持 30 级，
     * 避免提高满级附魔台的等级消耗，也避免出现原版不存在的附魔等级。
     */
    private static final int[] MAX_LEVEL_BY_ROW = {0, 13, 25, 30};

    private BetterLuckEnchantmentEvents() {
    }

    @SubscribeEvent
    public static void onEnchantmentLevelSet(EnchantmentLevelSetEvent event) {
        // 配置关闭时完全不触碰事件值，行为与原版保持一致。
        if (!Config.BETTER_LUCK_ENCHANTMENT.get()) return;

        int row = event.getEnchantRow();
        if (row < 1 || row >= MAX_LEVEL_BY_ROW.length) return;

        int level = event.getEnchantLevel();
        // 费用为 0 的档位原本不可用，不做解锁，避免凭空多出一个附魔选项。
        if (level <= 0) return;

        int power = event.getPower();

        // 书架越多，触发概率越高（裸附魔台保留一次小概率机会）；提升幅度同样随书架缓慢增长。
        int chancePercent = power <= 0 ? 10 : Math.min(85, 20 + power * 4);
        int bonus = 2 + power / 4;

        RandomSource random = event.getLevel().getRandom();
        if (random.nextInt(100) >= chancePercent) return;

        // 只做小幅提升并受档位上限约束，不会出现经验修补泛滥或保护 V 这类原版之外的结果。
        event.setEnchantLevel(Math.min(MAX_LEVEL_BY_ROW[row], level + bonus));
    }
}
