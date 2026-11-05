package com.emberstar.endbounember.util;

import net.minecraft.world.entity.player.Player;

/**
 * 玩家经验的换算工具。
 *
 * <p>{@code Player#totalExperience} 是独立字段，{@code /xp set <player> <n> levels}
 * 等操作只改等级、不改它，会出现「500 级却判定经验不足」「终界之星加伤偏低」这类误判。
 * 这里把「等级 + 进度换算出的总经验」与字段值取较大者，既兼容命令设置等级的情况，
 * 也不会在字段值更大时少算。</p>
 */
public final class ExperienceUtil {
    private ExperienceUtil() {
    }

    /** 玩家当前真正可用的总经验值。 */
    public static int availableExperience(Player player) {
        int fromLevel = experienceFromLevel(player.experienceLevel)
                + Math.round(player.experienceProgress * xpToNextLevel(player.experienceLevel));
        return Math.max(player.totalExperience, fromLevel);
    }

    /**
     * 到达指定等级所需的总经验，与原版经验曲线一致。
     * <ul>
     *   <li>0–16 级：{@code L² + 6L}</li>
     *   <li>17–31 级：{@code 2.5L² - 40.5L + 360}</li>
     *   <li>32 级以上：{@code 4.5L² - 162.5L + 2220}</li>
     * </ul>
     */
    public static int experienceFromLevel(int level) {
        if (level >= 32) {
            return (int) (4.5D * level * level - 162.5D * level + 2220.0D);
        }
        if (level >= 17) {
            return (int) (2.5D * level * level - 40.5D * level + 360.0D);
        }
        return level * level + 6 * level;
    }

    /** 从当前等级升到下一级所需的经验，与原版曲线一致。 */
    public static int xpToNextLevel(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        }
        if (level >= 15) {
            return 37 + (level - 15) * 5;
        }
        return 7 + level * 2;
    }
}
