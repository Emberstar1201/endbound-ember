package com.emberstar.endbounember.item;

import java.util.ArrayList;
import java.util.List;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 海洋之星：水下呼吸、洋流自由移动与守卫者中立。
 */
public class OceanStarItem extends Item {
    private static final int COLOR = 0x6FD8E8;

    public OceanStarItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack))
                .withStyle(style -> style.withColor(COLOR));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("item.endbound_ember.ocean_star.condition").withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.ocean_star.breath").withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.ocean_star.current").withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.ocean_star.abyss").withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.ocean_star.coral").withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.ocean_star.guardian").withStyle(style -> style.withColor(COLOR)));
    }

    @EventBusSubscriber(modid = EndboundEmber.MODID)
    public static class Events {
        private static final ResourceLocation SWIM_SPEED_ID =
                ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "ocean_star_swim_speed");
        /** 水中泳速加成：把被水拖慢的泳速补回接近陆地步行速度。 */
        private static final double SWIM_SPEED_BONUS = 2.0D;
        /** 水下挖掘倍率：相对正常陆地挖掘速度。 */
        private static final float UNDERWATER_MINING_BONUS = 1.25F;

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            Player player = event.getEntity();
            if (player.level().isClientSide) return;

            if (!hasOceanStar(player)) {
                clearSwimBonus(player);
                return;
            }

            if (player.hasEffect(MobEffects.DIG_SLOWDOWN)) player.removeEffect(MobEffects.DIG_SLOWDOWN);

            if (!player.isInWater()) {
                clearSwimBonus(player);
                return;
            }

            // 海渊之息：氧气始终维持满值，配合溺水伤害免疫形成双保险。
            player.setAirSupply(player.getMaxAirSupply());

            // 洋流之拥：抹去缓慢与负向移速修饰符，并抵消水流推力。
            if (player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            clearNegativeMoveModifiers(player);
            applySwimBonus(player);

            BlockPos pos = player.blockPosition();
            FluidState fluid = player.level().getFluidState(pos);
            Vec3 flow = fluid.getFlow(player.level(), pos);
            if (flow.lengthSqr() > 0.0D) {
                player.setDeltaMovement(player.getDeltaMovement().subtract(flow.scale(0.014D)));
            }
        }

        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof Player player)
                    || player.level().isClientSide
                    || !hasOceanStar(player)) {
                return;
            }
            if (event.getSource().is(DamageTypes.DROWN)) event.setCanceled(true);
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
            Player player = event.getEntity();
            if (!hasOceanStar(player)) return;

            boolean eyeInWater = player.isEyeInFluid(FluidTags.WATER);
            float speed = event.getOriginalSpeed();

            // 深渊无阻：抵消原版「眼睛浸水」与「悬空」两道 ÷5 惩罚。
            if (eyeInWater) speed /= submergedMiningFactor(player);
            if (!player.onGround()) speed *= 5.0F;

            // 珊瑚之刃：水下挖掘进一步提速。
            if (player.isInWater()) speed *= UNDERWATER_MINING_BONUS;

            event.setNewSpeed(speed);
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void onChangeTarget(LivingChangeTargetEvent event) {
            if (!(event.getEntity() instanceof Guardian guardian)) return;
            if (!(event.getNewAboutToBeSetTarget() instanceof Player player)) return;
            if (!hasOceanStar(player)) return;
            // 潮汐同源：只要不是这名玩家先动手，守卫者就不会主动锁定。
            if (guardian.getLastHurtByMob() == player) return;
            event.setCanceled(true);
        }

        private static float submergedMiningFactor(Player player) {
            double factor = player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
            return factor > 0.0D && factor < 1.0D ? (float) factor : 1.0F;
        }

        private static void applySwimBonus(Player player) {
            AttributeInstance instance = player.getAttribute(NeoForgeMod.SWIM_SPEED);
            if (instance == null) return;
            AttributeModifier existing = instance.getModifier(SWIM_SPEED_ID);
            if (existing != null && existing.amount() == SWIM_SPEED_BONUS) return;
            if (existing != null) instance.removeModifier(SWIM_SPEED_ID);
            instance.addTransientModifier(new AttributeModifier(
                    SWIM_SPEED_ID, SWIM_SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }

        private static void clearSwimBonus(Player player) {
            AttributeInstance instance = player.getAttribute(NeoForgeMod.SWIM_SPEED);
            if (instance != null && instance.getModifier(SWIM_SPEED_ID) != null) {
                instance.removeModifier(SWIM_SPEED_ID);
            }
        }

        private static void clearNegativeMoveModifiers(Player player) {
            AttributeInstance instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (instance == null) return;
            List<ResourceLocation> negative = new ArrayList<>();
            for (AttributeModifier modifier : instance.getModifiers()) {
                if (modifier.amount() < 0.0D) negative.add(modifier.id());
            }
            for (ResourceLocation id : negative) instance.removeModifier(id);
        }

        private static boolean hasOceanStar(Player player) {
            return player.getMainHandItem().is(EndboundEmber.OCEAN_STAR.get())
                    || player.getOffhandItem().is(EndboundEmber.OCEAN_STAR.get())
                    || hasCuriosOceanStar(player);
        }

        private static boolean hasCuriosOceanStar(Player player) {
            if (!net.neoforged.fml.ModList.get().isLoaded("curios")) return false;
            return com.emberstar.endbounember.compat.CuriosCompat.isEquipped(
                    player, EndboundEmber.OCEAN_STAR.get());
        }
    }
}
