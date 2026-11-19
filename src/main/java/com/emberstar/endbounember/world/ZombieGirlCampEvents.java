package com.emberstar.endbounember.world;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class ZombieGirlCampEvents {
    private static final ResourceLocation CAMP = ResourceLocation.fromNamespaceAndPath(
            EndboundEmber.MODID, "zombie_girl_camp");
    private static final ResourceLocation CAMP_RESOURCE = ResourceLocation.fromNamespaceAndPath(
            EndboundEmber.MODID, "structure/zombie_girl_camp.nbt");
    /** 出生点营地目标区块偏移，保持在出生点附近。 */
    private static final int SPAWN_CAMP_CHUNK_OFFSET = 5;
    private static final int SPAWN_CAMP_MIN_Y = 60;
    private static final int SPAWN_CAMP_RETRY_INTERVAL = 20;
    private static final int AUTOMATIC_BLOCK_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** 结构第 0 层是填充泥土，第 1 层是草地/木板地面；下移 1 格后草地层正好替换原地表方块。 */
    private static final int ORIGIN_Y_OFFSET = -1;
    /** 结构第 2 层是室内可站立地面。 */
    private static final int CAMP_FLOOR_LAYER = 2;
    /** 少女可站立的最高相对层。 */
    private static final int CAMP_INTERIOR_HEIGHT = 4;
    /** 营地常驻少女数量（新营地共 8 张床）。 */
    private static final int CAMP_GIRL_COUNT = 6;
    // 只保留值，不静态持有 ServerLevel / MinecraftServer，避免单人游戏换档串服。
    private static long nextSpawnCheck;
    private static ChunkPos pendingSpawnChunk;

    private ZombieGirlCampEvents() {}

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !ZombieGirlCampSavedData.get(level).isProtectedCropFarmland(event.getPos())) {
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(ServerLevel.OVERWORLD)
                || !(event.getChunk() instanceof LevelChunk chunk)) return;
        if (chunk.getPos().equals(getSpawnCampChunk(level.getSharedSpawnPos()))) {
            pendingSpawnChunk = chunk.getPos();
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ChunkPos chunk = pendingSpawnChunk;
        long tick = event.getServer().getTickCount();
        if (chunk == null || tick < nextSpawnCheck) return;
        nextSpawnCheck = tick + SPAWN_CAMP_RETRY_INTERVAL;
        ServerLevel level = event.getServer().overworld();
        if (!chunk.equals(getSpawnCampChunk(level.getSharedSpawnPos())) || start(level, chunk)) {
            pendingSpawnChunk = null;
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        nextSpawnCheck = 0;
        pendingSpawnChunk = null;
    }

    /** false 表示等待自然加载/模板恢复；不设置永久 attempted 标记。 */
    private static boolean start(ServerLevel level, ChunkPos chunk) {
        LevelChunk loaded = level.getChunkSource().getChunkNow(chunk.x, chunk.z);
        if (loaded == null) return false;
        if (ZombieGirlCampSavedData.get(level).hasGenerated(chunk)) return true;
        StructureTemplate template = loadTemplate(level);
        if (template == null) return false;
        Vec3i size = template.getSize();
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        int minX = x - size.getX() / 2;
        int minZ = z - size.getZ() / 2;
        // 模板放置的水涝处理可能读取相邻方块，连模板外一格也只接受已加载区块。
        if (!NaturalStructureEvents.allChunksLoaded(level, new BoundingBox(minX - 1, 0, minZ - 1,
                minX + size.getX(), 0, minZ + size.getZ()))) return false;
        if (loaded.getAllStarts().entrySet().stream().anyMatch(entry ->
                CAMP.equals(level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(entry.getKey()))
                        && entry.getValue().isValid())) return true;
        int y = loaded.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
        if (y < SPAWN_CAMP_MIN_Y) return true;
        BlockPos origin = new BlockPos(minX, y + ORIGIN_Y_OFFSET, minZ);
        if (hasCropsAround(level, origin, size)) return true;
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setIgnoreEntities(false).setKnownShape(true)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        if (!template.placeInWorld(level, origin, origin, settings, level.getRandom(), AUTOMATIC_BLOCK_FLAGS)) {
            return false;
        }
        protectCampCrops(level, origin, size);
        patchStructureBlocks(level, template, settings, origin);
        finish(level, origin, size, level.getRandom());
        ZombieGirlCampSavedData.get(level).addCamp(campCenter(origin, size), chunk);
        return true;
    }

    static boolean hasFinishedNatural(ServerLevel level, FixedTemplateStructurePiece piece) {
        BlockPos origin = piece.templateOrigin();
        return ZombieGirlCampSavedData.get(level).hasFinishedCamp(origin, campCenter(origin, piece.template().getSize()));
    }

    /** 原版自然结构已经放置完成；这里只做保护、少女与战利品收尾。 */
    static void finishNatural(ServerLevel level, ChunkPos chunk, FixedTemplateStructurePiece piece) {
        if (hasFinishedNatural(level, piece)) return;
        BlockPos origin = piece.templateOrigin();
        Vec3i size = piece.template().getSize();
        protectCampCrops(level, origin, size);
        finish(level, origin, size, level.getRandom());
        ZombieGirlCampSavedData.get(level).completeNaturalCamp(origin, campCenter(origin, size), chunk);
    }

    /** 保护结构中的作物和耕地，防止营地生物踩踏农田。 */
    private static void protectCampCrops(ServerLevel level, BlockPos origin, Vec3i size) {
        BlockPos end = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        BlockPos min = new BlockPos(Math.min(origin.getX(), end.getX()), Math.min(origin.getY(), end.getY()),
                Math.min(origin.getZ(), end.getZ()));
        BlockPos max = new BlockPos(Math.max(origin.getX(), end.getX()), Math.max(origin.getY(), end.getY()),
                Math.max(origin.getZ(), end.getZ()));
        ZombieGirlCampSavedData data = ZombieGirlCampSavedData.get(level);
        int protectedCrops = 0;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.getBlockState(pos).is(BlockTags.CROPS)) continue;
            BlockPos farmlandPos = pos.below();
            BlockState farmland = level.getBlockState(farmlandPos);
            if (farmland.is(Blocks.FARMLAND)) {
                data.addProtectedCropFarmland(farmlandPos);
                level.setBlock(farmlandPos, farmland.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), AUTOMATIC_BLOCK_FLAGS);
                protectedCrops++;
            }
        }
        EndboundEmber.LOGGER.info("[营地生成] 已保护结构内 {} 格作物农田，营地原点 {}", protectedCrops, origin);
    }

    /** 营地中心（室内地面高度），存档记录与互斥判定都以它为基准。 */
    private static BlockPos campCenter(BlockPos origin, Vec3i size) {
        return origin.offset(size.getX() / 2, CAMP_FLOOR_LAYER, size.getZ() / 2);
    }

    /**
     * 被忽略的 structure_block 会在放置后留下 1 格空洞，这里用草地补回。
     * 新营地第 1 层是整片草地，被占用的格子原本也应是草地。
     */
    private static void patchStructureBlocks(ServerLevel level, StructureTemplate template,
                                             StructurePlaceSettings settings, BlockPos origin) {
        for (StructureTemplate.StructureBlockInfo info
                : template.filterBlocks(BlockPos.ZERO, settings, Blocks.STRUCTURE_BLOCK)) {
            BlockPos pos = origin.offset(info.pos());
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), AUTOMATIC_BLOCK_FLAGS);
            }
        }
    }

    /** 从 StructureManager 或资源包中加载营地模板，失败时返回 null。 */
    @Nullable
    private static StructureTemplate loadTemplate(ServerLevel level) {
        StructureTemplate template = level.getStructureManager().get(CAMP).orElse(null);
        if (template != null) {
            return template;
        }
        Optional<Resource> resource = level.getServer().getResourceManager().getResource(CAMP_RESOURCE);
        if (resource.isEmpty()) {
            EndboundEmber.LOGGER.error("[营地生成] 结构模板 {} 加载失败：ResourceManager 中不存在 {}", CAMP, CAMP_RESOURCE);
            return null;
        }

        try (java.io.InputStream in = resource.get().open()) {
            net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.NbtIo.readCompressed(
                    in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            template = new StructureTemplate();
            template.load(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), tag);
            EndboundEmber.LOGGER.warn("[营地生成] StructureManager 未返回模板，已使用手动加载模板，size={}",
                    template.getSize());
            return template;
        } catch (Throwable e) {
            EndboundEmber.LOGGER.error("[营地生成] 手动加载结构模板失败", e);
            return null;
        }
    }

    /**
     * 测试指令用：以给定位置为中心立即放置一座营地（含战利品与少女）。
     * 与自然生成共用同一套模板加载与收尾逻辑，并写入存档记录避免同区块重复生成。
     */
    public static boolean placeCampAt(ServerLevel level, BlockPos center) {
        StructureTemplate template = loadTemplate(level);
        if (template == null) {
            return false;
        }
        Vec3i size = template.getSize();
        // 结构原点是最小角：水平居中到指令位置，垂直按 ORIGIN_Y_OFFSET 让草地层落在脚下方块。
        BlockPos origin = center.offset(-size.getX() / 2, ORIGIN_Y_OFFSET, -size.getZ() / 2);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setIgnoreEntities(false)
                .setKnownShape(true)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        if (!template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2)) {
            EndboundEmber.LOGGER.error("[营地生成] 测试指令放置失败，位置 {}", origin);
            return false;
        }
        protectCampCrops(level, origin, size);
        patchStructureBlocks(level, template, settings, origin);
        EndboundEmber.LOGGER.info("[营地生成] 测试指令已在 {} 放置营地", origin);
        finish(level, origin, size, level.getRandom());
        ZombieGirlCampSavedData.get(level).addCamp(campCenter(origin, size), new ChunkPos(origin));
        return true;
    }

    private static void finish(ServerLevel level, BlockPos origin, Vec3i size, RandomSource random) {
        BlockPos end = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        BlockPos min = new BlockPos(Math.min(origin.getX(), end.getX()), Math.min(origin.getY(), end.getY()),
                Math.min(origin.getZ(), end.getZ()));
        BlockPos max = new BlockPos(Math.max(origin.getX(), end.getX()), Math.max(origin.getY(), end.getY()),
                Math.max(origin.getZ(), end.getZ()));

        int containers = 0;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container) {
                fillCampLoot(level, container, random);
                container.setChanged();
                containers++;
            }
        }
        EndboundEmber.LOGGER.info("[营地生成] 战利品箱处理完成：找到 {} 个容器，已直接填充物品", containers);
        spawnCampGirls(level, min, max, random);
    }

    /** 任意生肉：牛、猪、鸡、羊、兔、鳕鱼、鲑鱼。 */
    private static final Item[] RAW_MEATS = {
            Items.BEEF, Items.PORKCHOP, Items.CHICKEN, Items.MUTTON, Items.RABBIT, Items.COD, Items.SALMON
    };
    /** 常见作物：小麦、胡萝卜、马铃薯、甜菜根。 */
    private static final Item[] CROPS = {
            Items.WHEAT, Items.CARROT, Items.POTATO, Items.BEETROOT
    };
    /** 铁制工具与盔甲。 */
    private static final Item[] IRON_GEAR = {
            Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE,
            Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS
    };

    private static void fillCampLoot(ServerLevel level, RandomizableContainerBlockEntity container,
                                     RandomSource random) {
        int rolls = 2 + random.nextInt(3);
        for (int i = 0; i < rolls; i++) {
            ItemStack stack = rollCampLoot(level, random);
            int slot = random.nextInt(container.getContainerSize());
            container.setItem(slot, stack);
        }
    }

    /** 按权重随机一件营地战利品。 */
    private static ItemStack rollCampLoot(ServerLevel level, RandomSource random) {
        int choice = random.nextInt(36);
        if (choice < 4) {
            return new ItemStack(Items.IRON_INGOT, 2 + random.nextInt(5));
        }
        if (choice < 8) {
            return new ItemStack(Items.ROTTEN_FLESH, 2 + random.nextInt(7));
        }
        if (choice < 11) {
            return new ItemStack(Items.GOLD_INGOT, 1 + random.nextInt(3));
        }
        if (choice < 13) {
            return new ItemStack(Items.BOOK);
        }
        if (choice < 14) {
            return new ItemStack(Items.DIAMOND, 1 + random.nextInt(2));
        }
        if (choice < 17) {
            return new ItemStack(Items.LAPIS_LAZULI, 4 + random.nextInt(9));
        }
        if (choice < 19) {
            return new ItemStack(Items.BREAD, 1 + random.nextInt(3));
        }
        if (choice < 22) {
            return new ItemStack(randomItem(RAW_MEATS, random), 1 + random.nextInt(3));
        }
        if (choice < 25) {
            return new ItemStack(randomItem(CROPS, random), 1 + random.nextInt(3));
        }
        if (choice < 29) {
            return new ItemStack(randomItem(IRON_GEAR, random));
        }
        if (choice < 33) {
            return randomEnchantedBook(level, random, "minecraft");
        }
        return randomEnchantedBook(level, random, EndboundEmber.MODID);
    }

    private static Item randomItem(Item[] items, RandomSource random) {
        return items[random.nextInt(items.length)];
    }

    /** 从指定命名空间的附魔中随机挑一个，生成 1 级附魔书；诅咒附魔不掉落。 */
    private static ItemStack randomEnchantedBook(ServerLevel level, RandomSource random, String namespace) {
        HolderLookup.Provider registries = level.registryAccess();
        List<ResourceKey<Enchantment>> keys = registries.lookupOrThrow(Registries.ENCHANTMENT)
                .listElements()
                .filter(holder -> holder.key().location().getNamespace().equals(namespace))
                .filter(holder -> !holder.is(EnchantmentTags.CURSE))
                .map(holder -> holder.key())
                .toList();
        if (keys.isEmpty()) {
            return new ItemStack(Items.BOOK);
        }
        return EndboundEmber.createEnchantedBook(registries, keys.get(random.nextInt(keys.size())), 1);
    }

    private static void spawnCampGirls(ServerLevel level, BlockPos min, BlockPos max, RandomSource random) {
        int spawned = 0;
        for (int i = 0; i < CAMP_GIRL_COUNT; i++) {
            for (int attempt = 0; attempt < 12; attempt++) {
                int x = min.getX() + random.nextInt(Math.max(1, max.getX() - min.getX() + 1));
                int z = min.getZ() + random.nextInt(Math.max(1, max.getZ() - min.getZ() + 1));
                boolean drowned = random.nextInt(3) == 0;
                Mob girl = (drowned ? ModEntities.DROWNED_GIRL : ModEntities.ZOMBIE_GIRL).get().create(level);
                if (girl == null) continue;
                BlockPos pos = findFloorSpawn(level, girl, x, z, min.getY(), drowned);
                if (pos == null) continue;
                girl.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
                girl.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
                level.addFreshEntity(girl);
                spawned++;
                break;
            }
        }
        EndboundEmber.LOGGER.info("[营地生成] 营地少女生成完成：实际生成 {} 只", spawned);
    }

    /**
     * 在营地里寻找落脚点：只扫结构第 {@link #CAMP_FLOOR_LAYER} 层到第
     * {@link #CAMP_INTERIOR_HEIGHT} 层，避免刷到床铺、家具与屋顶上。
     */
    private static BlockPos findFloorSpawn(ServerLevel level, Mob girl, int x, int z, int baseY, boolean drowned) {
        for (int layer = CAMP_FLOOR_LAYER; layer <= CAMP_INTERIOR_HEIGHT; layer++) {
            BlockPos pos = new BlockPos(x, baseY + layer, z);
            if (!level.noCollision(girl.getBoundingBox().move(x + 0.5D, pos.getY(), z + 0.5D))) continue;

            if (drowned) {
                if (level.getFluidState(pos).is(FluidTags.WATER)) return pos;
                continue;
            }

            BlockState ground = level.getBlockState(pos.below());
            // 只认草地、泥土、木板、石砖与平滑石，避免少女站到木桶、工作台、铁砧等家具顶上。
            if (!isCampFloor(ground)) continue;
            if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) continue;
            // 跳过耕地与作物所在的格，避免占据或踩坏营地里的农田
            if (ground.is(Blocks.FARMLAND) || level.getBlockState(pos).is(BlockTags.CROPS)) continue;
            return pos;
        }
        return null;
    }

    /** 营地可站立的底面材质：草地/泥土、木板、石砖、平滑石。 */
    private static boolean isCampFloor(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.PLANKS)
                || state.is(Blocks.STONE_BRICKS) || state.is(Blocks.SMOOTH_STONE);
    }

    private static ChunkPos getSpawnCampChunk(BlockPos spawnPos) {
        // 向东偏移 5 区块，中心距离出生点约 80 格。
        return new ChunkPos((spawnPos.getX() >> 4) + SPAWN_CAMP_CHUNK_OFFSET,
                spawnPos.getZ() >> 4);
    }

    /**
     * 结构将要覆盖的体积内是否存在农田或作物。结构草地层已经替换原地表方块，
     * 作物只可能出现在原点下方一格以内，因此从原点下一格起扫。
     */
    private static boolean hasCropsAround(ServerLevel level, BlockPos origin, Vec3i size) {
        BlockPos min = origin.offset(0, -1, 0);
        BlockPos max = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.CROPS)
                    || state.is(BlockTags.MAINTAINS_FARMLAND)
                    || state.is(Blocks.FARMLAND)
                    || state.is(Blocks.SUGAR_CANE)) {
                return true;
            }
        }
        return false;
    }

}
