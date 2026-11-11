package com.emberstar.endbounember.item;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

/** 炒面：对盛水的炼药锅右键，消耗炼药锅中一层水并换取一份湿炒面。 */
public class DryFriedFlourItem extends Item {
    public DryFriedFlourItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (player == null || !state.is(Blocks.WATER_CAULDRON)) {
            return super.useOn(context);
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack held = context.getItemInHand();
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }

        int waterLevel = state.getValue(LayeredCauldronBlock.LEVEL);
        level.setBlockAndUpdate(pos, waterLevel > 1
                ? state.setValue(LayeredCauldronBlock.LEVEL, waterLevel - 1)
                : Blocks.CAULDRON.defaultBlockState());

        ItemStack wetFlour = new ItemStack(EndboundEmber.WET_FRIED_FLOUR.get());
        if (!player.getInventory().add(wetFlour)) {
            player.drop(wetFlour, false);
        }
        level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.6F, 1.0F);
        return InteractionResult.SUCCESS;
    }
}
