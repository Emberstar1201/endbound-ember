package com.emberstar.endbounember.enchantment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 云来剑法 / 古·云来剑法 的共用事件处理。
 * 手持时维护攻速与攻击距离的属性修饰符，并在近战命中时放大伤害、按概率斩出剑气。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class YunlaiSwordmanshipHandler {

    private static final ResourceLocation YUNLAI_SPEED_ID = ResourceLocation.fromNamespaceAndPath(
            EndboundEmber.MODID, "yunlai_speed");
    private static final ResourceLocation YUNLAI_REACH_ID = ResourceLocation.fromNamespaceAndPath(
            EndboundEmber.MODID, "yunlai_reach");
    private static final ResourceLocation ANCIENT_YUNLAI_SPEED_ID = ResourceLocation.fromNamespaceAndPath(
            EndboundEmber.MODID, "ancient_yunlai_speed");
    private static final ResourceLocation ANCIENT_YUNLAI_REACH_ID = ResourceLocation.fromNamespaceAndPath(
            EndboundEmber.MODID, "ancient_yunlai_reach");

    /** 剑气沿视线取样步长（格）。 */
    private static final double SWORD_QI_STEP = 1.0D;
    /** 剑气判定盒膨胀半径。 */
    private static final double SWORD_QI_HITBOX_INFLATE = 0.5D;

    private YunlaiSwordmanshipHandler() {
    }

    /** 每个 tick 根据主手武器维护攻速/攻击距离修饰符。 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        ItemStack mainHand = player.getMainHandItem();
        boolean hasYunlai = YunlaiSwordmanshipEnchantment.levelOf(mainHand) > 0;
        boolean hasAncient = AncientYunlaiSwordmanshipEnchantment.levelOf(mainHand) > 0;

        manageModifier(player, Attributes.ATTACK_SPEED, YUNLAI_SPEED_ID,
                YunlaiSwordmanshipEnchantment.ATTACK_SPEED_BONUS, hasYunlai);
        manageModifier(player, Attributes.ENTITY_INTERACTION_RANGE, YUNLAI_REACH_ID,
                YunlaiSwordmanshipEnchantment.ATTACK_REACH_BONUS, hasYunlai);
        manageModifier(player, Attributes.ATTACK_SPEED, ANCIENT_YUNLAI_SPEED_ID,
                AncientYunlaiSwordmanshipEnchantment.ATTACK_SPEED_BONUS, hasAncient);
        manageModifier(player, Attributes.ENTITY_INTERACTION_RANGE, ANCIENT_YUNLAI_REACH_ID,
                AncientYunlaiSwordmanshipEnchantment.ATTACK_REACH_BONUS, hasAncient);
    }

    /** 近战命中：放大伤害并按概率斩出剑气。 */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Player attacker)) return;
        if (attacker.level().isClientSide()) return;
        // 仅近战攻击生效，剑气伤害不会再触发剑气
        if (event.getSource().getDirectEntity() != attacker) return;
        if (event.getEntity() == attacker) return;

        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.isEmpty()) return;

        double multiplier = 1.0D;
        boolean isAncient = false;
        if (YunlaiSwordmanshipEnchantment.levelOf(weapon) > 0) {
            multiplier = YunlaiSwordmanshipEnchantment.DAMAGE_MULTIPLIER;
        } else if (AncientYunlaiSwordmanshipEnchantment.levelOf(weapon) > 0) {
            multiplier = AncientYunlaiSwordmanshipEnchantment.DAMAGE_MULTIPLIER;
            isAncient = true;
        } else {
            return;
        }

        if (multiplier > 1.0D) {
            event.setAmount(event.getAmount() * (float) multiplier);
        }

        if (isAncient) {
            spawnSwordQi(attacker, event.getAmount(),
                    AncientYunlaiSwordmanshipEnchantment.SWORD_QI_CHANCE,
                    AncientYunlaiSwordmanshipEnchantment.SWORD_QI_DAMAGE_MULTIPLIER,
                    AncientYunlaiSwordmanshipEnchantment.SWORD_QI_RANGE, true);
        } else {
            spawnSwordQi(attacker, event.getAmount(),
                    YunlaiSwordmanshipEnchantment.SWORD_QI_CHANCE,
                    YunlaiSwordmanshipEnchantment.SWORD_QI_DAMAGE_MULTIPLIER,
                    YunlaiSwordmanshipEnchantment.SWORD_QI_RANGE, false);
        }
    }

    /**
     * 沿玩家视线方向斩出剑气：直线穿透沿途所有生物，最多命中射程内的目标。
     * 旧版使用 CrescentEntity 实体，这里改用射线判定 + 粒子表现。
     */
    private static void spawnSwordQi(Player attacker, float attackDamage, double chance,
            double damageMultiplier, double range, boolean ancient) {
        if (!(attacker.level() instanceof ServerLevel level)) return;
        if (chance <= 0.0D) return;
        if (attacker.getRandom().nextDouble() > chance) return;

        Vec3 eye = attacker.getEyePosition();
        Vec3 direction = attacker.getLookAngle().normalize();
        Vec3 end = eye.add(direction.scale(range));

        AABB searchBox = new AABB(eye, end).inflate(SWORD_QI_HITBOX_INFLATE);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                entity -> entity != attacker && entity.isAlive());
        candidates.sort(Comparator.comparingDouble(entity -> projectionDistance(eye, direction, entity)));

        float swordQiDamage = attackDamage * (float) damageMultiplier;
        for (LivingEntity target : candidates) {
            double distance = projectionDistance(eye, direction, target);
            if (distance <= 0.0D || distance > range) continue;
            Vec3 closest = eye.add(direction.scale(distance));
            if (target.getBoundingBox().inflate(SWORD_QI_HITBOX_INFLATE).distanceToSqr(closest) > 1.0D) {
                continue;
            }
            target.hurt(level.damageSources().magic(), swordQiDamage);
        }

        drawSwordQi(level, eye, direction, range, ancient);
    }

    /** 目标在视线方向上的投影距离（负值表示在身后）。 */
    private static double projectionDistance(Vec3 origin, Vec3 direction, LivingEntity entity) {
        Vec3 toEntity = entity.position().add(0.0D, entity.getBbHeight() / 2.0D, 0.0D).subtract(origin);
        return toEntity.dot(direction);
    }

    private static void drawSwordQi(ServerLevel level, Vec3 origin, Vec3 direction, double range,
            boolean ancient) {
        for (double travelled = 0.5D; travelled <= range; travelled += SWORD_QI_STEP) {
            Vec3 point = origin.add(direction.scale(travelled));
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, point.x, point.y, point.z, 1,
                    0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(ancient ? ParticleTypes.END_ROD : ParticleTypes.ENCHANT,
                    point.x, point.y, point.z, 2, 0.1D, 0.1D, 0.1D, 0.0D);
        }
    }

    /** 统一维护属性修饰符：需要时添加，不需要时移除。 */
    private static void manageModifier(Player player, Holder<Attribute> attribute, ResourceLocation id,
            double amount, boolean shouldHave) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;

        AttributeModifier existing = instance.getModifier(id);
        if (!shouldHave) {
            if (existing != null) instance.removeModifier(id);
            return;
        }
        if (existing != null && existing.amount() == amount) return;
        if (existing != null) instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, amount,
                AttributeModifier.Operation.ADD_VALUE));
    }
}
