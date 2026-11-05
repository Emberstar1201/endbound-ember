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
import net.minecraft.tags.BiomeTags;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Optional;

@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class ZombieGirlCampEvents {
    private static final ResourceLocation CAMP = ResourceLocation.fromNamespaceAndPath(
            EndboundEmber.MODID, "zombie_girl_camp");
    private static final ResourceLocation CAMP_RESOURCE = ResourceLocation.fromNamespaceAndPath(
            EndboundEmber.MODID, "structures/zombie_girl_camp.nbt");
    private static final ResourceKey<net.minecraft.world.level.storage.loot.LootTable> LOOT =
            ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                    ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "chests/zombie_girl_camp"));
    /** 普通营地候选间距：每 16×16 个区块确定一个候选，约每 256 格一个。 */
    private static final int SPACING = 16;
    /** 普通营地与世界出生点的最小水平距离。 */
    private static final int MIN_SPAWN_DISTANCE = 100;
    /** 出生点营地目标区块偏移，保持在出生点附近。 */
    private static final int SPAWN_CAMP_CHUNK_OFFSET = 5;
    /** 出生点营地唯一的地形条件。 */
    private static final int SPAWN_CAMP_MIN_Y = 60;
    /** 两座营地包围盒之间额外保留的水平间隙。 */
    private static final int CAMP_GAP = 16;
    /**
     * 生成少女时的地面层搜索高度：只在营地底部向上这几格内找落脚点，避免刷到屋顶。
     * 结构底部 0~3 层是地基与地砖，第 4 层才是可站的室内地面，再向上留两格容错。
     * 搜索窗口从结构底部起算，因此必须保证营地内部高度不超过这个值，否则会落到屋顶上。
     */
    private static final int FLOOR_SCAN_HEIGHT = 6;
    /** 营地内部净高上限：少女只能在结构底部以上这么多格内落脚，超过即视为屋顶。 */
    private static final int CAMP_INTERIOR_HEIGHT = 4;
    private static final Set<Long> ATTEMPTED = new HashSet<>();
    private static final Set<PendingCamp> PENDING = new HashSet<>();

    private record PendingCamp(ServerLevel level, ChunkPos chunk) {}

    private ZombieGirlCampEvents() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != ServerLevel.OVERWORLD
                || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        ChunkPos pos = chunk.getPos();
        BlockPos spawnPos = level.getSharedSpawnPos();
        ChunkPos spawnCampChunk = getSpawnCampChunk(spawnPos);
        boolean spawnCamp = pos.equals(spawnCampChunk);
        if (!spawnCamp && !isCandidate(level.getSeed(), pos.x, pos.z)) {
            return;
        }
        // 出生点营地独立于普通营地生成，并不受出生点距离、群系或水体条件限制。
        if (!spawnCamp) {
            long dx = pos.getMinBlockX() + 8 - spawnPos.getX();
            long dz = pos.getMinBlockZ() + 8 - spawnPos.getZ();
            if (dx * dx + dz * dz < (long) MIN_SPAWN_DISTANCE * MIN_SPAWN_DISTANCE) {
                return;
            }
        }
        long key = ChunkPos.asLong(pos.x, pos.z);
        if (!ATTEMPTED.add(key)) {
            if (spawnCamp) {
                EndboundEmber.LOGGER.warn("[营地生成] 出生点营地区块 {} 本次运行已尝试过，跳过", pos);
            }
            return;
        }
        if (ZombieGirlCampSavedData.get(level).hasGenerated(pos)) {
            EndboundEmber.LOGGER.warn("[营地生成] 区块 {} 存档已记录营地，跳过（旧存档需删除 data/endbound_ember_zombie_girl_camps.dat 才能重新生成）", pos);
            return;
        }
        // 入队延迟执行：不在此处调用 placeInWorld，避免递归区块加载导致死锁。
        EndboundEmber.LOGGER.info("[营地生成] 区块 {} 进入待生成队列（出生点营地={}）", pos, spawnCamp);
        PENDING.add(new PendingCamp(level, pos));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!PENDING.isEmpty()) {
            PendingCamp pending = PENDING.iterator().next();
            PENDING.remove(pending);
            start(pending);
        }
    }

    private static void start(PendingCamp pending) {
        ServerLevel level = pending.level();
        StructureTemplate template = loadTemplate(level);
        if (template == null) {
            return;
        }

        ChunkPos chunk = pending.chunk();
        int x = chunk.getMinBlockX() + 8;
        int z = chunk.getMinBlockZ() + 8;
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        BlockPos surface = new BlockPos(x, y, z);
        boolean spawnCamp = chunk.equals(getSpawnCampChunk(level.getSharedSpawnPos()));
        if (spawnCamp) {
            if (y < SPAWN_CAMP_MIN_Y) {
                EndboundEmber.LOGGER.warn("[营地生成] 出生点营地被拦截：地表高度 Y={} 低于 {}", y, SPAWN_CAMP_MIN_Y);
                return;
            }
        } else if (!isAllowedBiome(level, surface)) {
            EndboundEmber.LOGGER.warn("[营地生成] 区块 {} 被群系拦截：{}", chunk,
                    level.getBiome(surface).unwrapKey().map(k -> k.location().toString()).orElse("未知"));
            return;
        } else if (level.getFluidState(surface).isSource()) {
            EndboundEmber.LOGGER.warn("[营地生成] 区块 {} 被拦截：地表 {} 是水源", chunk, surface);
            return;
        }

        Vec3i size = template.getSize();
        ZombieGirlCampSavedData data = ZombieGirlCampSavedData.get(level);
        // 作物保护：营地一旦盖在农田上就会把作物顶掉，命中即放弃这块候选。
        if (hasCropsAround(level, surface, size)) {
            EndboundEmber.LOGGER.warn("[营地生成] 区块 {} 被拦截：结构范围内存在农田或作物，位置 {}", chunk, surface);
            return;
        }
        // 营地互斥：按结构水平对角线 + 间隙判定，保证两座营地的包围盒不会重叠或相接。
        if (!spawnCamp) {
            int minDistance = minCampDistance(size);
            BlockPos spawnCampCorner = getSpawnCampCorner(level.getSharedSpawnPos());
            if (data.isNearCamp(surface, minDistance)
                    || isTooCloseHorizontally(surface, spawnCampCorner, minDistance)) {
                EndboundEmber.LOGGER.warn("[营地生成] 区块 {} 被拦截：与已有营地（或出生点营地保留位置）距离小于 {} 格",
                        chunk, minDistance);
                return;
            }
        }

        BlockPos origin = surface;
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setIgnoreEntities(false)
                .setKnownShape(true);
        if (!template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2)) {
            EndboundEmber.LOGGER.error("[营地生成] placeInWorld 返回 false，位置 {}（出生点营地={}）", origin, spawnCamp);
            return;
        }
        EndboundEmber.LOGGER.info("[营地生成] 营地已放置于 {}，区块 {}（出生点营地={}）", origin, chunk, spawnCamp);
        finish(level, chunk, origin, size, level.getRandom());
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
        // 结构原点是最小角：水平居中到玩家，垂直下移 4 格让玩家落在营地室内地面上。
        BlockPos origin = center.offset(-size.getX() / 2, -4, -size.getZ() / 2);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setIgnoreEntities(false)
                .setKnownShape(true);
        if (!template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2)) {
            EndboundEmber.LOGGER.error("[营地生成] 测试指令放置失败，位置 {}", origin);
            return false;
        }
        EndboundEmber.LOGGER.info("[营地生成] 测试指令已在 {} 放置营地", origin);
        finish(level, new ChunkPos(origin), origin, size, level.getRandom());
        return true;
    }

    private static void finish(ServerLevel level, ChunkPos chunk, BlockPos origin,
                               Vec3i size, RandomSource random) {
        ZombieGirlCampSavedData data = ZombieGirlCampSavedData.get(level);
        data.addCamp(origin.above(7), chunk);
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
        for (int i = 0; i < 4; i++) {
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
     * 在营地最底层寻找落脚点：从结构底部向上最多 {@link #FLOOR_SCAN_HEIGHT} 格，
     * 避免刷到屋顶，也避免悬空后坠落把营地里的耕地与作物踩掉。
     * 超过 {@link #CAMP_INTERIOR_HEIGHT} 的位置一律视为屋顶，直接放弃。
     */
    private static BlockPos findFloorSpawn(ServerLevel level, Mob girl, int x, int z, int baseY, boolean drowned) {
        for (int dy = 0; dy < FLOOR_SCAN_HEIGHT; dy++) {
            if (dy > CAMP_INTERIOR_HEIGHT) return null;
            BlockPos pos = new BlockPos(x, baseY + dy, z);
            if (!level.noCollision(girl.getBoundingBox().move(x + 0.5D, pos.getY(), z + 0.5D))) continue;

            if (drowned) {
                if (level.getFluidState(pos).is(FluidTags.WATER)) return pos;
                continue;
            }

            BlockState ground = level.getBlockState(pos.below());
            if (!ground.blocksMotion()) continue;
            if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) continue;
            // 跳过耕地与作物所在的格，避免占据或踩坏营地里的农田
            if (ground.is(Blocks.FARMLAND) || level.getBlockState(pos).is(BlockTags.CROPS)) continue;
            return pos;
        }
        return null;
    }

    private static ChunkPos getSpawnCampChunk(BlockPos spawnPos) {
        // 向东偏移 5 区块，中心距离出生点约 80 格。
        return new ChunkPos((spawnPos.getX() >> 4) + SPAWN_CAMP_CHUNK_OFFSET,
                spawnPos.getZ() >> 4);
    }

    /** 出生点营地的确定性保留位置（结构最小角），用于普通营地避让。 */
    private static BlockPos getSpawnCampCorner(BlockPos spawnPos) {
        ChunkPos chunk = getSpawnCampChunk(spawnPos);
        return new BlockPos(chunk.getMinBlockX() + 8, 0, chunk.getMinBlockZ() + 8);
    }

    /**
     * 两座营地之间的最小角对角水平距离：结构水平对角线向上取整，再加上 {@link #CAMP_GAP} 的间隙。
     * 以结构最小角为基准比较，可保证两个包围盒既不相交也不贴边。
     */
    private static int minCampDistance(Vec3i size) {
        return (int) Math.ceil(Math.sqrt((double) size.getX() * size.getX()
                + (double) size.getZ() * size.getZ())) + CAMP_GAP;
    }

    /** 两个位置的水平距离（忽略高度）是否小于给定值。 */
    private static boolean isTooCloseHorizontally(BlockPos a, BlockPos b, int minDistance) {
        long dx = (long) a.getX() - b.getX();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dz * dz < (long) minDistance * minDistance;
    }

    /**
     * 结构将要覆盖的体积内是否存在农田或作物。高度图返回的是最高方块上方一格，
     * 作物实际位于原点下方，因此向下多扫两格；命中即放弃这块候选，避免把作物顶掉。
     */
    private static boolean hasCropsAround(ServerLevel level, BlockPos origin, Vec3i size) {
        BlockPos min = origin.offset(0, -2, 0);
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

    /** 每 4×4 个区块分区保证一个确定性候选，不再额外以概率跳过。 */
    private static boolean isCandidate(long seed, int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, SPACING);
        int regionZ = Math.floorDiv(chunkZ, SPACING);
        long hash = seed * 128712L + regionX * 987541L + regionZ;
        int offsetX = Math.floorMod((int) hash, SPACING);
        int offsetZ = Math.floorMod((int) (hash >>> 32), SPACING);
        return chunkX - regionX * SPACING == offsetX
                && chunkZ - regionZ * SPACING == offsetZ;
    }

    private static boolean isAllowedBiome(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).is(BiomeTags.IS_OVERWORLD)
                && !level.getBiome(pos).is(BiomeTags.IS_OCEAN)
                && !level.getBiome(pos).is(BiomeTags.IS_RIVER)
                && !level.getBiome(pos).is(BiomeTags.IS_MOUNTAIN);
    }
}
