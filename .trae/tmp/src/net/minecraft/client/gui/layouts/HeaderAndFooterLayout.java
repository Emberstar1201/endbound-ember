package net.minecraft.client.gui.layouts;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class HeaderAndFooterLayout implements Layout {
    public static final int DEFAULT_HEADER_AND_FOOTER_HEIGHT = 33;
    private static final int CONTENT_MARGIN_TOP = 30;
    private final FrameLayout headerFrame = new FrameLayout();
    private final FrameLayout footerFrame = new FrameLayout();
    private final FrameLayout contentsFrame = new FrameLayout();
    private final Screen screen;
    private int headerHeight;
    private int footerHeight;

    public HeaderAndFooterLayout(Screen screen) {
        this(screen, 33);
    }

    public HeaderAndFooterLayout(Screen screen, int height) {
        this(screen, height, height);
    }

    public HeaderAndFooterLayout(Screen screen, int headerHeight, int footerHeight) {
        this.screen = screen;
        this.headerHeight = headerHeight;
        this.footerHeight = footerHeight;
        this.headerFrame.defaultChildLayoutSetting().align(0.5F, 0.5F);
        this.footerFrame.defaultChildLayoutSetting().align(0.5F, 0.5F);
    }

    @Override
    public void setX(int x) {
    }

    @Override
    public void setY(int y) {
    }

    @Override
    public int getX() {
        return 0;
    }

    @Override
    public int getY() {
        return 0;
    }

    @Override
    public int getWidth() {
        return this.screen.width;
    }

    @Override
    public int getHeight() {
        return this.screen.height;
    }

    public int getFooterHeight() {
        return this.footerHeight;
    }

    public void setFooterHeight(int footerHeight) {
        this.footerHeight = footerHeight;
    }

    public void setHeaderHeight(int headerHeight) {
        this.headerHeight = headerHeight;
    }

    public int getHeaderHeight() {
        return this.headerHeight;
    }

    public int getContentHeight() {
        return this.screen.height - this.getHeaderHeight() - this.getFooterHeight();
    }

    @Override
    public void visitChildren(Consumer<LayoutElement> visitor) {
        this.headerFrame.visitChildren(visitor);
        this.contentsFrame.visitChildren(visitor);
        this.footerFrame.visitChildren(visitor);
    }

    @Override
    public void arrangeElements() {
        int i = this.getHeaderHeight();
        int j = this.getFooterHeight();
        this.headerFrame.setMinWidth(this.screen.width);
        this.headerFrame.setMinHeight(i);
        this.headerFrame.setPosition(0, 0);
        this.headerFrame.arrangeElements();
        this.footerFrame.setMinWidth(this.screen.width);
        this.footerFrame.setMinHeight(j);
        this.footerFrame.arrangeElements();
        this.footerFrame.setY(this.screen.height - j);
        this.contentsFrame.setMinWidth(this.screen.width);
        this.contentsFrame.arrangeElements();
        int k = i + 30;
        int l = this.screen.height - j - this.contentsFrame.getHeight();
        this.contentsFrame.setPosition(0, Math.min(k, l));
    }

    public <T extends LayoutElement> T addToHeader(T child) {
        return this.headerFrame.addChild(child);
    }

    public <T extends LayoutElement> T addToHeader(T child, Consumer<LayoutSettings> layoutSettingsFactory) {
        return this.headerFrame.addChild(child, layoutSettingsFactory);
    }

    public void addTitleHeader(Component message, Font font) {
        this.headerFrame.addChild(new StringWidget(message, font));
    }

    public <T extends LayoutElement> T addToFooter(T child) {
        return this.footerFrame.addChild(child);
    }

    public <T extends LayoutElement> T addToFooter(T child, Consumer<LayoutSettings> layoutSettingsFactory) {
        return this.footerFrame.addChild(child, layoutSettingsFactory);
    }

    public <T extends LayoutElement> T addToContents(T child) {
        return this.contentsFrame.addChild(child);
    }

    public <T extends LayoutElement> T addToContents(T child, Consumer<LayoutSettings> layoutSettingFactory) {
        return this.contentsFrame.addChild(child, layoutSettingFactory);
    }
}
