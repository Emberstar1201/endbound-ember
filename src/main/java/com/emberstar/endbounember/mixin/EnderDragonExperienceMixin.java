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
 * 死亡动画结束时再统一生成经验球，避免原版 {@code ExperienceOrb.award}
 * 把大批经验拆成数十个小球带来额外实体开销。</p>
 *
 * <p>单颗经验球的经验值上限为 {@link #MAX_ORB_VALUE}：原版用 short 存读与同步
 * {@code Value}，超过 32767 会被截断（存档后变负值、客户端显示为最小球），
 * 因此结算时按上限拆成少数几颗。末影龙又恰好死在返回传送门上方，
 * 经验球会顺着传送门被送去主世界，生成时需给足传送冷却。</p>
 */
@Mixin(EnderDragon.class)
public abstract class EnderDragonExperienceMixin {
    private static final String TAG_PENDING = "EndboundEmberDragonExperience";
    /** 单颗经验球的最大经验值，留在 short 范围内。 */
    private static final int MAX_ORB_VALUE = 30000;
    /** 传送冷却设为最大值，经验球存活期间都不会被返回传送门传走。 */
    private static final int PORTAL_COOLDOWN = Integer.MAX_VALUE;

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
    private void endboundEmber$spawnExperienceOrbs(CallbackInfo ci) {
        EnderDragon self = (EnderDragon) (Object) this;
        CompoundTag data = self.getPersistentData();
        int pending = data.getInt(TAG_PENDING);
        // 死亡动画结束的那一 tick 才结算，之前只累积
        if (pending <= 0 || self.dragonDeathTime < 200 || !(self.level() instanceof ServerLevel level)) return;

        data.putInt(TAG_PENDING, 0);
        while (pending > 0) {
            int amount = Math.min(pending, MAX_ORB_VALUE);
            pending -= amount;
            ExperienceOrb orb = new ExperienceOrb(level, self.getX(), self.getY(), self.getZ(), amount);
            // 末影龙死在返回传送门上方，不设冷却的话经验球会被传送门送去主世界
            orb.setPortalCooldown(PORTAL_COOLDOWN);
            level.addFreshEntity(orb);
        }
    }
}
