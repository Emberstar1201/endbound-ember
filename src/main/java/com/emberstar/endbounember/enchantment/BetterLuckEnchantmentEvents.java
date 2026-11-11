package com.emberstar.endbounember.enchantment;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.enchanting.EnchantmentLevelSetEvent;

/**
 * 按书架数量逐步提高三个附魔档位的菜单费用及候选生成等级。
 *
 * <p>事件值同时用于菜单等级门槛和候选生成，不直接写入附魔，
 * 也不改变点击时按档位扣除的等级数。候选仍经过附魔池、兼容性及数量过滤。</p>
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class BetterLuckEnchantmentEvents {
    /*
     * 每个档位允许达到的最高候选等级：
     * 分别对应原版 15 书架时三档的理论上限，第三档保持 30 级，
     * 避免提高满级附魔台的等级消耗，也避免出现原版不存在的附魔等级。
     */
    private static final int[] MAX_LEVEL_BY_ROW = {13, 25, 30};

    private BetterLuckEnchantmentEvents() {
    }

    @SubscribeEvent
    public static void onEnchantmentLevelSet(EnchantmentLevelSetEvent event) {
        // 配置关闭时完全不触碰事件值，行为与原版保持一致。
        if (!Config.BETTER_LUCK_ENCHANTMENT.get()) return;

        int row = event.getEnchantRow();
        if (row < 0 || row >= MAX_LEVEL_BY_ROW.length) return;

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
        event.setEnchantLevel(Math.max(level, Math.min(MAX_LEVEL_BY_ROW[row], level + bonus)));
    }
}
