package com.emberstar.endbounember.world;

import javax.annotation.Nullable;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.client.HaloWearing;
import com.emberstar.endbounember.mixin.DisplayInfoAccessor;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 成就发放工具。
 *
 * <p>少女系列成就（发现 / 驯服 / 治愈 / 共眠）在原版没有对应的触发器
 * （{@code tame_animal} 只认 {@code TamableAnimal}），统一使用
 * {@code minecraft:impossible} 触发器并在代码中手动发放。</p>
 */
public final class ModAdvancements {
    /** impossible 触发器成就的统一条件名，与各成就 JSON 中的 criteria 键一致。 */
    private static final String CRITERION = "granted";

    /** 「友善的亡灵？」：靠近一只丧尸娘或溺尸娘。 */
    public static final String FRIENDLY_UNDEAD = "friendly_undead";
    /** 「令人心疼的少女」：驯服一只丧尸娘或溺尸娘。 */
    public static final String TAME_GIRL = "tame_girl";
    /** 「驶向第2次生命」：治愈一只丧尸娘或溺尸娘。 */
    public static final String SECOND_LIFE = "second_life";
    /** 「美梦重现」：与正在睡觉的幸存者少女一同入睡。 */
    public static final String SWEET_DREAMS = "sweet_dreams";
    /** 「第四维度的常客」：举行时间光环仪式、获得并佩戴时光光环。 */
    public static final String VISITOR_OF_THE_FOURTH_DIMENSION = "visitor_of_the_fourth_dimension";
    /** 「不被世界承认的少女」：注视一只自然刷新的星尘魔女。 */
    public static final String UNRECOGNIZED_GIRL = "unrecognized_girl";

    private ModAdvancements() {
    }

    /** 发放成就；已完成或成就不存在时静默跳过。 */
    public static void award(ServerPlayer player, String path) {
        AdvancementHolder holder = find(player, path);
        if (holder == null) return;
        if (player.getAdvancements().getOrStartProgress(holder).isDone()) return;
        // 「不被世界承认的少女」未佩戴星尘光环时连聊天栏公告一起压掉，
        // 否则成就名会在解锁瞬间写进聊天栏，提示条藏住了也没用
        if (UNRECOGNIZED_GIRL.equals(path) && !HaloWearing.stardustHalo(player)) {
            DisplayInfo display = holder.value().display().orElse(null);
            if (display != null) {
                DisplayInfoAccessor accessor = (DisplayInfoAccessor) display;
                boolean announce = accessor.endbound$getAnnounceChat();
                accessor.endbound$setAnnounceChat(false);
                try {
                    player.getAdvancements().award(holder, CRITERION);
                } finally {
                    accessor.endbound$setAnnounceChat(announce);
                }
                return;
            }
        }
        player.getAdvancements().award(holder, CRITERION);
    }

    /** 是否已达成指定成就（用于跳过重复检测，省掉每轮的实体扫描）。 */
    public static boolean hasDone(ServerPlayer player, String path) {
        AdvancementHolder holder = find(player, path);
        if (holder == null) return true;
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    @Nullable
    private static AdvancementHolder find(ServerPlayer player, String path) {
        MinecraftServer server = player.getServer();
        if (server == null) return null;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, path);
        return server.getAdvancements().get(id);
    }
}
