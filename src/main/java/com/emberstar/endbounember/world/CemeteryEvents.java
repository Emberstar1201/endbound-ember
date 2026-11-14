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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.tags.BiomeTags;
import net.neoforged.neoforge.common.Tags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.Optional;
import net.minecraft.server.packs.resources.Resource;

@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class CemeteryEvents {
    private static final ResourceLocation STRUCTURE = ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "cemetery");
    private static final ResourceLocation RESOURCE = ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "structures/cemetery.nbt");
    private static final int SPACING = 12;
    private static final Queue<Candidate> PENDING = new ArrayDeque<>();
    private static final Set<String> ATTEMPTED = new HashSet<>();
    private static final Set<String> QUEUED = new HashSet<>();
    private static final java.util.Map<String, Integer> RETRIES = new java.util.HashMap<>();
    private static final Queue<CandleCheck> CANDLE_CHECKS = new ArrayDeque<>();
    private static final String EFFECT_TAG = "EndboundEmberCemeteryEffect";

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
        boolean lootInitialized = fillContainers(level, origin, size);
        if (lootInitialized) {
            int index = data.cemeteries().size() - 1;
            data.cemeteries().set(index, data.cemeteries().get(index).withLootInitialized());
            data.changed();
        }
        return true;
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != ServerLevel.OVERWORLD
                || !(event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)) return;
        ChunkPos chunkPos = chunk.getPos();
        if (!isCandidate(level.getSeed(), chunkPos.x, chunkPos.z)) return;
        String key = candidateKey(level, chunkPos.x, chunkPos.z);
        if (QUEUED.add(key)) PENDING.add(new Candidate(level, chunkPos));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        int work = Math.min(1, PENDING.size());
        List<Candidate> retry = new ArrayList<>();
        while (work-- > 0) {
            Candidate candidate = PENDING.poll();
            if (candidate != null && !ATTEMPTED.contains(candidate.key())) {
                if (tryCandidate(candidate)) {
                    RETRIES.remove(candidate.key());
                } else {
                    int retries = RETRIES.merge(candidate.key(), 1, Integer::sum);
                    if (retries < 1200) retry.add(candidate);
                    else {
                        RETRIES.remove(candidate.key());
                        ATTEMPTED.add(candidate.key());
                    }
                }
            }
        }
        PENDING.addAll(retry);
        ServerLevel overworld = event.getServer().overworld();
        for (CemeterySavedData.Cemetery cemetery : CemeterySavedData.get(overworld).cemeteries()) {
            if (!cemetery.lootInitialized() && overworld.hasChunkAt(cemetery.origin())
                    && fillContainers(overworld, cemetery.origin(), new Vec3i(51, 9, 51))) {
                CemeterySavedData data = CemeterySavedData.get(overworld);
                int index = data.cemeteries().indexOf(cemetery);
                data.cemeteries().set(index, cemetery.withLootInitialized());
                data.changed();
            }
        }
        int candleChecks = CANDLE_CHECKS.size();
        while (candleChecks-- > 0) {
            CandleCheck candleCheck = CANDLE_CHECKS.poll();
            if (candleCheck != null) checkCandles(candleCheck.level(), candleCheck.player(), candleCheck.pos());
        }
    }

    private static boolean tryCandidate(Candidate candidate) {
        ServerLevel level = candidate.level();
        ChunkPos chunk = candidate.chunk();
        if (!level.hasChunk(chunk.x, chunk.z)) return false;
        String key = candidate.key();
        if (ATTEMPTED.contains(key)) return true;
        BlockPos chunkCenter = new BlockPos(chunk.getMinBlockX() + 8, 0, chunk.getMinBlockZ() + 8);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (!level.hasChunk(chunk.x + dx, chunk.z + dz)) return false;
            }
        }
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, chunkCenter.getX(), chunkCenter.getZ());
        BlockPos surface = new BlockPos(chunkCenter.getX(), surfaceY - 1, chunkCenter.getZ());
        if (!isAllowedBiome(level, surface) || level.getFluidState(surface).isSource()) {
            ATTEMPTED.add(key);
            return true;
        }
        CemeterySavedData data = CemeterySavedData.get(level);
        if (data.cemeteries().stream().anyMatch(old -> {
            long dx = (long) old.origin().getX() + 25 - chunkCenter.getX();
            long dz = (long) old.origin().getZ() + 25 - chunkCenter.getZ();
            return dx * dx + dz * dz <= 128L * 128L;
        })) {
            ATTEMPTED.add(key);
            return true;
        }

        StructureTemplate template = loadTemplate(level);
        if (template == null) return false;
        Vec3i size = template.getSize();
        if (size.getX() != 51 || size.getY() != 9 || size.getZ() != 51) return true;
        BlockPos origin = new BlockPos(chunkCenter.getX() - size.getX() / 2, surfaceY - 3,
                chunkCenter.getZ() - size.getZ() / 2);
        int minChunkX = origin.getX() >> 4;
        int maxChunkX = (origin.getX() + 50) >> 4;
        int minChunkZ = origin.getZ() >> 4;
        int maxChunkZ = (origin.getZ() + 50) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            if (!level.hasChunk(cx, cz)) return false;
        }
        for (int dx = 0; dx < size.getX(); dx += 8) {
            for (int dz = 0; dz < size.getZ(); dz += 8) {
                BlockPos check = origin.offset(dx, 0, dz);
                int checkY = level.getHeight(Heightmap.Types.WORLD_SURFACE, check.getX(), check.getZ());
                BlockPos ground = new BlockPos(check.getX(), checkY - 1, check.getZ());
                if (Math.abs(checkY - surfaceY) > 8 || !isAllowedBiome(level, ground)
                        || level.getFluidState(ground).isSource()) {
                    ATTEMPTED.add(key);
                    return true;
                }
            }
        }

        StructurePlaceSettings settings = new StructurePlaceSettings().setKnownShape(true)
                .addProcessor(BlockIgnoreProcessor.AIR).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        if (!template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2)) return false;
        List<BlockPos> candles = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, settings, Blocks.CANDLE)) {
            candles.add(origin.offset(info.pos()).immutable());
        }
        CemeterySavedData.Cemetery cemetery = new CemeterySavedData.Cemetery(level.dimension(), chunkCenter, origin, false, false, candles);
        data.add(cemetery);
        boolean lootInitialized = fillContainers(level, origin, size);
        if (lootInitialized) {
            int index = data.cemeteries().indexOf(cemetery);
            data.cemeteries().set(index, cemetery.withLootInitialized());
            data.changed();
        }
        ATTEMPTED.add(key);
        return true;
    }

    private static boolean fillContainers(ServerLevel level, BlockPos origin, Vec3i size) {
        int minChunkX = origin.getX() >> 4;
        int maxChunkX = (origin.getX() + size.getX() - 1) >> 4;
        int minChunkZ = origin.getZ() >> 4;
        int maxChunkZ = (origin.getZ() + size.getZ() - 1) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) return false;
            }
        }
        StructureTemplate template = loadTemplate(level);
        if (template == null) return false;
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .addProcessor(BlockIgnoreProcessor.AIR)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        List<net.minecraft.world.Container> containers = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, settings, Blocks.BARREL)) {
            BlockPos pos = origin.offset(info.pos());
            if (level.getBlockState(pos).is(Blocks.BARREL)
                    && level.getBlockEntity(pos) instanceof net.minecraft.world.Container container) {
                containers.add(container);
            }
        }
        if (containers.isEmpty()) return false;

        Set<net.minecraft.world.Container> diaryContainers = new HashSet<>();
        for (net.minecraft.world.Container container : containers) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (isCemeteryDiary(container.getItem(slot))) {
                    diaryContainers.add(container);
                    break;
                }
            }
        }
        int targetDiaries = Math.min(containers.size(), 2 + level.getRandom().nextInt(3));
        while (diaryContainers.size() < targetDiaries) {
            List<ContainerSlot> diarySlots = new ArrayList<>();
            for (net.minecraft.world.Container container : containers) {
                if (diaryContainers.contains(container)) continue;
                List<Integer> emptySlots = new ArrayList<>();
                boolean hasLoot = false;
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    ItemStack stack = container.getItem(slot);
                    if (stack.isEmpty()) emptySlots.add(slot);
                    else if (!isCemeteryDiary(stack)) hasLoot = true;
                }
                if (hasLoot || emptySlots.size() > 1) {
                    for (int slot : emptySlots) diarySlots.add(new ContainerSlot(container, slot));
                }
            }
            if (diarySlots.isEmpty()) break;
            ContainerSlot diarySlot = diarySlots.get(level.getRandom().nextInt(diarySlots.size()));
            ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
            var diaryTag = new net.minecraft.nbt.CompoundTag();
            diaryTag.putBoolean("EndboundEmberCemeteryDiary", true);
            book.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(diaryTag));
            book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("烈士日记 / Martyr's Diary"),
                    "无名烈士 / Unknown Martyr", 0,
                    java.util.stream.IntStream.rangeClosed(1, 8)
                            .mapToObj(page -> Filterable.<Component>passThrough(Component.translatable(
                                    "book.endbound_ember.martyr_diary.page" + page)))
                            .toList(), false));
            diarySlot.container().setItem(diarySlot.slot(), book);
            diaryContainers.add(diarySlot.container());
        }
        boolean allContainersHaveLoot = true;
        for (net.minecraft.world.Container container : containers) {
            List<Integer> emptySlots = new ArrayList<>();
            int lootCount = 0;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty()) emptySlots.add(slot);
                else if (!isCemeteryDiary(stack)) lootCount++;
            }
            int targetLoot = 1 + level.getRandom().nextInt(3);
            int rolls = Math.min(emptySlots.size(), Math.max(0, targetLoot - lootCount));
            while (rolls-- > 0) {
                int slot = emptySlots.remove(level.getRandom().nextInt(emptySlots.size()));
                container.setItem(slot, randomTreasure(level));
            }
            boolean hasLoot = false;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (!container.getItem(slot).isEmpty() && !isCemeteryDiary(container.getItem(slot))) {
                    hasLoot = true;
                    break;
                }
            }
            if (!hasLoot) allContainersHaveLoot = false;
            if (container instanceof net.minecraft.world.level.block.entity.BlockEntity blockEntity) blockEntity.setChanged();
        }
        return diaryContainers.size() >= targetDiaries && allContainersHaveLoot;
    }

    private static boolean isCemeteryDiary(ItemStack stack) {
        if (!stack.is(Items.WRITTEN_BOOK)) return false;
        var customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null && customData.copyTag().getBoolean("EndboundEmberCemeteryDiary");
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
        boolean inside = false;
        if (player.serverLevel().dimension() == ServerLevel.OVERWORLD) {
            for (CemeterySavedData.Cemetery cemetery : CemeterySavedData.get(player.serverLevel()).cemeteries()) {
                if (cemetery.contains(player.blockPosition())) { inside = true; break; }
            }
        }
        if (inside) {
            player.getPersistentData().putBoolean(EFFECT_TAG, true);
            player.addEffect(new MobEffectInstance(ModEffects.CALM, 40, 0, true, true));
            if (player.getFoodData().getFoodLevel() < 20) player.getFoodData().setFoodLevel(player.getFoodData().getFoodLevel() + 1);
            if (player.getFoodData().getSaturationLevel() < 20.0F) player.getFoodData().setSaturation(player.getFoodData().getSaturationLevel() + 1.0F);
            if (player.getHealth() < player.getMaxHealth()) player.heal(1.0F);
        } else if (player.getPersistentData().getBoolean(EFFECT_TAG)) {
            player.removeEffect(ModEffects.CALM);
            player.getPersistentData().remove(EFFECT_TAG);
        }
    }

    @SubscribeEvent
    public static void onCandleLit(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player) || event.getItemStack().isEmpty()
                || !event.getItemStack().is(Items.FLINT_AND_STEEL)) return;
        ServerLevel level = player.serverLevel();
        if (level.dimension() != ServerLevel.OVERWORLD) return;
        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos).hasProperty(CandleBlock.LIT) || level.getBlockState(pos).getValue(CandleBlock.LIT)) return;
        boolean targetCandle = CemeterySavedData.get(level).cemeteries().stream()
                .anyMatch(cemetery -> cemetery.candles().contains(pos));
        if (!targetCandle) return;
        CANDLE_CHECKS.add(new CandleCheck(level, player, pos.immutable()));
    }

    private static void checkCandles(ServerLevel level, ServerPlayer player, BlockPos litPos) {
        CemeterySavedData data = CemeterySavedData.get(level);
        for (CemeterySavedData.Cemetery cemetery : data.cemeteries()) {
            if (cemetery.awarded() || !cemetery.candles().contains(litPos)) continue;
            boolean allLit = cemetery.candles().size() == 124;
            for (BlockPos candle : cemetery.candles()) {
                if (!level.hasChunkAt(candle) || !level.getBlockState(candle).hasProperty(CandleBlock.LIT)
                        || !level.getBlockState(candle).getValue(CandleBlock.LIT)) { allLit = false; break; }
            }
            if (allLit) {
                data.cemeteries().set(data.cemeteries().indexOf(cemetery), cemetery.withAwarded());
                data.changed();
                ModAdvancements.award(player, ModAdvancements.HOME_AGAIN);
            }
            return;
        }
    }

    private static boolean isAllowedBiome(ServerLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        return biome.is(BiomeTags.IS_FOREST) || biome.is(Tags.Biomes.IS_PLAINS);
    }

    private static boolean isCandidate(long seed, int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, SPACING);
        int regionZ = Math.floorDiv(chunkZ, SPACING);
        long hash = seed * 128712L + regionX * 987541L + regionZ;
        return chunkX - regionX * SPACING == Math.floorMod((int) hash, SPACING)
                && chunkZ - regionZ * SPACING == Math.floorMod((int) (hash >>> 32), SPACING);
    }

    private static String candidateKey(ServerLevel level, int chunkX, int chunkZ) {
        return level.dimension().location() + ":" + chunkX + ":" + chunkZ;
    }

    private record Candidate(ServerLevel level, ChunkPos chunk) {
        private String key() { return candidateKey(level, chunk.x, chunk.z); }
    }
    private record ContainerSlot(net.minecraft.world.Container container, int slot) {}
    private record CandleCheck(ServerLevel level, ServerPlayer player, BlockPos pos) {}
}
