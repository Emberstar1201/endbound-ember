package com.emberstar.endbounember.block;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.menu.DisenchantmentTableMenu;
import com.emberstar.endbounember.util.ExperienceUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class DisenchantmentTableBlockEntity extends BlockEntity implements MenuProvider, WorldlyContainer {
    private static final int AUTOMATIC_TICKS = 10;
    public int time;
    public float rot;
    public float oRot;
    public float open;
    public float oOpen;
    public float flip;
    public float oFlip;
    public float flipT;
    public float oFlipT;
    public float flipA;
    private int automaticProgress;
    private final Container inventory = new SimpleContainer(3) {
        @Override
        public void setChanged() {
            super.setChanged();
            DisenchantmentTableBlockEntity.this.setChanged();
        }
    };
    private NonNullList<ItemStack> legacyRecovery = NonNullList.create();

    public DisenchantmentTableBlockEntity(BlockPos pos, BlockState state) {
        super(EndboundEmber.DISENCHANTMENT_TABLE_BLOCK_ENTITY.get(), pos, state);
    }

    public Container inventory() {
        return inventory;
    }

    public static void clientTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
            DisenchantmentTableBlockEntity entity) {
        entity.oOpen = entity.open;
        entity.oRot = entity.rot;
        entity.oFlip = entity.flip;
        entity.oFlipT = entity.flipT;
        entity.time++;

        Player player = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3.0, false);
        if (player != null) {
            double dx = player.getX() - (pos.getX() + 0.5);
            double dz = player.getZ() - (pos.getZ() + 0.5);
            float targetRot = (float) Math.atan2(dz, dx);
            float deltaRot = targetRot - entity.rot;
            while (deltaRot >= (float) Math.PI) deltaRot -= (float) (Math.PI * 2);
            while (deltaRot < (float) -Math.PI) deltaRot += (float) (Math.PI * 2);
            entity.rot += deltaRot * 0.4F;
            entity.open = Math.min(1.0F, entity.open + 0.1F);
            if (entity.open < 0.5F || level.random.nextInt(40) == 0) {
                do {
                    entity.flipT += level.random.nextInt(4) - level.random.nextInt(4);
                } while (entity.flip <= entity.flipT + 1.0F && entity.flip >= entity.flipT - 1.0F);
            }
        } else {
            entity.rot += 0.02F;
            entity.open = Math.max(0.0F, entity.open - 0.1F);
        }

        float targetPageSpeed = net.minecraft.util.Mth.clamp((entity.flipT - entity.flip) * 0.4F, -0.2F, 0.2F);
        entity.flipA += (targetPageSpeed - entity.flipA) * 0.9F;
        entity.flip += entity.flipA;
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
            DisenchantmentTableBlockEntity entity) {
        if (!Config.DISENCHANTMENT_AUTOMATIC.get() || entity.hasLegacyRecovery()) {
            entity.automaticProgress = 0;
            return;
        }
        Player player = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 16.0, false);
        if (player == null || entity.inventory.getItem(2).isEmpty() == false || !entity.canAutomaticallyProcess(player)) {
            entity.automaticProgress = 0;
            return;
        }
        if (++entity.automaticProgress < AUTOMATIC_TICKS) return;
        entity.automaticProgress = 0;
        entity.processAutomatically(player);
    }

    private boolean canAutomaticallyProcess(Player player) {
        ItemStack target = inventory.getItem(0);
        if (!target.isEnchanted() || !inventory.getItem(1).is(Items.BOOK)) return false;
        if (target.is(Items.ENCHANTED_BOOK)
                && EnchantmentHelper.getEnchantmentsForCrafting(target).size() < 2) return false;
        if (!Config.DISENCHANTMENT_REQUIRES_EXPERIENCE.get() || player.getAbilities().instabuild) return true;
        int cost = Config.DISENCHANTMENT_EXPERIENCE_COST.get();
        return Config.DISENCHANTMENT_USES_POINTS.get()
                ? ExperienceUtil.availableExperience(player) >= cost
                : player.experienceLevel >= cost;
    }

    private void processAutomatically(Player player) {
        ItemStack target = inventory.getItem(0);
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(target);
        Holder<Enchantment> removed = enchantments.keySet().iterator().next();
        ItemStack output = new ItemStack(Items.ENCHANTED_BOOK);
        if (target.is(Items.ENCHANTED_BOOK)) {
            ItemEnchantments.Mutable remaining = new ItemEnchantments.Mutable(enchantments);
            remaining.removeIf(removed::equals);
            EnchantmentHelper.setEnchantments(output, remaining.toImmutable());
            ItemEnchantments.Mutable extracted = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            extracted.set(removed, enchantments.getLevel(removed));
            EnchantmentHelper.setEnchantments(target, extracted.toImmutable());
        } else {
            EnchantmentHelper.setEnchantments(output, enchantments);
            EnchantmentHelper.setEnchantments(target, ItemEnchantments.EMPTY);
        }
        inventory.setItem(2, output);
        inventory.getItem(1).shrink(1);
        if (Config.DISENCHANTMENT_RESETS_REPAIR_COST.get()) target.set(DataComponents.REPAIR_COST, 0);
        if (!player.getAbilities().instabuild && Config.DISENCHANTMENT_REQUIRES_EXPERIENCE.get()) {
            int cost = Config.DISENCHANTMENT_EXPERIENCE_COST.get();
            if (Config.DISENCHANTMENT_USES_POINTS.get()) player.giveExperiencePoints(-cost);
            else player.giveExperienceLevels(-cost);
        }
        inventory.setChanged();
    }

    @Override public int[] getSlotsForFace(Direction side) {
        if (side == Direction.DOWN) return new int[] {2};
        return new int[] {0, 1};
    }

    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        if (side == Direction.DOWN || hasLegacyRecovery()) {
            return false;
        }
        if (slot == 0) {
            return stack.isEnchanted() && (!stack.is(Items.ENCHANTED_BOOK)
                    || EnchantmentHelper.getEnchantmentsForCrafting(stack).size() >= 2);
        }
        return slot == 1 && stack.is(Items.BOOK);
    }

    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side == Direction.DOWN && slot == 2;
    }

    @Override public int getContainerSize() { return inventory.getContainerSize(); }
    @Override public boolean isEmpty() { return inventory.isEmpty(); }
    @Override public ItemStack getItem(int slot) { return inventory.getItem(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { return inventory.removeItem(slot, amount); }
    @Override public ItemStack removeItemNoUpdate(int slot) { return inventory.removeItemNoUpdate(slot); }
    @Override public void setItem(int slot, ItemStack stack) { inventory.setItem(slot, stack); }
    @Override public void setChanged() { super.setChanged(); }
    @Override public boolean stillValid(Player player) { return inventory.stillValid(player); }
    @Override public void clearContent() { inventory.clearContent(); }

    public boolean hasLegacyRecovery() {
        return !legacyRecovery.isEmpty();
    }

    public ItemStack peekLegacyRecovery() {
        return hasLegacyRecovery() ? legacyRecovery.getFirst() : ItemStack.EMPTY;
    }

    public ItemStack takeLegacyRecovery(int amount) {
        if (!hasLegacyRecovery()) {
            return ItemStack.EMPTY;
        }
        ItemStack first = legacyRecovery.getFirst();
        ItemStack result = first.split(amount);
        if (first.isEmpty()) {
            legacyRecovery.removeFirst();
        }
        setChanged();
        return result;
    }

    public void dropContents(net.minecraft.world.level.Level level, BlockPos pos) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ContainersHelper.drop(level, pos, inventory.removeItemNoUpdate(i));
        }
        while (hasLegacyRecovery()) {
            ContainersHelper.drop(level, pos, takeLegacyRecovery(Integer.MAX_VALUE));
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.endbound_ember.disenchantment_table");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new DisenchantmentTableMenu(id, playerInventory, inventory, this, this.worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        NonNullList<ItemStack> items = NonNullList.withSize(inventory.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) {
            items.set(i, inventory.getItem(i));
        }
        ContainerHelper.saveAllItems(tag, items, registries);
        net.minecraft.nbt.ListTag recoveryItems = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < legacyRecovery.size(); i++) {
            ItemStack stack = legacyRecovery.get(i);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.put("Stack", stack.save(registries));
                itemTag.putInt("Slot", i);
                recoveryItems.add(itemTag);
            }
        }
        tag.put("LegacyRecoveryItems", recoveryItems);
        tag.putInt("AutomaticProgress", automaticProgress);
        tag.putBoolean("LegacyMigrationComplete", true);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        legacyRecovery = NonNullList.create();
        automaticProgress = tag.getInt("AutomaticProgress");
        if (tag.getBoolean("LegacyMigrationComplete")) {
            NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag, items, registries);
            for (int i = 0; i < items.size(); i++) {
                inventory.setItem(i, items.get(i));
            }
            net.minecraft.nbt.ListTag recoveryItems = tag.getList("LegacyRecoveryItems", 10);
            for (int i = 0; i < recoveryItems.size(); i++) {
                ItemStack stack = ItemStack.parse(registries, recoveryItems.getCompound(i).get("Stack")).orElse(ItemStack.EMPTY);
                if (!stack.isEmpty()) legacyRecovery.add(stack);
            }
        } else {
            NonNullList<ItemStack> oldItems = NonNullList.withSize(30, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag, oldItems, registries);
            for (int i = 0; i < oldItems.size(); i++) {
                ItemStack stack = oldItems.get(i);
                if (stack.isEmpty()) {
                    continue;
                }
                if (i < 2) {
                    inventory.setItem(i, stack);
                } else {
                    legacyRecovery.add(stack);
                }
            }
        }
    }

    private static final class ContainersHelper {
        private static void drop(net.minecraft.world.level.Level level, BlockPos pos, ItemStack stack) {
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
    }
}
