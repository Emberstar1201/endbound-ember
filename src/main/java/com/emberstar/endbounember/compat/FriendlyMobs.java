package com.emberstar.endbounember.compat;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.entity.ZombieGirl;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 范围伤害的友方保护判定。
 * 玩家、友好生物标签内的生物（含本模组三少女与车万女仆的野生 / 驯服个体）、
 * 以及任何已认主的生物都不会被玩家的范围伤害命中。
 */
public final class FriendlyMobs {
    private FriendlyMobs() {
    }

    /** 目标是否应免受玩家范围伤害。 */
    public static boolean isProtected(LivingEntity target) {
        if (target instanceof Player) return true;
        // 友好生物标签内含三少女与车万女仆，未驯服的个体同样受保护
        if (target.getType().is(EndboundEmber.FRIENDLY_MOB_TAG)) return true;
        // 三少女：丧尸娘、溺尸娘与人类少女
        if (target instanceof ZombieGirl) return true;
        // 已驯服的宠物与坐骑（狼、猫、马、驴、羊驼等）
        return target instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null;
    }
}
