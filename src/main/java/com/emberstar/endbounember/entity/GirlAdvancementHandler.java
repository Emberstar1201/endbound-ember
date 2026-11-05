package com.emberstar.endbounember.entity;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.world.ModAdvancements;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 少女系列成就（冒险页）的条件检测，每秒（20 tick）对每名在线玩家执行一次：
 * <ul>
 *   <li>「友善的亡灵？」：24 格内出现丧尸娘 / 溺尸娘（幸存者少女是人类，不算）；</li>
 *   <li>「令人心疼的少女」：24 格内有自己驯服的丧尸娘 / 溺尸娘；</li>
 *   <li>「美梦重现」：玩家入睡期间，16 格内有正在睡觉的幸存者少女。</li>
 * </ul>
 * 「驶向第2次生命」不走轮询，在生命之星治愈成功时直接发放。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class GirlAdvancementHandler {
    /** 发现 / 驯服成就的检测半径（格）。 */
    private static final double FIND_RANGE = 24.0D;
    /** 共眠成就的检测半径（格）。 */
    private static final double SLEEP_RANGE = 16.0D;

    private GirlAdvancementHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                if (player.tickCount % 20 != 0) continue;
                checkGirls(player, level);
                checkSweetDreams(player, level);
            }
        }
    }

    private static void checkGirls(ServerPlayer player, ServerLevel level) {
        boolean needFound = !ModAdvancements.hasDone(player, ModAdvancements.FRIENDLY_UNDEAD);
        boolean needTamed = !ModAdvancements.hasDone(player, ModAdvancements.TAME_GIRL);
        if (!needFound && !needTamed) return;

        AABB box = player.getBoundingBox().inflate(FIND_RANGE);
        for (ZombieGirl girl : level.getEntitiesOfClass(ZombieGirl.class, box)) {
            // 幸存者少女是人类形态，不触发「亡灵」发现成就
            if (girl instanceof HumanGirl) continue;
            if (needFound) {
                ModAdvancements.award(player, ModAdvancements.FRIENDLY_UNDEAD);
                needFound = false;
            }
            if (needTamed && girl.isOwnedBy(player)) {
                ModAdvancements.award(player, ModAdvancements.TAME_GIRL);
                needTamed = false;
            }
            if (!needFound && !needTamed) return;
        }
    }

    private static void checkSweetDreams(ServerPlayer player, ServerLevel level) {
        if (!player.isSleeping() || ModAdvancements.hasDone(player, ModAdvancements.SWEET_DREAMS)) return;
        boolean sleepingTogether = !level.getEntitiesOfClass(HumanGirl.class,
                player.getBoundingBox().inflate(SLEEP_RANGE), LivingEntity::isSleeping).isEmpty();
        if (sleepingTogether) {
            ModAdvancements.award(player, ModAdvancements.SWEET_DREAMS);
        }
    }
}
