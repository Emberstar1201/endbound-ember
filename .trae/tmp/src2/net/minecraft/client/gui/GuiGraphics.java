package net.minecraft.client.gui;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector2ic;

@OnlyIn(Dist.CLIENT)
public class GuiGraphics implements net.neoforged.neoforge.client.extensions.IGuiGraphicsExtension {
    public static final float MAX_GUI_Z = 10000.0F;
    public static final float MIN_GUI_Z = -10000.0F;
    private static final int EXTRA_SPACE_AFTER_FIRST_TOOLTIP_LINE = 2;
    private final Minecraft minecraft;
    private final PoseStack pose;
    private final MultiBufferSource.BufferSource bufferSource;
    private final GuiGraphics.ScissorStack scissorStack = new GuiGraphics.ScissorStack();
    private final GuiSpriteManager sprites;
    private boolean managed;

    private GuiGraphics(Minecraft minecraft, PoseStack pose, MultiBufferSource.BufferSource bufferSource) {
        this.minecraft = minecraft;
        this.pose = pose;
        this.bufferSource = bufferSource;
        this.sprites = minecraft.getGuiSprites();
    }

    public GuiGraphics(Minecraft minecraft, MultiBufferSource.BufferSource bufferSource) {
        this(minecraft, new PoseStack(), bufferSource);
    }

    /**
     * Executes a runnable while managing the render state. The render state is flushed before and after executing the runnable.
     *
     * @param runnable the runnable to execute.
     */
    @Deprecated
    public void drawManaged(Runnable runnable) {
        this.flush();
        this.managed = true;
        runnable.run();
        this.managed = false;
        this.flush();
    }

    @Deprecated
    private void flushIfUnmanaged() {
        if (!this.managed) {
            this.flush();
        }
    }

    @Deprecated
    private void flushIfManaged() {
        if (this.managed) {
            this.flush();
        }
    }

    public int guiWidth() {
        return this.minecraft.getWindow().getGuiScaledWidth();
    }

    public int guiHeight() {
        return this.minecraft.getWindow().getGuiScaledHeight();
    }

    public PoseStack pose() {
        return this.pose;
    }

    public MultiBufferSource.BufferSource bufferSource() {
        return this.bufferSource;
    }

    public void flush() {
        RenderSystem.disableDepthTest();
        this.bufferSource.endBatch();
        RenderSystem.enableDepthTest();
    }

    /**
     * Draws a horizontal line from minX to maxX at the specified y-coordinate with the given color.
     *
     * @param minX  the x-coordinate of the start point.
     * @param maxX  the x-coordinate of the end point.
     * @param y     the y-coordinate of the line.
     * @param color the color of the line.
     */
    public void hLine(int minX, int maxX, int y, int color) {
        this.hLine(RenderType.gui(), minX, maxX, y, color);
    }

    /**
     * Draws a horizontal line from minX to maxX at the specified y-coordinate with the given color using the specified render type.
     *
     * @param renderType the render type to use.
     * @param minX       the x-coordinate of the start point.
     * @param maxX       the x-coordinate of the end point.
     * @param y          the y-coordinate of the line.
     * @param color      the color of the line.
     */
    public void hLine(RenderType renderType, int minX, int maxX, int y, int color) {
        if (maxX < minX) {
            int i = minX;
            minX = maxX;
            maxX = i;
        }

        this.fill(renderType, minX, y, maxX + 1, y + 1, color);
    }

    /**
     * Draws a vertical line from minY to maxY at the specified x-coordinate with the given color.
     *
     * @param x     the x-coordinate of the line.
     * @param minY  the y-coordinate of the start point.
     * @param maxY  the y-coordinate of the end point.
     * @param color the color of the line.
     */
    public void vLine(int x, int minY, int maxY, int color) {
        this.vLine(RenderType.gui(), x, minY, maxY, color);
    }

    /**
     * Draws a vertical line from minY to maxY at the specified x-coordinate with the given color using the specified render type.
     *
     * @param renderType the render type to use.
     * @param x          the x-coordinate of the line.
     * @param minY       the y-coordinate of the start point.
     * @param maxY       the y-coordinate of the end point.
     * @param color      the color of the line.
     */
    public void vLine(RenderType renderType, int x, int minY, int maxY, int color) {
        if (maxY < minY) {
            int i = minY;
            minY = maxY;
            maxY = i;
        }

        this.fill(renderType, x, minY + 1, x + 1, maxY, color);
    }

    /**
     * Enables scissoring with the specified screen coordinates.
     *
     * @param minX the minimum x-coordinate of the scissor region.
     * @param minY the minimum y-coordinate of the scissor region.
     * @param maxX the maximum x-coordinate of the scissor region.
     * @param maxY the maximum y-coordinate of the scissor region.
     */
    public void enableScissor(int minX, int minY, int maxX, int maxY) {
        this.applyScissor(this.scissorStack.push(new ScreenRectangle(minX, minY, maxX - minX, maxY - minY)));
    }

    public void disableScissor() {
        this.applyScissor(this.scissorStack.pop());
    }

    public boolean containsPointInScissor(int x, int y) {
        return this.scissorStack.containsPoint(x, y);
    }

    /**
     * Applies scissoring based on the provided screen rectangle.
     *
     * @param rectangle the screen rectangle to apply scissoring with. Can be null to
     *                  disable scissoring.
     */
    private void applyScissor(@Nullable ScreenRectangle rectangle) {
        this.flushIfManaged();
        if (rectangle != null) {
            Window window = Minecraft.getInstance().getWindow();
            int i = window.getHeight();
            double d0 = window.getGuiScale();
            double d1 = (double)rectangle.left() * d0;
            double d2 = (double)i - (double)rectangle.bottom() * d0;
            double d3 = (double)rectangle.width() * d0;
            double d4 = (double)rectangle.height() * d0;
            RenderSystem.enableScissor((int)d1, (int)d2, Math.max(0, (int)d3), Math.max(0, (int)d4));
        } else {
            RenderSystem.disableScissor();
        }
    }

    /**
     * Sets the current rendering color.
     *
     * @param red   the red component of the color.
     * @param green the green component of the color.
     * @param blue  the blue component of the color.
     * @param alpha the alpha component of the color.
     */
    public void setColor(float red, float green, float blue, float alpha) {
        this.flushIfManaged();
        RenderSystem.setShaderColor(red, green, blue, alpha);
    }

    /**
     * Fills a rectangle with the specified color using the given coordinates as the boundaries.
     *
     * @param minX  the minimum x-coordinate of the rectangle.
     * @param minY  the minimum y-coordinate of the rectangle.
     * @param maxX  the maximum x-coordinate of the rectangle.
     * @param maxY  the maximum y-coordinate of the rectangle.
     * @param color the color to fill the rectangle with.
     */
    public void fill(int minX, int minY, int maxX, int maxY, int color) {
        this.fill(minX, minY, maxX, maxY, 0, color);
    }

    /**
     * Fills a rectangle with the specified color and z-level using the given coordinates as the boundaries.
     *
     * @param minX  the minimum x-coordinate of the rectangle.
     * @param minY  the minimum y-coordinate of the rectangle.
     * @param maxX  the maximum x-coordinate of the rectangle.
     * @param maxY  the maximum y-coordinate of the rectangle.
     * @param z     the z-level of the rectangle.
     * @param color the color to fill the rectangle with.
     */
    public void fill(int minX, int minY, int maxX, int maxY, int z, int color) {
        this.fill(RenderType.gui(), minX, minY, maxX, maxY, z, color);
    }

    /**
     * Fills a rectangle with the specified color using the given render type and coordinates as the boundaries.
     *
     * @param renderType the render type to use.
     * @param minX       the minimum x-coordinate of the rectangle.
     * @param minY       the minimum y-coordinate of the rectangle.
     * @param maxX       the maximum x-coordinate of the rectangle.
     * @param maxY       the maximum y-coordinate of the rectangle.
     * @param color      the color to fill the rectangle with.
     */
    public void fill(RenderType renderType, int minX, int minY, int maxX, int maxY, int color) {
        this.fill(renderType, minX, minY, maxX, maxY, 0, color);
    }

    /**
     * Fills a rectangle with the specified color and z-level using the given render type and coordinates as the boundaries.
     *
     * @param renderType the render type to use.
     * @param minX       the minimum x-coordinate of the rectangle.
     * @param minY       the minimum y-coordinate of the rectangle.
     * @param maxX       the maximum x-coordinate of the rectangle.
     * @param maxY       the maximum y-coordinate of the rectangle.
     * @param z          the z-level of the rectangle.
     * @param color      the color to fill the rectangle with.
     */
    public void fill(RenderType renderType, int minX, int minY, int maxX, int maxY, int z, int color) {
        Matrix4f matrix4f = this.pose.last().pose();
        if (minX < maxX) {
            int i = minX;
            minX = maxX;
            maxX = i;
        }

        if (minY < maxY) {
            int j = minY;
            minY = maxY;
            maxY = j;
        }

        VertexConsumer vertexconsumer = this.bufferSource.getBuffer(renderType);
        vertexconsumer.addVertex(matrix4f, (float)minX, (float)minY, (float)z).setColor(color);
        vertexconsumer.addVertex(matrix4f, (float)minX, (float)maxY, (float)z).setColor(color);
        vertexconsumer.addVertex(matrix4f, (float)maxX, (float)maxY, (float)z).setColor(color);
        vertexconsumer.addVertex(matrix4f, (float)maxX, (float)minY, (float)z).setColor(color);
        this.flushIfUnmanaged();
    }

    /**
     * Fills a rectangle with a gradient color from colorFrom to colorTo using the given coordinates as the boundaries.
     *
     * @param x1        the x-coordinate of the first corner of the rectangle.
     * @param y1        the y-coordinate of the first corner of the rectangle.
     * @param x2        the x-coordinate of the second corner of the rectangle.
     * @param y2        the y-coordinate of the second corner of the rectangle.
     * @param colorFrom the starting color of the gradient.
     * @param colorTo   the ending color of the gradient.
     */
    public void fillGradient(int x1, int y1, int x2, int y2, int colorFrom, int colorTo) {
        this.fillGradient(x1, y1, x2, y2, 0, colorFrom, colorTo);
    }

    /**
     * Fills a rectangle with a gradient color from colorFrom to colorTo at the specified z-level using the given coordinates as the boundaries.
     *
     * @param x1        the x-coordinate of the first corner of the rectangle.
     * @param y1        the y-coordinate of the first corner of the rectangle.
     * @param x2        the x-coordinate of the second corner of the rectangle.
     * @param y2        the y-coordinate of the second corner of the rectangle.
     * @param z         the z-level of the rectangle.
     * @param colorFrom the starting color of the gradient.
     * @param colorTo   the ending color of the gradient.
     */
    public void fillGradient(int x1, int y1, int x2, int y2, int z, int colorFrom, int colorTo) {
        this.fillGradient(RenderType.gui(), x1, y1, x2, y2, colorFrom, colorTo, z);
    }

    /**
     * Fills a rectangle with a gradient color from colorFrom to colorTo at the specified z-level using the given render type and coordinates as the boundaries.
     *
     * @param renderType the render type to use.
     * @param x1         the x-coordinate of the first corner of the rectangle.
     * @param y1         the y-coordinate of the first corner of the rectangle.
     * @param x2         the x-coordinate of the second corner of the rectangle.
     * @param y2         the y-coordinate of the second corner of the rectangle.
     * @param colorFrom  the starting color of the gradient.
     * @param colorTo    the ending color of the gradient.
     * @param z          the z-level of the rectangle.
     */
    public void fillGradient(RenderType renderType, int x1, int y1, int x2, int y2, int colorFrom, int colorTo, int z) {
        VertexConsumer vertexconsumer = this.bufferSource.getBuffer(renderType);
        this.fillGradient(vertexconsumer, x1, y1, x2, y2, z, colorFrom, colorTo);
        this.flushIfUnmanaged();
    }

    /**
     * The core `fillGradient` method.
     * <p>
     * Fills a rectangle with a gradient color from colorFrom to colorTo at the specified z-level using the given render type and coordinates as the boundaries.
     *
     * @param consumer  the {@linkplain VertexConsumer} object for drawing the
     *                  vertices on screen.
     * @param x1        the x-coordinate of the first corner of the rectangle.
     * @param y1        the y-coordinate of the first corner of the rectangle.
     * @param x2        the x-coordinate of the second corner of the rectangle.
     * @param y2        the y-coordinate of the second corner of the rectangle.
     * @param z         the z-level of the rectangle.
     * @param colorFrom the starting color of the gradient.
     * @param colorTo   the ending color of the gradient.
     */
    private void fillGradient(VertexConsumer consumer, int x1, int y1, int x2, int y2, int z, int colorFrom, int colorTo) {
        Matrix4f matrix4f = this.pose.last().pose();
        consumer.addVertex(matrix4f, (float)x1, (float)y1, (float)z).setColor(colorFrom);
        consumer.addVertex(matrix4f, (float)x1, (float)y2, (float)z).setColor(colorTo);
        consumer.addVertex(matrix4f, (float)x2, (float)y2, (float)z).setColor(colorTo);
        consumer.addVertex(matrix4f, (float)x2, (float)y1, (float)z).setColor(colorFrom);
    }

    public void fillRenderType(RenderType renderType, int x1, int y1, int x2, int y2, int z) {
        Matrix4f matrix4f = this.pose.last().pose();
        VertexConsumer vertexconsumer = this.bufferSource.getBuffer(renderType);
        vertexconsumer.addVertex(matrix4f, (float)x1, (float)y1, (float)z);
        vertexconsumer.addVertex(matrix4f, (float)x1, (float)y2, (float)z);
        vertexconsumer.addVertex(matrix4f, (float)x2, (float)y2, (float)z);
        vertexconsumer.addVertex(matrix4f, (float)x2, (float)y1, (float)z);
        this.flushIfUnmanaged();
    }

    /**
     * Draws a centered string at the specified coordinates using the given font, text, and color.
     *
     * @param font  the font to use for rendering.
     * @param text  the text to draw.
     * @param x     the x-coordinate of the center of the string.
     * @param y     the y-coordinate of the string.
     * @param color the color of the string.
     */
    public void drawCenteredString(Font font, String text, int x, int y, int color) {
        this.drawString(font, text, x - font.width(text) / 2, y, color);
    }

    /**
     * Draws a centered string at the specified coordinates using the given font, text component, and color.
     *
     * @param font  the font to use for rendering.
     * @param text  the text component to draw.
     * @param x     the x-coordinate of the center of the string.
     * @param y     the y-coordinate of the string.
     * @param color the color of the string.
     */
    public void drawCenteredString(Font font, Component text, int x, int y, int color) {
        FormattedCharSequence formattedcharsequence = text.getVisualOrderText();
        this.drawString(font, formattedcharsequence, x - font.width(formattedcharsequence) / 2, y, color);
    }

    /**
     * Draws a centered string at the specified coordinates using the given font, formatted character sequence, and color.
     *
     * @param font  the font to use for rendering.
     * @param text  the formatted character sequence to draw.
     * @param x     the x-coordinate of the center of the string.
     * @param y     the y-coordinate of the string.
     * @param color the color of the string.
     */
    public void drawCenteredString(Font font, FormattedCharSequence text, int x, int y, int color) {
        this.drawString(font, text, x - font.width(text) / 2, y, color);
    }

    /**
     * Draws a string at the specified coordinates using the given font, text, and color. Returns the width of the drawn string.
     * <p>
     * @return the width of the drawn string.
     *
     * @param font  the font to use for rendering.
     * @param text  the text to draw.
     * @param x     the x-coordinate of the string.
     * @param y     the y-coordinate of the string.
     * @param color the color of the string.
     */
    public int drawString(Font font, @Nullable String text, int x, int y, int color) {
        return this.drawString(font, text, x, y, color, true);
    }

    /**
     * Draws a string at the specified coordinates using the given font, text, color, and drop shadow. Returns the width of the drawn string.
     * <p>
     * @return the width of the drawn string.
     *
     * @param font       the font to use for rendering.
     * @param text       the text to draw.
     * @param x          the x-coordinate of the string.
     * @param y          the y-coordinate of the string.
     * @param color      the color of the string.
     * @param dropShadow whether to apply a drop shadow to the string.
     */
    public int drawString(Font font, @Nullable String text, int x, int y, int color, boolean dropShadow) {
        return this.drawString(font, text, (float)x, (float)y, color, dropShadow);
    }

    // Forge: Add float variant for x,y coordinates, with a string as input
    public int drawString(Font p_283343_, @Nullable String p_281896_, float p_283569_, float p_283418_, int p_281560_, boolean p_282130_) {
        if (p_281896_ == null) {
            return 0;
        } else {
            int i = p_283343_.drawInBatch(
                p_281896_,
                (float)p_283569_,
                (float)p_283418_,
                p_281560_,
                p_282130_,
                this.pose.last().pose(),
                this.bufferSource,
                Font.DisplayMode.NORMAL,
                0,
                15728880,
                p_283343_.isBidirectional()
            );
            this.flushIfUnmanaged();
            return i;
        }
    }

    /**
     * Draws a formatted character sequence at the specified coordinates using the given font, text, and color. Returns the width of the drawn string.
     * <p>
     * @return the width of the drawn string.
     *
     * @param font  the font to use for rendering.
     * @param text  the formatted character sequence to draw.
     * @param x     the x-coordinate of the string.
     * @param y     the y-coordinate of the string.
     * @param color the color of the string
     */
    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color) {
        return this.drawString(font, text, x, y, color, true);
    }

    /**
     * Draws a formatted character sequence at the specified coordinates using the given font, text, color, and drop shadow. Returns the width of the drawn string.
     * <p>
     * @return returns the width of the drawn string.
     *
     * @param font       the font to use for rendering.
     * @param text       the formatted character sequence to draw.
     * @param x          the x-coordinate of the string.
     * @param y          the y-coordinate of the string.
     * @param color      the color of the string.
     * @param dropShadow whether to apply a drop shadow to the string.
     */
    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean dropShadow) {
        return this.drawString(font, text, (float)x, (float)y, color, dropShadow);
    }

    // Forge: Add float variant for x,y coordinates, with a formatted char sequence as input
    public int drawString(Font p_282636_, FormattedCharSequence p_281596_, float p_281586_, float p_282816_, int p_281743_, boolean p_282394_) {
        int i = p_282636_.drawInBatch(
            p_281596_,
            (float)p_281586_,
            (float)p_282816_,
            p_281743_,
            p_282394_,
            this.pose.last().pose(),
            this.bufferSource,
            Font.DisplayMode.NORMAL,
            0,
            15728880
        );
        this.flushIfUnmanaged();
        return i;
    }

    /**
     * Draws a component's visual order text at the specified coordinates using the given font, text component, and color.
     * <p>
     * @return the width of the drawn string.
     *
     * @param font  the font to use for rendering.
     * @param text  the text component to draw.
     * @param x     the x-coordinate of the string.
     * @param y     the y-coordinate of the string.
     * @param color the color of the string.
     */
    public int drawString(Font font, Component text, int x, int y, int color) {
        return this.drawString(font, text, x, y, color, true);
    }

    /**
     * Draws a component's visual order text at the specified coordinates using the given font, text component, color, and drop shadow.
     * <p>
     * @return the width of the drawn string.
     *
     * @param font       the font to use for rendering.
     * @param text       the text component to draw.
     * @param x          the x-coordinate of the string.
     * @param y          the y-coordinate of the string.
     * @param color      the color of the string.
     * @param dropShadow whether to apply a drop shadow to the string.
     */
    public int drawString(Font font, Component text, int x, int y, int color, boolean dropShadow) {
        return this.drawString(font, text.getVisualOrderText(), x, y, color, dropShadow);
    }

    /**
     * Draws a formatted text with word wrapping at the specified coordinates using the given font, text, line width, and color.
     *
     * @param font      the font to use for rendering.
     * @param text      the formatted text to draw.
     * @param x         the x-coordinate of the starting position.
     * @param y         the y-coordinate of the starting position.
     * @param lineWidth the maximum width of each line before wrapping.
     * @param color     the color of the text.
     */
    public void drawWordWrap(Font font, FormattedText text, int x, int y, int lineWidth, int color) {
        for (FormattedCharSequence formattedcharsequence : font.split(text, lineWidth)) {
            this.drawString(font, formattedcharsequence, x, y, color, false);
            y += 9;
        }
    }

    public int drawStringWithBackdrop(Font font, Component text, int x, int y, int xOffset, int color) {
        int i = this.minecraft.options.getBackgroundColor(0.0F);
        if (i != 0) {
            int j = 2;
            this.fill(x - 2, y - 2, x + xOffset + 2, y + 9 + 2, FastColor.ARGB32.multiply(i, color));
        }

        return this.drawString(font, text, x, y, color, true);
    }

    /**
     * Blits a portion of the specified texture atlas sprite onto the screen at the given coordinates.
     *
     * @param x          the x-coordinate of the blit position.
     * @param y          the y-coordinate of the blit position.
     * @param blitOffset the z-level offset for rendering order.
     * @param width      the width of the blitted portion.
     * @param height     the height of the blitted portion.
     * @param sprite     the texture atlas sprite to blit.
     */
    public void blit(int x, int y, int blitOffset, int width, int height, TextureAtlasSprite sprite) {
        this.blitSprite(sprite, x, y, blitOffset, width, height);
    }

    /**
     * Blits a portion of the specified texture atlas sprite onto the screen at the given coordinates with a color tint.
     *
     * @param x          the x-coordinate of the blit position.
     * @param y          the y-coordinate of the blit position.
     * @param blitOffset the z-level offset for rendering order.
     * @param width      the width of the blitted portion.
     * @param height     the height of the blitted portion.
     * @param sprite     the texture atlas sprite to blit.
     * @param red        the red component of the color tint.
     * @param green      the green component of the color tint.
     * @param blue       the blue component of the color tint.
     * @param alpha      the alpha component of the color tint.
     */
    public void blit(
        int x,
        int y,
        int blitOffset,
        int width,
        int height,
        TextureAtlasSprite sprite,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        this.innerBlit(
            sprite.atlasLocation(),
            x,
            x + width,
            y,
            y + height,
            blitOffset,
            sprite.getU0(),
            sprite.getU1(),
            sprite.getV0(),
            sprite.getV1(),
            red,
            green,
            blue,
            alpha
        );
    }

    /**
     * Renders an outline rectangle on the screen with the specified color.
     *
     * @param x      the x-coordinate of the top-left corner of the rectangle.
     * @param y      the y-coordinate of the top-left corner of the rectangle.
     * @param width  the width of the blitted portion.
     * @param height the height of the rectangle.
     * @param color  the color of the outline.
     */
    public void renderOutline(int x, int y, int width, int height, int color) {
        this.fill(x, y, x + width, y + 1, color);
        this.fill(x, y + height - 1, x + width, y + height, color);
        this.fill(x, y + 1, x + 1, y + height - 1, color);
        this.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public void blitSprite(ResourceLocation sprite, int x, int y, int width, int height) {
        this.blitSprite(sprite, x, y, 0, width, height);
    }

    public void blitSprite(ResourceLocation sprite, int x, int y, int blitOffset, int width, int height) {
        TextureAtlasSprite textureatlassprite = this.sprites.getSprite(sprite);
        GuiSpriteScaling guispritescaling = this.sprites.getSpriteScaling(textureatlassprite);
        if (guispritescaling instanceof GuiSpriteScaling.Stretch) {
            this.blitSprite(textureatlassprite, x, y, blitOffset, width, height);
        } else if (guispritescaling instanceof GuiSpriteScaling.Tile guispritescaling$tile) {
            this.blitTiledSprite(
                textureatlassprite,
                x,
                y,
                blitOffset,
                width,
                height,
                0,
                0,
                guispritescaling$tile.width(),
                guispritescaling$tile.height(),
                guispritescaling$tile.width(),
                guispritescaling$tile.height()
            );
        } else if (guispritescaling instanceof GuiSpriteScaling.NineSlice guispritescaling$nineslice) {
            this.blitNineSlicedSprite(textureatlassprite, guispritescaling$nineslice, x, y, blitOffset, width, height);
        }
    }

    public void blitSprite(
        ResourceLocation sprite, int textureWidth, int textureHeight, int uPosition, int vPosition, int x, int y, int uWidth, int vHeight
    ) {
        this.blitSprite(sprite, textureWidth, textureHeight, uPosition, vPosition, x, y, 0, uWidth, vHeight);
    }

    public void blitSprite(
        ResourceLocation sprite,
        int textureWidth,
        int textureHeight,
        int uPosition,
        int vPosition,
        int x,
        int y,
        int blitOffset,
        int uWidth,
        int vHeight
    ) {
        TextureAtlasSprite textureatlassprite = this.sprites.getSprite(sprite);
        GuiSpriteScaling guispritescaling = this.sprites.getSpriteScaling(textureatlassprite);
        if (guispritescaling instanceof GuiSpriteScaling.Stretch) {
            this.blitSprite(textureatlassprite, textureWidth, textureHeight, uPosition, vPosition, x, y, blitOffset, uWidth, vHeight);
        } else {
            this.blitSprite(textureatlassprite, x, y, blitOffset, uWidth, vHeight);
        }
    }

    private void blitSprite(
        TextureAtlasSprite sprite,
        int textureWidth,
        int textureHeight,
        int uPosition,
        int vPosition,
        int x,
        int y,
        int blitOffset,
        int uWidth,
        int vHeight
    ) {
        if (uWidth != 0 && vHeight != 0) {
            this.innerBlit(
                sprite.atlasLocation(),
                x,
                x + uWidth,
                y,
                y + vHeight,
                blitOffset,
                sprite.getU((float)uPosition / (float)textureWidth),
                sprite.getU((float)(uPosition + uWidth) / (float)textureWidth),
                sprite.getV((float)vPosition / (float)textureHeight),
                sprite.getV((float)(vPosition + vHeight) / (float)textureHeight)
            );
        }
    }

    private void blitSprite(TextureAtlasSprite sprite, int x, int y, int blitOffset, int width, int height) {
        if (width != 0 && height != 0) {
            this.innerBlit(
                sprite.atlasLocation(),
                x,
                x + width,
                y,
                y + height,
                blitOffset,
                sprite.getU0(),
                sprite.getU1(),
                sprite.getV0(),
                sprite.getV1()
            );
        }
    }

    /**
     * Blits a portion of the texture specified by the atlas location onto the screen at the given coordinates.
     *
     * @param atlasLocation the location of the texture atlas.
     * @param x             the x-coordinate of the blit position.
     * @param y             the y-coordinate of the blit position.
     * @param uOffset       the horizontal texture coordinate offset.
     * @param vOffset       the vertical texture coordinate offset.
     * @param uWidth        the width of the blitted portion in texture coordinates.
     * @param vHeight       the height of the blitted portion in texture coordinates.
     */
    public void blit(ResourceLocation atlasLocation, int x, int y, int uOffset, int vOffset, int uWidth, int vHeight) {
        this.blit(atlasLocation, x, y, 0, (float)uOffset, (float)vOffset, uWidth, vHeight, 256, 256);
    }

    /**
     * Blits a portion of the texture specified by the atlas location onto the screen at the given coordinates with a blit offset and texture coordinates.
     *
     * @param atlasLocation the location of the texture atlas.
     * @param x             the x-coordinate of the blit position.
     * @param y             the y-coordinate of the blit position.
     * @param blitOffset    the z-level offset for rendering order.
     * @param uOffset       the horizontal texture coordinate offset.
     * @param vOffset       the vertical texture coordinate offset.
     * @param uWidth        the width of the blitted portion in texture coordinates.
     * @param vHeight       the height of the blitted portion in texture coordinates.
     * @param textureWidth  the width of the texture.
     * @param textureHeight the height of the texture.
     */
    public void blit(
        ResourceLocation atlasLocation,
        int x,
        int y,
        int blitOffset,
        float uOffset,
        float vOffset,
        int uWidth,
        int vHeight,
        int textureWidth,
        int textureHeight
    ) {
        this.blit(
            atlasLocation,
            x,
            x + uWidth,
            y,
            y + vHeight,
            blitOffset,
            uWidth,
            vHeight,
            uOffset,
            vOffset,
            textureWidth,
            textureHeight
        );
    }

    /**
     * Blits a portion of the texture specified by the atlas location onto the screen at the given position and dimensions with texture coordinates.
     *
     * @param atlasLocation the location of the texture atlas.
     * @param x             the x-coordinate of the top-left corner of the blit
     *                      position.
     * @param y             the y-coordinate of the top-left corner of the blit
     *                      position.
     * @param width         the width of the blitted portion.
     * @param height        the height of the blitted portion.
     * @param uOffset       the horizontal texture coordinate offset.
     * @param vOffset       the vertical texture coordinate offset.
     * @param uWidth        the width of the blitted portion in texture coordinates.
     * @param vHeight       the height of the blitted portion in texture coordinates.
     * @param textureWidth  the width of the texture.
     * @param textureHeight the height of the texture.
     */
    public void blit(
        ResourceLocation atlasLocation,
        int x,
        int y,
        int width,
        int height,
        float uOffset,
        float vOffset,
        int uWidth,
        int vHeight,
        int textureWidth,
        int textureHeight
    ) {
        this.blit(
            atlasLocation, x, x + width, y, y + height, 0, uWidth, vHeight, uOffset, vOffset, textureWidth, textureHeight
        );
    }

    /**
     * Blits a portion of the texture specified by the atlas location onto the screen at the given position and dimensions with texture coordinates.
     *
     * @param atlasLocation the location of the texture atlas.
     * @param x             the x-coordinate of the top-left corner of the blit
     *                      position.
     * @param y             the y-coordinate of the top-left corner of the blit
     *                      position.
     * @param uOffset       the horizontal texture coordinate offset.
     * @param vOffset       the vertical texture coordinate offset.
     * @param width         the width of the blitted portion.
     * @param height        the height of the blitted portion.
     * @param textureWidth  the width of the texture.
     * @param textureHeight the height of the texture.
     */
    public void blit(
        ResourceLocation atlasLocation, int x, int y, float uOffset, float vOffset, int width, int height, int textureWidth, int textureHeight
    ) {
        this.blit(atlasLocation, x, y, width, height, uOffset, vOffset, width, height, textureWidth, textureHeight);
    }

    /**
     * Performs the inner blit operation for rendering a texture with the specified coordinates and texture coordinates.
     *
     * @param atlasLocation the location of the texture atlas.
     * @param x1            the x-coordinate of the first corner of the blit position.
     * @param x2            the x-coordinate of the second corner of the blit position
     *                      .
     * @param y1            the y-coordinate of the first corner of the blit position.
     * @param y2            the y-coordinate of the second corner of the blit position
     *                      .
     * @param blitOffset    the z-level offset for rendering order.
     * @param uWidth        the width of the blitted portion in texture coordinates.
     * @param vHeight       the height of the blitted portion in texture coordinates.
     * @param uOffset       the horizontal texture coordinate offset.
     * @param vOffset       the vertical texture coordinate offset.
     * @param textureWidth  the width of the texture.
     * @param textureHeight the height of the texture.
     */
    void blit(
        ResourceLocation atlasLocation,
        int x1,
        int x2,
        int y1,
        int y2,
        int blitOffset,
        int uWidth,
        int vHeight,
        float uOffset,
        float vOffset,
        int textureWidth,
        int textureHeight
    ) {
        this.innerBlit(
            atlasLocation,
            x1,
            x2,
            y1,
            y2,
            blitOffset,
            (uOffset + 0.0F) / (float)textureWidth,
            (uOffset + (float)uWidth) / (float)textureWidth,
            (vOffset + 0.0F) / (float)textureHeight,
            (vOffset + (float)vHeight) / (float)textureHeight
        );
    }

    /**
     * Performs the inner blit operation for rendering a texture with the specified coordinates and texture coordinates without color tinting.
     *
     * @param atlasLocation the location of the texture atlas.
     * @param x1            the x-coordinate of the first corner of the blit position.
     * @param x2            the x-coordinate of the second corner of the blit position
     *                      .
     * @param y1            the y-coordinate of the first corner of the blit position.
     * @param y2            the y-coordinate of the second corner of the blit position
     *                      .
     * @param blitOffset    the z-level offset for rendering order.
     * @param minU          the minimum horizontal texture coordinate.
     * @param maxU          the maximum horizontal texture coordinate.
     * @param minV          the minimum vertical texture coordinate.
     * @param maxV          the maximum vertical texture coordinate.
     */
    void innerBlit(
        ResourceLocation atlasLocation,
        int x1,
        int x2,
        int y1,
        int y2,
        int blitOffset,
        float minU,
        float maxU,
        float minV,
        float maxV
    ) {
        RenderSystem.setShaderTexture(0, atlasLocation);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        Matrix4f matrix4f = this.pose.last().pose();
        BufferBuilder bufferbuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.addVertex(matrix4f, (float)x1, (float)y1, (float)blitOffset).setUv(minU, minV);
        bufferbuilder.addVertex(matrix4f, (float)x1, (float)y2, (float)blitOffset).setUv(minU, maxV);
        bufferbuilder.addVertex(matrix4f, (float)x2, (float)y2, (float)blitOffset).setUv(maxU, maxV);
        bufferbuilder.addVertex(matrix4f, (float)x2, (float)y1, (float)blitOffset).setUv(maxU, minV);
        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
    }

    /**
     * Performs the inner blit operation for rendering a texture with the specified coordinates, texture coordinates, and color tint.
     *
     * @param atlasLocation the location of the texture atlas.
     * @param x1            the x-coordinate of the first corner of the blit position.
     * @param x2            the x-coordinate of the second corner of the blit position
     *                      .
     * @param y1            the y-coordinate of the first corner of the blit position.
     * @param y2            the y-coordinate of the second corner of the blit position
     *                      .
     * @param blitOffset    the z-level offset for rendering order.
     * @param minU          the minimum horizontal texture coordinate.
     * @param maxU          the maximum horizontal texture coordinate.
     * @param minV          the minimum vertical texture coordinate.
     * @param maxV          the maximum vertical texture coordinate.
     * @param red           the red component of the color tint.
     * @param green         the green component of the color tint.
     * @param blue          the blue component of the color tint.
     * @param alpha         the alpha component of the color tint.
     */
    void innerBlit(
        ResourceLocation atlasLocation,
        int x1,
        int x2,
        int y1,
        int y2,
        int blitOffset,
        float minU,
        float maxU,
        float minV,
        float maxV,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        RenderSystem.setShaderTexture(0, atlasLocation);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        Matrix4f matrix4f = this.pose.last().pose();
        BufferBuilder bufferbuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bufferbuilder.addVertex(matrix4f, (float)x1, (float)y1, (float)blitOffset)
            .setUv(minU, minV)
            .setColor(red, green, blue, alpha);
        bufferbuilder.addVertex(matrix4f, (float)x1, (float)y2, (float)blitOffset)
            .setUv(minU, maxV)
            .setColor(red, green, blue, alpha);
        bufferbuilder.addVertex(matrix4f, (float)x2, (float)y2, (float)blitOffset)
            .setUv(maxU, maxV)
            .setColor(red, green, blue, alpha);
        bufferbuilder.addVertex(matrix4f, (float)x2, (float)y1, (float)blitOffset)
            .setUv(maxU, minV)
            .setColor(red, green, blue, alpha);
        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private void blitNineSlicedSprite(
        TextureAtlasSprite sprite, GuiSpriteScaling.NineSlice nineSlice, int x, int y, int blitOffset, int width, int height
    ) {
        GuiSpriteScaling.NineSlice.Border guispritescaling$nineslice$border = nineSlice.border();
        int i = Math.min(guispritescaling$nineslice$border.left(), width / 2);
        int j = Math.min(guispritescaling$nineslice$border.right(), width / 2);
        int k = Math.min(guispritescaling$nineslice$border.top(), height / 2);
        int l = Math.min(guispritescaling$nineslice$border.bottom(), height / 2);
        if (width == nineSlice.width() && height == nineSlice.height()) {
            this.blitSprite(sprite, nineSlice.width(), nineSlice.height(), 0, 0, x, y, blitOffset, width, height);
        } else if (height == nineSlice.height()) {
            this.blitSprite(sprite, nineSlice.width(), nineSlice.height(), 0, 0, x, y, blitOffset, i, height);
            this.blitTiledSprite(
                sprite,
                x + i,
                y,
                blitOffset,
                width - j - i,
                height,
                i,
                0,
                nineSlice.width() - j - i,
                nineSlice.height(),
                nineSlice.width(),
                nineSlice.height()
            );
            this.blitSprite(
                sprite, nineSlice.width(), nineSlice.height(), nineSlice.width() - j, 0, x + width - j, y, blitOffset, j, height
            );
        } else if (width == nineSlice.width()) {
            this.blitSprite(sprite, nineSlice.width(), nineSlice.height(), 0, 0, x, y, blitOffset, width, k);
            this.blitTiledSprite(
                sprite,
                x,
                y + k,
                blitOffset,
                width,
                height - l - k,
                0,
                k,
                nineSlice.width(),
                nineSlice.height() - l - k,
                nineSlice.width(),
                nineSlice.height()
            );
            this.blitSprite(
                sprite, nineSlice.width(), nineSlice.height(), 0, nineSlice.height() - l, x, y + height - l, blitOffset, width, l
            );
        } else {
            this.blitSprite(sprite, nineSlice.width(), nineSlice.height(), 0, 0, x, y, blitOffset, i, k);
            this.blitTiledSprite(
                sprite, x + i, y, blitOffset, width - j - i, k, i, 0, nineSlice.width() - j - i, k, nineSlice.width(), nineSlice.height()
            );
            this.blitSprite(sprite, nineSlice.width(), nineSlice.height(), nineSlice.width() - j, 0, x + width - j, y, blitOffset, j, k);
            this.blitSprite(sprite, nineSlice.width(), nineSlice.height(), 0, nineSlice.height() - l, x, y + height - l, blitOffset, i, l);
            this.blitTiledSprite(
                sprite,
                x + i,
                y + height - l,
                blitOffset,
                width - j - i,
                l,
                i,
                nineSlice.height() - l,
                nineSlice.width() - j - i,
                l,
                nineSlice.width(),
                nineSlice.height()
            );
            this.blitSprite(
                sprite,
                nineSlice.width(),
                nineSlice.height(),
                nineSlice.width() - j,
                nineSlice.height() - l,
                x + width - j,
                y + height - l,
                blitOffset,
                j,
                l
            );
            this.blitTiledSprite(
                sprite,
                x,
                y + k,
                blitOffset,
                i,
                height - l - k,
                0,
                k,
                i,
                nineSlice.height() - l - k,
                nineSlice.width(),
                nineSlice.height()
            );
            this.blitTiledSprite(
                sprite,
                x + i,
                y + k,
                blitOffset,
                width - j - i,
                height - l - k,
                i,
                k,
                nineSlice.width() - j - i,
                nineSlice.height() - l - k,
                nineSlice.width(),
                nineSlice.height()
            );
            this.blitTiledSprite(
                sprite,
                x + width - j,
                y + k,
                blitOffset,
                i,
                height - l - k,
                nineSlice.width() - j,
                k,
                j,
                nineSlice.height() - l - k,
                nineSlice.width(),
                nineSlice.height()
            );
        }
    }

    private void blitTiledSprite(
        TextureAtlasSprite sprite,
        int x,
        int y,
        int blitOffset,
        int width,
        int height,
        int uPosition,
        int vPosition,
        int spriteWidth,
        int spriteHeight,
        int nineSliceWidth,
        int nineSliceHeight
    ) {
        if (width > 0 && height > 0) {
            if (spriteWidth > 0 && spriteHeight > 0) {
                for (int i = 0; i < width; i += spriteWidth) {
                    int j = Math.min(spriteWidth, width - i);

                    for (int k = 0; k < height; k += spriteHeight) {
                        int l = Math.min(spriteHeight, height - k);
                        this.blitSprite(sprite, nineSliceWidth, nineSliceHeight, uPosition, vPosition, x + i, y + k, blitOffset, j, l);
                    }
                }
            } else {
                throw new IllegalArgumentException("Tiled sprite texture size must be positive, got " + spriteWidth + "x" + spriteHeight);
            }
        }
    }

    /**
     * Renders an item stack at the specified coordinates.
     *
     * @param stack the item stack to render.
     * @param x     the x-coordinate of the rendering position.
     * @param y     the y-coordinate of the rendering position.
     */
    public void renderItem(ItemStack stack, int x, int y) {
        this.renderItem(this.minecraft.player, this.minecraft.level, stack, x, y, 0);
    }

    /**
     * Renders an item stack at the specified coordinates with a random seed.
     *
     * @param stack the item stack to render.
     * @param x     the x-coordinate of the rendering position.
     * @param y     the y-coordinate of the rendering position.
     * @param seed  the random seed.
     */
    public void renderItem(ItemStack stack, int x, int y, int seed) {
        this.renderItem(this.minecraft.player, this.minecraft.level, stack, x, y, seed);
    }

    /**
     * Renders an item stack at the specified coordinates with a random seed and a custom value.
     *
     * @param stack     the item stack to render.
     * @param x         the x-coordinate of the rendering position.
     * @param y         the y-coordinate of the rendering position.
     * @param seed      the random seed.
     * @param guiOffset the GUI offset.
     */
    public void renderItem(ItemStack stack, int x, int y, int seed, int guiOffset) {
        this.renderItem(this.minecraft.player, this.minecraft.level, stack, x, y, seed, guiOffset);
    }

    /**
     * Renders a fake item stack at the specified coordinates.
     *
     * @param stack the fake item stack to render.
     * @param x     the x-coordinate of the rendering position.
     * @param y     the y-coordinate of the rendering position.
     */
    public void renderFakeItem(ItemStack stack, int x, int y) {
        this.renderFakeItem(stack, x, y, 0);
    }

    public void renderFakeItem(ItemStack stack, int x, int y, int seed) {
        this.renderItem(null, this.minecraft.level, stack, x, y, seed);
    }

    /**
     * Renders an item stack for a living entity at the specified coordinates with a random seed.
     *
     * @param entity the living entity.
     * @param stack  the item stack to render.
     * @param x      the x-coordinate of the rendering position.
     * @param y      the y-coordinate of the rendering position.
     * @param seed   the random seed.
     */
    public void renderItem(LivingEntity entity, ItemStack stack, int x, int y, int seed) {
        this.renderItem(entity, entity.level(), stack, x, y, seed);
    }

    /**
     * Renders an item stack for a living entity in a specific level at the specified coordinates with a random seed.
     *
     * @param entity the living entity. Can be null.
     * @param level  the level in which the rendering occurs. Can be null.
     * @param stack  the item stack to render.
     * @param x      the x-coordinate of the rendering position.
     * @param y      the y-coordinate of the rendering position.
     * @param seed   the random seed.
     */
    private void renderItem(@Nullable LivingEntity entity, @Nullable Level level, ItemStack stack, int x, int y, int seed) {
        this.renderItem(entity, level, stack, x, y, seed, 0);
    }

    /**
     * Renders an item stack for a living entity in a specific level at the specified coordinates with a random seed and a custom GUI offset.
     *
     * @param entity    the living entity. Can be null.
     * @param level     the level in which the rendering occurs. Can be null.
     * @param stack     the item stack to render.
     * @param x         the x-coordinate of the rendering position.
     * @param y         the y-coordinate of the rendering position.
     * @param seed      the random seed.
     * @param guiOffset the GUI offset value.
     */
    private void renderItem(
        @Nullable LivingEntity entity, @Nullable Level level, ItemStack stack, int x, int y, int seed, int guiOffset
    ) {
        if (!stack.isEmpty()) {
            BakedModel bakedmodel = this.minecraft.getItemRenderer().getModel(stack, level, entity, seed);
            this.pose.pushPose();
            this.pose.translate((float)(x + 8), (float)(y + 8), (float)(150 + (bakedmodel.isGui3d() ? guiOffset : 0)));

            try {
                this.pose.scale(16.0F, -16.0F, 16.0F);
                boolean flag = !bakedmodel.usesBlockLight();
                if (flag) {
                    Lighting.setupForFlatItems();
                }

                this.minecraft
                    .getItemRenderer()
                    .render(stack, ItemDisplayContext.GUI, false, this.pose, this.bufferSource(), 15728880, OverlayTexture.NO_OVERLAY, bakedmodel);
                this.flush();
                if (flag) {
                    Lighting.setupFor3DItems();
                }
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Rendering item");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Item being rendered");
                crashreportcategory.setDetail("Item Type", () -> String.valueOf(stack.getItem()));
                crashreportcategory.setDetail("Item Components", () -> String.valueOf(stack.getComponents()));
                crashreportcategory.setDetail("Item Foil", () -> String.valueOf(stack.hasFoil()));
                throw new ReportedException(crashreport);
            }

            this.pose.popPose();
        }
    }

    /**
     * Renders additional decorations for an item stack at the specified coordinates.
     *
     * @param font  the font used for rendering text.
     * @param stack the item stack to decorate.
     * @param x     the x-coordinate of the rendering position.
     * @param y     the y-coordinate of the rendering position.
     */
    public void renderItemDecorations(Font font, ItemStack stack, int x, int y) {
        this.renderItemDecorations(font, stack, x, y, null);
    }

    /**
     * Renders additional decorations for an item stack at the specified coordinates with optional custom text.
     *
     * @param font  the font used for rendering text.
     * @param stack the item stack to decorate.
     * @param x     the x-coordinate of the rendering position.
     * @param y     the y-coordinate of the rendering position.
     * @param text  the custom text to display. Can be null.
     */
    public void renderItemDecorations(Font font, ItemStack stack, int x, int y, @Nullable String text) {
        if (!stack.isEmpty()) {
            this.pose.pushPose();
            if (stack.getCount() != 1 || text != null) {
                String s = text == null ? String.valueOf(stack.getCount()) : text;
                this.pose.translate(0.0F, 0.0F, 200.0F);
                this.drawString(font, s, x + 19 - 2 - font.width(s), y + 6 + 3, 16777215, true);
            }

            if (stack.isBarVisible()) {
                int l = stack.getBarWidth();
                int i = stack.getBarColor();
                int j = x + 2;
                int k = y + 13;
                this.fill(RenderType.guiOverlay(), j, k, j + 13, k + 2, -16777216);
                this.fill(RenderType.guiOverlay(), j, k, j + l, k + 1, i | 0xFF000000);
            }

            LocalPlayer localplayer = this.minecraft.player;
            float f = localplayer == null
                ? 0.0F
                : localplayer.getCooldowns().getCooldownPercent(stack.getItem(), this.minecraft.getTimer().getGameTimeDeltaPartialTick(true));
            if (f > 0.0F) {
                int i1 = y + Mth.floor(16.0F * (1.0F - f));
                int j1 = i1 + Mth.ceil(16.0F * f);
                this.fill(RenderType.guiOverlay(), x, i1, x + 16, j1, Integer.MAX_VALUE);
            }

            this.pose.popPose();
            net.neoforged.neoforge.client.ItemDecoratorHandler.of(stack).render(this, font, stack, x, y);
        }
    }

    private ItemStack tooltipStack = ItemStack.EMPTY;

    /**
     * Renders a tooltip for an item stack at the specified mouse coordinates.
     *
     * @param font   the font used for rendering text.
     * @param stack  the item stack to display the tooltip for.
     * @param mouseX the x-coordinate of the mouse position.
     * @param mouseY the y-coordinate of the mouse position.
     */
    public void renderTooltip(Font font, ItemStack stack, int mouseX, int mouseY) {
        this.tooltipStack = stack;
        this.renderTooltip(font, Screen.getTooltipFromItem(this.minecraft, stack), stack.getTooltipImage(), mouseX, mouseY);
        this.tooltipStack = ItemStack.EMPTY;
    }

    public void renderTooltip(Font font, List<Component> textComponents, Optional<TooltipComponent> tooltipComponent, ItemStack stack, int mouseX, int mouseY) {
        this.tooltipStack = stack;
        this.renderTooltip(font, textComponents, tooltipComponent, mouseX, mouseY);
        this.tooltipStack = ItemStack.EMPTY;
    }

    /**
     * Renders a tooltip with customizable components at the specified mouse coordinates.
     *
     * @param font                   the font used for rendering text.
     * @param tooltipLines           the lines of the tooltip.
     * @param visualTooltipComponent the visual tooltip component. Can be empty.
     * @param mouseX                 the x-coordinate of the mouse position.
     * @param mouseY                 the y-coordinate of the mouse position.
     */
    public void renderTooltip(Font font, List<Component> tooltipLines, Optional<TooltipComponent> visualTooltipComponent, int mouseX, int mouseY) {
        List<ClientTooltipComponent> list = net.neoforged.neoforge.client.ClientHooks.gatherTooltipComponents(this.tooltipStack, tooltipLines, visualTooltipComponent, mouseX, guiWidth(), guiHeight(), font);
        this.renderTooltipInternal(font, list, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE);
    }

    /**
     * Renders a tooltip with a single line of text at the specified mouse coordinates.
     *
     * @param font   the font used for rendering text.
     * @param text   the text to display in the tooltip.
     * @param mouseX the x-coordinate of the mouse position.
     * @param mouseY the y-coordinate of the mouse position.
     */
    public void renderTooltip(Font font, Component text, int mouseX, int mouseY) {
        this.renderTooltip(font, List.of(text.getVisualOrderText()), mouseX, mouseY);
    }

    /**
     * Renders a tooltip with multiple lines of component-based text at the specified mouse coordinates.
     *
     * @param font         the font used for rendering text.
     * @param tooltipLines the lines of the tooltip as components.
     * @param mouseX       the x-coordinate of the mouse position.
     * @param mouseY       the y-coordinate of the mouse position.
     */
    public void renderComponentTooltip(Font font, List<Component> tooltipLines, int mouseX, int mouseY) {
        List<ClientTooltipComponent> components = net.neoforged.neoforge.client.ClientHooks.gatherTooltipComponents(this.tooltipStack, tooltipLines, mouseX, guiWidth(), guiHeight(), font);
        this.renderTooltipInternal(font, components, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE);
    }

    /**
     * Renders a tooltip with multiple lines of component-based text at the specified mouse coordinates.
     *
     * @param tooltipLines the lines of the tooltip as components.
     * @param mouseX       the x-coordinate of the mouse position.
     * @param mouseY       the y-coordinate of the mouse position.
     * @param font         the font used for rendering text.
     */
    public void renderComponentTooltip(Font p_font, List<? extends net.minecraft.network.chat.FormattedText> tooltipLines, int p_mouseX, int p_mouseY, ItemStack stack) {
        this.tooltipStack = stack;
        List<ClientTooltipComponent> components = net.neoforged.neoforge.client.ClientHooks.gatherTooltipComponents(stack, tooltipLines, p_mouseX, guiWidth(), guiHeight(), p_font);
        this.renderTooltipInternal(p_font, components, p_mouseX, p_mouseY, DefaultTooltipPositioner.INSTANCE);
        this.tooltipStack = ItemStack.EMPTY;
    }

    public void renderComponentTooltipFromElements(Font font, List<com.mojang.datafixers.util.Either<FormattedText, TooltipComponent>> elements, int mouseX, int mouseY, ItemStack stack) {
        this.tooltipStack = stack;
        List<ClientTooltipComponent> components = net.neoforged.neoforge.client.ClientHooks.gatherTooltipComponentsFromElements(stack, elements, mouseX, guiWidth(), guiHeight(), font);
        this.renderTooltipInternal(font, components, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE);
        this.tooltipStack = ItemStack.EMPTY;
    }

    /**
     * Renders a tooltip with multiple lines of formatted text at the specified mouse coordinates.
     *
     * @param font         the font used for rendering text.
     * @param tooltipLines the lines of the tooltip as formatted character sequences.
     * @param mouseX       the x-coordinate of the mouse position.
     * @param mouseY       the y-coordinate of the mouse position.
     */
    public void renderTooltip(Font font, List<? extends FormattedCharSequence> tooltipLines, int mouseX, int mouseY) {
        this.renderTooltipInternal(
            font,
            tooltipLines.stream().map(ClientTooltipComponent::create).collect(Collectors.toList()),
            mouseX,
            mouseY,
            DefaultTooltipPositioner.INSTANCE
        );
    }

    /**
     * Renders a tooltip with multiple lines of formatted text using a custom tooltip positioner at the specified mouse coordinates.
     *
     * @param font              the font used for rendering text.
     * @param tooltipLines      the lines of the tooltip as formatted character
     *                          sequences.
     * @param tooltipPositioner the positioner to determine the tooltip's position.
     * @param mouseX            the x-coordinate of the mouse position.
     * @param mouseY            the y-coordinate of the mouse position.
     */
    public void renderTooltip(Font font, List<FormattedCharSequence> tooltipLines, ClientTooltipPositioner tooltipPositioner, int mouseX, int mouseY) {
        this.renderTooltipInternal(
            font, tooltipLines.stream().map(ClientTooltipComponent::create).collect(Collectors.toList()), mouseX, mouseY, tooltipPositioner
        );
    }

    /**
     * Renders an internal tooltip with customizable tooltip components at the specified mouse coordinates using a tooltip positioner.
     *
     * @param font              the font used for rendering text.
     * @param components        the tooltip components to render.
     * @param mouseX            the x-coordinate of the mouse position.
     * @param mouseY            the y-coordinate of the mouse position.
     * @param tooltipPositioner the positioner to determine the tooltip's position.
     */
    private void renderTooltipInternal(Font font, List<ClientTooltipComponent> components, int mouseX, int mouseY, ClientTooltipPositioner tooltipPositioner) {
        if (!components.isEmpty()) {
            net.neoforged.neoforge.client.event.RenderTooltipEvent.Pre preEvent = net.neoforged.neoforge.client.ClientHooks.onRenderTooltipPre(this.tooltipStack, this, mouseX, mouseY, guiWidth(), guiHeight(), components, font, tooltipPositioner);
            if (preEvent.isCanceled()) return;
            int i = 0;
            int j = components.size() == 1 ? -2 : 0;

            for (ClientTooltipComponent clienttooltipcomponent : components) {
                int k = clienttooltipcomponent.getWidth(preEvent.getFont());
                if (k > i) {
                    i = k;
                }

                j += clienttooltipcomponent.getHeight();
            }

            int i2 = i;
            int j2 = j;
            Vector2ic vector2ic = tooltipPositioner.positionTooltip(this.guiWidth(), this.guiHeight(), preEvent.getX(), preEvent.getY(), i2, j2);
            int l = vector2ic.x();
            int i1 = vector2ic.y();
            this.pose.pushPose();
            int j1 = 400;
            net.neoforged.neoforge.client.event.RenderTooltipEvent.Color colorEvent = net.neoforged.neoforge.client.ClientHooks.onRenderTooltipColor(this.tooltipStack, this, l, i1, preEvent.getFont(), components);
            this.drawManaged(() -> TooltipRenderUtil.renderTooltipBackground(this, l, i1, i2, j2, 400, colorEvent.getBackgroundStart(), colorEvent.getBackgroundEnd(), colorEvent.getBorderStart(), colorEvent.getBorderEnd()));
            this.pose.translate(0.0F, 0.0F, 400.0F);
            int k1 = i1;

            for (int l1 = 0; l1 < components.size(); l1++) {
                ClientTooltipComponent clienttooltipcomponent1 = components.get(l1);
                clienttooltipcomponent1.renderText(preEvent.getFont(), l, k1, this.pose.last().pose(), this.bufferSource);
                k1 += clienttooltipcomponent1.getHeight() + (l1 == 0 ? 2 : 0);
            }

            k1 = i1;

            for (int k2 = 0; k2 < components.size(); k2++) {
                ClientTooltipComponent clienttooltipcomponent2 = components.get(k2);
                clienttooltipcomponent2.renderImage(preEvent.getFont(), l, k1, this);
                k1 += clienttooltipcomponent2.getHeight() + (k2 == 0 ? 2 : 0);
            }

            this.pose.popPose();
        }
    }

    /**
     * Renders a hover effect for a text component at the specified mouse coordinates.
     *
     * @param font   the font used for rendering text.
     * @param style  the style of the text component. Can be null.
     * @param mouseX the x-coordinate of the mouse position.
     * @param mouseY the y-coordinate of the mouse position.
     */
    public void renderComponentHoverEffect(Font font, @Nullable Style style, int mouseX, int mouseY) {
        if (style != null && style.getHoverEvent() != null) {
            HoverEvent hoverevent = style.getHoverEvent();
            HoverEvent.ItemStackInfo hoverevent$itemstackinfo = hoverevent.getValue(HoverEvent.Action.SHOW_ITEM);
            if (hoverevent$itemstackinfo != null) {
                this.renderTooltip(font, hoverevent$itemstackinfo.getItemStack(), mouseX, mouseY);
            } else {
                HoverEvent.EntityTooltipInfo hoverevent$entitytooltipinfo = hoverevent.getValue(HoverEvent.Action.SHOW_ENTITY);
                if (hoverevent$entitytooltipinfo != null) {
                    if (this.minecraft.options.advancedItemTooltips) {
                        this.renderComponentTooltip(font, hoverevent$entitytooltipinfo.getTooltipLines(), mouseX, mouseY);
                    }
                } else {
                    Component component = hoverevent.getValue(HoverEvent.Action.SHOW_TEXT);
                    if (component != null) {
                        this.renderTooltip(font, font.split(component, Math.max(this.guiWidth() / 2, 200)), mouseX, mouseY);
                    }
                }
            }
        }
    }

    /**
     * A utility class for managing a stack of screen rectangles for scissoring.
     */
    @OnlyIn(Dist.CLIENT)
    static class ScissorStack {
        private final Deque<ScreenRectangle> stack = new ArrayDeque<>();

        /**
         * Pushes a screen rectangle onto the scissor stack.
         * <p>
         * @return The resulting intersection of the pushed rectangle with the previous top rectangle on the stack, or the pushed rectangle if the stack is empty.
         *
         * @param scissor the screen rectangle to push.
         */
        public ScreenRectangle push(ScreenRectangle scissor) {
            ScreenRectangle screenrectangle = this.stack.peekLast();
            if (screenrectangle != null) {
                ScreenRectangle screenrectangle1 = Objects.requireNonNullElse(scissor.intersection(screenrectangle), ScreenRectangle.empty());
                this.stack.addLast(screenrectangle1);
                return screenrectangle1;
            } else {
                this.stack.addLast(scissor);
                return scissor;
            }
        }

        @Nullable
        public ScreenRectangle pop() {
            if (this.stack.isEmpty()) {
                throw new IllegalStateException("Scissor stack underflow");
            } else {
                this.stack.removeLast();
                return this.stack.peekLast();
            }
        }

        public boolean containsPoint(int x, int y) {
            return this.stack.isEmpty() ? true : this.stack.peek().containsPoint(x, y);
        }
    }
}
