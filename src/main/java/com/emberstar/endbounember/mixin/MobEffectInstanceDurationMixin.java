package com.emberstar.endbounember.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.emberstar.endbounember.world.TimeTurbulenceLevels;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * 时光光环的时效加成：
 * <ul>
 * <li>正面效果流逝速度降低 50%（每 2 tick 补回 1 tick 时长）</li>
 * <li>负面效果流逝速度加快 120%（每 tick 多扣 1 tick，每 5 tick 再多扣 1 tick）</li>
 * </ul>
 * 只对佩戴光环的玩家生效，无限时长的效果不参与结算。
 */
@Mixin(MobEffectInstance.class)
public abstract class MobEffectInstanceDurationMixin {

    @Shadow
    private int duration;

    @Shadow
    public abstract boolean isInfiniteDuration();

    @Shadow
    public abstract Holder<MobEffect> getEffect();

    @Inject(method = "tick", at = @At("HEAD"))
    private void endbound$adjustDuration(LivingEntity entity, Runnable onExpirationRunnable,
            CallbackInfoReturnable<Boolean> cir) {
        if (this.duration <= 0 || this.isInfiniteDuration()) return;
        if (!(entity instanceof ServerPlayer player) || !TimeTurbulenceLevels.wearsTimeHalo(player)) return;
        MobEffectCategory category = this.getEffect().value().getCategory();
        if (category == MobEffectCategory.BENEFICIAL) {
            if (player.tickCount % 2 == 0) {
                this.duration += 1;
            }
        } else if (category == MobEffectCategory.HARMFUL) {
            this.duration = Math.max(1, this.duration - (player.tickCount % 5 == 0 ? 2 : 1));
        }
    }
}
