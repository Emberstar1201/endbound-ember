package com.emberstar.endbounember.world;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 移动光源：手持火把、荧石这类会发光的方块时，脚下会自动放一个隐形光源方块，
 * 走开后再把它收回来，效果等同于随身带一盏灯。
 * <p>用的是真实的隐形光源方块，所以服务端和所有客户端看到的光照完全一致。</p>
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class MovingLight {
    /** 每个玩家当前放置的光源位置，用于移动时回收 */
    private static final Map<UUID, Placement> PLACEMENTS = new HashMap<>();

    private MovingLight() {
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!Config.MOVING_LIGHT_ENABLED.get() || player.isSpectator()) {
            clear(player);
            return;
        }
        int light = Math.max(lightOf(player.getMainHandItem()), lightOf(player.getOffhandItem()));
        if (light <= 0) {
            clear(player);
            return;
        }
        ServerLevel level = player.serverLevel();
        Placement current = PLACEMENTS.get(player.getUUID());
        BlockPos ownPos = current != null && current.level() == level ? current.pos() : null;
        BlockPos target = findTarget(level, player, ownPos);
        if (target == null) {
            clear(player);
            return;
        }
        if (current != null && current.level() == level && current.pos().equals(target)
                && current.light() == light && level.getBlockState(target).is(Blocks.LIGHT)) {
            return;
        }
        clear(player);
        level.setBlock(target, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, light), 3);
        PLACEMENTS.put(player.getUUID(), new Placement(level, target, light));
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // 玩家退场时把光源收干净，避免世界里的隐形光源越堆越多
        if (event.getEntity() instanceof ServerPlayer player) {
            clear(player);
        }
    }

    /** 优先放在脚下，脚下被占住（例如站在高草里）时改放头部高度。 */
    private static BlockPos findTarget(ServerLevel level, ServerPlayer player, BlockPos ownPos) {
        BlockPos feet = player.blockPosition();
        if (isFree(level, feet, ownPos)) return feet;
        BlockPos head = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        if (!head.equals(feet) && isFree(level, head, ownPos)) return head;
        return null;
    }

    /** 空气可以放；原本就是自己上次放的光源方块也可以复用，避免每 tick 来回搬。 */
    private static boolean isFree(ServerLevel level, BlockPos pos, BlockPos ownPos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || (pos.equals(ownPos) && state.is(Blocks.LIGHT));
    }

    /** 手持物品的发光等级：方块物品取方块本身的亮度，其它物品视为不发光。 */
    private static int lightOf(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return 0;
        return blockItem.getBlock().defaultBlockState().getLightEmission();
    }

    private static void clear(ServerPlayer player) {
        Placement placement = PLACEMENTS.remove(player.getUUID());
        if (placement == null) return;
        // 只回收自己放下的光源，玩家在那里另放了方块就不动
        if (placement.level().getBlockState(placement.pos()).is(Blocks.LIGHT)) {
            placement.level().setBlock(placement.pos(), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private record Placement(ServerLevel level, BlockPos pos, int light) {
    }
}
