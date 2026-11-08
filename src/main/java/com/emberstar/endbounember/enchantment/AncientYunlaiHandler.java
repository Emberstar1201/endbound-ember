package com.emberstar.endbounember.enchantment;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.network.CloudArrowVelocityPayload;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 古·云来弓法的事件处理：拉弓加速、箭矢加速与飞行轨迹粒子。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class AncientYunlaiHandler {

    /** 旧版 AncientYunLaiHandler.PARTICLE_SPACING。 */
    private static final double PARTICLE_SPACING = 0.1D;
    /** 旧版 AncientYunLaiHandler.MAX_PARTICLES_PER_TICK。 */
    private static final int MAX_PARTICLES_PER_TICK = 128;
    /** 旧版 AncientYunLaiHandler.MAX_INTERPOLATION_DISTANCE。 */
    private static final double MAX_INTERPOLATION_DISTANCE = 64.0D;
    /** 原版生成包对速度分量的钳制上限（格/刻）。 */
    private static final double VELOCITY_PACKET_LIMIT = 3.9D;

    private static final Map<UUID, Vec3> ARROW_TRAIL_POSITIONS = new HashMap<>();

    private AncientYunlaiHandler() {
    }

    /** 拉弓加速：每 tick 额外扣掉 1 tick 蓄力进度，等效蓄力倍率 2.0。 */
    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack usingItem = event.getItem();
        if (!(usingItem.getItem() instanceof BowItem)) return;
        if (AncientYunlaiEnchantment.levelOf(usingItem) <= 0) return;

        double extraProgress = AncientYunlaiEnchantment.CHARGE_MULTIPLIER - 1.0D;
        int wholeSkips = (int) extraProgress;
        if (wholeSkips <= 0) return;

        if (player.getTicksUsingItem() >= AncientYunlaiEnchantment.FULL_CHARGE_TICKS) return;
        for (int i = 0; i < wholeSkips; i++) {
            if (event.getDuration() <= 1) return;
            event.setDuration(event.getDuration() - 1);
        }
    }

    /** 箭矢生成：按 3.5 倍放大初速度与基础伤害，并记录轨迹起点。 */
    @SubscribeEvent
    public static void onArrowJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (arrow.tickCount > 0) return;

        Entity owner = arrow.getOwner();
        if (!(owner instanceof Player player)) return;

        ItemStack bow = player.getMainHandItem();
        if (!(bow.getItem() instanceof BowItem)) {
            bow = player.getOffhandItem();
        }
        if (!(bow.getItem() instanceof BowItem)) return;
        if (AncientYunlaiEnchantment.levelOf(bow) <= 0) return;

        arrow.setDeltaMovement(arrow.getDeltaMovement().scale(AncientYunlaiEnchantment.ARROW_SPEED_MULTIPLIER));
        arrow.setBaseDamage(arrow.getBaseDamage() * AncientYunlaiEnchantment.ARROW_BASE_DAMAGE_MULTIPLIER);

        ARROW_TRAIL_POSITIONS.put(arrow.getUUID(), arrow.position());
    }

    /** 补发真实速度，避免原版生成包把高速分量钳制到 3.9 导致轨迹错位。 */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof AbstractArrow arrow)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Vec3 velocity = arrow.getDeltaMovement();
        boolean clamped = Math.abs(velocity.x) > VELOCITY_PACKET_LIMIT
                || Math.abs(velocity.y) > VELOCITY_PACKET_LIMIT
                || Math.abs(velocity.z) > VELOCITY_PACKET_LIMIT;
        if (!clamped) return;

        PacketDistributor.sendToPlayer(player, new CloudArrowVelocityPayload(
                arrow.getId(), velocity.x, velocity.y, velocity.z));
    }

    /** 每 tick 在箭矢上一位置与当前位置之间插值生成 END_ROD 轨迹。 */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ARROW_TRAIL_POSITIONS.isEmpty()) return;

        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, Vec3>> iterator = ARROW_TRAIL_POSITIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Vec3> entry = iterator.next();
            AbstractArrow arrow = findArrow(server, entry.getKey());
            if (arrow == null || !arrow.isAlive()) {
                iterator.remove();
                continue;
            }

            Vec3 previousPosition = entry.getValue();
            Vec3 currentPosition = arrow.position();
            Vec3 movement = currentPosition.subtract(previousPosition);
            double distance = movement.length();

            // 位移必须是有限值且在合理范围内，避免异常坐标导致粒子循环失控
            if (!Double.isFinite(distance) || distance > MAX_INTERPOLATION_DISTANCE) {
                entry.setValue(currentPosition);
                continue;
            }

            int particleCount = Mth.clamp((int) Math.ceil(distance / PARTICLE_SPACING),
                    1, MAX_PARTICLES_PER_TICK);
            if (!(arrow.level() instanceof ServerLevel serverLevel)) {
                iterator.remove();
                continue;
            }

            for (int i = 0; i < particleCount; i++) {
                Vec3 particlePosition = previousPosition.lerp(currentPosition, (double) i / particleCount);
                serverLevel.sendParticles(ParticleTypes.END_ROD,
                        particlePosition.x, particlePosition.y, particlePosition.z,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            entry.setValue(currentPosition);
        }
    }

    private static AbstractArrow findArrow(MinecraftServer server, UUID arrowId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(arrowId) instanceof AbstractArrow arrow) {
                return arrow;
            }
        }
        return null;
    }
}
