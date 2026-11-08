package com.emberstar.endbounember.enchantment;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.emberstar.endbounember.EndboundEmber;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/**
 * 末影箭：弓 / 弩专属，最高 I 级。
 * 命中生物时把射手传送到目标身边，冷却 3 秒；命中方块时不触发，且不消耗冷却。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class EnderArrowEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "ender_arrow"));

    /** 旧版 EnderArrowConfig.cooldownSeconds 默认值（3 秒）。 */
    private static final int COOLDOWN_TICKS = 60;
    /** 旧版 EnderArrowConfig.teleportToEntity 默认值（true：传送到目标身前，false 则传送到命中点）。 */
    private static final boolean TELEPORT_TO_ENTITY = true;

    /** 旧版 EnderArrowHandler.TAG_ENDER_ARROW：标记带有末影箭附魔的箭。 */
    private static final String TAG_MARKED = "EndboundEmberEnderArrow";
    /** 旧版 EnderArrowHandler.cooldowns：按玩家记录冷却结束的游戏时间。 */
    private static final Map<UUID, Long> COOLDOWNS = new ConcurrentHashMap<>();

    private EnderArrowEnchantment() {
    }

    /** 旧版 EnderArrowHandler.getEnderArrowLevel：取主副手弓 / 弩上的较大等级。 */
    private static int levelOfHands(LivingEntity shooter) {
        return Math.max(levelOf(shooter.getMainHandItem()), levelOf(shooter.getOffhandItem()));
    }

    /** 读取物品上的末影箭附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
    public static int levelOf(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (!(stack.getItem() instanceof BowItem) && !(stack.getItem() instanceof CrossbowItem)) return 0;

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

    /** 箭矢生成：给带有末影箭附魔的箭打上标记。 */
    @SubscribeEvent
    public static void onArrowJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;

        CompoundTag tag = arrow.getPersistentData();
        if (tag.getBoolean(TAG_MARKED)) return;
        if (!(arrow.getOwner() instanceof LivingEntity shooter)) return;

        if (levelOfHands(shooter) > 0) {
            tag.putBoolean(TAG_MARKED, true);
        }
    }

    /** 箭矢命中生物：冷却就绪时把射手传送到目标身前。 */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;

        Level level = arrow.level();
        if (level.isClientSide()) return;
        if (!arrow.getPersistentData().getBoolean(TAG_MARKED)) return;

        HitResult hit = event.getRayTraceResult();
        if (!(hit instanceof EntityHitResult entityHit)) return;
        if (!(entityHit.getEntity() instanceof LivingEntity target)) return;
        if (!(arrow.getOwner() instanceof LivingEntity shooter)) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        long gameTime = level.getGameTime();
        if (shooter instanceof ServerPlayer player) {
            Long cooldownEnd = COOLDOWNS.get(player.getUUID());
            if (cooldownEnd != null && gameTime < cooldownEnd) {
                long remainTicks = cooldownEnd - gameTime;
                player.displayClientMessage(
                        Component.literal("§5末影箭冷却中：" + String.format("%.1f", remainTicks / 20.0F) + " 秒"),
                        true);
                return;
            }
        }

        Vec3 targetPos = TELEPORT_TO_ENTITY ? getFrontPosition(target) : hit.getLocation();
        Vec3 destination = findSafePosition(serverLevel, targetPos);

        serverLevel.sendParticles(ParticleTypes.PORTAL,
                shooter.getX(), shooter.getY() + shooter.getBbHeight() * 0.5D, shooter.getZ(),
                20, 0.5D, 0.5D, 0.5D, 0.1D);
        serverLevel.sendParticles(ParticleTypes.PORTAL,
                destination.x, destination.y + 1.0D, destination.z,
                20, 0.5D, 0.5D, 0.5D, 0.1D);
        serverLevel.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
                SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        if (shooter instanceof ServerPlayer player) {
            player.teleportTo(serverLevel, destination.x, destination.y, destination.z,
                    player.getYRot(), player.getXRot());
            player.resetFallDistance();
            COOLDOWNS.put(player.getUUID(), gameTime + COOLDOWN_TICKS);
        } else {
            shooter.teleportTo(destination.x, destination.y, destination.z);
            shooter.resetFallDistance();
        }
    }

    /** 目标朝向射手一侧的身前落点（旧版 target.position().subtract(target.getLookAngle())）。 */
    private static Vec3 getFrontPosition(LivingEntity target) {
        return target.position().subtract(target.getLookAngle());
    }

    /** 保留旧版规则：落点高于目标脚部 1 格，并保证不高于该处世界高度上限。 */
    private static Vec3 findSafePosition(ServerLevel level, Vec3 base) {
        double y = Math.min(base.y + 1.0D, level.getMaxBuildHeight() - 1);
        return new Vec3(base.x, y, base.z);
    }
}
