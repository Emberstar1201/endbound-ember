package com.emberstar.endbounember.mixin;

import com.emberstar.endbounember.entity.Herobrine;

import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 原版在和平难度禁止怪物攻击玩家（{@code Mob#canAttackType} 对玩家类型直接返回 false），
 * BOSS 战召唤的亡灵精锐需要在和平难度下照常作战。
 */
@Mixin(Mob.class)
public abstract class MobPeacefulAttackMixin {
    @Inject(method = "canAttackType(Lnet/minecraft/world/entity/EntityType;)Z",
            at = @At("HEAD"), cancellable = true)
    private void endboundEmber$minionCanAttackInPeaceful(EntityType<?> type,
            CallbackInfoReturnable<Boolean> cir) {
        Mob self = (Mob) (Object) this;
        if (type == EntityType.PLAYER && self.level().getDifficulty() == Difficulty.PEACEFUL
                && self.getTags().contains(Herobrine.BOSS_MINION_TAG)) {
            cir.setReturnValue(true);
        }
    }
}
