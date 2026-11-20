package com.emberstar.endbounember.world;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 服务端记录的风踏涟漪开关状态。
 * <p>客户端自己也维护同一份开关结果：玩家自身位置由客户端权威，
 * 服务端无法通过 setPos 把玩家抬到水面，贴水必须在客户端执行。</p>
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class WindRippleToggle {
    private static final Map<UUID, Boolean> STATES = new ConcurrentHashMap<>();

    private WindRippleToggle() {
    }

    public static boolean isEnabled(Player player) {
        return STATES.getOrDefault(player.getUUID(), false);
    }

    /** 直接写入客户端上报的目标状态，避免连续按键时两端状态错位。 */
    public static void set(ServerPlayer player, boolean enabled) {
        if (enabled) {
            STATES.put(player.getUUID(), true);
        } else {
            STATES.remove(player.getUUID());
        }
    }

    public static void clear(ServerPlayer player) {
        STATES.remove(player.getUUID());
    }

    /** 离开服务器时清理，避免同一 UUID 再次进入时残留旧状态。 */
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }
}
