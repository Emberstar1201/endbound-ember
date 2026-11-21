package com.emberstar.endbounember.item;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.effect.ModEffects;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** 千位劫取的程序劫掠命中叠层与潜行引爆逻辑。原作：dsh。 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class KiloSnatchAbilities {
    private static final double PROC_CHANCE = 0.44D;
    private static final double DETONATION_RADIUS = 8.0D;
    private static final double DAMAGE_PER_STACK_OR_EFFECT = 0.02D;
    private static final double DAMAGE_CAP = 0.44D;
    private static final int EFFECT_DURATION = 200;

    private KiloSnatchAbilities() {
    }

    private static boolean holdsKilo(Player player) {
        ItemStack stack = player.getMainHandItem();
        return stack.getItem() instanceof KiloSnatchItem;
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof Player player) || !holdsKilo(player)) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || target.level().random.nextDouble() >= PROC_CHANCE) {
            return;
        }

        MobEffectInstance current = target.getEffect(ModEffects.PROGRAM_LOOT);
        int amplifier = current == null ? 0 : Math.min(current.getAmplifier() + 1, 4);
        target.addEffect(new MobEffectInstance(ModEffects.PROGRAM_LOOT, EFFECT_DURATION,
                amplifier, false, true));
    }

    /** 对玩家周围范围内的存活生物结算引爆伤害，并移除其程序劫掠效果。 */
    public static int detonate(Player player) {
        int detonated = 0;
        AABB bounds = player.getBoundingBox().inflate(DETONATION_RADIUS);
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, bounds,
                entity -> entity != player && entity.isAlive())) {
            MobEffectInstance effect = target.getEffect(ModEffects.PROGRAM_LOOT);
            int stacks = effect == null ? 0 : effect.getAmplifier() + 1;
            int otherEffects = target.getActiveEffects().size() - (effect == null ? 0 : 1);
            double fraction = DAMAGE_PER_STACK_OR_EFFECT * stacks
                    + DAMAGE_PER_STACK_OR_EFFECT * Math.max(0, otherEffects);
            fraction = Math.min(fraction, DAMAGE_CAP);
            double damage = fraction * target.getMaxHealth();
            if (damage > 0.5D) {
                target.hurt(player.damageSources().playerAttack(player), (float) damage);
                detonated++;
            }
            if (effect != null) {
                target.removeEffect(ModEffects.PROGRAM_LOOT);
            }
        }
        return detonated;
    }
}
