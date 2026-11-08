package com.emberstar.endbounember.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 无缝玻璃板：连接逻辑完全沿用 {@link IronBarsBlock}（四面可连接、可含水），
 * 无缝效果由资源侧的自定义模型实现——连接处不再绘制每个方块边界的竖线。
 * <p>
 * 这里只补充遮光玻璃板需要的光照行为：不透过天光、光衰为最大光照等级。
 */
public class SeamlessGlassPaneBlock extends IronBarsBlock {
    public static final MapCodec<SeamlessGlassPaneBlock> CODEC = simpleCodec(
            properties -> new SeamlessGlassPaneBlock(properties, false));

    private final boolean tinted;

    public SeamlessGlassPaneBlock(BlockBehaviour.Properties properties, boolean tinted) {
        super(properties);
        this.tinted = tinted;
    }

    @Override
    public MapCodec<? extends IronBarsBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return !this.tinted;
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return this.tinted ? level.getMaxLightLevel() : super.getLightBlock(state, level, pos);
    }
}
