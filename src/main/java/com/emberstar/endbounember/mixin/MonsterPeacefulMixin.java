package com.emberstar.endbounember.mixin;

import com.emberstar.endbounember.entity.Herobrine;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 BOSS 战期间召唤的亡灵精锐在和平难度下依然保留，
 * 否则原版会在每个 tick 的 checkDespawn 中把它们直接移除。
 */
@Mixin(Monster.class)
public abstract class MonsterPeacefulMixin {
    @Inject(method = "shouldDespawnInPeaceful", at = @At("HEAD"), cancellable = true)
    private void endboundEmber$keepBossMinions(CallbackInfoReturnable<Boolean> cir) {
        Mob self = (Mob) (Object) this;
        if (self.getTags().contains(Herobrine.BOSS_MINION_TAG)) {
            cir.setReturnValue(false);
        }
    }
}
