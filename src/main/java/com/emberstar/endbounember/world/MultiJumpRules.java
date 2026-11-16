package com.emberstar.endbounember.world;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * 多段跳的公共判定规则。
 * <p>
 * 客户端预测与服务端校验共用同一套规则，避免两边各自实现后出现
 * “客户端能跳、服务端拒绝”或起跳力度不一致的问题。
 */
public final class MultiJumpRules {
    /** 原版起跳初速度，跳跃提升每级在此基础上增加 0.1 */
    private static final double BASE_JUMP_POWER = 0.42D;
    private static final double JUMP_BOOST_PER_LEVEL = 0.1D;

    private MultiJumpRules() {
    }

    /** 空中二段跳的适用条件：活着、非创造飞行、非鞘翅、非骑乘、不在液体与可攀爬方块上 */
    public static boolean isEligible(Player player) {
        return player.isAlive() && !player.getAbilities().flying && !player.isFallFlying()
                && !player.isPassenger() && !player.isInWaterOrBubble() && !player.isInLava()
                && !player.onClimbable();
    }

    /** 起跳初速度，与原版跳跃提升效果保持一致 */
    public static double jumpPower(Player player) {
        double power = BASE_JUMP_POWER;
        MobEffectInstance boost = player.getEffect(MobEffects.JUMP);
        if (boost != null) {
            power += JUMP_BOOST_PER_LEVEL * (boost.getAmplifier() + 1);
        }
        return power;
    }
}
