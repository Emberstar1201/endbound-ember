package com.emberstar.endbounember.menu;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.util.ExperienceUtil;
import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.block.DisenchantmentTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.component.DataComponents;
import org.jetbrains.annotations.Nullable;

public class DisenchantmentTableMenu extends AbstractContainerMenu {
    private static final int INPUT = 0;
    private static final int BOOK = 1;
    private static final int OUTPUT = 2;
    private static final int LEGACY_OUTPUT = 3;
    private static final int PLAYER_INVENTORY_START = 4;

    private final Container input;
    private final Container recoveryView;
    private final Container previewContainer = new SimpleContainer(1);
    @Nullable private final DisenchantmentTableBlockEntity blockEntity;
    private final ContainerLevelAccess access;
    private ItemStack preview = ItemStack.EMPTY;
    @Nullable private Holder<Enchantment> previewExtractedEnchantment;

    public DisenchantmentTableMenu(int id, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(id, playerInventory, new SimpleContainer(3), null, buffer.readBlockPos());
    }

    public DisenchantmentTableMenu(int id, Inventory playerInventory, Container input, BlockPos pos) {
        this(id, playerInventory, input, null, pos);
    }

    public DisenchantmentTableMenu(int id, Inventory playerInventory, Container input,
            DisenchantmentTableBlockEntity blockEntity, BlockPos pos) {
        super(EndboundEmber.DISENCHANTMENT_TABLE_MENU.get(), id);
        if (input.getContainerSize() < 3) {
            throw new IllegalArgumentException("Disenchantment table storage must have at least 3 slots");
        }
        this.input = input;
        this.blockEntity = blockEntity;
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), pos);
        this.recoveryView = new Container() {
            private ItemStack clientStack = ItemStack.EMPTY;

            @Override public int getContainerSize() { return 1; }
            @Override public boolean isEmpty() { return getItem(0).isEmpty(); }
            @Override public ItemStack getItem(int slot) {
                return blockEntity != null ? blockEntity.peekLegacyRecovery() : clientStack;
            }
            @Override public ItemStack removeItem(int slot, int amount) {
                if (blockEntity != null) return blockEntity.takeLegacyRecovery(amount);
                ItemStack result = clientStack.split(amount);
                return result;
            }
            @Override public ItemStack removeItemNoUpdate(int slot) { return removeItem(slot, 1); }
            @Override public void setItem(int slot, ItemStack stack) { clientStack = stack; }
            @Override public void setChanged() { }
            @Override public boolean stillValid(Player player) { return true; }
            @Override public void clearContent() { clientStack = ItemStack.EMPTY; }
        };

        addSlot(new Slot(input, INPUT, 27, 47) {
            @Override public boolean mayPlace(ItemStack stack) {
                return !hasLegacyRecovery() && isDisenchantable(stack);
            }
        });
        addSlot(new Slot(input, BOOK, 76, 47) {
            @Override public boolean mayPlace(ItemStack stack) {
                return !hasLegacyRecovery() && stack.is(Items.BOOK);
            }
        });
        addSlot(new Slot(previewContainer, 0, 134, 47) {
            @Override public boolean isActive() { return !hasLegacyRecovery(); }
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return canTakeResult(player); }
            @Override public void onTake(Player player, ItemStack stack) {
                super.onTake(player, stack);
                commitDisenchantment(player);
            }
        });
        addSlot(new Slot(recoveryView, 0, 134, 47) {
            @Override public boolean isActive() { return hasLegacyRecovery(); }
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return !getItem().isEmpty(); }
            @Override public void onTake(Player player, ItemStack stack) { refreshPreview(); }
        });
        refreshPreview();

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
    }

    private boolean hasLegacyRecovery() {
        return !recoveryView.getItem(0).isEmpty();
    }

    private boolean isDisenchantable(ItemStack stack) {
        if (stack.isEmpty() || stack.is(Items.BOOK)) return false;
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        if (enchantments.isEmpty()) return false;
        return !stack.is(Items.ENCHANTED_BOOK) || enchantments.size() >= 2;
    }

    private ItemStack createResult() {
        previewExtractedEnchantment = null;
        if (hasLegacyRecovery() || !input.getItem(INPUT).isEnchanted() || !input.getItem(BOOK).is(Items.BOOK)) {
            return ItemStack.EMPTY;
        }
        ItemStack target = input.getItem(INPUT);
        ItemEnchantments enchants = EnchantmentHelper.getEnchantmentsForCrafting(target);
        if (enchants.isEmpty() || (target.is(Items.ENCHANTED_BOOK) && enchants.size() < 2)) return ItemStack.EMPTY;
        if (target.is(Items.ENCHANTED_BOOK)) {
            previewExtractedEnchantment = enchants.keySet().iterator().next();
            ItemEnchantments.Mutable remaining = new ItemEnchantments.Mutable(enchants);
            remaining.removeIf(previewExtractedEnchantment::equals);
            ItemStack result = new ItemStack(Items.ENCHANTED_BOOK);
            EnchantmentHelper.setEnchantments(result, remaining.toImmutable());
            return result;
        }
        ItemStack result = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.setEnchantments(result, enchants);
        return result;
    }

    private void refreshPreview() {
        preview = createResult();
        previewContainer.setItem(0, preview.copy());
    }

    private boolean canTakeResult(Player player) {
        if (preview.isEmpty() || hasLegacyRecovery()) return false;
        if (player.getAbilities().instabuild || !Config.DISENCHANTMENT_REQUIRES_EXPERIENCE.get()) return true;
        int cost = Config.DISENCHANTMENT_EXPERIENCE_COST.get();
        if (Config.DISENCHANTMENT_USES_POINTS.get()) return ExperienceUtil.availableExperience(player) >= cost;
        return player.experienceLevel >= cost;
    }

    private void commitDisenchantment(Player player) {
        if (!canTakeResult(player)) return;
        ItemStack target = input.getItem(INPUT);
        ItemEnchantments enchants = EnchantmentHelper.getEnchantmentsForCrafting(target);
        if (target.is(Items.ENCHANTED_BOOK)) {
            ItemEnchantments.Mutable extracted = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            extracted.set(previewExtractedEnchantment, enchants.getLevel(previewExtractedEnchantment));
            EnchantmentHelper.setEnchantments(target, extracted.toImmutable());
        } else {
            EnchantmentHelper.setEnchantments(target, ItemEnchantments.EMPTY);
        }
        ItemStack book = input.getItem(BOOK);
        book.shrink(1);
        if (Config.DISENCHANTMENT_RESETS_REPAIR_COST.get()) {
            target.set(DataComponents.REPAIR_COST, 0);
        }
        if (!player.getAbilities().instabuild && Config.DISENCHANTMENT_REQUIRES_EXPERIENCE.get()) {
            int cost = Config.DISENCHANTMENT_EXPERIENCE_COST.get();
            if (Config.DISENCHANTMENT_USES_POINTS.get()) player.giveExperiencePoints(-cost);
            else player.giveExperienceLevels(-cost);
        }
        input.setChanged();
        refreshPreview();
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        refreshPreview();
    }

    @Override
    public void broadcastChanges() {
        refreshPreview();
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return input.stillValid(player) && access.evaluate((level, pos) ->
                player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0, true);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        if (index == LEGACY_OUTPUT) {
            int originalCount = slot.getItem().getCount();
            ItemStack recovery = slot.getItem().copy();
            if (!moveItemStackTo(recovery, PLAYER_INVENTORY_START, slots.size(), true)) return ItemStack.EMPTY;
            int movedCount = originalCount - recovery.getCount();
            if (movedCount <= 0) return ItemStack.EMPTY;
            slot.remove(movedCount);
            slot.onTake(player, recovery);
            return recovery;
        }
        if (index == OUTPUT) {
            if (!canTakeResult(player)) return ItemStack.EMPTY;
            ItemStack result = preview.copy();
            if (!moveItemStackTo(result, PLAYER_INVENTORY_START, slots.size(), true)) return ItemStack.EMPTY;
            commitDisenchantment(player);
            broadcastChanges();
            return result.copy();
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index >= PLAYER_INVENTORY_START) {
            if (stack.is(Items.BOOK)) {
                if (hasLegacyRecovery() || !moveItemStackTo(stack, BOOK, BOOK + 1, false)) return ItemStack.EMPTY;
            } else if (!hasLegacyRecovery() && isDisenchantable(stack)
                    && !moveItemStackTo(stack, INPUT, INPUT + 1, false)) {
                return ItemStack.EMPTY;
            } else if (hasLegacyRecovery() || !isDisenchantable(stack)) {
                if (index < PLAYER_INVENTORY_START + 27) {
                    if (!moveItemStackTo(stack, PLAYER_INVENTORY_START + 27, slots.size(), false)) return ItemStack.EMPTY;
                } else if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_START + 27, false)) {
                    return ItemStack.EMPTY;
                }
            }
        } else if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, slots.size(), false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        refreshPreview();
        return original;
    }
}
