package com.emberstar.endbounember.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.network.EnchantmentPreviewClientCache;
import com.emberstar.endbounember.network.EnchantmentPreviewPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;

/**
 * 在附魔台界面把服务端算好的三档结果直接覆盖显示在选项文字区域。
 *
 * <p>只覆盖原本刷新乱码文字的那一段，左侧的 1/2/3 等级图标与右侧消耗等级保持原样；
 * 数据来自 {@link EnchantmentPreviewClientCache}，没有收到预览时不改动界面。</p>
 */
@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public final class EnchantmentPreviewRenderer {
    /* 与原版 renderBg 中选项文字区域一致的坐标偏移 */
    private static final int ROW_LEFT_OFFSET = 60;
    private static final int ROW_RIGHT_OFFSET = 168;
    private static final int TEXT_LEFT_OFFSET = 80;
    private static final int ROW_TOP_OFFSET = 14;
    private static final int ROW_HEIGHT = 19;
    private static final int COST_RIGHT_OFFSET = 166;

    private static final int PANEL_COLOR = 0xF0100C08;
    private static final int AVAILABLE_COLOR = 0xFFFFFF;
    private static final int LOCKED_COLOR = 0xA8A8A8;
    private static final int LINE_HEIGHT = 9;
    private static final String ELLIPSIS = "…";
    private static final String NAME_SEPARATOR = "、";

    private EnchantmentPreviewRenderer() {
    }

    @SubscribeEvent
    public static void onForeground(ContainerScreenEvent.Render.Foreground event) {
        if (!Config.BETTER_LUCK_ENCHANTMENT.get()) return;
        if (!(event.getContainerScreen() instanceof EnchantmentScreen screen)) return;

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null) return;

        EnchantmentMenu menu = screen.getMenu();
        List<List<EnchantmentPreviewPayload.Entry>> rows = EnchantmentPreviewClientCache.rowsFor(menu.containerId);
        if (rows.size() < 3) return;

        Registry<Enchantment> registry = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        Font font = minecraft.font;
        GuiGraphics graphics = event.getGuiGraphics();
        // 事件触发时 pose 已平移到 (leftPos, topPos)，因此这里全部使用界面局部坐标
        int goldCount = menu.getGoldCount();
        int localMouseX = event.getMouseX() - screen.getGuiLeft();
        int localMouseY = event.getMouseY() - screen.getGuiTop();
        int hoveredRow = -1;

        for (int row = 0; row < 3; row++) {
            List<EnchantmentPreviewPayload.Entry> entries = rows.get(row);
            int cost = menu.costs[row];
            if (entries.isEmpty() || cost <= 0) continue;

            int rowTop = ROW_TOP_OFFSET + ROW_HEIGHT * row;
            if (localMouseX >= ROW_LEFT_OFFSET && localMouseX < ROW_RIGHT_OFFSET
                    && localMouseY >= rowTop && localMouseY < rowTop + ROW_HEIGHT) {
                hoveredRow = row;
            }

            int textRight = COST_RIGHT_OFFSET - font.width(Integer.toString(cost));
            int textLeft = TEXT_LEFT_OFFSET + 1;
            int textWidth = textRight - textLeft - 1;
            if (textWidth < 20) continue;

            // 遮住原版的乱码文字，仅为阅读清晰，不影响任何交互判定
            graphics.fill(TEXT_LEFT_OFFSET, rowTop, textRight, rowTop + ROW_HEIGHT, PANEL_COLOR);

            boolean affordable = goldCount >= row + 1
                    && (player.experienceLevel >= cost || player.getAbilities().instabuild);
            int color = affordable ? AVAILABLE_COLOR : LOCKED_COLOR;

            List<String> lines = wrapNames(font, joinNames(registry, entries), textWidth);
            int lineTop = rowTop + (lines.size() > 1 ? 1 : 2);
            for (int line = 0; line < lines.size(); line++) {
                graphics.drawString(font, lines.get(line), textLeft, lineTop + LINE_HEIGHT * line, color, false);
            }
        }

        if (hoveredRow >= 0) {
            List<Component> tooltip = tooltipLines(registry, rows.get(hoveredRow));
            if (!tooltip.isEmpty()) {
                // pose 当前位于界面左上角，绘制提示框前先平移回屏幕坐标系
                graphics.pose().pushPose();
                graphics.pose().translate(-screen.getGuiLeft(), -screen.getGuiTop(), 0.0F);
                graphics.renderComponentTooltip(font, tooltip, event.getMouseX(), event.getMouseY());
                graphics.pose().popPose();
            }
        }
    }

    /** 提示框内容：附魔名与等级，下面跟随该附魔的描述文本（与 Enchantment Descriptions 的格式一致） */
    private static List<Component> tooltipLines(Registry<Enchantment> registry,
            List<EnchantmentPreviewPayload.Entry> entries) {
        List<Component> lines = new ArrayList<>();
        for (EnchantmentPreviewPayload.Entry entry : entries) {
            Optional<Holder.Reference<Enchantment>> holder = registry.getHolder(entry.enchantment());
            if (holder.isEmpty()) continue;
            if (!lines.isEmpty()) lines.add(Component.empty());
            lines.add(Enchantment.getFullname(holder.get(), entry.level()));
            Component description = description(holder.get().value());
            if (description != null) lines.add(description);
        }
        return lines;
    }

    /**
     * 读取附魔描述文本：优先 {@code <描述键>.desc}，其次 {@code <描述键>.description}，
     * 并兼容本模组已有的短键（去掉 {@code enchantment.} 前缀）；都找不到时返回 null，只显示附魔名。
     */
    private static Component description(Enchantment enchantment) {
        if (!(enchantment.description().getContents() instanceof TranslatableContents contents)) return null;
        String base = contents.getKey();
        String stripped = base.startsWith("enchantment.") ? base.substring("enchantment.".length()) : base;
        String key = findDescriptionKey(base, stripped);
        return key == null ? null : Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY);
    }

    private static String findDescriptionKey(String base, String stripped) {
        for (String prefix : new String[] {base, stripped}) {
            String descriptionKey = prefix + ".desc";
            if (I18n.exists(descriptionKey)) return descriptionKey;
            descriptionKey = prefix + ".description";
            if (I18n.exists(descriptionKey)) return descriptionKey;
        }
        return null;
    }

    /** 把该档的所有附魔拼成“锋利 IV、耐久 III”这样的文本；客户端按当前语言解析名称 */
    private static String joinNames(Registry<Enchantment> registry, List<EnchantmentPreviewPayload.Entry> entries) {
        StringBuilder builder = new StringBuilder();
        for (EnchantmentPreviewPayload.Entry entry : entries) {
            Optional<Holder.Reference<Enchantment>> holder = registry.getHolder(entry.enchantment());
            if (holder.isEmpty()) continue;
            if (!builder.isEmpty()) builder.append(NAME_SEPARATOR);
            builder.append(Enchantment.getFullname(holder.get(), entry.level()).getString());
        }
        return builder.toString();
    }

    /** 按附魔名逐个换行，超过两行时截断并加省略号 */
    private static List<String> wrapNames(Font font, String text, int width) {
        List<String> lines = new ArrayList<>(2);
        String[] tokens = text.split(NAME_SEPARATOR);
        int index = 0;

        for (int lineNo = 0; lineNo < 2 && index < tokens.length; lineNo++) {
            StringBuilder line = new StringBuilder();
            while (index < tokens.length) {
                String piece = line.isEmpty() ? tokens[index] : NAME_SEPARATOR + tokens[index];
                if (font.width(line + piece) <= width) {
                    line.append(piece);
                    index++;
                } else {
                    break;
                }
            }

            if (line.isEmpty()) {
                // 单个附魔名都放不下时按字符截断
                lines.add(clip(font, tokens[index], width - font.width(ELLIPSIS)) + ELLIPSIS);
                return lines;
            }

            if (index < tokens.length && lineNo == 1) {
                // 已经是最后一行，余下的内容用省略号收尾
                String trimmed = line.toString();
                while (!trimmed.isEmpty() && font.width(trimmed + ELLIPSIS) > width) {
                    trimmed = trimmed.substring(0, trimmed.length() - 1);
                }
                lines.add(trimmed + ELLIPSIS);
                return lines;
            }

            lines.add(line.toString());
        }
        return lines;
    }

    /** 取能放进给定宽度的最长前缀 */
    private static String clip(Font font, String text, int width) {
        String clipped = text;
        while (!clipped.isEmpty() && font.width(clipped) > width) {
            clipped = clipped.substring(0, clipped.length() - 1);
        }
        return clipped;
    }
}
