package com.emberstar.endbounember.world;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.effect.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.packs.resources.Resource;

@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class CemeteryEvents {
    private static final ResourceLocation STRUCTURE = ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "cemetery");
    private static final ResourceLocation RESOURCE = ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "structure/cemetery.nbt");
    private static final String EFFECT_TAG = "EndboundEmberCemeteryEffect";
    private static final String DWELL_TAG = "EndboundEmberCemeteryDwell";
    /** 需要在墓园内安静停留的时间：60 秒。 */
    private static final int REQUIRED_DWELL_TICKS = 20 * 60;

    private CemeteryEvents() {}

    public static String validateTemplate(ServerLevel level) {
        StructureTemplate template = loadTemplate(level);
        if (template == null) return "模板未找到或无法读取";
        Vec3i size = template.getSize();
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .addProcessor(BlockIgnoreProcessor.AIR)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        int candles = template.filterBlocks(BlockPos.ZERO, settings, Blocks.CANDLE).size();
        int barrels = template.filterBlocks(BlockPos.ZERO, settings, Blocks.BARREL).size();
        if (size.getX() != 51 || size.getY() != 9 || size.getZ() != 51 || candles != 124 || barrels != 248) {
            return "结构数据异常：尺寸=" + size.getX() + "×" + size.getY() + "×" + size.getZ()
                    + "，蜡烛=" + candles + "，木桶=" + barrels;
        }
        return "结构正常：尺寸=" + size.getX() + "×" + size.getY() + "×" + size.getZ()
                + "，蜡烛=" + candles + "，木桶=" + barrels + "，结构实际局部Y坐标=0..8；生成锚点额外下移3格";
    }

    public static boolean placeAt(ServerLevel level, BlockPos target) {
        StructureTemplate template = loadTemplate(level);
        if (template == null) return false;
        Vec3i size = template.getSize();
        if (size.getX() != 51 || size.getY() != 9 || size.getZ() != 51) return false;
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, target.getX(), target.getZ());
        BlockPos origin = new BlockPos(target.getX() - size.getX() / 2, surfaceY - 3,
                target.getZ() - size.getZ() / 2);
        StructurePlaceSettings settings = new StructurePlaceSettings().setKnownShape(true)
                .addProcessor(BlockIgnoreProcessor.AIR).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        if (!template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2)) return false;
        List<BlockPos> candles = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, settings, Blocks.CANDLE)) {
            candles.add(origin.offset(info.pos()).immutable());
        }
        CemeterySavedData data = CemeterySavedData.get(level);
        data.add(new CemeterySavedData.Cemetery(level.dimension(), target, origin, false, false, candles));
        fillContainers(level, origin, size);
        int index = data.cemeteries().size() - 1;
        data.cemeteries().set(index, data.cemeteries().get(index).withLootInitialized());
        data.changed();
        return true;
    }

    /** 自然结构收尾：按原点去重，沿用旧存档的 LootInitialized / Awarded 状态。 */
    static void finishNatural(ServerLevel level, ChunkPos chunk, FixedTemplateStructurePiece piece) {
        BlockPos origin = piece.templateOrigin();
        StructureTemplate template = piece.template();
        Vec3i size = template.getSize();
        CemeterySavedData data = CemeterySavedData.get(level);
        CemeterySavedData.Cemetery existing = data.cemeteries().stream()
                .filter(c -> c.dimension().equals(level.dimension()) && c.origin().equals(origin))
                .findFirst().orElse(null);
        if (existing != null && existing.lootInitialized()) return;
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .addProcessor(BlockIgnoreProcessor.AIR).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        List<BlockPos> candles = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, settings, Blocks.CANDLE)) {
            candles.add(origin.offset(info.pos()).immutable());
        }
        CemeterySavedData.Cemetery cemetery = new CemeterySavedData.Cemetery(level.dimension(),
                existing == null ? new BlockPos(chunk.getMiddleBlockX(), origin.getY() + 3,
                        chunk.getMiddleBlockZ()) : existing.village(), origin, false,
                existing != null && existing.awarded(), candles);
        int index;
        if (existing == null) {
            data.add(cemetery);
            index = data.cemeteries().size() - 1;
        } else {
            index = data.cemeteries().indexOf(existing);
            data.cemeteries().set(index, cemetery);
            data.changed();
        }
        fillContainers(level, origin, size);
        data.cemeteries().set(index, cemetery.withLootInitialized());
        data.changed();
    }

    private static void fillContainers(ServerLevel level, BlockPos origin, Vec3i size) {
        List<net.minecraft.world.Container> containers = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
            if (level.getBlockState(pos).is(Blocks.BARREL)
                    && level.getBlockEntity(pos) instanceof net.minecraft.world.Container container) containers.add(container);
        }
        if (!containers.isEmpty()) {
            net.minecraft.world.Container treasure = containers.get(level.getRandom().nextInt(containers.size()));
            int rolls = 2 + level.getRandom().nextInt(4);
            for (int i = 0; i < rolls; i++) {
                treasure.setItem(level.getRandom().nextInt(treasure.getContainerSize()), randomTreasure(level));
            }
            net.minecraft.world.Container diaryContainer = containers.size() > 1
                    ? containers.get((containers.indexOf(treasure) + 1 + level.getRandom().nextInt(containers.size() - 1)) % containers.size())
                    : treasure;
            ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
            book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("烈士日记 / Martyr's Diary"),
                    "无名烈士 / Unknown Martyr", 0,
                    List.of(Filterable.passThrough(Component.translatable("book.endbound_ember.martyr_diary.page1")),
                            Filterable.passThrough(Component.translatable("book.endbound_ember.martyr_diary.page2"))), false));
            int slot = level.getRandom().nextInt(diaryContainer.getContainerSize());
            if (diaryContainer == treasure && !treasure.getItem(slot).isEmpty()) {
                slot = (slot + 1) % diaryContainer.getContainerSize();
            }
            diaryContainer.setItem(slot, book);
            if (treasure instanceof net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
                blockEntity.setChanged();
            }
            if (diaryContainer instanceof net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
                blockEntity.setChanged();
            }
        }
    }

    private static StructureTemplate loadTemplate(ServerLevel level) {
        StructureTemplate template = level.getStructureManager().get(STRUCTURE).orElse(null);
        if (template != null) return template;
        Optional<Resource> resource = level.getServer().getResourceManager().getResource(RESOURCE);
        if (resource.isEmpty()) {
            EndboundEmber.LOGGER.error("[墓园生成] 结构模板 {} 加载失败：ResourceManager 中不存在 {}", STRUCTURE, RESOURCE);
            return null;
        }
        try (java.io.InputStream input = resource.get().open()) {
            var tag = net.minecraft.nbt.NbtIo.readCompressed(input, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            template = new StructureTemplate();
            template.load(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), tag);
            return template;
        } catch (Exception exception) {
            EndboundEmber.LOGGER.error("[墓园生成] 手动加载结构模板失败", exception);
            return null;
        }
    }

    private static ItemStack randomTreasure(ServerLevel level) {
        return switch (level.getRandom().nextInt(8)) {
            case 0 -> new ItemStack(Items.GOLD_INGOT, 1 + level.getRandom().nextInt(4));
            case 1 -> new ItemStack(Items.IRON_INGOT, 2 + level.getRandom().nextInt(6));
            case 2 -> new ItemStack(Items.DIAMOND);
            case 3 -> new ItemStack(Items.EMERALD, 1 + level.getRandom().nextInt(3));
            case 4 -> new ItemStack(Items.GOLDEN_APPLE);
            case 5 -> new ItemStack(Items.EXPERIENCE_BOTTLE, 2 + level.getRandom().nextInt(5));
            case 6 -> new ItemStack(Items.BREAD, 2 + level.getRandom().nextInt(5));
            default -> new ItemStack(Items.ROTTEN_FLESH, 2 + level.getRandom().nextInt(6));
        };
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 != 0) return;
        CemeterySavedData.Cemetery inside = cemeteryAt(player);
        if (inside != null) {
            player.getPersistentData().putBoolean(EFFECT_TAG, true);
            player.addEffect(new MobEffectInstance(ModEffects.CALM, 40, 0, true, true));
            if (player.getFoodData().getFoodLevel() < 20) player.getFoodData().setFoodLevel(player.getFoodData().getFoodLevel() + 1);
            if (player.getFoodData().getSaturationLevel() < 20.0F) player.getFoodData().setSaturation(player.getFoodData().getSaturationLevel() + 1.0F);
            if (player.getHealth() < player.getMaxHealth()) player.heal(1.0F);
            if (!inside.awarded()) dwell(player, inside);
        } else {
            if (player.getPersistentData().getBoolean(EFFECT_TAG)) {
                player.removeEffect(ModEffects.CALM);
                player.getPersistentData().remove(EFFECT_TAG);
            }
            player.getPersistentData().remove(DWELL_TAG);
        }
    }

    private static CemeterySavedData.Cemetery cemeteryAt(ServerPlayer player) {
        if (player.serverLevel().dimension() != ServerLevel.OVERWORLD) return null;
        for (CemeterySavedData.Cemetery cemetery : CemeterySavedData.get(player.serverLevel()).cemeteries()) {
            if (cemetery.contains(player.blockPosition())) return cemetery;
        }
        return null;
    }

    /** 累计停留时间；离开墓园或破坏墓园方块会重置。 */
    private static void dwell(ServerPlayer player, CemeterySavedData.Cemetery cemetery) {
        int ticks = player.getPersistentData().getInt(DWELL_TAG) + 20;
        if (ticks < REQUIRED_DWELL_TICKS) {
            player.getPersistentData().putInt(DWELL_TAG, ticks);
            player.displayClientMessage(Component.translatable("message.endbound_ember.cemetery_dwell",
                    ticks * 100 / REQUIRED_DWELL_TICKS), true);
            return;
        }
        player.getPersistentData().remove(DWELL_TAG);
        CemeterySavedData data = CemeterySavedData.get(player.serverLevel());
        data.cemeteries().set(data.cemeteries().indexOf(cemetery), cemetery.withAwarded());
        data.changed();
        ModAdvancements.award(player, ModAdvancements.HOME_AGAIN);
    }

    @SubscribeEvent
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        if (level.dimension() != ServerLevel.OVERWORLD) return;
        BlockPos pos = event.getPos();
        boolean inCemetery = CemeterySavedData.get(level).cemeteries().stream()
                .anyMatch(cemetery -> cemetery.contains(pos));
        if (!inCemetery || player.getPersistentData().getInt(DWELL_TAG) <= 0) return;
        player.getPersistentData().remove(DWELL_TAG);
        player.displayClientMessage(Component.translatable("message.endbound_ember.cemetery_disturbed"), true);
    }

}
