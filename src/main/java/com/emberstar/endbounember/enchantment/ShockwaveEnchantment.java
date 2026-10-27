package com.emberstar.endbounember.enchantment;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.compat.FriendlyMobs;

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
 * 震波扩散：重锤专属，最高 III 级。
 * 下落重击命中目标时，以目标为中心向四周扩散冲击波，
 * 对周围生物造成随等级与下落高度提升的溅射伤害并击退。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class ShockwaveEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "shockwave"));

    /** 触发重击所需的最小下落距离。 */
    private static final float MIN_FALL_DISTANCE = 1.5F;
    private static final double RADIUS_BASE = 2.5D;
    private static final double RADIUS_PER_LEVEL = 0.5D;
    private static final float DAMAGE_BASE = 2.0F;
    private static final float DAMAGE_PER_LEVEL = 2.0F;
    /** 每格下落距离追加的伤害。 */
    private static final float DAMAGE_PER_BLOCK = 0.5F;
    /** 下落距离加成上限。 */
    private static final float MAX_FALL_BONUS_DISTANCE = 20.0F;
    private static final double KNOCKBACK_BASE = 0.3D;
    private static final double KNOCKBACK_PER_LEVEL = 0.1D;
    private static final int TOOLTIP_COLOR = 0xC9A66B;

    private ShockwaveEnchantment() {
    }

    /** 读取物品上的震波扩散附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    /** 下落重击命中后，对目标周围的生物造成溅射伤害与击退。 */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (event.getSource().getDirectEntity() != attacker) return;
        if (attacker.isFallFlying() || attacker.fallDistance < MIN_FALL_DISTANCE) return;

        int level = levelOf(attacker.getMainHandItem());
        if (level <= 0) return;
        if (!(target.level() instanceof ServerLevel serverLevel)) return;

        double radius = RADIUS_BASE + RADIUS_PER_LEVEL * level;
        float splashDamage = DAMAGE_BASE + DAMAGE_PER_LEVEL * level
                + Math.min(attacker.fallDistance, MAX_FALL_BONUS_DISTANCE) * DAMAGE_PER_BLOCK;
        double knockback = KNOCKBACK_BASE + KNOCKBACK_PER_LEVEL * level;

        List<LivingEntity> nearby = serverLevel.getEntitiesOfClass(LivingEntity.class,
                target.getBoundingBox().inflate(radius),
                victim -> victim != attacker && victim != target && victim.isAlive() && !victim.isSpectator()
                        && !FriendlyMobs.isProtected(victim));

        for (LivingEntity victim : nearby) {
            // 用无来源的魔法伤害，避免再次触发本附魔
            victim.hurt(serverLevel.damageSources().magic(), splashDamage);

            Vec3 offset = victim.position().subtract(target.position());
            Vec3 horizontal = new Vec3(offset.x, 0.0D, offset.z);
            if (horizontal.lengthSqr() < 1.0E-4D) {
                horizontal = new Vec3(serverLevel.random.nextDouble() - 0.5D, 0.0D,
                        serverLevel.random.nextDouble() - 0.5D);
            }
            horizontal = horizontal.normalize().scale(knockback);
            victim.push(horizontal.x, 0.25D, horizontal.z);
            victim.hurtMarked = true;
        }

        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                target.getX(), target.getY() + 0.2D, target.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        serverLevel.playSound(null, target.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY,
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** 物品提示：在附魔名称之后追加冲击范围说明。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        int level = levelOf(stack);
        if (level <= 0) return;

        List<Component> tooltip = event.getToolTip();
        String enchantName = Component.translatable("enchantment.endbound_ember.shockwave").getString();
        int insertAt = tooltip.size();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().startsWith(enchantName)) {
                insertAt = i + 1;
                break;
            }
        }

        tooltip.add(insertAt, line("endbound_ember.shockwave.desc"));
        tooltip.add(insertAt + 1, line("endbound_ember.shockwave.radius",
                String.format(Locale.ROOT, "%.1f", RADIUS_BASE + RADIUS_PER_LEVEL * level)));
    }

    private static Component line(String key, Object... args) {
        return Component.translatable(key, args).withStyle(style -> style.withColor(TOOLTIP_COLOR));
    }
}
