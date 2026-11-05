package com.emberstar.endbounember.block;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 混合竖半砖：两片不同材质的竖半砖合并成的完整方块。
 * 两侧各自的方块状态存放在 {@link MixedVerticalSlabBlockEntity} 中，
 * 渲染由 {@code MixedVerticalSlabRenderer} 按两片独立绘制。
 * 该方块不可直接获取，只能通过两片异类竖半砖合并产生。
 */
public class MixedVerticalSlabBlock extends BaseEntityBlock {
    public static final MapCodec<MixedVerticalSlabBlock> CODEC = simpleCodec(MixedVerticalSlabBlock::new);

    public MixedVerticalSlabBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    /** 模型不直接渲染，交给方块实体渲染器绘制两片半砖 */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MixedVerticalSlabBlockEntity(pos, state);
    }

    /** 破坏时分别掉落两侧各自的竖半砖 */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof MixedVerticalSlabBlockEntity mixed) {
            List<ItemStack> drops = new ArrayList<>(2);
            drops.add(new ItemStack(mixed.getPrimary().getBlock()));
            drops.add(new ItemStack(mixed.getSecondary().getBlock()));
            return drops;
        }
        return super.getDrops(state, params);
    }

    /** 鼠标中键选取先放入的那片半砖 */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos,
                                       Player player) {
        if (level.getBlockEntity(pos) instanceof MixedVerticalSlabBlockEntity mixed) {
            return new ItemStack(mixed.getPrimary().getBlock());
        }
        return super.getCloneItemStack(state, target, level, pos, player);
    }
}
