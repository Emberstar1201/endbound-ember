package com.emberstar.endbounember.world;

import com.emberstar.endbounember.EndboundEmber;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** 营地定位指令：与原版 /locate 行为一致，坐标可点击传送，支持跨维度查询。 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class ModCommands {
    private static final SimpleCommandExceptionType ERROR_NOT_FOUND =
            new SimpleCommandExceptionType(Component.translatable(
                    "commands.endbound_ember.locate_camp.failed"));

    private ModCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("endbound_ember")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("locate")
                        .then(Commands.literal("camp")
                                .executes(context -> locateCamp(context.getSource())))));
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
