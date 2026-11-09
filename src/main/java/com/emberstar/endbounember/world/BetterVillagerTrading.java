package com.emberstar.endbounember.world;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class BetterVillagerTrading {
    public static final String DISCOUNT_KEY = "EndboundEmberPermanentTradeDiscount";
    public static final String FOOL_KEY = "EndboundEmberFoolVillager";
    private static final String FOOL_RESTOCK_KEY = "EndboundEmberFoolRestockTime";
    public static final int MAX_DISCOUNT = 80;
    private static final double FOOL_SPAWN_CHANCE = 0.0001D;
    private static final int VILLAGE_RADIUS = 32;
    private static final int PROFESSION_SEARCH_RADIUS = 16;
    /** 愚者独立补货周期：1~2 个游戏日（24000~48000 tick），无条件自动补满。 */
    private static final int FOOL_RESTOCK_MIN_TICKS = 24000;
    private static final int FOOL_RESTOCK_RANDOM_TICKS = 24000;

    /** 已发现的愚者与其下次自动补货时间。弱引用键，区块卸载后不会造成泄漏。 */
    private static final Map<Villager, Long> FOOL_RESTOCK = new WeakHashMap<>();

    private BetterVillagerTrading() {}

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!Config.BETTER_VILLAGER_TRADING.get() || event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos broken = event.getPos();
        BlockState state = event.getState();
        for (Villager villager : level.getEntitiesOfClass(Villager.class,
                new net.minecraft.world.phys.AABB(broken).inflate(48.0D))) {
            VillagerProfession profession = villager.getVillagerData().getProfession();
            if (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT
                    || villager.getVillagerData().getLevel() >= 5) continue;
            if (PoiTypes.forState(state).filter(profession.heldJobSite()).isEmpty()) continue;
            BlockPos villagerJobSite = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                    .map(net.minecraft.core.GlobalPos::pos).orElse(null);
            if (!broken.equals(villagerJobSite)) continue;
            if (hasOtherJobSite(level, villager, profession, broken, villagerJobSite)) continue;
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
            villager.setVillagerData(villager.getVillagerData()
                    .setProfession(VillagerProfession.NONE).setLevel(1));
            villager.setVillagerXp(0);
            villager.getOffers().clear();
            villager.refreshBrain(level);
        }
    }

    private static boolean hasOtherJobSite(ServerLevel level, Villager villager,
                                           VillagerProfession profession, BlockPos broken, BlockPos villagerJobSite) {
        BlockPos origin = villager.blockPosition();
        return level.getPoiManager().findAll(profession.heldJobSite(), pos -> !pos.equals(broken)
                        && (villagerJobSite == null || !pos.equals(villagerJobSite)), origin,
                PROFESSION_SEARCH_RADIUS, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
                .findAny().isPresent();
    }

    @SubscribeEvent
    public static void onVillagerJoin(EntityJoinLevelEvent event) {
        if (!Config.BETTER_VILLAGER_TRADING.get() || event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (villager.getPersistentData().getBoolean(FOOL_KEY)) {
            // 从存档重新加载的愚者也要纳入自动补货，并保留原来的补货周期
            if (!villager.getPersistentData().contains(FOOL_RESTOCK_KEY)) {
                villager.getPersistentData().putLong(FOOL_RESTOCK_KEY, nextFoolRestock(villager));
            }
            FOOL_RESTOCK.put(villager, villager.getPersistentData().getLong(FOOL_RESTOCK_KEY));
            return;
        }
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (profession == VillagerProfession.NITWIT) {
            markAsFool(villager);
            return;
        }
        if (profession != VillagerProfession.NONE || villager.getVillagerData().getLevel() != 1) return;
        if (villager.getRandom().nextDouble() >= FOOL_SPAWN_CHANCE) return;
        markAsFool(villager);
    }

    /** 把指定村民标记为愚者，供自然生成与测试指令共用。 */
    public static void markAsFool(Villager villager) {
        villager.getPersistentData().putBoolean(FOOL_KEY, true);
        villager.setVillagerData(villager.getVillagerData()
                .setProfession(VillagerProfession.NITWIT).setLevel(1));
        villager.setVillagerXp(0);
        villager.setCustomName(net.minecraft.network.chat.Component.literal("愚者"));
        villager.setCustomNameVisible(false);
        long next = villager.getPersistentData().getLong(FOOL_RESTOCK_KEY);
        if (next <= 0) {
            next = nextFoolRestock(villager);
            villager.getPersistentData().putLong(FOOL_RESTOCK_KEY, next);
        }
        FOOL_RESTOCK.put(villager, next);
        // 立刻构建交易池，测试指令生成后可直接打开界面
        villager.getOffers();
    }

    /** 愚者没有工作站，原版补货行为不会触发；这里按独立的 1~2 游戏日周期无条件补满库存。 */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!Config.BETTER_VILLAGER_TRADING.get()) return;
        if (event.getServer().getTickCount() % 20 != 0) return;
        Iterator<Map.Entry<Villager, Long>> iterator = FOOL_RESTOCK.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Villager, Long> entry = iterator.next();
            Villager villager = entry.getKey();
            if (!villager.isAlive() || villager.isRemoved()) {
                iterator.remove();
                continue;
            }
            long now = villager.level().getGameTime();
            if (now < entry.getValue()) continue;
            for (MerchantOffer offer : villager.getOffers()) {
                offer.resetUses();
            }
            long next = nextFoolRestock(villager);
            villager.getPersistentData().putLong(FOOL_RESTOCK_KEY, next);
            entry.setValue(next);
        }
    }

    private static long nextFoolRestock(Villager villager) {
        return villager.level().getGameTime()
                + FOOL_RESTOCK_MIN_TICKS
                + villager.getRandom().nextInt(FOOL_RESTOCK_RANDOM_TICKS + 1);
    }

    /**
     * 愚者的交易池：沿用原版 MerchantOffer 结构，高价值但库存受限。
     * 已经添加过时直接返回，避免重复 build。
     */
    public static void addFoolTrades(Villager villager) {
        MerchantOffers offers = villager.getOffers();
        if (offers.stream().anyMatch(offer -> offer.getBaseCostA().is(Items.DIRT))) return;

        offers.add(new MerchantOffer(new ItemCost(Items.DIRT), new ItemStack(Items.EMERALD, 7), 16, 1, 0.05F));
        offers.add(new MerchantOffer(new ItemCost(Items.WHEAT), new ItemStack(Items.EMERALD, 6), 16, 1, 0.05F));
        offers.add(new MerchantOffer(new ItemCost(Items.COBBLESTONE), new ItemStack(Items.EMERALD, 8), 16, 1, 0.05F));
        offers.add(new MerchantOffer(new ItemCost(Items.STICK), new ItemStack(Items.EMERALD, 4), 16, 1, 0.05F));
        offers.add(new MerchantOffer(new ItemCost(Items.COAL), new ItemStack(Items.EMERALD, 6), 16, 1, 0.05F));
        offers.add(new MerchantOffer(new ItemCost(Items.IRON_INGOT), new ItemStack(Items.DIAMOND), 8, 2, 0.05F));
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(Items.BREAD, 16), 16, 2, 0.05F));
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(Items.EXPERIENCE_BOTTLE, 8), 12, 2, 0.05F));
        offers.add(new MerchantOffer(new ItemCost(Items.APPLE, 8), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), 3, 3, 0.05F));

        ItemStack enchanted = new ItemStack(Items.DIAMOND_CHESTPLATE);
        var enchantments = villager.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        enchanted.enchant(enchantments.getOrThrow(Enchantments.PROTECTION), 4);
        enchanted.enchant(enchantments.getOrThrow(Enchantments.UNBREAKING), 3);
        offers.add(new MerchantOffer(new ItemCost(Items.IRON_CHESTPLATE), enchanted, 1, 5, 0.05F));
    }

    public static void awardVillageDiscount(ServerLevel level, BlockPos center) {
        for (Villager villager : level.getEntitiesOfClass(Villager.class,
                new net.minecraft.world.phys.AABB(center).inflate(VILLAGE_RADIUS))) {
            if (villager.getPersistentData().getBoolean(FOOL_KEY)) continue;
            int discount = villager.getPersistentData().getInt(DISCOUNT_KEY);
            villager.getPersistentData().putInt(DISCOUNT_KEY, Math.min(MAX_DISCOUNT, discount + 20));
        }
    }

    public static void resetProfessionIfNoJobSite(Villager villager) {
        if (!Config.BETTER_VILLAGER_TRADING.get() || villager.getVillagerData().getLevel() >= 5) return;
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        BlockPos origin = villager.blockPosition();
        net.minecraft.core.GlobalPos jobSite = villager.getBrain()
                .getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        if (jobSite != null && jobSite.dimension().equals(level.dimension())
                && PoiTypes.forState(level.getBlockState(jobSite.pos()))
                        .filter(profession.heldJobSite()).isPresent()) return;
        boolean replacementJobSite = level.getPoiManager().findAll(profession.heldJobSite(), pos -> true,
                origin, PROFESSION_SEARCH_RADIUS,
                net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).findAny().isPresent();
        if (replacementJobSite) return;
        villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE).setLevel(1));
        villager.setVillagerXp(0);
        villager.getOffers().clear();
        villager.refreshBrain(level);
    }

    public static int discount(Villager villager) {
        return villager.getPersistentData().getInt(DISCOUNT_KEY);
    }
}
