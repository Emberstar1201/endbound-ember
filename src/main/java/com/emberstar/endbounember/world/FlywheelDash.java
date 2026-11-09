package com.emberstar.endbounember.world;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 飞轮冲刺：内置能力，默认按键 R（可在按键绑定中修改）。
 * 朝准心方向高速突进，期间免疫伤害并可穿过生物；撞墙会提前停下。冷却 5 秒。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class FlywheelDash {
    private static final String TAG_ROOT = "FlywheelData";
    private static final String TAG_LAST_USE = "lastUseTime";

    /** 冲刺距离（格）。 */
    private static final double DASH_DISTANCE = 8.0D;
    /** 冷却时间：5 秒。 */
    private static final int COOLDOWN_TICKS = 100;

    /** 每 tick 移动距离。 */
    private static final double DASH_SPEED = 1.5D;
    /** 最短冲刺时长，保证手感。 */
    private static final int MIN_DASH_TICKS = 4;
    /** 视线垂直分量上限，避免一路飞上天空。 */
    private static final double MAX_VERTICAL = 0.5D;
    /** 抵达终点后额外保留的无敌帧。 */
    private static final int LINGER_TICKS = 20;

    private static final Map<UUID, DashState> DASHING = new HashMap<>();
    private static final Map<UUID, Boolean> WAS_ON_COOLDOWN = new HashMap<>();

    private FlywheelDash() {
    }

    /** 单次冲刺的运行时状态。 */
    private static final class DashState {
        private final Vec3 direction;
        private final int totalTicks;
        private final boolean wasInvulnerable;
        private int ticksElapsed;
        private int lingerTicks;

        private DashState(Vec3 direction, int totalTicks, boolean wasInvulnerable) {
            this.direction = direction;
            this.totalTicks = totalTicks;
            this.wasInvulnerable = wasInvulnerable;
        }
    }

    /** 客户端按键请求入口：校验通过后开始冲刺。 */
    public static void tryDash(ServerPlayer player) {
        if (!Config.FLYWHEEL_DASH_ENABLED.get()) return;
        if (player.isPassenger()) return;

        if (isOnCooldown(player)) {
            player.displayClientMessage(
                    Component.translatable("endbound_ember.flywheel_effect.cooldown_active")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        setLastUseTime(player, player.level().getGameTime());

        Vec3 look = player.getLookAngle();
        Vec3 direction = new Vec3(look.x, Math.max(-MAX_VERTICAL, Math.min(MAX_VERTICAL, look.y)), look.z);
        int totalTicks = Math.max(MIN_DASH_TICKS,
                (int) Math.ceil(DASH_DISTANCE / DASH_SPEED));

        DashState state = new DashState(direction, totalTicks, player.isInvulnerable());
        DASHING.put(player.getUUID(), state);

        applyDashMotion(player, state);
        spawnBurstParticles(player.serverLevel(), player);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        DashState state = DASHING.get(player.getUUID());
        if (state != null) updateDash(player, state);
        checkCooldownReady(player);
    }

    private static void updateDash(ServerPlayer player, DashState state) {
        state.ticksElapsed++;

        if (isInsideWall(player)) {
            stopDash(player, state);
            return;
        }
        if (state.ticksElapsed > state.totalTicks) {
            // 抵达终点后保留一段无敌帧，期间不再位移
            state.lingerTicks++;
            if (state.lingerTicks > LINGER_TICKS) {
                stopDash(player, state);
                return;
            }
            player.setInvulnerable(true);
            return;
        }

        applyDashMotion(player, state);
        spawnDashParticles(player.serverLevel(), player, state);

        if (state.ticksElapsed % 4 == 0) {
            ServerLevel level = player.serverLevel();
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS,
                    0.6F, 1.0F + level.random.nextFloat() * 0.3F);
        }
    }

    private static void stopDash(ServerPlayer player, DashState state) {
        DASHING.remove(player.getUUID());
        if (!state.wasInvulnerable) player.setInvulnerable(false);

        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                player.getX(), player.getY() + 0.8D, player.getZ(),
                10, 0.3D, 0.3D, 0.3D, 0.05D);
    }

    private static void applyDashMotion(ServerPlayer player, DashState state) {
        Vec3 direction = state.direction;
        player.setDeltaMovement(direction.x * DASH_SPEED, direction.y * DASH_SPEED, direction.z * DASH_SPEED);
        player.setInvulnerable(true);
        // 告知客户端位置已被服务端修正，避免被判定为异常移动
        player.hurtMarked = true;
    }

    /** 脚、身体中心、头部任一位置被阻挡即视为撞墙。 */
    private static boolean isInsideWall(Player player) {
        BlockPos[] positions = {
                player.blockPosition(),
                BlockPos.containing(player.getX(), player.getY() + 0.9D, player.getZ()),
                BlockPos.containing(player.getX(), player.getY() + 1.6D, player.getZ())
        };
        for (BlockPos pos : positions) {
            BlockState state = player.level().getBlockState(pos);
            if (state.blocksMotion()) return true;
        }
        return false;
    }

    private static void spawnDashParticles(ServerLevel level, ServerPlayer player, DashState state) {
        double x = player.getX();
        double y = player.getY() + 0.8D;
        double z = player.getZ();

        double angle = state.ticksElapsed * 0.8D;
        double radius = 0.6D;

        level.sendParticles(ParticleTypes.END_ROD,
                x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                x + Math.cos(angle + Math.PI) * radius, y, z + Math.sin(angle + Math.PI) * radius,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private static void spawnBurstParticles(ServerLevel level, ServerPlayer player) {
        level.sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 0.8D, player.getZ(),
                20, 0.5D, 0.5D, 0.5D, 0.1D);
    }

    /** 冷却状态保存在玩家持久化数据中，跨维度与重生都能保留。 */
    private static boolean isOnCooldown(Player player) {
        long elapsed = player.level().getGameTime() - lastUseTime(player);
        return elapsed < COOLDOWN_TICKS;
    }

    private static long lastUseTime(Player player) {
        CompoundTag data = player.getPersistentData().getCompound(TAG_ROOT);
        // 从未使用过时视为早已冷却完毕
        return data.contains(TAG_LAST_USE) ? data.getLong(TAG_LAST_USE) : Long.MIN_VALUE / 2;
    }

    private static void setLastUseTime(Player player, long time) {
        CompoundTag data = player.getPersistentData().getCompound(TAG_ROOT);
        data.putLong(TAG_LAST_USE, time);
        player.getPersistentData().put(TAG_ROOT, data);
    }

    /** 冷却由「进行中」转为「已完成」时提示玩家。 */
    private static void checkCooldownReady(ServerPlayer player) {
        if (!Config.FLYWHEEL_DASH_ENABLED.get()) return;

        UUID uuid = player.getUUID();
        boolean onCooldown = isOnCooldown(player);
        Boolean previous = WAS_ON_COOLDOWN.put(uuid, onCooldown);
        if (previous == null || !previous || onCooldown) return;

        player.displayClientMessage(
                Component.translatable("endbound_ember.flywheel_effect.ready")
                        .withStyle(ChatFormatting.GREEN),
                true);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 2.0F);
    }
}
