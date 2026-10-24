package com.emberstar.endbounember.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 末影龙经验掉落：首次击杀 120000 点，重生后每次 60000 点。
 *
 * <p>原版在 {@code tickDeath} 中用局部变量 {@code i} 表示总经验（首次 12000，重生 500），
 * 随后按 10 次 8% 与 1 次 20% 分批发放，合计正好等于 {@code i}。
 * 这里替换这两个常量，并把经验球合并：分批经验先累积到龙的持久数据里，
 * 死亡动画结束时只生成一颗大经验球，避免原版 {@code ExperienceOrb.award}
 * 把大批经验拆成数十个小球带来额外实体开销。</p>
 */
@Mixin(EnderDragon.class)
public abstract class EnderDragonExperienceMixin {
    private static final String TAG_PENDING = "EndboundEmberDragonExperience";

    @ModifyConstant(method = "tickDeath", constant = @Constant(intValue = 12000))
    private int endboundEmber$firstKillExperience(int original) {
        return 120000;
    }

    @ModifyConstant(method = "tickDeath", constant = @Constant(intValue = 500))
    private int endboundEmber$rebirthExperience(int original) {
        return 60000;
    }

    @Redirect(
            method = "tickDeath",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"))
    private static void endboundEmber$gatherExperience(ServerLevel level, Vec3 pos, int amount) {
        if (amount <= 0) return;

        AABB area = new AABB(pos, pos).inflate(16.0D);
        EnderDragon dragon = level.getEntitiesOfClass(EnderDragon.class, area, EnderDragon::isDeadOrDying)
                .stream()
                .findFirst()
                .orElse(null);
        if (dragon == null) {
            ExperienceOrb.award(level, pos, amount);
            return;
        }

        CompoundTag data = dragon.getPersistentData();
        data.putInt(TAG_PENDING, data.getInt(TAG_PENDING) + amount);
    }

    @Inject(method = "tickDeath", at = @At("TAIL"))
    private void endboundEmber$spawnSingleOrb(CallbackInfo ci) {
        EnderDragon self = (EnderDragon) (Object) this;
        CompoundTag data = self.getPersistentData();
        int pending = data.getInt(TAG_PENDING);
        // 死亡动画结束的那一 tick 才结算，之前只累积
        if (pending <= 0 || self.dragonDeathTime < 200 || !(self.level() instanceof ServerLevel level)) return;

        data.putInt(TAG_PENDING, 0);
        level.addFreshEntity(new ExperienceOrb(level, self.getX(), self.getY(), self.getZ(), pending));
    }
}
