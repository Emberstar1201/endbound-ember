package com.emberstar.endbounember.world;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 多段跳的服务端权威逻辑。
 * <p>
 * 客户端只负责输入与即时预测，真正的次数扣除、运动应用与表现广播都在这里完成；
 * 每次请求都会回传序号与权威剩余次数，客户端据此丢弃过期确认并校正预测。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class MultiJump {
    /** 玩家剩余的空中跳次数 */
    private static final Map<UUID, Integer> REMAINING = new ConcurrentHashMap<>();
    /** 已处理的最大请求序号，用于丢弃网络重发与乱序的重复请求 */
    private static final Map<UUID, Integer> LAST_SEQUENCE = new ConcurrentHashMap<>();
    /** 上一次成功起跳的游戏刻，避免同一刻内被重复执行 */
    private static final Map<UUID, Long> LAST_JUMP_TICK = new ConcurrentHashMap<>();

    private MultiJump() {
    }

    /** 服务端处理结果：序号、权威剩余次数、是否真正执行了这次跳跃 */
    public record Result(int sequence, int remaining, boolean accepted) {
    }

    public static Result handleRequest(ServerPlayer player, int sequence) {
        UUID id = player.getUUID();
        int configured = configuredJumps();

        // 重复或过期的请求不重复扣次数，直接回送当前权威状态
        if (sequence <= LAST_SEQUENCE.getOrDefault(id, 0)) {
            return new Result(Math.max(sequence, LAST_SEQUENCE.getOrDefault(id, 0)),
                    remaining(id, configured), false);
        }
        LAST_SEQUENCE.put(id, sequence);

        if (configured <= 0 || !MultiJumpRules.isEligible(player) || player.onGround()) {
            return new Result(sequence, remaining(id, configured), false);
        }

        int remaining = remaining(id, configured);
        if (remaining <= 0) {
            return new Result(sequence, 0, false);
        }

        long gameTime = player.level().getGameTime();
        if (LAST_JUMP_TICK.getOrDefault(id, Long.MIN_VALUE) >= gameTime) {
            return new Result(sequence, remaining, false);
        }

        // 起跳动作由客户端预测完成。服务端这里不能再改写速度并标记 hurtMarked：
        // 服务端的 deltaMovement 是经过摩擦衰减的旧值，回传的速度包会覆盖客户端速度，
        // 表现为起跳瞬间水平速度被清空。这里刻意不清空 fallDistance，
        // 空中多段跳不免费抵消摔落伤害，落地仍按累计下落高度结算。
        REMAINING.put(id, remaining - 1);
        LAST_JUMP_TICK.put(id, gameTime);
        broadcastEffect(player);
        return new Result(sequence, remaining - 1, true);
    }

    /** 成功起跳的表现只由服务端广播一次，客户端预测不重复播放 */
    private static void broadcastEffect(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        level.sendParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.1D, player.getZ(),
                8, 0.25D, 0.05D, 0.25D, 0.02D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.4F, 1.5F);
    }

    private static int configuredJumps() {
        return Config.MULTI_JUMP_ENABLED.get() ? Config.MULTI_JUMP_EXTRA_JUMPS.get() : 0;
    }

    /** 取玩家剩余次数，首次访问时初始化为配置值 */
    private static int remaining(UUID id, int configured) {
        if (configured <= 0) {
            clear(id);
            return 0;
        }
        return REMAINING.computeIfAbsent(id, ignored -> configured);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        // 重生后客户端会创建一个新的本地玩家实例并把请求序号归零，
        // 服务端必须同步清空，否则之后的请求都会被当成过期请求拒绝。
        clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        clear(event.getEntity().getUUID());
    }

    private static void clear(UUID id) {
        REMAINING.remove(id);
        LAST_SEQUENCE.remove(id);
        LAST_JUMP_TICK.remove(id);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        if (configuredJumps() <= 0 || !player.isAlive()) {
            clear(id);
            return;
        }
        // 落地即恢复全部空中跳次数（包含死亡复活后的首次落地）
        if (player.onGround()) REMAINING.put(id, configuredJumps());
    }
}
