package com.emberstar.endbounember.world;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.village.VillageSiegeEvent;

/**
 * 僵尸围城的精确胜利判定。
 *
 * <p>原版 VillageSiege 每晚最多只安排一场围城，每次固定生成 20 只僵尸。
 * 本类只在 {@link VillageSiegeEvent} 触发的这一次围城里登记实际生成成功的僵尸，
 * 并且只把由玩家击杀的僵尸计入战绩；20 只全部生成、全部由玩家击杀时才发放永久折扣。
 * 只要有一只死于非玩家之手（例如铁傀儡、阳光或坠落），本次围城就作废。</p>
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class ZombieSiegeTracker {
    /** 原版一次围城的目标生成数量。 */
    public static final int SIEGE_ZOMBIE_COUNT = 20;
    /** 超过 2 个游戏日仍未打完的围城直接作废，避免旧状态残留。 */
    private static final long SESSION_TIMEOUT_TICKS = 48000L;

    private static final Map<ResourceKey<Level>, Session> SESSIONS = new HashMap<>();

    private ZombieSiegeTracker() {}

    /** 围城真正开始安排时建立本次会话；被其他模组取消的围城不会开始。 */
    @SubscribeEvent
    public static void onSiegeSetup(VillageSiegeEvent event) {
        if (!Config.BETTER_VILLAGER_TRADING.get()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        SESSIONS.put(level.dimension(), new Session(level, event.getPlayer().blockPosition(), level.getGameTime()));
    }

    /** 由 VillageSiege 生成路径回调，只登记真正成功加入世界的围城僵尸。 */
    public static void registerSiegeZombie(ServerLevel level, Zombie zombie) {
        if (!Config.BETTER_VILLAGER_TRADING.get()) return;
        Session session = SESSIONS.get(level.dimension());
        if (session == null || session.level != level
                || level.getGameTime() - session.startedAt > SESSION_TIMEOUT_TICKS) {
            session = new Session(level, zombie.blockPosition(), level.getGameTime());
            SESSIONS.put(level.dimension(), session);
        }
        if (session.tracked >= SIEGE_ZOMBIE_COUNT) return;
        if (session.alive.add(zombie.getUUID())) {
            session.tracked++;
        }
    }

    /** 只有本次围城登记过的僵尸才计入，且击杀者必须是玩家本人（含玩家的弹射物）。 */
    @SubscribeEvent
    public static void onSiegeZombieDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Zombie zombie)) return;
        if (!(zombie.level() instanceof ServerLevel level) || level.isClientSide()) return;
        Session session = SESSIONS.get(level.dimension());
        if (session == null || session.level != level || !session.alive.contains(zombie.getUUID())) return;

        session.alive.remove(zombie.getUUID());
        if (!(event.getSource().getEntity() instanceof Player)) {
            // 非玩家击杀：本次围城无法再达成「全部由玩家击杀」
            session.broken = true;
            SESSIONS.remove(level.dimension());
            return;
        }
        tryAward(session);
    }

    /** 定期清理超时会话；关闭配置时一并清空。 */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) return;
        if (!Config.BETTER_VILLAGER_TRADING.get()) {
            SESSIONS.clear();
            return;
        }
        Iterator<Session> iterator = SESSIONS.values().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            if (session.level.getGameTime() - session.startedAt > SESSION_TIMEOUT_TICKS) {
                iterator.remove();
            }
        }
    }

    private static void tryAward(Session session) {
        if (session.broken
                || session.tracked < SIEGE_ZOMBIE_COUNT
                || !session.alive.isEmpty()) return;
        BetterVillagerTrading.awardVillageDiscount(session.level, session.center);
        SESSIONS.remove(session.level.dimension());
    }

    private static final class Session {
        final ServerLevel level;
        final BlockPos center;
        final long startedAt;
        final Set<UUID> alive = new HashSet<>();
        int tracked;
        boolean broken;

        Session(ServerLevel level, BlockPos center, long startedAt) {
            this.level = level;
            this.center = center;
            this.startedAt = startedAt;
        }
    }
}
