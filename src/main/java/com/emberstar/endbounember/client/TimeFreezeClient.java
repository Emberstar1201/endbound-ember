package com.emberstar.endbounember.client;

import java.util.ArrayList;
import java.util.List;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.effect.ModEffects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 客户端镜像的时间暂停范围。
 *
 * <p>暂停判定在服务端完成，客户端如果照常推进实体与粒子，怪物会在原地继续走动、
 * 弹射物会自行飞出，解除暂停后再被服务端位置拽回来，出现瞬移。
 * 这里按玩家身上的暂停标记重建同一片范围，让客户端也把范围内的实体和粒子停住。</p>
 */
@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public final class TimeFreezeClient {

    /** 当前所有正在暂停的玩家所在区块 */
    private static List<ChunkPos> centers = List.of();

    private TimeFreezeClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            centers = List.of();
            return;
        }
        List<ChunkPos> found = new ArrayList<>();
        for (Player player : level.players()) {
            if (player.hasEffect(ModEffects.TIME_STOP)) {
                found.add(player.chunkPosition());
            }
        }
        centers = found.isEmpty() ? List.of() : List.copyOf(found);
    }

    /** 与服务端保持一致：玩家、掉落物、经验球与载人载具不受影响 */
    public static boolean isEntityFrozen(Entity entity) {
        if (entity instanceof Player) return false;
        if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) return false;
        if (entity.isVehicle() && entity.getFirstPassenger() instanceof Player) return false;
        return isFrozen(entity.blockPosition());
    }

    public static boolean isFrozen(BlockPos pos) {
        return withinArea(pos.getX(), pos.getZ());
    }

    public static boolean isFrozen(Vec3 pos) {
        return withinArea((int) Math.floor(pos.x), (int) Math.floor(pos.z));
    }

    private static boolean withinArea(int blockX, int blockZ) {
        if (centers.isEmpty()) return false;
        int radius = Config.TIME_HALO_SKILL_CHUNK_RADIUS.getAsInt();
        int cx = SectionPos.blockToSectionCoord(blockX);
        int cz = SectionPos.blockToSectionCoord(blockZ);
        for (ChunkPos center : centers) {
            if (Math.abs(center.x - cx) <= radius && Math.abs(center.z - cz) <= radius) {
                return true;
            }
        }
        return false;
    }
}
