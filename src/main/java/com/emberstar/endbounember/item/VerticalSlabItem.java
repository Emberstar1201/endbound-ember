package com.emberstar.endbounember.item;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.block.MixedVerticalSlabBlockEntity;
import com.emberstar.endbounember.block.VerticalSlabBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * 竖半砖物品：在常规放置逻辑（见 {@link BlockItem}）之前，
 * 接管「点击的格子已是另一种材质的单片竖半砖」的情况，
 * 将其合并为混合竖半砖（{@code MIXED_VERTICAL_SLAB}），两片材质存入方块实体。
 */
public class VerticalSlabItem extends BlockItem {
    public VerticalSlabItem(VerticalSlabBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        BlockState existing = level.getBlockState(pos);
        if (existing.getBlock() instanceof VerticalSlabBlock
                && !existing.getValue(VerticalSlabBlock.DOUBLE)
                && !existing.is(getBlock())) {
            // 新放入的一片占据已有半砖的互补半边
            BlockState secondary = getBlock().defaultBlockState()
                    .setValue(VerticalSlabBlock.FACING,
                            existing.getValue(VerticalSlabBlock.FACING).getOpposite())
                    .setValue(VerticalSlabBlock.DOUBLE, false)
                    .setValue(VerticalSlabBlock.WATERLOGGED, existing.getValue(VerticalSlabBlock.WATERLOGGED));
            BlockState mixed = EndboundEmber.MIXED_VERTICAL_SLAB.get().defaultBlockState();
            level.setBlock(pos, mixed, 11);
            if (level.getBlockEntity(pos) instanceof MixedVerticalSlabBlockEntity entity) {
                entity.setSlabs(existing, secondary);
            }
            if (!level.isClientSide) {
                Player player = context.getPlayer();
                level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, mixed));
                SoundType soundType = getBlock().getSoundType(secondary, level, pos, player);
                level.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS,
                        (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
                ItemStack stack = context.getItemInHand();
                if (player == null || !player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.place(context);
    }
}
