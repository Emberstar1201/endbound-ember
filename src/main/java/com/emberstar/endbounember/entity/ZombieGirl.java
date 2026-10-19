package com.emberstar.endbounember.entity;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.item.LifeStarItem;
import com.emberstar.endbounember.item.UnownedStardustItem;

import java.util.EnumSet;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.Container;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 丧尸娘。装备栏直接绑定实体的实际 EquipmentSlot，因此原版护甲属性、附魔和同步机制均由原版实体处理。
 * 客户端不添加盔甲渲染层，所以装备只生效，不会覆盖 Alex 细手模型。
 */
public class ZombieGirl extends Zombie implements RangedAttackMob, CrossbowAttackMob {
    private static final EntityDataAccessor<Integer> SKIN_VARIANT =
            SynchedEntityData.defineId(ZombieGirl.class, EntityDataSerializers.INT);
    private static final String OWNER_TAG = "EndboundOwner";
    private static final String SKIN_VARIANT_TAG = "SkinVariant";
    private static final String COMPANION_INVENTORY_TAG = "CompanionInventory";
    private UUID owner;
    /** 仅保存 64 格背包；前 6 格菜单槽位是实体实际装备槽的代理。 */
    protected final ItemStackHandler companionInventory = new ItemStackHandler(64);
    private int tridentCooldown;

    public ZombieGirl(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new LowHealthEscapeGoal(this));
        this.goalSelector.addGoal(1, new TamedFollowOwnerGoal(this, 1.15D));
        this.goalSelector.addGoal(2, new ConditionalMeleeAttackGoal(this));
        this.goalSelector.addGoal(2, new ArrowRecoveryGoal(this));
        this.goalSelector.addGoal(2, new RangedBowAttackGoal<>(this, 1.0D, 20, 15.0F));
        this.goalSelector.addGoal(2, new RangedCrossbowAttackGoal<>(this, 1.0D, 8.0F));
        this.goalSelector.addGoal(2, new TridentAttackGoal(this));
        // 无敌时可移动目标，野生与驯服状态都会四处走动
        this.goalSelector.addGoal(6, new SafeRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new WildWanderGoal(this));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new ZombieGirlTargetingGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN_VARIANT, 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.owner != null) tag.putUUID(OWNER_TAG, this.owner);
        tag.putInt(SKIN_VARIANT_TAG, this.getSkinVariant());
        tag.put(COMPANION_INVENTORY_TAG, this.companionInventory.serializeNBT(this.registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID(OWNER_TAG)) this.owner = tag.getUUID(OWNER_TAG);
        if (tag.contains(SKIN_VARIANT_TAG)) this.setSkinVariant(tag.getInt(SKIN_VARIANT_TAG));
        if (tag.contains(COMPANION_INVENTORY_TAG)) this.companionInventory.deserializeNBT(this.registryAccess(), tag.getCompound(COMPANION_INVENTORY_TAG));
    }

    public int getSkinVariant() {
        return this.entityData.get(SKIN_VARIANT);
    }

    public void setSkinVariant(int variant) {
        this.entityData.set(SKIN_VARIANT, Math.max(0, Math.min(19, variant)));
    }

    @Override
    public SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty,
            net.minecraft.world.entity.MobSpawnType reason,
            @Nullable SpawnGroupData spawnData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData);
        this.setSkinVariant(this.random.nextInt(19) + 1);
        return data;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return switch (this.random.nextInt(3)) {
            case 0 -> EndboundEmber.HUSK_GIRL_IDLE_1.get();
            case 1 -> EndboundEmber.HUSK_GIRL_IDLE_2.get();
            default -> EndboundEmber.HUSK_GIRL_IDLE_3.get();
        };
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return this.random.nextBoolean() ? EndboundEmber.HUSK_GIRL_HUST_1.get() : EndboundEmber.HUSK_GIRL_HUST_2.get();
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() { return EndboundEmber.HUSK_GIRL_FAREWELL_1.get(); }
    @Override public boolean isSunSensitive() { return false; }
    @Override protected boolean convertsInWater() { return false; }
    @Override public boolean isInvertedHealAndHarm() { return false; }
    @Override public void checkDespawn() { }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        if (this.tickCount % 10 == 0) {
            openNearbyPassage();
            if (isTamed()) {
                eatFromCompanionInventory();
                handleBedRest();
            }
        }
    }

    private void openNearbyPassage() {
        BlockPos center = blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 1, 1))) {
            BlockState state = level().getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock && !state.getValue(DoorBlock.OPEN)) {
                DoorBlock.setOpen(this, level(), pos, true);
            } else if (state.getBlock() instanceof TrapDoorBlock && !state.getValue(TrapDoorBlock.OPEN)) {
                level().setBlock(pos, state.setValue(TrapDoorBlock.OPEN, true), 10);
            }
        }
    }

    private void handleBedRest() {
        if (isSleeping()) {
            if (!level().isNight() || getTarget() != null || isInWater()) stopSleeping();
            return;
        }
        if (!level().isNight() || getTarget() != null || isInWater()) return;
        BlockPos center = blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-8, -1, -8), center.offset(8, 1, 8))) {
            BlockState state = level().getBlockState(pos);
            if (!state.is(BlockTags.BEDS) || state.getValue(BedBlock.OCCUPIED)) continue;
            if (distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D) > 64.0D) continue;
            getNavigation().stop();
            startSleeping(pos.immutable());
            return;
        }
    }

    private void eatFromCompanionInventory() {
        if (getHealth() > getMaxHealth() * 0.75F) return;
        for (int i = 0; i < companionInventory.getSlots(); i++) {
            ItemStack stack = companionInventory.getStackInSlot(i);
            if (!isHealingFood(stack)) continue;
            consumeFood(stack);
            if (stack.isEmpty()) companionInventory.setStackInSlot(i, ItemStack.EMPTY);
            return;
        }
    }

    private void consumeFood(ItemStack stack) {
        heal(8.0F);
        if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 1));
            addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
            addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 600));
            addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600));
        }
        stack.shrink(1);
    }

    protected boolean isHealingFood(ItemStack stack) {
        return isTamingFood(stack);
    }

    private boolean feedByPlayer(Player player, ItemStack stack) {
        if (!isTamed() || !isOwnedBy(player) || !isHealingFood(stack) || getHealth() >= getMaxHealth()) return false;
        if (!player.getAbilities().instabuild) consumeFood(stack);
        else {
            ItemStack copy = stack.copyWithCount(1);
            consumeFood(copy);
        }
        return true;
    }

    public boolean isTamed() { return this.owner != null; }
    public boolean isOwnedBy(Player player) {
        return this.owner != null && this.owner.equals(player.getUUID());
    }
    @Nullable public UUID getOwnerUUID() { return this.owner; }
    public void setOwnerUUID(@Nullable UUID owner) { this.owner = owner; }
    @Nullable
    public Player getOwnerPlayer() {
        return this.owner == null ? null : this.level().getPlayerByUUID(this.owner);
    }

    protected boolean isTamingFood(ItemStack stack) {
        return stack.is(Items.ROTTEN_FLESH) || stack.is(Items.BEEF) || stack.is(Items.PORKCHOP)
                || stack.is(Items.CHICKEN) || stack.is(Items.MUTTON) || stack.is(Items.RABBIT);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof UnownedStardustItem
                || stack.getItem() instanceof LifeStarItem) {
            return InteractionResult.PASS;
        }
        if (this.isTamed() && feedByPlayer(player, stack)) {
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (!this.isTamed() && this.isTamingFood(stack)) {
            if (!player.getAbilities().instabuild) stack.shrink(1);
            if (!this.level().isClientSide) {
                this.owner = player.getUUID();
                this.setTarget(null);
                this.navigation.stop();
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (this.isTamed() && this.owner.equals(player.getUUID())) {
            if (!this.level().isClientSide) {
                player.openMenu(new MenuProvider() {
                    @Override public Component getDisplayName() { return Component.translatable("entity.endbound_ember.companion_inventory"); }
                    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                        return new CompanionMenu(id, inventory, ZombieGirl.this);
                    }
                }, buffer -> buffer.writeVarInt(this.getId()));
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean canAttackType(EntityType<?> type) {
        if (isFriendlySoul(type)) return false;
        return super.canAttackType(type);
    }

    /** 玩家、友好生物与同类（丧尸娘、溺尸娘）永远不能成为攻击目标。 */
    public static boolean isFriendlySoul(EntityType<?> type) {
        return type == EntityType.PLAYER
                || type.is(EndboundEmber.FRIENDLY_MOB_TAG)
                || type.is(ModTags.EntityTypes.ZOMBIE_GIRLS);
    }

    public boolean isRangedWeapon(ItemStack stack) {
        return stack.is(Items.BOW) || stack.is(Items.CROSSBOW) || stack.is(Items.TRIDENT);
    }

    /** 是否处于三叉戟远程姿态，背包中取出三叉戟时也算。 */
    public boolean isWieldingTrident() {
        return this.getWeaponForCombat().is(Items.TRIDENT);
    }

    /** 攻击时使用的武器：优先主手，主手无武器时使用背包中的三叉戟。 */
    public ItemStack getWeaponForCombat() {
        ItemStack mainHand = this.getMainHandItem();
        if (!mainHand.isEmpty()) return mainHand;
        return this.findCompanionTrident();
    }

    /** 背包中的三叉戟，供子类覆盖。 */
    public ItemStack findCompanionTrident() {
        return ItemStack.EMPTY;
    }

    public int getCompanionInventorySize() {
        return this.companionInventory.getSlots();
    }

    public ItemStack getCompanionStack(int index) {
        return this.companionInventory.getStackInSlot(index);
    }

    public void setCompanionStack(int index, ItemStack stack) {
        this.companionInventory.setStackInSlot(index, stack);
    }

    private ItemStack takeCompanionProjectile() {
        for (int i = 0; i < this.companionInventory.getSlots(); i++) {
            ItemStack stack = this.companionInventory.getStackInSlot(i);
            if (stack.is(Items.TIPPED_ARROW) || stack.is(Items.SPECTRAL_ARROW) || stack.is(Items.ARROW)) {
                ItemStack result = stack.copyWithCount(1);
                stack.shrink(1);
                return result;
            }
        }
        return ItemStack.EMPTY;
    }

    public void addArrowToCompanionInventory(ItemStack recovered) {
        if (recovered.isEmpty()) return;
        for (int i = 0; i < this.companionInventory.getSlots(); i++) {
            ItemStack stack = this.companionInventory.getStackInSlot(i);
            if (ItemStack.isSameItemSameComponents(stack, recovered)
                    && stack.getCount() < stack.getMaxStackSize()) {
                int amount = Math.min(recovered.getCount(), stack.getMaxStackSize() - stack.getCount());
                stack.grow(amount);
                recovered.shrink(amount);
                if (recovered.isEmpty()) return;
            }
        }
        for (int i = 0; i < this.companionInventory.getSlots(); i++) {
            if (this.companionInventory.getStackInSlot(i).isEmpty()) {
                this.companionInventory.setStackInSlot(i, recovered.copy());
                return;
            }
        }
    }

    public static boolean isAllowedEquipment(int index, ItemStack stack) {
        if (index < 0 || index > 5) return false;
        if (index == 4) {
            return stack.is(Items.BOW) || stack.is(Items.CROSSBOW) || stack.is(Items.TRIDENT)
                    || stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem
                    || stack.getItem() instanceof MaceItem;
        }
        if (index == 5) return true;
        EquipmentSlot slot = CompanionContainer.equipmentSlot(index);
        return stack.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == slot;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        ItemStack weapon = this.getWeaponForCombat();
        if (weapon.getItem() instanceof CrossbowItem crossbow) {
            crossbow.performShooting(this.level(), this, InteractionHand.MAIN_HAND, weapon, velocity, 1.0F, target);
            return;
        }
        if (weapon.is(Items.TRIDENT)) {
            ThrownTrident trident = new ThrownTrident(this.level(), this, weapon.copy());
            double distance = this.distanceTo(target);
            Vec3 predicted = target.position().add(target.getDeltaMovement().scale(Math.min(distance / 2.5D, 12.0D)));
            Vec3 delta = predicted.add(0.0D, target.getBbHeight() * 0.85D, 0.0D)
                    .subtract(this.getX(), this.getEyeY(), this.getZ());
            trident.shoot(delta.x, delta.y + delta.length() * 0.04D, delta.z, 2.5F, 1.0F);
            this.level().addFreshEntity(trident);
            this.releaseUsingItem();
            this.setAggressive(false);
            return;
        }
        ItemStack projectile = weapon.getItem() instanceof BowItem
                ? this.takeCompanionProjectile()
                : this.getProjectile(weapon);
        if (projectile.isEmpty()) return;
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, projectile, velocity, weapon);
        double distance = this.distanceTo(target);
        Vec3 predicted = target.position().add(target.getDeltaMovement().scale(Math.min(distance / 3.0D, 12.0D)));
        double dx = predicted.x - this.getX();
        double dy = predicted.y + target.getBbHeight() * 0.3333333333333333D - arrow.getY();
        double dz = predicted.z - this.getZ();
        distance = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + distance * 0.2D, dz, 3.0F, 0.75F);
        this.level().addFreshEntity(arrow);
    }

    @Override
    public void performCrossbowAttack(LivingEntity target, float velocity) {
        if (this.getMainHandItem().getItem() instanceof CrossbowItem crossbow) {
            crossbow.performShooting(this.level(), this, InteractionHand.MAIN_HAND, this.getMainHandItem(), velocity, 1.0F, target);
        }
    }

    @Override public void setChargingCrossbow(boolean charging) { this.setAggressive(charging); }
    @Override public void onCrossbowAttackPerformed() { this.setAggressive(false); }

    public static class CompanionMenu extends AbstractContainerMenu {
        private final CompanionContainer container;
        public CompanionMenu(int id, Inventory playerInventory, ZombieGirl entity) {
            super(EndboundEmber.COMPANION_MENU.get(), id);
            this.container = new CompanionContainer(entity);
            for (int i = 0; i < 6; i++) addSlot(new EquipmentSlotSlot(container, i, 8 + i * 18, 18));
            for (int i = 0; i < 64; i++) addSlot(new Slot(container, 6 + i, 8 + (i % 8) * 18, 50 + (i / 8) * 18));
            for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 208 + row * 18));
            for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col, 8 + col * 18, 266));
        }
        public CompanionMenu(int id, Inventory playerInventory, int entityId) {
            this(id, playerInventory, (ZombieGirl) playerInventory.player.level().getEntity(entityId));
        }
        @Override public boolean stillValid(Player player) { return container.entity.distanceToSqr(player) < 64.0D; }
        @Override public ItemStack quickMoveStack(Player player, int index) {
            Slot source = this.slots.get(index);
            if (!source.hasItem()) return ItemStack.EMPTY;
            ItemStack original = source.getItem().copy();
            ItemStack moving = source.getItem();
            if (index < 70) {
                if (!moveItemStackTo(moving, 70, this.slots.size(), true)) return ItemStack.EMPTY;
            } else {
                boolean moved = false;
                for (int equipmentIndex = 0; equipmentIndex < 6; equipmentIndex++) {
                    Slot equipment = this.slots.get(equipmentIndex);
                    if (!equipment.hasItem() && equipment.mayPlace(moving) && equipment.safeInsert(moving).getCount() != moving.getCount()) {
                        moved = true;
                        break;
                    }
                }
                if (!moved && !moveItemStackTo(moving, 6, 70, false)) return ItemStack.EMPTY;
            }
            if (moving.getCount() == original.getCount()) return ItemStack.EMPTY;
            if (moving.isEmpty()) source.set(ItemStack.EMPTY); else source.setChanged();
            return original;
        }
    }

    private static final class EquipmentSlotSlot extends Slot {
        private final CompanionContainer container;
        private final int equipmentIndex;
        EquipmentSlotSlot(CompanionContainer container, int index, int x, int y) { super(container, index, x, y); this.container = container; this.equipmentIndex = index; }
        @Override public boolean mayPlace(ItemStack stack) { return container.entity.isAllowedEquipment(equipmentIndex, stack); }
        @Override public int getMaxStackSize() { return 1; }
    }

    private static final class CompanionContainer implements net.minecraft.world.Container {
        private final ZombieGirl entity;
        CompanionContainer(ZombieGirl entity) { this.entity = entity; }
        @Override public ItemStack getItem(int slot) {
            if (slot < 0 || slot >= 70) return ItemStack.EMPTY;
            return slot < 6 ? entity.getItemBySlot(equipmentSlot(slot)) : entity.companionInventory.getStackInSlot(slot - 6);
        }
        @Override public void setItem(int slot, ItemStack stack) {
            if (slot < 0 || slot >= 70) return;
            if (slot < 6) entity.setItemSlot(equipmentSlot(slot), stack);
            else entity.companionInventory.setStackInSlot(slot - 6, stack);
        }
        @Override public int getContainerSize() { return 70; }
        @Override public boolean isEmpty() { for (int i = 0; i < 70; i++) if (!getItem(i).isEmpty()) return false; return true; }
        @Override public ItemStack removeItem(int slot, int amount) { ItemStack current = getItem(slot); if (current.isEmpty()) return ItemStack.EMPTY; ItemStack result = current.split(amount); if (slot < 6) entity.setItemSlot(equipmentSlot(slot), current); return result; }
        @Override public ItemStack removeItemNoUpdate(int slot) { ItemStack result = getItem(slot).copy(); setItem(slot, ItemStack.EMPTY); return result; }
        @Override public void setChanged() { }
        @Override public boolean stillValid(Player player) { return entity.isAlive(); }
        @Override public void clearContent() { for (int i = 0; i < 70; i++) setItem(i, ItemStack.EMPTY); }
        private static EquipmentSlot equipmentSlot(int index) { return switch (index) { case 0 -> EquipmentSlot.HEAD; case 1 -> EquipmentSlot.CHEST; case 2 -> EquipmentSlot.LEGS; case 3 -> EquipmentSlot.FEET; case 4 -> EquipmentSlot.MAINHAND; default -> EquipmentSlot.OFFHAND; }; }
    }

    private static boolean isDangerous(BlockPos pos, net.minecraft.world.level.Level level) {
        var state = level.getBlockState(pos);
        return state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.CACTUS) || state.is(BlockTags.FIRE);
    }

    private static final class SafeRandomStrollGoal extends net.minecraft.world.entity.ai.goal.RandomStrollGoal {
        SafeRandomStrollGoal(ZombieGirl mob, double speed) { super(mob, speed); }

        @Override
        public boolean canUse() {
            return super.canUse() && !isDangerous(mob.blockPosition(), mob.level());
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && !isDangerous(mob.blockPosition(), mob.level());
        }
    }

    private static final class WildWanderGoal extends Goal {
        private final ZombieGirl mob;
        private int cooldown;
        WildWanderGoal(ZombieGirl mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }
        @Override public boolean canUse() {
            if (mob.isTamed() || mob.getTarget() != null || mob.isAggressive()) return false;
            return --cooldown <= 0;
        }
        @Override public boolean canContinueToUse() {
            return !mob.isTamed() && mob.getTarget() == null && !mob.isAggressive()
                    && mob.getNavigation().isInProgress();
        }
        @Override public void start() {
            cooldown = 40 + mob.getRandom().nextInt(80);
            for (int i = 0; i < 12; i++) {
                double x = mob.getX() + (mob.getRandom().nextDouble() - 0.5D) * 16.0D;
                double z = mob.getZ() + (mob.getRandom().nextDouble() - 0.5D) * 16.0D;
                BlockPos pos = BlockPos.containing(x, mob.getY(), z);
                if (!isDangerous(pos, mob.level()) && !isDangerous(pos.below(), mob.level())
                        && mob.getNavigation().moveTo(x, pos.getY(), z, 1.0D)) return;
            }
        }
        @Override public void tick() {
            if (!mob.getNavigation().isInProgress()) stop();
        }
        @Override public void stop() { mob.getNavigation().stop(); }
    }

    private static final class LowHealthEscapeGoal extends Goal {
        private final ZombieGirl mob;
        LowHealthEscapeGoal(ZombieGirl mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }
        private boolean shouldEscape() {
            LivingEntity attacker = mob.getLastHurtByMob();
            return attacker != null && attacker.isAlive() && !(attacker instanceof Player)
                    && mob.getHealth() <= mob.getMaxHealth() * 0.35F;
        }
        @Override public boolean canUse() { return shouldEscape(); }
        @Override public boolean canContinueToUse() { return shouldEscape(); }
        @Override public void start() { mob.setTarget(null); }
        @Override public void tick() {
            LivingEntity attacker = mob.getLastHurtByMob();
            if (attacker == null) return;
            Vec3 away = mob.position().subtract(attacker.position());
            if (away.lengthSqr() < 0.01D) away = new Vec3(1.0D, 0.0D, 0.0D);
            away = away.normalize().scale(10.0D);
            BlockPos destination = BlockPos.containing(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z);
            if (!isDangerous(destination, mob.level()) && !isDangerous(destination.below(), mob.level())) {
                mob.getNavigation().moveTo(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D, 1.35D);
            }
            mob.getLookControl().setLookAt(attacker, 30.0F, 30.0F);
        }
    }

    private static final class ArrowRecoveryGoal extends Goal {
        private final ZombieGirl mob;
        private Arrow arrow;
        ArrowRecoveryGoal(ZombieGirl mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }
        @Override public boolean canUse() {
            if (!(mob.getWeaponForCombat().getItem() instanceof BowItem)
                    || mob.getTarget() == null || !mob.getTarget().isAlive()) return false;
            this.arrow = mob.level().getEntitiesOfClass(Arrow.class,
                    mob.getBoundingBox().inflate(16.0D), candidate -> !candidate.isPickable()
                            && candidate.getOwner() == mob
                            && !candidate.isInWater()).stream()
                    .findFirst().orElse(null);
            return arrow != null;
        }
        @Override public boolean canContinueToUse() {
            return arrow != null && arrow.isAlive() && mob.getTarget() != null;
        }
        @Override public void tick() {
            mob.getLookControl().setLookAt(arrow, 30.0F, 30.0F);
            mob.getNavigation().moveTo(arrow, 1.2D);
            if (mob.distanceToSqr(arrow) <= 2.25D) {
                arrow.discard();
                mob.addArrowToCompanionInventory(arrow.getPickupItemStackOrigin().copyWithCount(1));
                arrow = null;
            }
        }
    }

    private static final class ConditionalMeleeAttackGoal extends MeleeAttackGoal {
        private final ZombieGirl mob;
        ConditionalMeleeAttackGoal(ZombieGirl mob) { super(mob, 1.2D, true); this.mob = mob; }
        @Override public boolean canUse() { return !mob.isRangedWeapon(mob.getWeaponForCombat()) && super.canUse(); }
        @Override public boolean canContinueToUse() { return !mob.isRangedWeapon(mob.getWeaponForCombat()) && super.canContinueToUse(); }
    }

    private static final class TridentAttackGoal extends Goal {
        private final ZombieGirl mob;
        TridentAttackGoal(ZombieGirl mob) { this.mob = mob; this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { return mob.isWieldingTrident() && mob.getTarget() != null && mob.getTarget().isAlive(); }
        @Override public boolean canContinueToUse() { return mob.isWieldingTrident() && mob.getTarget() != null && mob.distanceToSqr(mob.getTarget()) <= 18.0D * 18.0D; }
        @Override public void start() { mob.setAggressive(true); mob.startUsingItem(InteractionHand.MAIN_HAND); mob.tridentCooldown = 10; }
        @Override public void stop() { mob.stopUsingItem(); mob.setAggressive(false); mob.tridentCooldown = 0; }
        @Override public void tick() {
            LivingEntity target = mob.getTarget();
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            mob.getNavigation().stop();
            if (--mob.tridentCooldown <= 0) {
                mob.performRangedAttack(target, 1.0F);
                mob.tridentCooldown = 40;
            }
        }
    }

    public static boolean isHated(EntityType<?> type) { return type.is(ModTags.EntityTypes.ZOMBIE_GIRL_ENEMIES); }
}
