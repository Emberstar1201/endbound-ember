package com.emberstar.endbounember.world;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * 时间乱流维度的进入许可。
 * <p>
 * 其他玩家用 {@code /tp} 等指令跨维度传送进来时不会立刻落地，而是先挂起一条申请，
 * 只有该维度的主人会在聊天栏收到 [允许] / [拒绝] 两个按钮；
 * 申请者自己戴着时光光环时直接放行，不打扰任何人。
 */
public final class TimeTurbulenceAccess {
    /** 申请有效期：超过时间未处理自动作废 */
    private static final long REQUEST_TIMEOUT_MS = 60_000L;

    private static final Map<Integer, PendingRequest> PENDING = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

    /** 吞掉原版成功提示的有效期（只需覆盖当前这条指令的执行，留一点余量防止残留） */
    private static final long SILENCE_WINDOW_MS = 500L;
    /** 仅在服务端主线程读写，因此用普通字段即可 */
    private static long silenceUntil = 0L;

    private TimeTurbulenceAccess() {
    }

    private record PendingRequest(int id, UUID requesterId, String requesterName, ResourceKey<Level> dimension,
            double x, double y, double z, float yRot, float xRot, long expiresAt) {
    }

    /**
     * 拦截跨维度传送：目标维度属于其他玩家且申请者没戴时光光环时挂起申请。
     *
     * @return true 表示这次传送应被取消
     */
    public static boolean interceptTeleport(ServerPlayer player, ServerLevel target,
            double x, double y, double z, float yRot, float xRot) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        Optional<UUID> owner = TimeTurbulenceLevels.ownerOf(target);
        if (owner.isEmpty() || owner.get().equals(player.getUUID())) {
            return false;
        }
        // 戴着时光光环的人无需经过主人同意
        if (TimeTurbulenceLevels.wearsTimeHalo(player)) {
            return false;
        }

        // 传送会被取消，原版的 “Teleported” 提示也随之失去意义
        silenceUntil = System.currentTimeMillis() + SILENCE_WINDOW_MS;

        ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(owner.get());
        if (ownerPlayer == null) {
            player.sendSystemMessage(Component.translatable(
                    "message.endbound_ember.time_turbulence.no_decider"));
            return true;
        }

        // 同一玩家对同一维度只保留最新的一条申请
        PENDING.values().removeIf(request -> request.requesterId().equals(player.getUUID())
                && request.dimension().equals(target.dimension()));

        PendingRequest request = new PendingRequest(
                NEXT_ID.getAndIncrement(),
                player.getUUID(),
                player.getGameProfile().getName(),
                target.dimension(),
                x, y, z, yRot, xRot,
                System.currentTimeMillis() + REQUEST_TIMEOUT_MS);
        PENDING.put(request.id(), request);

        // 申请只推送给维度主人，其他人不受打扰
        ownerPlayer.sendSystemMessage(Component.translatable(
                "message.endbound_ember.time_turbulence.notify",
                Component.literal(request.requesterName()),
                button(request.id(), true),
                button(request.id(), false)));
        player.sendSystemMessage(Component.translatable(
                "message.endbound_ember.time_turbulence.request_sent"));
        return true;
    }

    /** 处理一条进入申请；返回 1 表示处理成功。 */
    public static int decide(ServerPlayer actor, int id, boolean allow) {
        MinecraftServer server = actor.getServer();
        PendingRequest request = PENDING.get(id);
        if (server == null || request == null) {
            actor.sendSystemMessage(Component.translatable("message.endbound_ember.time_turbulence.unknown"));
            return 0;
        }
        if (System.currentTimeMillis() > request.expiresAt()) {
            PENDING.remove(id);
            actor.sendSystemMessage(Component.translatable("message.endbound_ember.time_turbulence.expired"));
            return 0;
        }

        ServerLevel target = server.getLevel(request.dimension());
        if (target == null) {
            PENDING.remove(id);
            actor.sendSystemMessage(Component.translatable("message.endbound_ember.time_turbulence.unknown"));
            return 0;
        }
        boolean isOwner = TimeTurbulenceLevels.ownerOf(target)
                .map(uuid -> uuid.equals(actor.getUUID()))
                .orElse(false);
        if (!isOwner) {
            actor.sendSystemMessage(Component.translatable("message.endbound_ember.time_turbulence.not_decider"));
            return 0;
        }
        PENDING.remove(id);

        ServerPlayer requester = server.getPlayerList().getPlayer(request.requesterId());
        Component actorName = Component.literal(actor.getGameProfile().getName());
        Component requesterName = Component.literal(request.requesterName());

        if (!allow) {
            if (requester != null) {
                requester.sendSystemMessage(Component.translatable(
                        "message.endbound_ember.time_turbulence.denied"));
            }
            actor.sendSystemMessage(Component.translatable(
                    "message.endbound_ember.time_turbulence.deny.done", requesterName));
            return 1;
        }

        if (requester == null) {
            actor.sendSystemMessage(Component.translatable(
                    "message.endbound_ember.time_turbulence.requester_offline", requesterName));
            return 0;
        }

        TimeTurbulenceLevels.enterAsGuest(requester, target, request.x(), request.y(), request.z(),
                request.yRot(), request.xRot());
        requester.sendSystemMessage(Component.translatable(
                "message.endbound_ember.time_turbulence.accepted", actorName));
        actor.sendSystemMessage(Component.translatable(
                "message.endbound_ember.time_turbulence.allow.done", requesterName));
        return 1;
    }

    /** 清理超时的申请，并把结果告知申请者 */
    public static void purgeExpired(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        PENDING.values().removeIf(request -> {
            if (now <= request.expiresAt()) {
                return false;
            }
            ServerPlayer requester = server.getPlayerList().getPlayer(request.requesterId());
            if (requester != null) {
                requester.sendSystemMessage(Component.translatable(
                        "message.endbound_ember.time_turbulence.expired"));
            }
            return true;
        });
    }

    /**
     * 读一次“是否要吞掉原版传送提示”。
     * <p>
     * 只有刚刚被 {@link #interceptTeleport} 拦下的那条传送指令会读到 true，读取后立即清除。
     */
    public static boolean consumeSilencedTeleportFeedback() {
        long until = silenceUntil;
        silenceUntil = 0L;
        return until != 0L && System.currentTimeMillis() <= until;
    }

    private static Component button(int id, boolean allow) {
        String label = allow
                ? "message.endbound_ember.time_turbulence.allow"
                : "message.endbound_ember.time_turbulence.deny";
        String hover = allow
                ? "message.endbound_ember.time_turbulence.allow.hover"
                : "message.endbound_ember.time_turbulence.deny.hover";
        String command = "/endbound_ember turbulence " + (allow ? "accept " : "deny ") + id;
        return Component.translatable(label)
                .withStyle(style -> style
                        .withColor(allow ? ChatFormatting.GREEN : ChatFormatting.RED)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable(hover))));
    }
}
