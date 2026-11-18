package com.emberstar.endbounember.item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 星辉之星：身处亮度不高于 14 的环境时获得星光庇护，并把击杀经验翻倍。
 */
public class StarlightStarItem extends Item {
    private static final int COLOR = 0xFFE59A;

    public StarlightStarItem(Properties properties) {
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
        tooltip.add(Component.translatable("item.endbound_ember.starlight_star.condition").withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.starlight_star.night_step").withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.starlight_star.night_eye").withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.starlight_star.echo").withStyle(style -> style.withColor(COLOR)));
    }

    @EventBusSubscriber(modid = EndboundEmber.MODID)
    public static class Events {
        private static final ResourceLocation NIGHT_SPEED_ID =
                ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "starlight_star_night_speed");
        /** 生效所需的最高光照等级（含）。 */
        private static final int LIGHT_THRESHOLD = 14;
        private static final int TAIL_EFFECT_TICKS = 8 * 60 * 20;
        /** 低光移速加成：最终移速 ×1.2。 */
        private static final double NIGHT_SPEED_BONUS = 0.20D;
        private static final int NIGHT_VISION_TICKS = 300;
        private static final int NIGHT_VISION_REFRESH = 220;
        private static final Map<UUID, Integer> TAIL_EFFECTS = new HashMap<>();

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            Player player = event.getEntity();
            if (player.level().isClientSide) return;

            UUID playerId = player.getUUID();
            boolean active = isDark(player) && hasStarlightStar(player);
            if (active) {
                TAIL_EFFECTS.remove(playerId);
                updateNightSpeed(player, true);
                refreshNightVision(player);
                return;
            }

            Integer remaining = TAIL_EFFECTS.get(playerId);
            if (remaining == null) {
                if (!hasNightSpeed(player)) return;
                remaining = TAIL_EFFECT_TICKS;
                TAIL_EFFECTS.put(playerId, remaining);
                player.addEffect(new MobEffectInstance(
                        MobEffects.NIGHT_VISION, TAIL_EFFECT_TICKS, 0, false, false, true));
            }
            if (remaining <= 0) {
                TAIL_EFFECTS.remove(playerId);
                updateNightSpeed(player, false);
                return;
            }

            TAIL_EFFECTS.put(playerId, remaining - 1);
            updateNightSpeed(player, true);
        }

        @SubscribeEvent
        public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            TAIL_EFFECTS.remove(event.getEntity().getUUID());
        }

        @SubscribeEvent
        public static void onExperienceDrop(LivingExperienceDropEvent event) {
            Player killer = event.getAttackingPlayer();
            if (killer == null || killer.level().isClientSide) return;
            if (!hasStarlightStar(killer)) return;
            int original = event.getDroppedExperience();
            if (original <= 0) return;
            event.setDroppedExperience(original * 2);
        }

        private static boolean hasNightSpeed(Player player) {
            AttributeInstance instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
            return instance != null && instance.getModifier(NIGHT_SPEED_ID) != null;
        }

        private static void updateNightSpeed(Player player, boolean active) {
            AttributeInstance instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (instance == null) return;
            AttributeModifier existing = instance.getModifier(NIGHT_SPEED_ID);
            if (active) {
                if (existing == null) {
                    instance.addTransientModifier(new AttributeModifier(
                            NIGHT_SPEED_ID, NIGHT_SPEED_BONUS,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
            } else if (existing != null) {
                instance.removeModifier(NIGHT_SPEED_ID);
            }
        }

        private static void refreshNightVision(Player player) {
            MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
            if (current == null || current.getDuration() <= NIGHT_VISION_REFRESH) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0, false, false, true));
            }
        }

        /** 以玩家所在位置的最大本地原始亮度判定，亮度不高于阈值即生效。 */
        private static boolean isDark(Player player) {
            return player.level().getMaxLocalRawBrightness(player.blockPosition()) <= LIGHT_THRESHOLD;
        }

        private static boolean hasStarlightStar(Player player) {
            return player.getMainHandItem().is(EndboundEmber.STARLIGHT_STAR.get())
                    || player.getOffhandItem().is(EndboundEmber.STARLIGHT_STAR.get())
                    || hasCuriosStarlightStar(player);
        }

        private static boolean hasCuriosStarlightStar(Player player) {
            if (!net.neoforged.fml.ModList.get().isLoaded("curios")) return false;
            return com.emberstar.endbounember.compat.CuriosCompat.isEquipped(
                    player, EndboundEmber.STARLIGHT_STAR.get());
        }
    }
}
