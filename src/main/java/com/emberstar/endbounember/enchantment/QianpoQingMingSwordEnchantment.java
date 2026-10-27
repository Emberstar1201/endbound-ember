package com.emberstar.endbounember.enchantment;

import java.util.List;
import java.util.Optional;

import com.emberstar.endbounember.EndboundEmber;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 千破·青溟剑：剑类附魔，最高 V 级。
 * 近战命中时在最终伤害上叠加一段无视护甲与护盾的固定额外伤害，
 * 并生成玩家到目标的橙色尾焰轨迹与铁傀儡挥击音效。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class QianpoQingMingSwordEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "qianpo_qing_ming_sword"));

    /** I ~ V 级的额外伤害。 */
    private static final float[] DAMAGE_BY_LEVEL = {12.0F, 24.0F, 36.0F, 48.0F, 60.0F};
    private static final int TOOLTIP_COLOR = 0x8FD6E8;

    private QianpoQingMingSwordEnchantment() {
    }

    /** 读取物品上的青溟剑附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
    public static int levelOf(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        int level = levelOf(stack, DataComponents.ENCHANTMENTS);
        if (level > 0) return level;
        return levelOf(stack, DataComponents.STORED_ENCHANTMENTS);
    }

    private static int levelOf(ItemStack stack, DataComponentType<ItemEnchantments> type) {
        ItemEnchantments enchantments = stack.getOrDefault(type, ItemEnchantments.EMPTY);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            Optional<ResourceKey<Enchantment>> key = entry.getKey().unwrapKey();
            if (key.isPresent() && key.get().equals(KEY)) return entry.getIntValue();
        }
        return 0;
    }

    /** 额外伤害直接叠加到最终伤害上，等效于无视护甲的附加伤害，且不触碰无敌帧。 */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        // 只对近战生效，弹射物不触发
        if (event.getSource().getDirectEntity() != attacker) return;

        int level = levelOf(attacker.getMainHandItem());
        if (level <= 0) return;

        float extraDamage = DAMAGE_BY_LEVEL[Math.min(level, DAMAGE_BY_LEVEL.length) - 1];
        if (extraDamage <= 0.0F) return;

        if (target.level() instanceof ServerLevel serverLevel) {
            spawnFlameTrail(serverLevel, attacker, target);
            playAttackSound(serverLevel, target);
        }

        event.setAmount(event.getAmount() + extraDamage);
    }

    /** 在攻击者到目标的连线上插值生成橙色火焰粒子。 */
    private static void spawnFlameTrail(ServerLevel level, Entity from, Entity to) {
        Vec3 start = from.position().add(0.0D, from.getBbHeight() * 0.6D, 0.0D);
        Vec3 end = to.position().add(0.0D, to.getBbHeight() * 0.5D, 0.0D);

        double distance = start.distanceTo(end);
        int particleCount = Math.max(10, Math.min(30, (int) (distance * 8.0D)));

        for (int i = 0; i < particleCount; i++) {
            double t = (double) i / (particleCount - 1);
            double x = start.x + (end.x - start.x) * t;
            double y = start.y + (end.y - start.y) * t;
            double z = start.z + (end.z - start.z) * t;
            level.sendParticles(ParticleTypes.FLAME,
                    x + (level.random.nextDouble() - 0.5D) * 0.15D,
                    y + (level.random.nextDouble() - 0.5D) * 0.15D,
                    z + (level.random.nextDouble() - 0.5D) * 0.15D,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void playAttackSound(ServerLevel level, LivingEntity target) {
        float pitch = 0.8F + level.random.nextFloat() * 0.4F;
        level.playSound(null, target.blockPosition(), SoundEvents.IRON_GOLEM_ATTACK,
                SoundSource.HOSTILE, 1.0F, pitch);
    }

    /** 物品提示：在附魔名称之后追加效果说明与当前等级的额外伤害。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        int level = levelOf(stack);
        if (level <= 0) return;

        List<Component> tooltip = event.getToolTip();
        String enchantName = Component.translatable("enchantment.endbound_ember.qianpo_qing_ming_sword").getString();
        int insertAt = tooltip.size();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().startsWith(enchantName)) {
                insertAt = i + 1;
                break;
            }
        }

        float extraDamage = DAMAGE_BY_LEVEL[Math.min(level, DAMAGE_BY_LEVEL.length) - 1];
        tooltip.add(insertAt, line("endbound_ember.qianpo_qing_ming_sword.desc"));
        tooltip.add(insertAt + 1, line("endbound_ember.qianpo_qing_ming_sword.damage",
                String.format(java.util.Locale.ROOT, "%.0f", extraDamage)));
    }

    private static Component line(String key, Object... args) {
        return Component.translatable(key, args).withStyle(style -> style.withColor(TOOLTIP_COLOR));
    }
}
