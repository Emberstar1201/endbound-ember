package com.emberstar.endbounember.item;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * 永恒图腾：击败万灵之主的战利品，30 耐久。
 * <p>玩家手持（主手或副手）受到致死伤害时，取消这次死亡、立刻回满生命，
 * 并给予 40 秒的伤害吸收 IV、抗性提升 III、防火 I、生命回复 V；
 * 每次触发消耗 1 点耐久，耗尽即损毁。虚空与 /kill 等伤害不可抵挡。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class EternalTotemEvents {

    /** 全部增益的统一时长：40 秒 */
    private static final int EFFECT_DURATION = 40 * 20;

    private EternalTotemEvents() {
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        // 虚空与 /kill 一路的伤害不可抵挡，与原版图腾一致
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.is(EndboundEmber.ETERNAL_TOTEM.get())) {
                continue;
            }
            // 取消死亡、回满生命，再挂上四重防护增益
            event.setCanceled(true);
            player.setHealth(player.getMaxHealth());
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, EFFECT_DURATION, 3));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, EFFECT_DURATION, 2));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, EFFECT_DURATION, 0));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, EFFECT_DURATION, 4));
            // 消耗 1 点耐久，耗尽时由 hurtAndBreak 播放碎裂音效并移除物品
            stack.hurtAndBreak(1, player,
                    hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            // 复用原版 35 号实体事件：客户端播放图腾音效与灵魂粒子
            player.level().broadcastEntityEvent(player, (byte) 35);
            return;
        }
    }
}
