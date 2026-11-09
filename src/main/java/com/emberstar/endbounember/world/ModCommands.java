package com.emberstar.endbounember.world;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** 营地指令：定位最近营地与测试用的营地放置。 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class ModCommands {
    private static final SimpleCommandExceptionType ERROR_NOT_FOUND =
            new SimpleCommandExceptionType(Component.translatable(
                    "commands.endbound_ember.locate_camp.failed"));
    private static final SimpleCommandExceptionType ERROR_PLACE_FAILED =
            new SimpleCommandExceptionType(Component.translatable(
                    "commands.endbound_ember.summon_camp.failed"));
    private static final SimpleCommandExceptionType ERROR_FOOL_SPAWN_FAILED =
            new SimpleCommandExceptionType(Component.literal("无法生成愚者村民"));
    private static final SimpleCommandExceptionType ERROR_TRADING_DISABLED =
            new SimpleCommandExceptionType(Component.literal("更好的村民交易机制已在配置中关闭"));

    private ModCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("endbound_ember")
                .then(Commands.literal("locate")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("camp")
                                .executes(context -> locateCamp(context.getSource()))))
                .then(Commands.literal("summon")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("camp")
                                .executes(context -> summonCamp(context.getSource())))
                        .then(Commands.literal("fool")
                                .executes(context -> summonFool(context.getSource()))))
                // 时间乱流进入申请的处理，由聊天栏按钮调用，因此不限制权限等级
                .then(Commands.literal("turbulence")
                        .then(Commands.literal("accept")
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(context -> TimeTurbulenceAccess.decide(
                                                context.getSource().getPlayerOrException(),
                                                context.getArgument("id", Integer.class), true))))
                        .then(Commands.literal("deny")
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(context -> TimeTurbulenceAccess.decide(
                                                context.getSource().getPlayerOrException(),
                                                context.getArgument("id", Integer.class), false))))));
    }

    /** 测试用：在指令执行位置直接生成一名愚者村民。 */
    private static int summonFool(CommandSourceStack source) throws CommandSyntaxException {
        if (!Config.BETTER_VILLAGER_TRADING.get()) {
            throw ERROR_TRADING_DISABLED.create();
        }
        ServerLevel level = source.getLevel();
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            throw ERROR_FOOL_SPAWN_FAILED.create();
        }
        BlockPos pos = BlockPos.containing(source.getPosition());
        villager.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, source.getRotation().y, 0.0F);
        level.addFreshEntity(villager);
        BetterVillagerTrading.markAsFool(villager);
        source.sendSuccess(() -> Component.literal("已生成愚者村民："
                + pos.getX() + " " + pos.getY() + " " + pos.getZ()), false);
        return 1;
    }

    /** 测试用：在指令执行位置立即放置一座营地，省去等待自然生成。 */
    private static int summonCamp(CommandSourceStack source) throws CommandSyntaxException {
        BlockPos origin = BlockPos.containing(source.getPosition());
        if (!ZombieGirlCampEvents.placeCampAt(source.getLevel(), origin)) {
            throw ERROR_PLACE_FAILED.create();
        }
        source.sendSuccess(() -> Component.translatable(
                "commands.endbound_ember.summon_camp.success",
                origin.getX(), origin.getY(), origin.getZ()), false);
        return 1;
    }

    private static int locateCamp(CommandSourceStack source) throws CommandSyntaxException {
        // 营地只在主世界生成，因此始终查询主世界的存档数据，支持在下界/末地执行。
        ServerLevel overworld = source.getServer().overworld();
        BlockPos from = BlockPos.containing(source.getPosition());
        BlockPos nearest = ZombieGirlCampSavedData.get(overworld).findNearest(from);
        if (nearest == null) {
            throw ERROR_NOT_FOUND.create();
        }

        int distance = Mth.floor(Math.sqrt(from.distSqr(nearest)));
        // 点击坐标时建议跨维度传送命令，避免在下界按主世界坐标传送。
        String teleport = "/execute in minecraft:overworld run tp @s "
                + nearest.getX() + " " + nearest.getY() + " " + nearest.getZ();
        Component coordinates = ComponentUtils.wrapInSquareBrackets(Component.translatable(
                        "chat.coordinates", nearest.getX(), nearest.getY(), nearest.getZ()))
                .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, teleport))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("chat.coordinates.tooltip"))));
        source.sendSuccess(() -> Component.translatable(
                "commands.endbound_ember.locate_camp.success", coordinates, distance), false);
        return distance;
    }
}
