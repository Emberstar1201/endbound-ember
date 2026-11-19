package com.emberstar.endbounember.item;

import java.util.List;
import java.util.function.Predicate;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.enchantment.DawnEnchantment;
import com.emberstar.endbounember.enchantment.ExperienceGiftEnchantment;
import com.emberstar.endbounember.enchantment.MadeInChinaEnchantment;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;

public final class DawnBreakBowItem extends BowItem {
    public static final int FULL_DRAW_TICKS = 25;
    public static final float FULL_DRAW_SPEED = 6.0F;
    /** 黎明圣裁的显示名颜色。 */
    private static final int NAME_COLOR = 0xFEFFE9;
    private static final String DEFAULTS_APPLIED = "EndboundEmberDawnBreakDefaults";

    public DawnBreakBowItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack))
                .withStyle(style -> style.withColor(NAME_COLOR));
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return ammo -> ammo.is(EndboundEmber.DAWN_ARROW.get());
    }

    @Override
    public ItemStack getDefaultCreativeAmmo(Player player, ItemStack weapon) {
        return new ItemStack(EndboundEmber.DAWN_ARROW.get());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel) applyDefaultEnchantments(stack, level.registryAccess());
        boolean hasAmmo = getAllSupportedProjectiles().test(player.getProjectile(stack));
        InteractionResultHolder<ItemStack> result = EventHooks.onArrowNock(stack, level, player, hand, hasAmmo);
        if (result != null) return result;
        if (!hasAmmo) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity shooter, int timeLeft) {
        if (!(shooter instanceof Player player)) return;
        ItemStack ammo = player.getProjectile(stack);
        if (!getAllSupportedProjectiles().test(ammo)) return;

        int charge = EventHooks.onArrowLoose(stack, level, player, getUseDuration(stack, shooter) - timeLeft, true);
        if (charge < 0) return;
        float power = drawPower(charge);
        if (power < 0.1F) return;

        if (level instanceof ServerLevel serverLevel) {
            applyDefaultEnchantments(stack, serverLevel.registryAccess());
            List<ItemStack> projectiles = draw(stack, ammo, player);
            if (projectiles.isEmpty()) return;
            shoot(serverLevel, player, player.getUsedItemHand(), stack, projectiles,
                    power * FULL_DRAW_SPEED, 1.0F, power == 1.0F, null);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT,
                SoundSource.PLAYERS, 1.0F, 1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F);
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    private static float drawPower(int ticks) {
        float draw = (float) ticks / FULL_DRAW_TICKS;
        return Math.min((draw * draw + 2.0F * draw) / 3.0F, 1.0F);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level instanceof ServerLevel) applyDefaultEnchantments(stack, level.registryAccess());
    }

    @Override
    public void onCraftedPostProcess(ItemStack stack, Level level) {
        if (level instanceof ServerLevel) applyDefaultEnchantments(stack, level.registryAccess());
    }

    // Initialize once so grindstone and player-applied enchantment changes remain effective.
    public static ItemStack applyDefaultEnchantments(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains(DEFAULTS_APPLIED)) return stack;
        var enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
        var defaults = new ItemEnchantments.Mutable(stack.getEnchantments());
        addDefault(defaults, enchantments, Enchantments.POWER, 12);
        addDefault(defaults, enchantments, Enchantments.FLAME, 1);
        addDefault(defaults, enchantments, ExperienceGiftEnchantment.KEY, 1);
        addDefault(defaults, enchantments, Enchantments.SMITE, 12);
        addDefault(defaults, enchantments, Enchantments.LOOTING, 12);
        addDefault(defaults, enchantments, MadeInChinaEnchantment.KEY, 1);
        addDefault(defaults, enchantments, DawnEnchantment.KEY, 1);
        stack.set(DataComponents.ENCHANTMENTS, defaults.toImmutable());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(DEFAULTS_APPLIED, true));
        return stack;
    }

    private static void addDefault(ItemEnchantments.Mutable defaults, HolderLookup.RegistryLookup<Enchantment> registry,
            ResourceKey<Enchantment> key, int level) {
        registry.get(key).ifPresent(holder -> {
            if (defaults.keySet().stream().allMatch(existing -> existing.equals(holder)
                    || Enchantment.areCompatible(existing, holder))) {
                defaults.upgrade(holder, level);
            }
        });
    }
}
