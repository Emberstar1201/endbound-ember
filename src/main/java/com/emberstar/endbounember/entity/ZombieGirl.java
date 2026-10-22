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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.Container;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.server.network.Filterable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 丧尸娘。装备栏直接绑定实体的实际 EquipmentSlot，因此原版护甲属性、附魔和同步机制均由原版实体处理。
 * 客户端不添加盔甲渲染层，所以装备只生效，不会覆盖 Alex 细手模型。
 */
public class ZombieGirl extends Zombie implements RangedAttackMob, CrossbowAttackMob {
    private static final EntityDataAccessor<Integer> SKIN_VARIANT =
            SynchedEntityData.defineId(ZombieGirl.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ORDERED_SIT =
            SynchedEntityData.defineId(ZombieGirl.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> OWNER_UUID =
            SynchedEntityData.defineId(ZombieGirl.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> AFFECTION =
            SynchedEntityData.defineId(ZombieGirl.class, EntityDataSerializers.INT);
    private static final String OWNER_TAG = "EndboundOwner";
    private static final String SKIN_VARIANT_TAG = "SkinVariant";
    private static final String COMPANION_INVENTORY_TAG = "CompanionInventory";
    private static final String ORDERED_SIT_TAG = "OrderedSit";
    private static final String AFFECTION_TAG = "Affection";
    private static final String AFFECTION_DAY_TAG = "AffectionDay";
    private static final String COMPANIONSHIP_DAY_TAG = "CompanionshipDay";
    private static final String DAILY_AFFECTION_TAG = "DailyAffection";
    /** 每日好感度上限：陪伴、助战、喂食共用同一份额度。 */
    private static final int DAILY_AFFECTION_LIMIT = 5;
    private static final double COMPANIONSHIP_DISTANCE_SQR = 16.0D * 16.0D;
    /**
     * 丧尸娘的日记分支，按皮肤编号取模选择。
     * 每个分支对应一段不同的「感染经过」，AI 人设会据此只描述该分支的经历。
     */
    private static final String[] DIARY_VARIANTS = {
            "zombie", "zombie_forest", "zombie_plague", "zombie_mine", "zombie_cave", "zombie_siege"};
    private UUID owner;
    private long affectionDay = -1L;
    private long companionshipDay = -1L;
    private int dailyAffection;
    /** 仅保存 64 格背包；前 6 格菜单槽位是实体实际装备槽的代理。 */
    protected final ItemStackHandler companionInventory = new ItemStackHandler(64);
    private int tridentCooldown;
    /** 生命值已在加入世界时随机化（25~50）的标记，避免重复随机。 */
    public static final String HEALTH_ROLLED_TAG = "EndboundHealthRolled";
    private static final String DIARY_BOOK_TAG = "EndboundDiaryBook";
    private static final String DIARY_VARIANT_TAG = "EndboundDiaryVariant";
    private static final String DIARY_BOOK_VERSION_TAG = "EndboundDiaryVersion";
    /** 日记书的排版版本。旧书只有一整页，版本号不同时会被替换为分页版本。 */
    private static final int DIARY_BOOK_VERSION = 2;
    private static final int DIARY_PAGE_COUNT = 4;
    /** 日记书只检查一次，避免每个 tick 扫描全部背包槽位；皮肤变化时重置。 */
    private boolean diaryBookChecked;

    public ZombieGirl(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new LowHealthEscapeGoal(this));
        this.goalSelector.addGoal(1, new RangedThreatEscapeGoal(this));
        this.goalSelector.addGoal(2, new TamedFollowOwnerGoal(this, 1.15D));
        this.goalSelector.addGoal(3, new ConditionalMeleeAttackGoal(this));
        this.goalSelector.addGoal(3, new ArrowRecoveryGoal(this));
        this.goalSelector.addGoal(3, new StationaryBowAttackGoal(this));
        this.goalSelector.addGoal(3, new StationaryCrossbowAttackGoal(this));
        this.goalSelector.addGoal(3, new TridentAttackGoal(this));
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
        builder.define(ORDERED_SIT, false);
        builder.define(OWNER_UUID, "");
        builder.define(AFFECTION, 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.owner != null) tag.putUUID(OWNER_TAG, this.owner);
        tag.putInt(SKIN_VARIANT_TAG, this.getSkinVariant());
        tag.putBoolean(ORDERED_SIT_TAG, this.isOrderedToSit());
        tag.putInt(AFFECTION_TAG, this.getAffection());
        tag.putLong(AFFECTION_DAY_TAG, this.affectionDay);
        tag.putLong(COMPANIONSHIP_DAY_TAG, this.companionshipDay);
        tag.putInt(DAILY_AFFECTION_TAG, this.dailyAffection);
        tag.put(COMPANION_INVENTORY_TAG, this.companionInventory.serializeNBT(this.registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID(OWNER_TAG)) this.setOwnerUUID(tag.getUUID(OWNER_TAG));
        if (tag.contains(SKIN_VARIANT_TAG)) this.setSkinVariant(tag.getInt(SKIN_VARIANT_TAG));
        if (tag.contains(ORDERED_SIT_TAG)) this.setOrderedToSit(tag.getBoolean(ORDERED_SIT_TAG));
        if (tag.contains(AFFECTION_TAG)) this.setAffection(tag.getInt(AFFECTION_TAG));
        this.affectionDay = tag.contains(AFFECTION_DAY_TAG) ? tag.getLong(AFFECTION_DAY_TAG) : -1L;
        this.companionshipDay = tag.contains(COMPANIONSHIP_DAY_TAG) ? tag.getLong(COMPANIONSHIP_DAY_TAG) : -1L;
        this.dailyAffection = tag.contains(DAILY_AFFECTION_TAG) ? tag.getInt(DAILY_AFFECTION_TAG) : 0;
        if (tag.contains(COMPANION_INVENTORY_TAG)) this.companionInventory.deserializeNBT(this.registryAccess(), tag.getCompound(COMPANION_INVENTORY_TAG));
    }

    /** 日记每页对应一段日期记录，使用独立翻译键，客户端按各自语言显示。 */
    private static java.util.List<Filterable<Component>> diaryPages(String variant) {
        java.util.List<Filterable<Component>> pages = new java.util.ArrayList<>(DIARY_PAGE_COUNT);
        boolean healed = variant.startsWith("healed_");
        String sourceVariant = healed ? variant.substring("healed_".length()) : variant;
        for (int page = 1; page <= DIARY_PAGE_COUNT; page++) {
            String key = healed && page == DIARY_PAGE_COUNT
                    ? "diary.endbound_ember.healed." + sourceVariant + ".page4"
                    : "diary.endbound_ember." + sourceVariant + ".page" + page;
            pages.add(Filterable.passThrough(Component.translatable(key)));
        }
        return pages;
    }

    private void ensureDiaryBook() {
        if (this.level().isClientSide || this.diaryBookChecked || this.diaryVariant().isBlank()) return;
        String variant = this.diaryVariant();
        int targetSlot = -1;
        for (int i = 0; i < companionInventory.getSlots(); i++) {
            ItemStack stack = companionInventory.getStackInSlot(i);
            if (!stack.has(DataComponents.CUSTOM_DATA)) continue;
            CompoundTag data = stack.get(DataComponents.CUSTOM_DATA).copyTag();
            if (!data.getBoolean(DIARY_BOOK_TAG)) continue;
            if (variant.equals(data.getString(DIARY_VARIANT_TAG))
                    && data.getInt(DIARY_BOOK_VERSION_TAG) == DIARY_BOOK_VERSION) {
                this.diaryBookChecked = true;
                return;
            }
            companionInventory.setStackInSlot(i, ItemStack.EMPTY);
            if (targetSlot < 0) targetSlot = i;
        }
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag bookData = new CompoundTag();
        bookData.putBoolean(DIARY_BOOK_TAG, true);
        bookData.putString(DIARY_VARIANT_TAG, variant);
        bookData.putInt(DIARY_BOOK_VERSION_TAG, DIARY_BOOK_VERSION);
        book.set(DataComponents.CUSTOM_DATA, CustomData.of(bookData));
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("少女日记 / Girl's Diary"),
                "一位少女 / A Girl",
                0,
                diaryPages(variant),
                false));
        if (targetSlot < 0) {
            for (int i = 0; i < companionInventory.getSlots(); i++) {
                if (companionInventory.getStackInSlot(i).isEmpty()) {
                    targetSlot = i;
                    break;
                }
            }
        }
        if (targetSlot < 0) return;
        companionInventory.setStackInSlot(targetSlot, book);
        this.diaryBookChecked = true;
    }

    public int getSkinVariant() {
        return this.entityData.get(SKIN_VARIANT);
    }

    public boolean isOrderedToSit() {
        return this.entityData.get(ORDERED_SIT);
    }

    public void setOrderedToSit(boolean orderedSit) {
        this.entityData.set(ORDERED_SIT, orderedSit);
        if (orderedSit) this.getNavigation().stop();
    }

    public int getAffection() {
        return this.entityData.get(AFFECTION);
    }

    public void setAffection(int affection) {
        this.entityData.set(AFFECTION, Math.max(0, Math.min(100, affection)));
    }

    public void changeAffection(int amount) {
        this.setAffection(this.getAffection() + amount);
    }

    private void addDailyAffection(int amount) {
        long day = this.level().getDayTime() / 24000L;
        if (this.affectionDay != day) {
            this.affectionDay = day;
            this.dailyAffection = 0;
        }
        int gain = Math.min(Math.max(amount, 0), DAILY_AFFECTION_LIMIT - this.dailyAffection);
        if (gain <= 0) return;
        this.dailyAffection += gain;
        changeAffection(gain);
    }

    public void recordCombatAffection() {
        if (!this.level().isClientSide) addDailyAffection(1);
    }

    public void setSkinVariant(int variant) {
        this.entityData.set(SKIN_VARIANT, Math.max(0, Math.min(19, variant)));
        this.diaryBookChecked = false;
    }

    /** 该少女使用的日记文本变体，子类可覆盖以提供各自的日记集合。 */
    public String diaryVariant() {
        String[] variants = diaryVariants();
        int index = Math.max(0, this.getSkinVariant() - 1);
        return variants[index % variants.length];
    }

    /** 日记变体列表，子类覆盖本方法即可更换整套日记内容。 */
    protected String[] diaryVariants() {
        return DIARY_VARIANTS;
    }

    @Override
    public SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty,
            net.minecraft.world.entity.MobSpawnType reason,
            @Nullable SpawnGroupData spawnData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData);
        this.setSkinVariant(this.random.nextInt(19) + 1);
        // 野生少女初始武器：铁剑、钻石剑、弓或弩；弓手与弩手额外在伙伴背包放入 16 支箭供射击消耗。
        // 主手已有武器时（如实体转换、存档读取）不覆盖。溺尸娘子类会因此把三叉戟收进背包。
        if (this.getMainHandItem().isEmpty()) {
            float roll = this.random.nextFloat();
            if (roll < 0.40F) {
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            } else if (roll < 0.55F) {
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
            } else if (roll < 0.75F) {
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                storeCompanionStack(new ItemStack(Items.ARROW, 16));
            } else {
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
                storeCompanionStack(new ItemStack(Items.ARROW, 16));
            }
        }
        // 盔甲：九成个体穿齐四件，最低铁质；每件约一成概率升级为钻石件。
        if (this.random.nextFloat() < 0.9F) {
            equipArmorPiece(EquipmentSlot.HEAD, Items.IRON_HELMET, Items.DIAMOND_HELMET);
            equipArmorPiece(EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE);
            equipArmorPiece(EquipmentSlot.LEGS, Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS);
            equipArmorPiece(EquipmentSlot.FEET, Items.IRON_BOOTS, Items.DIAMOND_BOOTS);
        }
        return data;
    }

    /** 空槽位时装上盔甲；约一成概率使用钻石件，否则为铁件。 */
    private void equipArmorPiece(EquipmentSlot slot, net.minecraft.world.item.Item iron, net.minecraft.world.item.Item diamond) {
        if (!this.getItemBySlot(slot).isEmpty()) return;
        this.setItemSlot(slot, new ItemStack(this.random.nextFloat() < 0.12F ? diamond : iron));
    }

    /** 把物品放入伙伴背包的第一个空位。 */
    private void storeCompanionStack(ItemStack stack) {
        for (int i = 0; i < this.companionInventory.getSlots(); i++) {
            if (this.companionInventory.getStackInSlot(i).isEmpty()) {
                this.companionInventory.setStackInSlot(i, stack);
                return;
            }
        }
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        if (this instanceof HumanGirl) {
            return switch (this.random.nextInt(3)) {
                case 0 -> EndboundEmber.HUMAN_GIRL_IDLE_1.get();
                case 1 -> EndboundEmber.HUMAN_GIRL_IDLE_2.get();
                default -> EndboundEmber.HUMAN_GIRL_IDLE_3.get();
            };
        }
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
        // 睡眠时强制锁在床位：先清导航与速度，再执行原版 tick，避免任何 Goal 把它从床上推走。
        if (isSleeping()) {
            getNavigation().stop();
            setDeltaMovement(Vec3.ZERO);
        }
        super.tick();
        if (this.level().isClientSide) return;
        ensureDiaryBook();
        if (this.tickCount % 10 == 0) {
            openNearbyPassage();
            if (isTamed()) {
                eatFromCompanionInventory();
                handleBedRest();
            }
        }
        if (!this.level().isClientSide && this.tickCount % 20 == 0) {
            updateCompanionship();
        }
    }

    /**
     * 陪伴判定：每个游戏日只需陪伴一次即可获得 1 点好感度。
     * 已驯服的少女优先判定主人，未驯服（野生）的少女则以最近的玩家作为陪伴对象，
     * 因此「陪在他身边」对驯服与野生状态同样成立。
     */
    private void updateCompanionship() {
        long day = this.level().getDayTime() / 24000L;
        if (this.companionshipDay == day) return;
        if (findCompanionPlayer() == null) return;
        this.companionshipDay = day;
        addDailyAffection(1);
    }

    @Nullable
    private Player findCompanionPlayer() {
        Player ownerPlayer = getOwnerPlayer();
        if (ownerPlayer != null && ownerPlayer.isAlive()
                && distanceToSqr(ownerPlayer) <= COMPANIONSHIP_DISTANCE_SQR) {
            return ownerPlayer;
        }
        Player nearest = this.level().getNearestPlayer(this, Math.sqrt(COMPANIONSHIP_DISTANCE_SQR));
        return nearest != null && nearest.isAlive() ? nearest : null;
    }

    /**
     * 通行导航：与村民一致使用可开门的地面导航，关闭的木门在寻路中视为可通过节点。
     * 此前默认导航把关闭的门当障碍，少女要么绕路要么原地撞门（表现为「把关闭的门当成开的」）；
     * 改为走到门前由 tick 的通行检查实际开门，通过更顺畅。
     */
    @Override
    protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation navigation = new GroundPathNavigation(this, level);
        navigation.setCanOpenDoors(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    /**
     * 通行门开启（局部重写）。
     * 开门前先显式读取门的 OPEN 属性，只有「确实关闭」的木门/活板门才会被处理，已开的门一律跳过，
     * 不再依赖导航推断门的开关状态。
     * 触发条件：
     * 1) 野生少女：正在寻路移动（正常路过/追击时要过门）；
     * 2) 驯服少女：正在寻路移动，或被物理阻挡（挤在门口）且主人就在附近；
     * 3) 驯服少女：主人刚进门并立刻关门——主人位于这扇门的另一侧时立即开门。
     * 只开不关、幂等执行；多个少女挤在同一扇门前不会互相打断。
     */
    private void openNearbyPassage() {
        boolean tamed = this.isTamed() && !this.isOrderedToSit();
        boolean pathing = this.getNavigation().isInProgress();
        boolean pressing = this.horizontalCollision && tamed && this.getOwnerPlayer() != null;
        BlockPos center = blockPosition();
        // 驯服少女多扫一圈，覆盖主人进门后被同伴挤开一格的情况
        int radius = tamed ? 2 : 1;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, 0, -radius), center.offset(radius, 1, radius))) {
            BlockState state = level().getBlockState(pos);
            if (!isClosedPassage(state)) continue;
            boolean adjacent = Math.abs(pos.getX() - center.getX()) <= 1 && Math.abs(pos.getZ() - center.getZ()) <= 1;
            boolean ownerAcross = tamed && isOwnerAcrossDoor(pos, state);
            if ((adjacent && (pathing || pressing)) || ownerAcross) {
                openPassage(pos, state);
            }
        }
    }

    /** 门的真实开关状态：直接读 OPEN 属性；非门方块返回 false，表示无需开门。 */
    private static boolean isClosedPassage(BlockState state) {
        if (state.getBlock() instanceof DoorBlock) return !state.getValue(DoorBlock.OPEN);
        if (state.getBlock() instanceof TrapDoorBlock) return !state.getValue(TrapDoorBlock.OPEN);
        return false;
    }

    /** 执行开门：木门使用原版 setOpen（同步双开门与音效），活板门直接改方块状态。 */
    private void openPassage(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof DoorBlock door) {
            door.setOpen(this, level(), state, pos, true);
        } else if (state.getBlock() instanceof TrapDoorBlock) {
            level().setBlock(pos, state.setValue(TrapDoorBlock.OPEN, true), 10);
        }
    }

    /** 主人是否恰好位于这扇关闭木门的另一侧（少女紧贴门、主人就在门附近）。 */
    private boolean isOwnerAcrossDoor(BlockPos doorPos, BlockState doorState) {
        Player owner = this.getOwnerPlayer();
        if (owner == null || !owner.isAlive()) return false;
        Vec3 center = Vec3.atCenterOf(doorPos);
        if (this.distanceToSqr(center) > 6.25D) return false;
        if (owner.distanceToSqr(center) > 20.25D) return false;
        Direction facing = doorState.getValue(DoorBlock.FACING);
        Vec3 normal = new Vec3(facing.getStepX(), 0.0D, facing.getStepZ());
        double mobSide = this.position().subtract(center).dot(normal);
        double ownerSide = owner.position().subtract(center).dot(normal);
        return mobSide * ownerSide < 0.0D;
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
        if (!(this instanceof HumanGirl)) {
            // 走 NEUTRAL 声道：否则会随"敌对生物"音量滑块一起被压低甚至静音，导致听不到啃肉声
            level().playSound(null, getX(), getY(), getZ(),
                    EndboundEmber.HUSK_GIRL_EAT_1.get(), SoundSource.NEUTRAL, 1.2F, 1.0F);
        }
        if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 1));
            addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
            addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 600));
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
        addDailyAffection(1);
        return true;
    }

    public boolean isTamed() { return this.getOwnerUUID() != null; }
    public boolean isOwnedBy(Player player) {
        return player.getUUID().equals(this.getOwnerUUID());
    }
    @Nullable public UUID getOwnerUUID() {
        if (this.owner != null) return this.owner;
        String synced = this.entityData.get(OWNER_UUID);
        try { return synced.isEmpty() ? null : UUID.fromString(synced); }
        catch (IllegalArgumentException ignored) { return null; }
    }
    public void setOwnerUUID(@Nullable UUID owner) {
        this.owner = owner;
        this.entityData.set(OWNER_UUID, owner == null ? "" : owner.toString());
    }
    @Nullable
    public Player getOwnerPlayer() {
        UUID ownerId = this.getOwnerUUID();
        return ownerId == null ? null : this.level().getPlayerByUUID(ownerId);
    }

    protected boolean isTamingFood(ItemStack stack) {
        return stack.is(Items.ROTTEN_FLESH) || stack.is(Items.BEEF) || stack.is(Items.PORKCHOP)
                || stack.is(Items.CHICKEN) || stack.is(Items.MUTTON) || stack.is(Items.RABBIT);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 手持生命之星右键少女时只进行治愈，不打开背包界面。
        if (stack.getItem() instanceof LifeStarItem) return InteractionResult.PASS;
        // 无主的星尘交给物品自身的 interactLivingEntity 处理收容。
        if (stack.getItem() instanceof UnownedStardustItem) return super.mobInteract(player, hand);
        if (this.isTamed() && feedByPlayer(player, stack)) {
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (!this.isTamed() && this.isTamingFood(stack)) {
            if (!player.getAbilities().instabuild) stack.shrink(1);
            if (!this.level().isClientSide) {
                this.setOwnerUUID(player.getUUID());
                this.setTarget(null);
                this.navigation.stop();
                this.addDailyAffection(1);
                // 驯服喂食与日常喂食体验一致，同样播放嚼肉音效（幸存者少女无专用音效，保持安静）
                if (!(this instanceof HumanGirl)) {
                    this.playSound(EndboundEmber.HUSK_GIRL_EAT_1.get(), 1.0F, 1.0F);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        // 用 isOwnedBy 而非直接读 this.owner：客户端的 owner 字段未同步可能为 null，
        // getOwnerUUID() 会回退到已同步的 entityData，避免在客户端 mobInteract 时 NPE。
        if (this.isTamed() && this.isOwnedBy(player)) {
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

    /** 车万女仆的实体 ID：野生与被驯服的女仆均在保护白名单内；敌对女仆妖精（touhou_little_maid:fairy）不在白名单。 */
    private static final ResourceLocation PROTECTED_MAID_ID =
            ResourceLocation.fromNamespaceAndPath("touhou_little_maid", "maid");

    @Override
    public boolean canAttackType(EntityType<?> type) {
        if (isFriendlySoul(type) || isProtectedMaid(type)) return false;
        return super.canAttackType(type);
    }

    /** 玩家、友好生物与同类（丧尸娘、溺尸娘）永远不能成为攻击目标。 */
    public static boolean isFriendlySoul(EntityType<?> type) {
        return type == EntityType.PLAYER
                || type.is(EndboundEmber.FRIENDLY_MOB_TAG)
                || type.is(ModTags.EntityTypes.ZOMBIE_GIRLS);
    }

    /**
     * 车万女仆保护白名单：野生或被玩家驯服的女仆即便被主人攻击，少女也不会协助攻击她们。
     * 通过实体注册 ID 判断而非直接引用类型，保持对车万女仆模组的软依赖（未安装时恒为 false）。
     */
    public static boolean isProtectedMaid(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).equals(PROTECTED_MAID_ID);
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

    /**
     * 弩装填时的弹药来源。LivingEntity 默认返回空栈，而 1.21.1 的弩只有在 releaseUsing 阶段
     * 通过本方法取得弹药才会真正装填；返回空会让少女反复拉弩、永远装填不完，表现为原地站桩。
     */
    @Override
    public ItemStack getProjectile(ItemStack weaponStack) {
        if (weaponStack.getItem() instanceof CrossbowItem) {
            for (int i = 0; i < this.companionInventory.getSlots(); i++) {
                ItemStack stack = this.companionInventory.getStackInSlot(i);
                if (stack.is(Items.ARROW) || stack.is(Items.TIPPED_ARROW) || stack.is(Items.SPECTRAL_ARROW)) {
                    // draw() 内部会复制该栈再扣减副本，返回副本不会凭空消耗背包弹药
                    return stack.copyWithCount(1);
                }
            }
            return ItemStack.EMPTY;
        }
        return super.getProjectile(weaponStack);
    }

    @Override
    public void performCrossbowAttack(LivingEntity target, float velocity) {
        ItemStack weapon = this.getMainHandItem();
        if (weapon.getItem() instanceof CrossbowItem crossbow && CrossbowItem.isCharged(weapon)) {
            crossbow.performShooting(this.level(), this, InteractionHand.MAIN_HAND, weapon, velocity, 1.0F, target);
            // 装填时未扣减背包弹药，实际射出后在这里消耗一支
            this.takeCompanionProjectile();
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

    /**
     * 持有武器时的近战触及距离：钻石/下界合金级 7 格，其余近战武器（铁/金/石/木剑、斧、三叉戟）5 格，
     * 非武器与空手沿用原版触及。触及按三维距离判定，因此持武器时对头顶数格内的目标同样有效（对空）。
     */
    static double weaponReachSqr(ItemStack weapon, LivingEntity target, double vanillaReachSqr) {
        if (weapon.isEmpty()) return vanillaReachSqr;
        boolean meleeWeapon = weapon.getItem() instanceof SwordItem || weapon.getItem() instanceof AxeItem
                || weapon.getItem() instanceof TridentItem;
        if (!meleeWeapon) return vanillaReachSqr;
        double reach = weapon.is(Items.DIAMOND_SWORD) || weapon.is(Items.DIAMOND_AXE)
                || weapon.is(Items.NETHERITE_SWORD) || weapon.is(Items.NETHERITE_AXE) ? 7.0D : 5.0D;
        return reach * reach + target.getBbWidth();
    }

    /** 近战攻击目标：持有远程武器时停用，触及距离按武器品质提升并支持对空。 */
    static final class ConditionalMeleeAttackGoal extends MeleeAttackGoal {
        private final ZombieGirl mob;
        ConditionalMeleeAttackGoal(ZombieGirl mob) { super(mob, 1.2D, true); this.mob = mob; }
        @Override public boolean canUse() { return !mob.isRangedWeapon(mob.getWeaponForCombat()) && super.canUse(); }
        @Override public boolean canContinueToUse() { return !mob.isRangedWeapon(mob.getWeaponForCombat()) && super.canContinueToUse(); }
        @Override
        protected boolean canPerformAttack(LivingEntity entity) {
            if (!this.isTimeToAttack() || !this.mob.getSensing().hasLineOfSight(entity)) return false;
            // 空手或非近战武器时沿用原版触及；持近战武器时按 5~7 格触及判定（三维距离，支持对空）
            return this.mob.isWithinMeleeAttackRange(entity)
                    || this.mob.distanceToSqr(entity) <= weaponReachSqr(this.mob.getWeaponForCombat(), entity, 0.0D);
        }
    }

    protected static final class RangedThreatEscapeGoal extends Goal {
        private final ZombieGirl mob;
        protected RangedThreatEscapeGoal(ZombieGirl mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }
        private boolean shouldEscape() {
            ItemStack weapon = mob.getWeaponForCombat();
            LivingEntity attacker = mob.getLastHurtByMob();
            return (weapon.is(Items.BOW) || weapon.is(Items.CROSSBOW))
                    && attacker != null && attacker.isAlive() && !(attacker instanceof Player)
                    && mob.getTarget() != null && mob.getTarget().isAlive();
        }
        @Override public boolean canUse() { return shouldEscape(); }
        @Override public boolean canContinueToUse() { return shouldEscape(); }
        @Override public void start() { mob.setAggressive(false); }
        @Override public void tick() {
            LivingEntity attacker = mob.getLastHurtByMob();
            if (attacker == null) return;
            Vec3 away = mob.position().subtract(attacker.position());
            if (away.lengthSqr() < 0.01D) away = new Vec3(1.0D, 0.0D, 0.0D);
            away = away.normalize().scale(8.0D);
            mob.getNavigation().moveTo(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z, 1.25D);
            mob.getLookControl().setLookAt(attacker, 30.0F, 30.0F);
        }
        @Override public void stop() { mob.getNavigation().stop(); }
    }

    protected static final class StationaryBowAttackGoal extends Goal {
        private final ZombieGirl mob;
        private int attackCooldown;
        protected StationaryBowAttackGoal(ZombieGirl mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }
        private boolean valid() {
            return mob.getWeaponForCombat().is(Items.BOW)
                    && mob.getTarget() != null && mob.getTarget().isAlive();
        }
        @Override public boolean canUse() { return valid(); }
        @Override public boolean canContinueToUse() { return valid(); }
        @Override public void start() { attackCooldown = 0; mob.getNavigation().stop(); }
        @Override public void tick() {
            LivingEntity target = mob.getTarget();
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (attackCooldown > 0) attackCooldown--;
            if (attackCooldown <= 0) {
                mob.performRangedAttack(target, 1.0F);
                attackCooldown = 20;
            }
        }
    }

    protected static final class StationaryCrossbowAttackGoal extends Goal {
        private final ZombieGirl mob;
        private int attackCooldown;
        private int pathCooldown;
        protected StationaryCrossbowAttackGoal(ZombieGirl mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }
        private boolean valid() {
            return mob.getWeaponForCombat().getItem() instanceof CrossbowItem
                    && mob.getTarget() != null && mob.getTarget().isAlive();
        }
        /** 已装填，或背包中还有可用弩箭；没有弹药时让位给其他 AI，避免反复空拉弩。 */
        private boolean hasAmmo() {
            ItemStack weapon = mob.getWeaponForCombat();
            return CrossbowItem.isCharged(weapon) || !mob.getProjectile(weapon).isEmpty();
        }
        @Override public boolean canUse() { return valid() && hasAmmo(); }
        @Override public boolean canContinueToUse() { return valid() && hasAmmo(); }
        @Override public void start() { attackCooldown = 0; pathCooldown = 0; }
        @Override public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) return;
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            ItemStack crossbow = mob.getWeaponForCombat();
            if (CrossbowItem.isCharged(crossbow)) {
                // 装填完成：站定瞄准，确认视线后再射击
                mob.getNavigation().stop();
                mob.setAggressive(false);
                if (attackCooldown > 0) attackCooldown--;
                if (attackCooldown <= 0 && mob.getSensing().hasLineOfSight(target)) {
                    mob.performCrossbowAttack(target, 1.0F);
                    attackCooldown = 40;
                }
                return;
            }
            if (mob.isUsingItem()) {
                // 拉弩中：蓄满后必须主动释放，1.21.1 的弩只在 releaseUsing 里完成装填
                mob.getNavigation().stop();
                if (mob.getTicksUsingItem() >= CrossbowItem.getChargeDuration(mob.getUseItem(), mob)) {
                    mob.releaseUsingItem();
                    attackCooldown = 10;
                }
                return;
            }
            // 没有视线或距离过远先靠近，避免原地罚站
            if (!mob.getSensing().hasLineOfSight(target) || mob.distanceToSqr(target) > 256.0D) {
                if (--pathCooldown <= 0) {
                    mob.getNavigation().moveTo(target, 1.0D);
                    pathCooldown = 20;
                }
                return;
            }
            mob.getNavigation().stop();
            mob.setAggressive(true);
            mob.startUsingItem(InteractionHand.MAIN_HAND);
        }
        @Override public void stop() {
            mob.stopUsingItem();
            mob.setAggressive(false);
        }
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
