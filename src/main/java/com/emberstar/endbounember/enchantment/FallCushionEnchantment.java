package com.emberstar.endbounember.enchantment;

import java.util.Optional;

import com.emberstar.endbounember.EndboundEmber;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

/**
 * 坠落缓冲：靴子专属，最高 II 级。
 * 高坠落地时按等级减免摔落伤害，摔落距离达到 3 格后产生冲击波击退周围敌人。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class FallCushionEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "fall_cushion"));

    /** 对应旧版配置 fallCushion.damageMultiplierLevel1（默认 0.5）。 */
    private static final float DAMAGE_MULTIPLIER_L1 = 0.5F;
    /** 对应旧版配置 fallCushion.damageMultiplierLevel2（默认 0.25）。 */
    private static final float DAMAGE_MULTIPLIER_L2 = 0.25F;
    /** 对应旧版配置 fallCushion.knockbackLevel1（默认 1.0）。 */
    private static final double KNOCKBACK_L1 = 1.0D;
    /** 对应旧版配置 fallCushion.knockbackLevel2（默认 2.0）。 */
    private static final double KNOCKBACK_L2 = 2.0D;
    /** 对应旧版配置 fallCushion.radiusLevel1（默认 3.0）。 */
    private static final double RADIUS_L1 = 3.0D;
    /** 对应旧版配置 fallCushion.radiusLevel2（默认 5.0）。 */
    private static final double RADIUS_L2 = 5.0D;
    /** 对应旧版配置 fallCushion.minFallDistance（默认 3.0）。 */
    private static final float MIN_FALL_DISTANCE = 3.0F;

    private FallCushionEnchantment() {
    }

    /** 读取物品上的坠落缓冲等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        int level = levelOf(player.getItemBySlot(EquipmentSlot.FEET));
        if (level <= 0) return;

        event.setDamageMultiplier(level >= 2 ? DAMAGE_MULTIPLIER_L2 : DAMAGE_MULTIPLIER_L1);

        float fallDistance = event.getDistance();
        if (fallDistance < MIN_FALL_DISTANCE) return;

        double radius = level >= 2 ? RADIUS_L2 : RADIUS_L1;
        double knockback = level >= 2 ? KNOCKBACK_L2 : KNOCKBACK_L1;
        Level levelWorld = player.level();

        AABB area = player.getBoundingBox().inflate(radius);
        for (LivingEntity entity : levelWorld.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != player && e.isAlive())) {
            double dx = entity.getX() - player.getX();
            double dz = entity.getZ() - player.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > radius) continue;

            double strength = knockback * (1.0D - distance / radius);
            if (strength <= 0.0D) continue;
            entity.knockback(strength, dx, dz);
        }

        levelWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 0.8F, 0.6F);
        if (levelWorld instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY() + 0.1D, player.getZ(),
                    20, radius * 0.3D, 0.1D, radius * 0.3D, 0.1D);
        }
    }
}
