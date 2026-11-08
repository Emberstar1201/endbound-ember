package com.emberstar.endbounember.enchantment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.emberstar.endbounember.EndboundEmber;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 强夺：武器附魔，最高 III 级，仅以宝藏附魔书形式出现。
 * 稀有战利品保底（头颅、唱片）与怪物装备掉落，等级越高数量越多、耐久越新。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class PlunderEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "plunder"));

    /** 旧版 PlunderConfig.rareLootChanceLevel1~3 默认值。 */
    private static final double[] RARE_LOOT_CHANCE = { 0.75D, 1.0D, 1.0D };
    /** 旧版 PlunderConfig.rareLootMinCountLevel1~3 默认值。 */
    private static final int[] RARE_LOOT_MIN = { 1, 2, 3 };
    /** 旧版 PlunderConfig.rareLootMaxCountLevel1~3 默认值。 */
    private static final int[] RARE_LOOT_MAX = { 2, 3, 4 };
    /** 旧版 PlunderConfig.equipDropChanceLevel1~3 默认值。 */
    private static final double[] EQUIP_DROP_CHANCE = { 0.6D, 1.0D, 1.0D };
    /** 旧版 PlunderConfig.equipDurabilityMinLevel1~3 默认值。 */
    private static final double[] EQUIP_DURABILITY_MIN = { 0.2D, 0.5D, 1.0D };
    /** 旧版 PlunderConfig.equipDurabilityMaxLevel1~3 默认值。 */
    private static final double[] EQUIP_DURABILITY_MAX = { 0.3D, 0.75D, 1.0D };
    /** 旧版 PlunderConfig.equipFullDurabilityChanceLevel1 默认值：2%。 */
    private static final double EQUIP_FULL_DURABILITY_CHANCE_L1 = 0.02D;

    private static final int MAX_LEVEL = 3;
    private static final int TOOLTIP_COLOR = 0xC0A030;

    private PlunderEnchantment() {
    }

    /** 读取物品上的强夺附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
    public static int levelOf(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        int level = levelOf(stack, DataComponents.ENCHANTMENTS);
        if (level > 0) return level;
        return levelOf(stack, DataComponents.STORED_ENCHANTMENTS);
    }

    private static int levelOf(ItemStack stack, DataComponentType<ItemEnchantments> type) {
        ItemEnchantments enchantments = stack.getOrDefault(type, ItemEnchantments.EMPTY);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            Optional<ResourceKey<Enchantment>> key = entry.getKey().unwrapKey();
            if (key.isPresent() && key.get().equals(KEY)) return entry.getIntValue();
        }
        return 0;
    }

    /** 稀有战利品保底：已掉落的头颅/唱片补足数量，并为凋灵骷髅、爬行者补发。 */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        Level level = victim.level();
        if (level.isClientSide()) return;

        DamageSource source = event.getSource();
        if (source == null || !(source.getEntity() instanceof Player killer)) return;

        int plunderLevel = levelOf(killer.getMainHandItem());
        if (plunderLevel <= 0) return;

        RandomSource random = level.getRandom();
        if (random.nextFloat() >= rareLootChance(plunderLevel)) return;

        int count = rollRareLootCount(plunderLevel, random);

        // 对已在掉落列表中的稀有战利品补足数量
        List<ItemEntity> drops = new ArrayList<>(event.getDrops());
        for (ItemEntity drop : drops) {
            ItemStack stack = drop.getItem();
            if (isRareLoot(stack) && stack.getCount() < count) {
                stack.setCount(count);
            }
        }

        // 凋灵骷髅：原版 2.5% 掉头，触发时必掉头颅
        if (victim instanceof WitherSkeleton && !hasRareLootOf(event, Items.WITHER_SKELETON_SKULL)) {
            addDropAtVictim(event, victim, new ItemStack(Items.WITHER_SKELETON_SKULL, count));
        }

        // 爬行者：原版需被骷髅射杀才掉唱片，触发时必掉一张随机唱片
        if (victim instanceof Creeper && !hasAnyMusicDisc(event)) {
            ItemStack disc = getRandomMusicDisc(random);
            if (!disc.isEmpty()) {
                disc.setCount(count);
                addDropAtVictim(event, victim, disc);
            }
        }
    }

    /** 怪物装备掉落：按等级判定概率与剩余耐久，并清空槽位避免原版重复掉落。 */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Level level = victim.level();
        if (level.isClientSide()) return;
        if (victim instanceof Player) return;

        DamageSource source = event.getSource();
        if (source == null || !(source.getEntity() instanceof Player killer)) return;

        int plunderLevel = levelOf(killer.getMainHandItem());
        if (plunderLevel <= 0) return;

        RandomSource random = level.getRandom();
        double dropChance = equipDropChance(plunderLevel);

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isArmor() && slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) continue;

            ItemStack equip = victim.getItemBySlot(slot);
            if (equip.isEmpty()) continue;
            if (random.nextFloat() >= dropChance) continue;

            ItemStack dropStack = equip.copy();
            applyPlunderDurability(dropStack, plunderLevel, random);
            spawnDropAt(level, victim, dropStack);
            victim.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    private static void applyPlunderDurability(ItemStack stack, int level, RandomSource random) {
        int maxDamage = stack.getMaxDamage();
        if (maxDamage <= 0) return;
        double ratio = rollEquipDurability(level, random);
        int damage = (int) Math.round(maxDamage * (1.0D - ratio));
        stack.setDamageValue(Math.max(0, Math.min(maxDamage, damage)));
    }

    private static void spawnDropAt(Level level, LivingEntity victim, ItemStack stack) {
        ItemEntity drop = new ItemEntity(level, victim.getX(), victim.getY(), victim.getZ(), stack);
        RandomSource random = level.getRandom();
        drop.setDeltaMovement((random.nextDouble() - 0.5D) * 0.2D,
                random.nextDouble() * 0.3D, (random.nextDouble() - 0.5D) * 0.2D);
        level.addFreshEntity(drop);
    }

    private static void addDropAtVictim(LivingDropsEvent event, LivingEntity victim, ItemStack stack) {
        ItemEntity drop = new ItemEntity(victim.level(), victim.getX(), victim.getY(), victim.getZ(), stack);
        RandomSource random = victim.level().getRandom();
        drop.setDeltaMovement((random.nextDouble() - 0.5D) * 0.2D,
                random.nextDouble() * 0.3D, (random.nextDouble() - 0.5D) * 0.2D);
        event.getDrops().add(drop);
    }

    private static boolean isRareLoot(ItemStack stack) {
        Item item = stack.getItem();
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (path.endsWith("_head") || path.endsWith("_skull")) return true;
        return stack.has(DataComponents.JUKEBOX_PLAYABLE);
    }

    private static boolean hasRareLootOf(LivingDropsEvent event, Item target) {
        for (ItemEntity drop : event.getDrops()) {
            if (drop.getItem().is(target)) return true;
        }
        return false;
    }

    private static boolean hasAnyMusicDisc(LivingDropsEvent event) {
        for (ItemEntity drop : event.getDrops()) {
            if (drop.getItem().has(DataComponents.JUKEBOX_PLAYABLE)) return true;
        }
        return false;
    }

    private static ItemStack getRandomMusicDisc(RandomSource random) {
        var tag = BuiltInRegistries.ITEM.getTag(ItemTags.CREEPER_DROP_MUSIC_DISCS);
        if (tag.isEmpty()) return ItemStack.EMPTY;
        List<Holder<Item>> items = tag.get().stream().toList();
        if (items.isEmpty()) return ItemStack.EMPTY;
        return new ItemStack(items.get(random.nextInt(items.size())).value());
    }

    private static int clamped(int level) {
        return Math.max(1, Math.min(MAX_LEVEL, level));
    }

    private static double rareLootChance(int level) {
        return RARE_LOOT_CHANCE[clamped(level) - 1];
    }

    private static int rollRareLootCount(int level, RandomSource random) {
        int index = clamped(level) - 1;
        int min = RARE_LOOT_MIN[index];
        int max = Math.max(min, RARE_LOOT_MAX[index]);
        return min + random.nextInt(max - min + 1);
    }

    private static double equipDropChance(int level) {
        return EQUIP_DROP_CHANCE[clamped(level) - 1];
    }

    private static double rollEquipDurability(int level, RandomSource random) {
        int index = clamped(level) - 1;
        if (index == 0 && random.nextFloat() < EQUIP_FULL_DURABILITY_CHANCE_L1) return 1.0D;
        double min = EQUIP_DURABILITY_MIN[index];
        double max = Math.max(min, EQUIP_DURABILITY_MAX[index]);
        if (max <= min) return min;
        return min + random.nextDouble() * (max - min);
    }

    /** 物品提示：在附魔名称之后追加效果说明。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (levelOf(stack) <= 0) return;

        List<Component> tooltip = event.getToolTip();
        String enchantName = Component.translatable("enchantment.endbound_ember.plunder").getString();
        int insertAt = tooltip.size();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().startsWith(enchantName)) {
                insertAt = i + 1;
                break;
            }
        }
        tooltip.add(insertAt, Component.translatable("endbound_ember.plunder.desc")
                .withStyle(style -> style.withColor(TOOLTIP_COLOR)));
    }
}
