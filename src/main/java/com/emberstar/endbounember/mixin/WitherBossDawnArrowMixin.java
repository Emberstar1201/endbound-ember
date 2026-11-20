package com.emberstar.endbounember.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.emberstar.endbounember.entity.DawnArrowEntity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;

/**
 * 让黎明之箭无视凋零「开盾」后的弹射物免疫。
 *
 * <p>原版 {@code WitherBoss.hurt} 在凋零半血、{@code isPowered()} 为真时，
 * 只要攻击的直接来源是 {@code AbstractArrow} 或风弹就直接返回 false，
 * 导致黎明圣裁射出的箭完全打不出伤害。这里只在该判定读取直接来源时，
 * 把黎明之箭替换成它的发射者（没有发射者时退化为凋零自身），
 * 使判定放过黎明之箭；其他弹射物仍被原版挡下，后续结算保持原版流程。</p>
 */
@Mixin(WitherBoss.class)
public abstract class WitherBossDawnArrowMixin {
    @Redirect(
            method = "hurt",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/damagesource/DamageSource;getDirectEntity()Lnet/minecraft/world/entity/Entity;"),
            require = 0)
    private Entity endboundEmber$dawnArrowBypassesWitherArmor(DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (direct instanceof DawnArrowEntity) {
            Entity owner = source.getEntity();
            return owner != null ? owner : (Entity) (Object) this;
        }
        return direct;
    }
}
