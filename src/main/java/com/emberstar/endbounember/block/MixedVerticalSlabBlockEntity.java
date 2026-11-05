package com.emberstar.endbounember.block;

import com.emberstar.endbounember.EndboundEmber;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 混合竖半砖的方块实体：保存两侧各自的竖半砖方块状态，
 * primary 为先放置的一片，secondary 为后并入的一片。
 */
public class MixedVerticalSlabBlockEntity extends BlockEntity {
    private BlockState primary = Blocks.STONE.defaultBlockState();
    private BlockState secondary = Blocks.STONE.defaultBlockState();

    public MixedVerticalSlabBlockEntity(BlockPos pos, BlockState state) {
        super(EndboundEmber.MIXED_VERTICAL_SLAB_BLOCK_ENTITY.get(), pos, state);
    }

    public BlockState getPrimary() {
        return primary;
    }

    public BlockState getSecondary() {
        return secondary;
    }

    /** 写入两片半砖状态并同步到客户端渲染 */
    public void setSlabs(BlockState primary, BlockState secondary) {
        this.primary = primary;
        this.secondary = secondary;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("primary", NbtUtils.writeBlockState(primary));
        tag.put("secondary", NbtUtils.writeBlockState(secondary));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        HolderGetter<Block> blockGetter = registries.lookupOrThrow(Registries.BLOCK);
        if (tag.contains("primary", Tag.TAG_COMPOUND)) {
            primary = NbtUtils.readBlockState(blockGetter, tag.getCompound("primary"));
        }
        if (tag.contains("secondary", Tag.TAG_COMPOUND)) {
            secondary = NbtUtils.readBlockState(blockGetter, tag.getCompound("secondary"));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
