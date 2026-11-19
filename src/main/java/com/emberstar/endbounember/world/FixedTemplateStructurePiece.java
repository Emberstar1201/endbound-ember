package com.emberstar.endbounember.world;

import com.emberstar.endbounember.EndboundEmber;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.block.Mirror;

public final class FixedTemplateStructurePiece extends TemplateStructurePiece {

    public FixedTemplateStructurePiece(StructureTemplateManager manager, ResourceLocation template, BlockPos origin,
                                       boolean ignoreAir) {
        super(ModStructures.FIXED_TEMPLATE_PIECE.get(), 0, manager, template, template.toString(),
                makeSettings(ignoreAir), origin);
    }

    private FixedTemplateStructurePiece(StructureTemplateManager manager, CompoundTag tag) {
        super(ModStructures.FIXED_TEMPLATE_PIECE.get(), tag, manager,
                ignored -> makeSettings(tag.getBoolean("IgnoreAir")));
    }

    public static FixedTemplateStructurePiece load(StructureTemplateManager manager, CompoundTag tag) {
        return new FixedTemplateStructurePiece(manager, tag);
    }

    private static final ResourceLocation CAMP_TEMPLATE = ResourceLocation.fromNamespaceAndPath(
            EndboundEmber.MODID, "zombie_girl_camp");

    private static StructurePlaceSettings makeSettings(boolean ignoreAir) {
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(false)
                .setKnownShape(true)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        if (ignoreAir) settings.addProcessor(BlockIgnoreProcessor.AIR);
        return settings;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putBoolean("IgnoreAir", this.placeSettings.getProcessors().contains(BlockIgnoreProcessor.AIR));
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level,
                                   RandomSource random, BoundingBox box) {
        // 当前两个模板没有需要额外实体或方块处理的数据标记；覆盖此钩子以完成TemplateStructurePiece契约。
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        super.postProcess(level, structureManager, generator, random, box, chunkPos, pos);
        if (!CAMP_TEMPLATE.equals(this.makeTemplateLocation())) return;
        for (StructureTemplate.StructureBlockInfo info
                : this.template.filterBlocks(this.templatePosition, this.placeSettings, Blocks.STRUCTURE_BLOCK)) {
            if (box.isInside(info.pos()) && level.getBlockState(info.pos()).isAir()) {
                level.setBlock(info.pos(), Blocks.GRASS_BLOCK.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    public BlockPos templateOrigin() {
        return this.templatePosition;
    }

}
