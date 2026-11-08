package com.emberstar.endbounember.item;

import java.util.List;
import java.util.Set;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 晨曦护符。
 *
 * <p>佩戴后免疫凋灵、末影龙与远古城市监守者施加的黑暗与凋零效果，
 * 攻击亡灵与 BOSS 时伤害提升。</p>
 */
public class DawnAmuletItem extends Item {
    private static final int COLOR = 0xFFD98A;

    /** 对亡灵生物的攻击伤害加成（+200%）。 */
    public static final double UNDEAD_BONUS = 3.0D;
    /** 对 BOSS 生物的攻击伤害加成（+250%）。 */
    public static final double BOSS_BONUS = 3.5D;

    /** 护符免疫的负面效果：黑暗（凋灵 / 末影龙 / 监守者）与凋零。 */
    private static final Set<Holder<MobEffect>> IMMUNE_EFFECTS =
            Set.of(MobEffects.DARKNESS, MobEffects.BLINDNESS, MobEffects.WITHER);

    /** 判定为 BOSS 的生物（原版 BOSS 全家桶）。 */
    private static final Set<EntityType<?>> BOSSES = Set.of(EntityType.WITHER, EntityType.ENDER_DRAGON,
            EntityType.ELDER_GUARDIAN, EntityType.WARDEN);

    public DawnAmuletItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack))
                .withStyle(style -> style.withColor(COLOR));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("item.endbound_ember.dawn_amulet.condition")
                .withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.dawn_amulet.immunity")
                .withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.dawn_amulet.undead")
                .withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.dawn_amulet.boss")
                .withStyle(style -> style.withColor(COLOR)));
    }

    /** 是否佩戴护符：主手、副手或 Curios 饰品栏。 */
    public static boolean isEquipped(Player player) {
        if (player.getMainHandItem().is(EndboundEmber.DAWN_AMULET.get())
                || player.getOffhandItem().is(EndboundEmber.DAWN_AMULET.get())) {
            return true;
        }
        if (!ModList.get().isLoaded("curios")) return false;
        return com.emberstar.endbounember.compat.CuriosCompat.isEquipped(
                player, EndboundEmber.DAWN_AMULET.get());
    }

    /** 是否为亡灵生物。 */
    public static boolean isUndead(LivingEntity entity) {
        return entity.getType().is(EntityTypeTags.UNDEAD);
    }

    /** 是否为 BOSS 生物。 */
    public static boolean isBoss(LivingEntity entity) {
        return BOSSES.contains(entity.getType());
    }

    @EventBusSubscriber(modid = EndboundEmber.MODID)
    public static final class Events {
        /** 佩戴时清除并持续压制黑暗 / 失明 / 凋零效果。 */
        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!isEquipped(player)) return;

            for (Holder<MobEffect> effect : IMMUNE_EFFECTS) {
                player.removeEffect(effect);
            }
        }

        /** 攻击亡灵 / BOSS 时提升伤害；与上位护符同时佩戴时取最高倍率，不叠加。 */
        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (event.getEntity().level().isClientSide) return;
            if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
            if (event.getSource().getDirectEntity() != player) return;

            boolean dawn = isEquipped(player);
            boolean anahita = AnahitaFirstlightLiddellItem.isEquipped(player);
            if (!dawn && !anahita) return;

            LivingEntity target = event.getEntity();
            double multiplier = 1.0D;
            if (isBoss(target)) {
                multiplier = Math.max(dawn ? BOSS_BONUS : 1.0D,
                        anahita ? AnahitaFirstlightLiddellItem.BOSS_MULTIPLIER : 1.0D);
            } else if (isUndead(target)) {
                multiplier = Math.max(dawn ? UNDEAD_BONUS : 1.0D,
                        anahita ? AnahitaFirstlightLiddellItem.UNDEAD_MULTIPLIER : 1.0D);
            }
            if (multiplier <= 1.0D) return;
            event.setAmount((float) (event.getAmount() * multiplier));
        }
    }
}
