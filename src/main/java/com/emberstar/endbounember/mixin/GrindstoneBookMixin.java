package com.emberstar.endbounember.mixin;

import com.emberstar.endbounember.Config;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Slot.class)
public abstract class GrindstoneBookMixin {
    @Inject(method = "onTake", at = @At("HEAD"))
    private void endboundEmber$grantCombinedBook(Player player, ItemStack result, CallbackInfo ci) {
        if (!(player.containerMenu instanceof GrindstoneMenu menu)
                || !Config.GRINDSTONE_ENCHANTED_BOOK_ENABLED.get()
                || player.level().isClientSide()) {
            return;
        }

        if ((Object) this != menu.getSlot(GrindstoneMenu.RESULT_SLOT)) return;

        ItemStack first = menu.getSlot(GrindstoneMenu.INPUT_SLOT).getItem();
        ItemStack second = menu.getSlot(GrindstoneMenu.ADDITIONAL_SLOT).getItem();
        if (first.isEmpty() == second.isEmpty()) return;

        ItemStack input = first.isEmpty() ? second : first;
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(input);
        if (enchantments.isEmpty()
                || enchantments.entrySet().stream().noneMatch(entry -> !entry.getKey().is(EnchantmentTags.CURSE))) {
            return;
        }

        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponents.STORED_ENCHANTMENTS, enchantments);
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
    }
}
