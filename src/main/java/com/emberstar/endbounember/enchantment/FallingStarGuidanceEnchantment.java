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
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 坠星之引：重锤专属，最高 III 级。
 * 手持重锤从高处坠落时减缓下坠速度并延长实体交互距离，
 * 让玩家有更充裕的时间在空中瞄准并命中生物。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class FallingStarGuidanceEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "falling_star_guidance"));

    private static final ResourceLocation GRAVITY_ID = modifierId("falling_star_guidance_gravity");
    private static final ResourceLocation REACH_ID = modifierId("falling_star_guidance_reach");

    /** 触发所需的累计下落距离。 */
    private static final float MIN_FALL_DISTANCE = 2.0F;
    private static final double[] GRAVITY_BONUS = {0.0D, -0.03D, -0.04D, -0.05D};
    private static final double[] REACH_BONUS = {0.0D, 0.5D, 1.0D, 1.5D};

    private static final int TOOLTIP_COLOR = 0xC9A66B;

    private FallingStarGuidanceEnchantment() {
    }

    private static ResourceLocation modifierId(String path) {
        return ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, path);
    }

    /** 读取物品上的坠星之引附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    /** 每 tick 维护重力与交互距离修饰符，仅在手持重锤自由坠落时生效。 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        int level = levelOf(player.getMainHandItem());
        if (level > 3) level = 3;
        boolean falling = level > 0
                && !player.onGround()
                && !player.isFallFlying()
                && !player.isInWater()
                && !player.isInLava()
                && !player.isPassenger()
                && player.fallDistance > MIN_FALL_DISTANCE;

        double gravity = GRAVITY_BONUS[level];
        double reach = REACH_BONUS[level];
        manageModifier(player, Attributes.GRAVITY, GRAVITY_ID, gravity, falling);
        manageModifier(player, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, reach, falling);
    }

    /** 需要时添加 / 更新修饰符，不需要时移除。 */
    private static void manageModifier(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id,
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

    /** 物品提示：在附魔名称之后追加瞄准距离说明。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        int level = levelOf(stack);
        if (level <= 0) return;

        List<Component> tooltip = event.getToolTip();
        String enchantName = Component.translatable("enchantment.endbound_ember.falling_star_guidance").getString();
        int insertAt = tooltip.size();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().startsWith(enchantName)) {
                insertAt = i + 1;
                break;
            }
        }

        tooltip.add(insertAt, line("endbound_ember.falling_star_guidance.desc"));
        tooltip.add(insertAt + 1, line("endbound_ember.falling_star_guidance.reach",
                String.format(Locale.ROOT, "%.1f", REACH_BONUS[Math.min(level, 3)])));
    }

    private static Component line(String key, Object... args) {
        return Component.translatable(key, args).withStyle(style -> style.withColor(TOOLTIP_COLOR));
    }
}
