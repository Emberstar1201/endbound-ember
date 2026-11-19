package com.emberstar.endbounember.world;

import com.emberstar.endbounember.EndboundEmber;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, EndboundEmber.MODID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, EndboundEmber.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<FixedTemplateStructure>> CAMP =
            STRUCTURE_TYPES.register("zombie_girl_camp", () -> () -> FixedTemplateStructure.CODEC);
    public static final DeferredHolder<StructureType<?>, StructureType<FixedTemplateStructure>> CEMETERY =
            STRUCTURE_TYPES.register("cemetery", () -> () -> FixedTemplateStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> FIXED_TEMPLATE_PIECE =
            STRUCTURE_PIECES.register("fixed_template", () ->
                    (StructurePieceType.StructureTemplateType) FixedTemplateStructurePiece::load);

    private ModStructures() {}
}
