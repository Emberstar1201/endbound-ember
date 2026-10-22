package com.emberstar.endbounember.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** 被治愈后的友好人类伙伴。 */
public class HumanGirl extends ZombieGirl {
    private static final String DROWNED_SOURCE_TAG = "DrownedSource";
    private static final String LIFE_STAR_DIARY_SOURCE_TAG = "LifeStarDiarySource";
    private String lifeStarDiarySource = "";
    /** 幸存者少女的日记分支，按皮肤编号取模选择，每个分支对应一段不同的获救经历。 */
    private static final String[] DIARY_VARIANTS = {
            "survivor", "survivor_observe", "survivor_resonance", "survivor_heal",
            "survivor_explore", "survivor_village", "survivor_wander"};
    private boolean drownedSource;

    public HumanGirl(EntityType<? extends net.minecraft.world.entity.monster.Zombie> entityType, Level level) {
        super(entityType, level);
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new RangedThreatEscapeGoal(this));
        this.goalSelector.addGoal(2, new TamedFollowOwnerGoal(this, 1.15D));
        this.goalSelector.addGoal(3, new ConditionalMeleeAttackGoal(this));
        this.goalSelector.addGoal(3, new StationaryBowAttackGoal(this));
        this.goalSelector.addGoal(3, new StationaryCrossbowAttackGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new ZombieGirlTargetingGoal(this));
    }

    @Override
    protected boolean isHealingFood(ItemStack stack) {
        return stack.getFoodProperties(this) != null;
    }

    @Override
    protected String[] diaryVariants() {
        return DIARY_VARIANTS;
    }

    @Override
    public String diaryVariant() {
        return this.lifeStarDiarySource.isBlank()
                ? super.diaryVariant()
                : "healed_" + this.lifeStarDiarySource;
    }

    public void setLifeStarDiarySource(String source) {
        this.lifeStarDiarySource = source;
    }

    @Override
    public boolean isInvertedHealAndHarm() {
        return false;
    }

    public boolean isDrownedSource() {
        return this.drownedSource;
    }

    public void setDrownedSource(boolean drownedSource) {
        this.drownedSource = drownedSource;
    }

    @Override
    public boolean canBeLeashed() {
        return true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(DROWNED_SOURCE_TAG, this.drownedSource);
        tag.putString(LIFE_STAR_DIARY_SOURCE_TAG, this.lifeStarDiarySource);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.drownedSource = tag.getBoolean(DROWNED_SOURCE_TAG);
        this.lifeStarDiarySource = tag.getString(LIFE_STAR_DIARY_SOURCE_TAG);
    }
}
