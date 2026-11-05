package com.emberstar.endbounember.mixin;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.client.HaloWearing;

import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 「第四维度的常客」在未完成时，把悬停提示的标题与说明全部替换成不断变化的乱码：
 * 把原文按一种字符集编码后用另一种字符集解码（模拟 GBK / Shift_JIS / Big5 等错误转码），
 * 每隔百余毫秒换一组错误的编解码组合，读起来就是满屏错乱的方块与假汉字。
 * 完成后恢复成正常的成就名与那句以「克洛诺娅」落款的引言。
 *
 * <p>「不被世界承认的少女」则相反：未佩戴星尘光环时，成就名与说明一个字都没有；
 * 戴上星尘光环后再正常的显示出来。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mixin(AdvancementWidget.class)
public abstract class AdvancementWidgetGarbledTitleMixin {
    /** 「第四维度的常客」：未完成时悬停文字为乱码 */
    private static final ResourceLocation ENDBOUND$TARGET =
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "visitor_of_the_fourth_dimension");
    /** 「不被世界承认的少女」：未佩戴星尘光环时，成就名与说明都是空白 */
    private static final ResourceLocation ENDBOUND$VOID_TARGET =
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "unrecognized_girl");
    /** 用来制造乱码的字符集：编码与解码各取其一，模拟错误转码 */
    private static final Charset[] ENDBOUND$CHARSETS = {
            StandardCharsets.UTF_8,
            Charset.forName("GBK"),
            Charset.forName("Big5"),
            Charset.forName("Shift_JIS"),
            Charset.forName("EUC-JP")
    };
    /** 转码后长度不足时补的错字，让提示框宽度保持稳定 */
    private static final String ENDBOUND$FILLER = "锟斤拷烫屯鈥銆�";

    @Shadow
    @Final
    @Mutable
    private FormattedCharSequence title;
    @Shadow
    @Final
    @Mutable
    private List<FormattedCharSequence> description;
    @Shadow
    @Final
    private AdvancementNode advancementNode;
    @Shadow
    @Final
    private DisplayInfo display;
    @Shadow
    @Nullable
    private AdvancementProgress progress;
    /** 悬停提示框的整体宽度，用于把乱码限制在框内 */
    @Shadow
    @Final
    private int width;

    /** 未乱码前的原文，用于完成成就后恢复 */
    @Unique
    private FormattedCharSequence endbound$originalTitle;
    @Unique
    private List<FormattedCharSequence> endbound$originalDescription;
    @Unique
    private boolean endbound$garbled;
    /** 「不被世界承认的少女」是否正处于文字隐藏状态 */
    @Unique
    private boolean endbound$hidden;

    @Inject(method = "drawHover", at = @At("HEAD"))
    private void endbound$garbleTooltip(GuiGraphics guiGraphics, int x, int y, float fade, int width, int height,
            CallbackInfo ci) {
        ResourceLocation id = this.advancementNode.holder().id();
        if (ENDBOUND$VOID_TARGET.equals(id)) {
            endbound$hideUnrecognizedGirl();
            return;
        }
        if (!ENDBOUND$TARGET.equals(id)) return;
        boolean done = this.progress != null && this.progress.isDone();
        if (!done) {
            if (!this.endbound$garbled) {
                this.endbound$originalTitle = this.title;
                this.endbound$originalDescription = this.description;
                this.endbound$garbled = true;
            }
            this.title = endbound$garbledTitle(this.display.getTitle());
            this.description = endbound$garbledLines(this.display.getDescription());
        } else if (this.endbound$garbled) {
            this.title = this.endbound$originalTitle;
            this.description = this.endbound$originalDescription;
            this.endbound$garbled = false;
        }
    }

    /** 未佩戴星尘光环时隐藏「不被世界承认的少女」的全部文字，戴上后恢复 */
    @Unique
    private void endbound$hideUnrecognizedGirl() {
        if (!HaloWearing.stardustHalo(Minecraft.getInstance().player)) {
            if (!this.endbound$hidden) {
                this.endbound$originalTitle = this.title;
                this.endbound$originalDescription = this.description;
                this.endbound$hidden = true;
            }
            this.title = Language.getInstance().getVisualOrder(Component.empty());
            this.description = List.of();
        } else if (this.endbound$hidden) {
            this.title = this.endbound$originalTitle;
            this.description = this.endbound$originalDescription;
            this.endbound$hidden = false;
        }
    }

    /** 乱码种子：约每 150 毫秒换一组，读起来就是不断跳动的乱码 */
    @Unique
    private static long endbound$seed() {
        return System.currentTimeMillis() / 150L;
    }

    /** 模拟错误转码：按某种字符集编码，再用另一种字符集解码 */
    @Unique
    private static String endbound$mojibake(String text, long seed) {
        if (text.isEmpty()) return text;
        Random random = new Random(seed);
        int encode = random.nextInt(ENDBOUND$CHARSETS.length);
        int decode = (encode + 1 + random.nextInt(ENDBOUND$CHARSETS.length - 1))
                % ENDBOUND$CHARSETS.length;
        String decoded = new String(text.getBytes(ENDBOUND$CHARSETS[encode]), ENDBOUND$CHARSETS[decode]);
        // 保持与原文等长，避免提示框宽度被乱码撑开
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length() && i < decoded.length(); i++) {
            result.append(decoded.charAt(i));
        }
        while (result.length() < text.length()) {
            result.append(ENDBOUND$FILLER.charAt(random.nextInt(ENDBOUND$FILLER.length())));
        }
        return result.toString();
    }

    /** 标题乱码：单行，保留原颜色，并限制在提示框内 */
    @Unique
    private FormattedCharSequence endbound$garbledTitle(Component source) {
        int maxWidth = Math.max(16, this.width - 32);
        String text = endbound$mojibake(source.getString(), endbound$seed());
        String fitted = endbound$fitToWidth(text, maxWidth);
        Component garbled = Component.literal(fitted).withStyle(source.getStyle());
        return Language.getInstance().getVisualOrder(garbled);
    }

    /** 按像素宽度裁剪字符串，防止乱码大字撑出提示框 */
    @Unique
    private static String endbound$fitToWidth(String text, int maxWidth) {
        Font font = Minecraft.getInstance().font;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String candidate = result.toString() + text.charAt(i);
            if (font.width(candidate) > maxWidth) break;
            result.append(text.charAt(i));
        }
        return result.toString();
    }

    /** 说明乱码：逐段转码后按提示框自身的宽度换行，避免越界 */
    @Unique
    private List<FormattedCharSequence> endbound$garbledLines(Component source) {
        long seed = endbound$seed();
        String[] paragraphs = source.getString().split("\\n", -1);
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < paragraphs.length; i++) {
            if (i > 0) joined.append('\n');
            joined.append(endbound$mojibake(paragraphs[i], seed + i * 31L));
        }
        Component garbled = Component.literal(joined.toString()).withStyle(source.getStyle());
        int wrapWidth = Math.max(60, this.width - 36);
        return Minecraft.getInstance().font.split(garbled, wrapWidth);
    }
}
