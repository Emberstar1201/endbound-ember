package com.emberstar.endbounember.client;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.network.MultiJumpPayload;
import com.emberstar.endbounember.world.MultiJumpRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 多段跳的客户端输入与预测。
 * <p>
 * 客户端只做即时预测以便手感顺滑，次数以服务端确认结果为准；
 * 每次请求携带递增序号，晚到的旧确认不会覆盖已经更新的预测状态。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public final class MultiJumpClient {
    private static LocalPlayer trackedPlayer;
    private static boolean jumpWasDown;
    /** 客户端预测的剩余次数 */
    private static int remaining;
    /** 已发出的最大请求序号 */
    private static int sentSequence;
    /** 已应用的最大确认序号，用于丢弃过期确认 */
    private static int appliedAckSequence;

    private MultiJumpClient() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            reset();
            return;
        }
        if (trackedPlayer != player) {
            trackedPlayer = player;
            remaining = configuredJumps();
            jumpWasDown = false;
            sentSequence = 0;
            appliedAckSequence = 0;
        }
        int configured = configuredJumps();
        if (player.onGround()) {
            // 落地恢复次数时直接跳过所有在途确认，避免旧确认把刚恢复的次数又扣回去
            remaining = configured;
            appliedAckSequence = sentSequence;
        }
        remaining = Math.min(remaining, configured);
        if (configured <= 0 || minecraft.screen != null) {
            jumpWasDown = minecraft.options.keyJump.isDown();
            return;
        }
        boolean jumpDown = minecraft.options.keyJump.isDown();
        boolean pressed = jumpDown && !jumpWasDown;
        jumpWasDown = jumpDown;
        if (!pressed || remaining <= 0 || player.onGround() || !MultiJumpRules.isEligible(player)) return;

        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, MultiJumpRules.jumpPower(player), motion.z);
        // 与服务端一致：保留 fallDistance，多段跳不再免除摔落伤害
        player.hasImpulse = true;
        remaining--;
        sentSequence++;
        PacketDistributor.sendToServer(new MultiJumpPayload(sentSequence, remaining, false));
    }

    /** 应用服务端回传的权威结果 */
    public static void applyServerResult(int sequence, int serverRemaining, boolean accepted) {
        if (trackedPlayer == null || sequence <= appliedAckSequence) return;
        appliedAckSequence = sequence;
        int configured = configuredJumps();
        int authoritative = Math.max(0, Math.min(serverRemaining, configured));
        if (sequence == sentSequence) {
            // 没有更新的请求在途，直接采用服务端次数
            remaining = authoritative;
        } else {
            // 仍有更晚的请求未确认，只做上限收敛，避免旧结果把新预测顶回去
            remaining = Math.min(remaining, authoritative);
        }
    }

    private static int configuredJumps() {
        return Config.MULTI_JUMP_ENABLED.get() ? Config.MULTI_JUMP_EXTRA_JUMPS.get() : 0;
    }

    private static void reset() {
        trackedPlayer = null;
        jumpWasDown = false;
        remaining = 0;
        sentSequence = 0;
        appliedAckSequence = 0;
    }
}
