package com.emberstar.endbounember.entity;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 实体类型注册表。
 */
public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, EndboundEmber.MODID);

    /** 丧尸娘 */
    public static final DeferredHolder<EntityType<?>, EntityType<ZombieGirl>> ZOMBIE_GIRL = ENTITY_TYPES.register("zombie_girl",
            () -> EntityType.Builder.of(ZombieGirl::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.74F)
                    .build("zombie_girl"));

    /** 溺尸娘：使用真正独立的实体类型，继承丧尸娘的驯服与装备逻辑。 */
    public static final DeferredHolder<EntityType<?>, EntityType<DrownedGirl>> DROWNED_GIRL = ENTITY_TYPES.register("drowned_girl",
            () -> EntityType.Builder.of(DrownedGirl::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.74F)
                    .build("drowned_girl"));

    public static final DeferredHolder<EntityType<?>, EntityType<HumanGirl>> HUMAN_GIRL = ENTITY_TYPES.register("human_girl",
            () -> EntityType.Builder.of(HumanGirl::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.74F)
                    .build("human_girl"));

    /** Emberstar Sorceress：静止、无碰撞的限时奖励实体。 */
    public static final DeferredHolder<EntityType<?>, EntityType<EmberStarSorceress>> EMBERSTAR_SORCERESS =
            ENTITY_TYPES.register("emberstar_sorceress",
                    () -> EntityType.Builder.<EmberStarSorceress>of(EmberStarSorceress::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.8F)
                            .eyeHeight(1.62F)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build("emberstar_sorceress"));

    public static final DeferredHolder<EntityType<?>, EntityType<DawnArrowEntity>> DAWN_ARROW =
            ENTITY_TYPES.register("dawn_arrow", () -> EntityType.Builder.<DawnArrowEntity>of(DawnArrowEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("dawn_arrow"));

    /** 黎明圣裁「判罚之雷」的纯表现落雷：不点火、不伤害，也不会烧毁掉落物。 */
    public static final DeferredHolder<EntityType<?>, EntityType<DawnJudgementThunder>> DAWN_JUDGEMENT_THUNDER =
            ENTITY_TYPES.register("dawn_judgement_thunder",
                    () -> EntityType.Builder.<DawnJudgementThunder>of(DawnJudgementThunder::new, MobCategory.MISC)
                            .sized(0.2F, 0.2F)
                            .clientTrackingRange(16)
                            .updateInterval(1)
                            .build("dawn_judgement_thunder"));

    /** 「刺破长夜的黎明」剑气。 */
    public static final DeferredHolder<EntityType<?>, EntityType<DawnSwordQiEntity>> DAWN_SWORD_QI =
            ENTITY_TYPES.register("dawn_sword_qi",
                    () -> EntityType.Builder.<DawnSwordQiEntity>of(DawnSwordQiEntity::new, MobCategory.MISC)
                            .sized(0.6F, 0.6F)
                            .clientTrackingRange(16)
                            .updateInterval(1)
                            .build("dawn_sword_qi"));

    /** 母凋零之首：强化版凋零发射的巨大头颅，飞行一段时间后分裂为多个子头颅。 */
    public static final DeferredHolder<EntityType<?>, EntityType<MotherWitherSkull>> MOTHER_WITHER_SKULL =
            ENTITY_TYPES.register("mother_wither_skull",
                    () -> EntityType.Builder.<MotherWitherSkull>of(MotherWitherSkull::new, MobCategory.MISC)
                            .sized(1.4F, 1.4F)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build("mother_wither_skull"));

    /** 下界流星：Herobrine 预警技能的坠落岩块，纯视觉实体，不参与战斗。 */
    public static final DeferredHolder<EntityType<?>, EntityType<NetherMeteor>> NETHER_METEOR =
            ENTITY_TYPES.register("nether_meteor",
                    () -> EntityType.Builder.<NetherMeteor>of(NetherMeteor::new, MobCategory.MISC)
                            .sized(1.4F, 1.4F)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build("nether_meteor"));

    /** 钢筋铁骨触发的引力黑洞：固定位置、无碰撞、只影响生物。 */
    public static final DeferredHolder<EntityType<?>, EntityType<StardustBlackHoleEntity>> STARDUST_BLACK_HOLE =
            ENTITY_TYPES.register("stardust_black_hole",
                    () -> EntityType.Builder.<StardustBlackHoleEntity>of(StardustBlackHoleEntity::new, MobCategory.MISC)
                            .sized(0.2F, 0.2F)
                            .clientTrackingRange(32)
                            .updateInterval(1)
                            .build("stardust_black_hole"));

    /** Herobrine：仪式召唤的自定义 Boss，人形模型，每个玩家一份战斗目标。 */
    public static final DeferredHolder<EntityType<?>, EntityType<Herobrine>> HEROBRINE =
            ENTITY_TYPES.register("herobrine",
                    () -> EntityType.Builder.of(Herobrine::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.8F)
                            .eyeHeight(1.62F)
                            .clientTrackingRange(10)
                            .build("herobrine"));
}
