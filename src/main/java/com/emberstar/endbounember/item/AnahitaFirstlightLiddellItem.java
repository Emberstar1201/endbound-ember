package com.emberstar.endbounember.item;

import java.util.List;
import java.util.Set;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 阿娜希塔·晨曦·莉德尔：晨曦护符的上位护符，强度介于晨曦护符与星尘光环之间。
 *
 * <p>保留晨曦护符的黑暗 / 失明 / 凋零免疫，并提高对亡灵与 BOSS 的伤害；
 * 额外提供 60% 伤害减免、随身照明，以及水下 / 悬空挖掘惩罚与减速惩罚免疫。</p>
 */
public class AnahitaFirstlightLiddellItem extends Item {
    private static final int COLOR = 0xFFE1A8;

    /** 对亡灵生物的伤害倍率（×5）。 */
    public static final double UNDEAD_MULTIPLIER = 5.0D;
    /** 对 BOSS 生物的伤害倍率（×6）。 */
    public static final double BOSS_MULTIPLIER = 6.0D;

    /** 受到伤害的减免比例（60%），低于星尘光环的 99%。 */
    public static final float DAMAGE_REDUCTION = 0.6F;

    /** 护符免疫的负面效果：黑暗、失明与凋零。 */
    private static final Set<Holder<MobEffect>> IMMUNE_EFFECTS =
            Set.of(MobEffects.DARKNESS, MobEffects.BLINDNESS, MobEffects.WITHER);

    public AnahitaFirstlightLiddellItem(Properties properties) {
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
        tooltip.add(Component.translatable("item.endbound_ember.anahita_firstlight_liddell.condition")
                .withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.anahita_firstlight_liddell.immunity")
                .withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.anahita_firstlight_liddell.undead")
                .withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.anahita_firstlight_liddell.boss")
                .withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.anahita_firstlight_liddell.reduction")
                .withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.anahita_firstlight_liddell.utility")
                .withStyle(style -> style.withColor(COLOR)));
    }

    /** 是否佩戴护符：主手、副手或 Curios 饰品栏。 */
    public static boolean isEquipped(Player player) {
        if (player.getMainHandItem().is(EndboundEmber.ANAHITA_FIRSTLIGHT_LIDDELL.get())
                || player.getOffhandItem().is(EndboundEmber.ANAHITA_FIRSTLIGHT_LIDDELL.get())) {
            return true;
        }
        if (!ModList.get().isLoaded("curios")) return false;
        return com.emberstar.endbounember.compat.CuriosCompat.isEquipped(
                player, EndboundEmber.ANAHITA_FIRSTLIGHT_LIDDELL.get());
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

        /**
         * 60% 伤害减免。
         * <p>与星尘光环同戴时取最高减免（99%），与时光光环同戴时以 60% 为准；
         * 放在最低优先级，确保在其他减伤逻辑结算后按原始伤害重算。</p>
         */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!isEquipped(player)) return;
            if (StardustWitchHaloEvents.wearingStardustHalo(player)) return;
            event.setNewDamage(event.getOriginalDamage() * (1.0F - DAMAGE_REDUCTION));
        }
    }
}
