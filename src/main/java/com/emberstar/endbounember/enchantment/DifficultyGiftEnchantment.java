package com.emberstar.endbounember.enchantment;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.emberstar.endbounember.EndboundEmber;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 难度馈赠：武器与盔甲通用，最高 I 级。
 * 武器提供随难度提升的伤害与攻击速度加成，盔甲提供护甲、韧性与每件独立的最大生命加成。
 * 和平难度下全部加成为 0。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class DifficultyGiftEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "difficulty_gift"));

    private static final ResourceLocation ATTACK_SPEED_ID = modifierId("difficulty_gift_attack_speed");
    private static final ResourceLocation ARMOR_ID = modifierId("difficulty_gift_armor");
    private static final ResourceLocation TOUGHNESS_ID = modifierId("difficulty_gift_toughness");
    private static final ResourceLocation HEALTH_HEAD_ID = modifierId("difficulty_gift_health_head");
    private static final ResourceLocation HEALTH_CHEST_ID = modifierId("difficulty_gift_health_chest");
    private static final ResourceLocation HEALTH_LEGS_ID = modifierId("difficulty_gift_health_legs");
    private static final ResourceLocation HEALTH_FEET_ID = modifierId("difficulty_gift_health_feet");

    private static final int TOOLTIP_COLOR = 0xD9C48F;

    private DifficultyGiftEnchantment() {
    }

    private static ResourceLocation modifierId(String path) {
        return ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, path);
    }

    /** 读取物品上的难度馈赠附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    private static double damageBonus(Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> 0.0D;
            case EASY -> 3.0D;
            case NORMAL -> 6.0D;
            case HARD -> 9.0D;
        };
    }

    private static double attackSpeedBonus(Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> 0.0D;
            case EASY -> 1.2D;
            case NORMAL -> 1.6D;
            case HARD -> 2.8D;
        };
    }

    private static double armorBonus(Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> 0.0D;
            case EASY -> 3.0D;
            case NORMAL -> 6.0D;
            case HARD -> 10.0D;
        };
    }

    private static double toughnessBonus(Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> 0.0D;
            case EASY -> 1.0D;
            case NORMAL -> 2.0D;
            case HARD -> 4.0D;
        };
    }

    private static double healthPerArmor(Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> 0.0D;
            case EASY -> 1.0D;
            case NORMAL -> 3.0D;
            case HARD -> 9.0D;
        };
    }

    /** 四件盔甲全部附魔时的生命上限合计，用于提示文本。 */
    private static double healthFullSet(Difficulty difficulty) {
        return healthPerArmor(difficulty) * 4.0D;
    }

    /** 主手或副手武器带难度馈赠时，按当前难度追加固定伤害。 */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;

        int level = levelOf(attacker.getMainHandItem());
        if (level <= 0) level = levelOf(attacker.getOffhandItem());
        if (level <= 0) return;

        double bonus = damageBonus(attacker.level().getDifficulty());
        if (bonus > 0.0D) {
            event.setAmount(event.getAmount() + (float) bonus);
        }
    }

    /** 每 tick 维护攻速 / 护甲 / 韧性 / 生命上限四组修饰符，数值随难度变化。 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Difficulty difficulty = player.level().getDifficulty();
        boolean peaceful = difficulty == Difficulty.PEACEFUL;

        // 武器加成：主手或副手带附魔
        boolean hasWeapon = levelOf(player.getMainHandItem()) > 0 || levelOf(player.getOffhandItem()) > 0;
        double speed = peaceful ? 0.0D : attackSpeedBonus(difficulty);
        manageModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, speed, hasWeapon && speed > 0.0D);

        // 盔甲加成：任意部位带附魔
        boolean hasArmor = hasEnchantedArmor(player);
        double armor = peaceful ? 0.0D : armorBonus(difficulty);
        double toughness = peaceful ? 0.0D : toughnessBonus(difficulty);
        manageModifier(player, Attributes.ARMOR, ARMOR_ID, armor, hasArmor && armor > 0.0D);
        manageModifier(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, toughness, hasArmor && toughness > 0.0D);

        // 生命上限：每件附魔盔甲独立提供一份加成
        double health = peaceful ? 0.0D : healthPerArmor(difficulty);
        boolean changed = applyHealthModifier(player, EquipmentSlot.HEAD, HEALTH_HEAD_ID, health);
        changed |= applyHealthModifier(player, EquipmentSlot.CHEST, HEALTH_CHEST_ID, health);
        changed |= applyHealthModifier(player, EquipmentSlot.LEGS, HEALTH_LEGS_ID, health);
        changed |= applyHealthModifier(player, EquipmentSlot.FEET, HEALTH_FEET_ID, health);
        if (changed) {
            float max = player.getMaxHealth();
            if (player.getHealth() > max) player.setHealth(max);
        }
    }

    private static boolean hasEnchantedArmor(Player player) {
        return levelOf(player.getItemBySlot(EquipmentSlot.HEAD)) > 0
                || levelOf(player.getItemBySlot(EquipmentSlot.CHEST)) > 0
                || levelOf(player.getItemBySlot(EquipmentSlot.LEGS)) > 0
                || levelOf(player.getItemBySlot(EquipmentSlot.FEET)) > 0;
    }

    /** 需要时添加 / 更新修饰符，不需要时移除。 */
    private static void manageModifier(Player player, Holder<Attribute> attribute, ResourceLocation id,
            double value, boolean shouldHave) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;

        AttributeModifier existing = instance.getModifier(id);
        if (!shouldHave) {
            if (existing != null) instance.removeModifier(id);
            return;
        }
        if (existing != null && existing.amount() == value) return;
        if (existing != null) instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, value, AttributeModifier.Operation.ADD_VALUE));
    }

    /** 单件盔甲的生命上限修饰符，返回值表示修饰符是否发生变化。 */
    private static boolean applyHealthModifier(ServerPlayer player, EquipmentSlot slot, ResourceLocation id,
            double healthPerArmor) {
        AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null) return false;

        double value = levelOf(player.getItemBySlot(slot)) > 0 && healthPerArmor > 0.0D ? healthPerArmor : 0.0D;
        AttributeModifier existing = instance.getModifier(id);

        if (value <= 0.0D) {
            if (existing == null) return false;
            instance.removeModifier(id);
            return true;
        }
        if (existing != null && existing.amount() == value) return false;
        if (existing != null) instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, value, AttributeModifier.Operation.ADD_VALUE));
        return true;
    }

    /** 物品提示：在附魔名称之后追加难度加成说明。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (levelOf(stack) <= 0) return;

        List<Component> tooltip = event.getToolTip();
        String enchantName = Component.translatable("enchantment.endbound_ember.difficulty_gift").getString();
        int insertAt = tooltip.size();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().startsWith(enchantName)) {
                insertAt = i + 1;
                break;
            }
        }

        tooltip.add(insertAt, line("endbound_ember.difficulty_gift.desc"));
        tooltip.add(insertAt + 1, line("endbound_ember.difficulty_gift.health",
                String.format(Locale.ROOT, "%.0f", healthFullSet(Difficulty.HARD))));
    }

    private static Component line(String key, Object... args) {
        return Component.translatable(key, args).withStyle(style -> style.withColor(TOOLTIP_COLOR));
    }
}
