package com.emberstar.endbounember.world;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 时间乱流维度的规则：石英地面不可破坏、不可被爆炸摧毁、不自然生成生物；
 * 佩戴时间光环时按 I 键进出该维度（见 {@code TimeTurbulenceKeyClient}）。
 * <p>没有光环的玩家在维度之间穿梭时，有小概率被缝隙卷进乱流；进入后可按 I 键
 * 打开维度轮盘选择离开。</p>
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class TimeTurbulenceEvents {
    /** 无光环穿越维度时被卷进乱流的概率 */
    private static final float WILD_ENTRY_CHANCE = 0.02F;
    /** 保底次数：连续这么多次穿越都没抽中，下一次必定被卷进乱流 */
    private static final int WILD_ENTRY_PITY_ATTEMPTS = 5;
    /** 记录连续未抽中的穿越次数 */
    private static final String WILD_ENTRY_PITY_KEY = "endbound_ember:time_turbulence_entry_attempts";

    private TimeTurbulenceEvents() {
    }

    /** 禁止挖掘乱流维度生成的那层石英地板（玩家自己摆的石英照常可拆） */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (TimeTurbulenceLevels.isTimeTurbulence(event.getPlayer().level())
                && isGeneratedFloor(event.getPos(), event.getState())) {
            event.setCanceled(true);
        }
    }

    /** 爆炸波及的方块里剔除生成的地板，避免地板被炸掉 */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!TimeTurbulenceLevels.isTimeTurbulence(event.getLevel())) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> isGeneratedFloor(
                pos, event.getLevel().getBlockState(pos)));
    }

    /** 是否属于维度生成的地板：只有地板及其下层受保护，玩家另摆的石英不受影响 */
    private static boolean isGeneratedFloor(BlockPos pos, BlockState state) {
        return pos.getY() <= TimeTurbulenceLevels.GROUND_Y && state.is(Blocks.QUARTZ_BLOCK);
    }

    /**
     * 禁止乱流维度里的自然生成与区块生成刷怪。
     * <p>刷怪蛋、指令召唤、刷怪笼等主动生成不受影响，留给玩家自己安排。
     */
    @SubscribeEvent
    public static void onMobSpawnPosition(MobSpawnEvent.PositionCheck event) {
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION) {
            return;
        }
        if (event.getLevel() instanceof ServerLevel level && TimeTurbulenceLevels.isTimeTurbulence(level)) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    /** 定期清理超时的进入申请，避免请求一直挂着 */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        int tick = event.getServer().getTickCount();
        if (tick % 20 == 0) {
            TimeTurbulenceAccess.purgeExpired(event.getServer());
        }
        // 每 5 秒记一次维度主人在自己乱流里的位置，覆盖登出、死亡、被传送等意外离场
        if (tick % 100 == 0) {
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                TimeTurbulenceLevels.saveOwnPosition(player);
            }
        }
    }

    /**
     * 没有时光光环的玩家在主世界↔下界、主世界↔末地之间穿梭时，有小概率被维度缝隙卷进乱流。
     * <p>刚离开乱流的那次传送不参与判定，否则按 I 键出去后可能立刻被重新吸回来。</p>
     */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // 目的地本来就是乱流（自己按 I 键进入，或刚被卷进来）：不重复判定
        if (TimeTurbulenceLevels.isTimeTurbulence(player.level())) {
            return;
        }
        // 起点是乱流：这是离开动作，不能再把人吸回去
        if (TimeTurbulenceLevels.ownerOf(event.getFrom()).isPresent()) {
            return;
        }
        if (player.isSpectator() || TimeTurbulenceLevels.wearsTimeHalo(player)) {
            return;
        }
        if (player.getRandom().nextFloat() >= WILD_ENTRY_CHANCE) {
            // 连续穿越多次仍未抽中时触发保底，保证迟早能被卷进乱流
            CompoundTag data = player.getPersistentData();
            int attempts = data.getInt(WILD_ENTRY_PITY_KEY) + 1;
            if (attempts < WILD_ENTRY_PITY_ATTEMPTS) {
                data.putInt(WILD_ENTRY_PITY_KEY, attempts);
                return;
            }
            data.remove(WILD_ENTRY_PITY_KEY);
        } else {
            player.getPersistentData().remove(WILD_ENTRY_PITY_KEY);
        }
        TimeTurbulenceLevels.enter(player);
        player.sendSystemMessage(Component.translatable("endbound_ember.time_turbulence.dragged_in"));
    }
}
