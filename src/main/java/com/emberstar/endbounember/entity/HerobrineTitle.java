package com.emberstar.endbounember.entity;

/**
 * Herobrine 的词条（头衔）。
 *
 * <p>每个词条只强化 BOSS 本体，代价一律外移到玩家侧（且都能被晨曦护符减免）。
 * 每场战斗固定以「亡灵领主」开场，之后每损失 10% 最大生命值就按枚举顺序换成下一个，
 * 血量打空时正好轮完一圈；血量被治疗回来时也会按同样的规则退回上一个词条。
 * {@link #MYRIAD_LORD} 是第一阶段最后一个词条，会汇总前 9 个词条的全部效果并在其基础上再强化一档。
 * {@link #NECROTIC_DOOM} 是第二阶段专属词条，不参与血量轮换，同样继承前 9 个词条，
 * 并额外开启「亡者之柱」机制。</p>
 */
public enum HerobrineTitle {
    /** 基准词条：只是 20% 追加减伤，无额外机制 */
    UNDEAD_LORD("undead_lord", false),
    /** 判罚之雷更频繁、伤害更高；额外获得带预警圈的落雷 */
    THUNDER_LORD("thunder_lord", false),
    /** 取消落雷，改为定期凋零；玩家治疗量下降 */
    PLAGUE_LORD("plague_lord", false),
    /** 再生更快；每波召唤时给玩家挂虚弱 */
    FLESH_GLUTTON("flesh_glutton", false),
    /** 每波召唤更多、上限更高；精锐死亡留下亡魂区 */
    LEGION_LORD("legion_lord", false),
    /** 移速大幅提升、定期瞬移并分裂幻影分身；瞬移落点附近玩家失明 */
    PHANTOM_WRAITH("phantom_wraith", false),
    /** 连射更长、真空期更短；中箭后短暂无法疾跑 */
    ARROW_MARSHAL("arrow_marshal", false),
    /** 每场一次锁血并回复 25%，额外召来带预警的下界流星；触发瞬间全场黑暗 */
    UNDYING_GRUDGE("undying_grudge", false),
    /** 对持有晨曦护符 / 破晓武器的玩家额外增伤 */
    BANE_OF_DAWN("bane_of_dawn", false),
    /** 第一阶段最后一个词条：汇总前 9 条并各强化一档 */
    MYRIAD_LORD("myriad_lord", true),
    /** 第二阶段专属：继承前 9 条的全部效果，并召出平摊伤害的亡者之柱 */
    NECROTIC_DOOM("necrotic_doom", true);

    private final String id;
    private final boolean composite;

    HerobrineTitle(String id, boolean composite) {
        this.id = id;
        this.composite = composite;
    }

    public String id() {
        return this.id;
    }

    public boolean isComposite() {
        return this.composite;
    }

    /** 血条上显示的头衔翻译键 */
    public String translationKey() {
        return "entity.endbound_ember.herobrine.title." + this.id;
    }

    /** 本词条对应的本体强化数值 */
    public Stats stats() {
        if (this.composite) {
            Stats stats = compositeStats();
            // 万灵之主：开场夺走玩家的盔甲与饰品，击败后才会归还
            stats.plunderGear = this == MYRIAD_LORD;
            // 亡灵末日：在继承全部第一阶段词条的基础上再开启亡者之柱
            stats.doomPillars = this == NECROTIC_DOOM;
            return stats;
        }
        Stats stats = new Stats();
        applyBase(stats);
        return stats;
    }

    /** 把本词条的强化写进给定数值表（不含彩蛋词条） */
    private void applyBase(Stats stats) {
        switch (this) {
            case THUNDER_LORD -> {
                stats.thunderIntervalScale = 0.7F;
                stats.thunderDamage = 5.0F;
                stats.slowOnThunder = true;
                stats.telegraphThunder = true;
            }
            case PLAGUE_LORD -> {
                stats.thunderDisabled = true;
                stats.witherAura = true;
                stats.healReduction = 0.15F;
            }
            case FLESH_GLUTTON -> {
                stats.regenInterval = 15;
                stats.weaknessOnSummon = true;
            }
            case LEGION_LORD -> {
                stats.summonMin = 4;
                stats.summonMax = 5;
                stats.summonCap = 12;
                stats.soulZone = true;
            }
            case PHANTOM_WRAITH -> {
                stats.moveSpeedScale = 1.4D;
                stats.teleport = true;
                stats.blindOnTeleport = true;
                stats.phantomClones = true;
            }
            case ARROW_MARSHAL -> {
                stats.burstSize = 14;
                stats.burstRest = 20;
                stats.noSprintOnArrow = true;
            }
            case UNDYING_GRUDGE -> {
                stats.lockHealth = true;
                stats.darknessOnLock = true;
                stats.netherMeteor = true;
            }
            case BANE_OF_DAWN -> stats.baneOfDawn = true;
            default -> {
                // 亡灵领主：仅保留默认的追加减伤
            }
        }
    }

    /** 彩蛋词条：取前 9 条的最强值，再各强化一档 */
    private static Stats compositeStats() {
        Stats stats = new Stats();
        for (HerobrineTitle title : values()) {
            if (title.composite) {
                continue;
            }
            Stats base = new Stats();
            title.applyBase(base);
            stats.regenInterval = Math.min(stats.regenInterval, base.regenInterval);
            stats.summonMin = Math.max(stats.summonMin, base.summonMin);
            stats.summonMax = Math.max(stats.summonMax, base.summonMax);
            stats.summonCap = Math.max(stats.summonCap, base.summonCap);
            stats.thunderIntervalScale = Math.min(stats.thunderIntervalScale, base.thunderIntervalScale);
            stats.thunderDamage = Math.max(stats.thunderDamage, base.thunderDamage);
            stats.burstSize = Math.max(stats.burstSize, base.burstSize);
            stats.burstRest = Math.min(stats.burstRest, base.burstRest);
            stats.moveSpeedScale = Math.max(stats.moveSpeedScale, base.moveSpeedScale);
            stats.damageReduction = Math.max(stats.damageReduction, base.damageReduction);
            stats.healReduction = Math.max(stats.healReduction, base.healReduction);
            stats.witherAura |= base.witherAura;
            stats.teleport |= base.teleport;
            stats.lockHealth |= base.lockHealth;
            stats.baneOfDawn |= base.baneOfDawn;
            stats.slowOnThunder |= base.slowOnThunder;
            stats.weaknessOnSummon |= base.weaknessOnSummon;
            stats.soulZone |= base.soulZone;
            stats.blindOnTeleport |= base.blindOnTeleport;
            stats.noSprintOnArrow |= base.noSprintOnArrow;
            stats.darknessOnLock |= base.darknessOnLock;
            stats.telegraphThunder |= base.telegraphThunder;
            stats.netherMeteor |= base.netherMeteor;
            stats.phantomClones |= base.phantomClones;
        }
        // 汇总之后再强化一档；落雷与凋零同时保留，不继承「取消落雷」
        stats.damageReduction += 0.05D;
        stats.summonCap += 2;
        stats.regenInterval = Math.max(1, stats.regenInterval - 3);
        stats.thunderIntervalScale *= 0.8F;
        stats.burstSize += 2;
        stats.moveSpeedScale += 0.1D;
        stats.healReduction = 0.25F;
        stats.thunderDisabled = false;
        return stats;
    }

    public static HerobrineTitle byId(String id) {
        for (HerobrineTitle title : values()) {
            if (title.id.equals(id)) {
                return title;
            }
        }
        return UNDEAD_LORD;
    }

    /** 词条对本体的强化数值表，默认值即「亡灵领主」的基准 */
    public static final class Stats {
        /** 追加减伤，叠加在基础 40% 之上 */
        public double damageReduction = 0.20D;
        /** 亡灵再生间隔（tick） */
        public int regenInterval = 25;
        /** 每波召唤数量与附近精锐上限 */
        public int summonMin = 2;
        public int summonMax = 3;
        public int summonCap = 8;
        /** 判罚之雷的间隔倍率与单次伤害 */
        public float thunderIntervalScale = 1.0F;
        public float thunderDamage = 4.0F;
        /** 完全取消落雷 */
        public boolean thunderDisabled;
        /** 改为定期凋零 */
        public boolean witherAura;
        /** 一轮连射的箭数与真空期 */
        public int burstSize = 10;
        public int burstRest = 30;
        /** 移速倍率 */
        public double moveSpeedScale = 1.0D;
        /** 定期瞬移 */
        public boolean teleport;
        /** 每场一次的锁血回血 */
        public boolean lockHealth;
        /** 玩家治疗量削减比例 */
        public float healReduction;
        /** 对持有晨曦护符 / 破晓武器的玩家增伤 */
        public boolean baneOfDawn;
        /** 开场夺走玩家的盔甲与饰品 */
        public boolean plunderGear;

        /** 玩家侧压力开关 */
        public boolean slowOnThunder;
        public boolean weaknessOnSummon;
        public boolean soulZone;
        public boolean blindOnTeleport;
        public boolean noSprintOnArrow;
        public boolean darknessOnLock;
        /** 带预警圈的延迟落雷 */
        public boolean telegraphThunder;
        /** 带预警圈的下界流星 */
        public boolean netherMeteor;
        /** 定期分裂幻影分身 */
        public boolean phantomClones;
        /** 第二阶段：召出亡者之柱平摊伤害 */
        public boolean doomPillars;
    }
}
