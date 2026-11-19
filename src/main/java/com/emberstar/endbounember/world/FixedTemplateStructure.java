package com.emberstar.endbounember.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.common.Tags;

import java.util.Optional;

public final class FixedTemplateStructure extends Structure {
    public static final MapCodec<FixedTemplateStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Structure.settingsCodec(instance),
            ResourceLocation.CODEC.fieldOf("template").forGetter(structure -> structure.template),
            Codec.INT.fieldOf("y_offset").forGetter(structure -> structure.yOffset),
            Codec.INT.optionalFieldOf("minimum_surface_y", Integer.MIN_VALUE).forGetter(structure -> structure.minimumSurfaceY),
            Codec.INT.optionalFieldOf("maximum_corner_height_difference", -1).forGetter(structure -> structure.maximumCornerHeightDifference),
            Codec.BOOL.optionalFieldOf("forest_or_plains", false).forGetter(structure -> structure.forestOrPlains),
            Codec.BOOL.optionalFieldOf("overworld_surface", false).forGetter(structure -> structure.overworldSurface)
    ).apply(instance, FixedTemplateStructure::new));

    private final ResourceLocation template;
    private final int yOffset;
    private final int minimumSurfaceY;
    private final int maximumCornerHeightDifference;
    private final boolean forestOrPlains;
    private final boolean overworldSurface;

    private FixedTemplateStructure(StructureSettings settings, ResourceLocation template, int yOffset,
                                   int minimumSurfaceY, int maximumCornerHeightDifference,
                                   boolean forestOrPlains, boolean overworldSurface) {
        super(settings);
        this.template = template;
        this.yOffset = yOffset;
        this.minimumSurfaceY = minimumSurfaceY;
        this.maximumCornerHeightDifference = maximumCornerHeightDifference;
        this.forestOrPlains = forestOrPlains;
        this.overworldSurface = overworldSurface;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int centerX = chunk.getMiddleBlockX();
        int centerZ = chunk.getMiddleBlockZ();
        ChunkGenerator generator = context.chunkGenerator();
        LevelHeightAccessor height = context.heightAccessor();
        int surfaceY = generator.getFirstFreeHeight(centerX, centerZ, Heightmap.Types.WORLD_SURFACE_WG,
                height, context.randomState());
        if (surfaceY < this.minimumSurfaceY || surfaceY <= height.getMinBuildHeight()
                || surfaceY >= height.getMaxBuildHeight()) return Optional.empty();
        if (generator.getBaseColumn(centerX, centerZ, height, context.randomState()).getBlock(surfaceY - 1)
                .getFluidState().isSource()) return Optional.empty();

        Holder<Biome> centerBiome = context.biomeSource().getNoiseBiome(QuartPos.fromBlock(centerX),
                QuartPos.fromBlock(surfaceY - 1), QuartPos.fromBlock(centerZ), context.randomState().sampler());
        if (!validBiome(centerBiome)) return Optional.empty();

        var templateSize = context.structureTemplateManager().get(this.template)
                .map(StructureTemplate::getSize).orElse(null);
        if (templateSize == null || templateSize.getX() <= 0
                || templateSize.getY() <= 0 || templateSize.getZ() <= 0) return Optional.empty();
        int originY = surfaceY + this.yOffset;
        if (originY < height.getMinBuildHeight()
                || originY + templateSize.getY() > height.getMaxBuildHeight()) return Optional.empty();
        if (this.maximumCornerHeightDifference >= 0) {
            for (int dx = 0; dx < templateSize.getX(); dx += 8) {
                for (int dz = 0; dz < templateSize.getZ(); dz += 8) {
                    int x = centerX - templateSize.getX() / 2 + dx;
                    int z = centerZ - templateSize.getZ() / 2 + dz;
                    int cornerY = generator.getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                            height, context.randomState());
                    if (cornerY <= height.getMinBuildHeight()
                            || Math.abs(cornerY - surfaceY) > this.maximumCornerHeightDifference) return Optional.empty();
                    Holder<Biome> biome = context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x),
                            QuartPos.fromBlock(cornerY - 1), QuartPos.fromBlock(z), context.randomState().sampler());
                    if (!validBiome(biome)) return Optional.empty();
                    if (generator.getBaseColumn(x, z, height, context.randomState()).getBlock(cornerY - 1)
                            .getFluidState().isSource()) return Optional.empty();
                }
            }
        }

        BlockPos origin = new BlockPos(centerX - templateSize.getX() / 2, originY,
                centerZ - templateSize.getZ() / 2);
        return Optional.of(new GenerationStub(new BlockPos(centerX, surfaceY, centerZ),
                pieces -> pieces.addPiece(new FixedTemplateStructurePiece(context.structureTemplateManager(),
                        this.template, origin, this.template.getPath().equals("cemetery")))));
    }

    private boolean validBiome(Holder<Biome> biome) {
        if (this.forestOrPlains) return biome.is(BiomeTags.IS_FOREST) || biome.is(Tags.Biomes.IS_PLAINS);
        if (this.overworldSurface) {
            return biome.is(BiomeTags.IS_OVERWORLD) && !biome.is(BiomeTags.IS_OCEAN)
                    && !biome.is(BiomeTags.IS_RIVER) && !biome.is(BiomeTags.IS_MOUNTAIN);
        }
        return true;
    }

    @Override
    public StructureType<?> type() {
        return this.template.getPath().equals("cemetery") ? ModStructures.CEMETERY.get() : ModStructures.CAMP.get();
    }
}
