package com.emberstar.endbounember.entity;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 丧尸娘的通用事件：属性注册与索敌保护。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public class ZombieGirlEvents {

    @SubscribeEvent
    public static void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.ZOMBIE_GIRL.get(), createAttributes());
        event.put(ModEntities.DROWNED_GIRL.get(), createAttributes());
    }

    /**
     * 铁傀儡不是 Enemy，因此不能用 Enemy 类型判断；直接拦截铁傀儡选中两种实体。
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof IronGolem
                && event.getNewAboutToBeSetTarget() instanceof ZombieGirl) {
            event.setCanceled(true);
        }
    }

    /**
     * 目标事件遗漏或铁傀儡已经锁定目标时，额外取消铁傀儡造成的伤害。
     */
    @SubscribeEvent
    public static void onLivingAttack(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ZombieGirl
                && event.getSource().getEntity() instanceof IronGolem) {
            event.setCanceled(true);
        }
    }

    private static net.minecraft.world.entity.ai.attributes.AttributeSupplier createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 7.5D)
                .add(Attributes.MOVEMENT_SPEED, 0.2875D * 1.25D)
                .add(Attributes.FOLLOW_RANGE, 35.0D)
                .build();
    }
}
