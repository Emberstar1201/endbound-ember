package com.emberstar.endbounember.block;

import com.emberstar.endbounember.EndboundEmber;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 亡者之柱方块：外形是一根三格高的黑曜石柱，无法被挖掘破坏，
 * 只能通过左键攻击削减柱子血量（见 {@link DoomPillarBlockEntity}）。
 */
public class DoomPillarBlock extends BaseEntityBlock {
    public static final MapCodec<DoomPillarBlock> CODEC = simpleCodec(DoomPillarBlock::new);
    /** 碰撞箱只占一格，模型本身向上延伸到三格，避免影响走位与寻路 */
    private static final VoxelShape SHAPE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 16.0D, 12.0D);

    public DoomPillarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DoomPillarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, EndboundEmber.DOOM_PILLAR_BLOCK_ENTITY.get(),
                DoomPillarBlockEntity::serverTick);
    }

    /** 左键攻击柱子：伤害走柱子自己的血量，而不是原版挖掘 */
    @EventBusSubscriber(modid = EndboundEmber.MODID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
            Level level = event.getLevel();
            if (!level.getBlockState(event.getPos()).is(EndboundEmber.DOOM_PILLAR.get())) {
                return;
            }
            // 客户端照常发包，交给服务端统一结算，避免两边重复扣血
            if (level.isClientSide) {
                return;
            }
            event.setCanceled(true);
            if (level.getBlockEntity(event.getPos()) instanceof DoomPillarBlockEntity pillar) {
                pillar.hit(event.getEntity());
            }
        }

        @SubscribeEvent
        public static void onBreak(BlockEvent.BreakEvent event) {
            if (event.getState().is(EndboundEmber.DOOM_PILLAR.get())) {
                event.setCanceled(true);
            }
        }
    }
}
