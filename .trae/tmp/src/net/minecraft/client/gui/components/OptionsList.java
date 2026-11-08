package net.minecraft.client.gui.components;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class OptionsList extends ContainerObjectSelectionList<OptionsList.Entry> {
    private static final int BIG_BUTTON_WIDTH = 310;
    private static final int DEFAULT_ITEM_HEIGHT = 25;
    private final OptionsSubScreen screen;

    public OptionsList(Minecraft minecraft, int width, OptionsSubScreen screen) {
        super(minecraft, width, screen.layout.getContentHeight(), screen.layout.getHeaderHeight(), 25);
        this.centerListVertically = false;
        this.screen = screen;
    }

    public void addBig(OptionInstance<?> option) {
        this.addEntry(OptionsList.OptionEntry.big(this.minecraft.options, option, this.screen));
    }

    public void addSmall(OptionInstance<?>... options) {
        for (int i = 0; i < options.length; i += 2) {
            OptionInstance<?> optioninstance = i < options.length - 1 ? options[i + 1] : null;
            this.addEntry(OptionsList.OptionEntry.small(this.minecraft.options, options[i], optioninstance, this.screen));
        }
    }

    public void addSmall(List<AbstractWidget> options) {
        for (int i = 0; i < options.size(); i += 2) {
            this.addSmall(options.get(i), i < options.size() - 1 ? options.get(i + 1) : null);
        }
    }

    public void addSmall(AbstractWidget leftOption, @Nullable AbstractWidget rightOption) {
        this.addEntry(OptionsList.Entry.small(leftOption, rightOption, this.screen));
    }

    @Override
    public int getRowWidth() {
        return 310;
    }

    @Nullable
    public AbstractWidget findOption(OptionInstance<?> option) {
        for (OptionsList.Entry optionslist$entry : this.children()) {
            if (optionslist$entry instanceof OptionsList.OptionEntry optionslist$optionentry) {
                AbstractWidget abstractwidget = optionslist$optionentry.options.get(option);
                if (abstractwidget != null) {
                    return abstractwidget;
                }
            }
        }

        return null;
    }

    public void applyUnsavedChanges() {
        for (OptionsList.Entry optionslist$entry : this.children()) {
            if (optionslist$entry instanceof OptionsList.OptionEntry) {
                OptionsList.OptionEntry optionslist$optionentry = (OptionsList.OptionEntry)optionslist$entry;

                for (AbstractWidget abstractwidget : optionslist$optionentry.options.values()) {
                    if (abstractwidget instanceof OptionInstance.OptionInstanceSliderButton<?> optioninstancesliderbutton) {
                        optioninstancesliderbutton.applyUnsavedValue();
                    }
                }
            }
        }
    }

    public Optional<GuiEventListener> getMouseOver(double mouseX, double mouseY) {
        for (OptionsList.Entry optionslist$entry : this.children()) {
            for (GuiEventListener guieventlistener : optionslist$entry.children()) {
                if (guieventlistener.isMouseOver(mouseX, mouseY)) {
                    return Optional.of(guieventlistener);
                }
            }
        }

        return Optional.empty();
    }

    @OnlyIn(Dist.CLIENT)
    protected static class Entry extends ContainerObjectSelectionList.Entry<OptionsList.Entry> {
        private final List<AbstractWidget> children;
        private final Screen screen;
        private static final int X_OFFSET = 160;

        Entry(List<AbstractWidget> children, Screen screen) {
            this.children = ImmutableList.copyOf(children);
            this.screen = screen;
        }

        public static OptionsList.Entry big(List<AbstractWidget> options, Screen screen) {
            return new OptionsList.Entry(options, screen);
        }

        public static OptionsList.Entry small(AbstractWidget leftOption, @Nullable AbstractWidget rightOption, Screen screen) {
            return rightOption == null
                ? new OptionsList.Entry(ImmutableList.of(leftOption), screen)
                : new OptionsList.Entry(ImmutableList.of(leftOption, rightOption), screen);
        }

        @Override
        public void render(
            GuiGraphics guiGraphics,
            int index,
            int top,
            int left,
            int width,
            int height,
            int mouseX,
            int mouseY,
            boolean hovering,
            float partialTick
        ) {
            int i = 0;
            int j = this.screen.width / 2 - 155;

            for (AbstractWidget abstractwidget : this.children) {
                abstractwidget.setPosition(j + i, top);
                abstractwidget.render(guiGraphics, mouseX, mouseY, partialTick);
                i += 160;
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.children;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.children;
        }
    }

    @OnlyIn(Dist.CLIENT)
    protected static class OptionEntry extends OptionsList.Entry {
        final Map<OptionInstance<?>, AbstractWidget> options;

        private OptionEntry(Map<OptionInstance<?>, AbstractWidget> options, OptionsSubScreen screen) {
            super(ImmutableList.copyOf(options.values()), screen);
            this.options = options;
        }

        public static OptionsList.OptionEntry big(Options options, OptionInstance<?> option, OptionsSubScreen screen) {
            return new OptionsList.OptionEntry(ImmutableMap.of(option, option.createButton(options, 0, 0, 310)), screen);
        }

        public static OptionsList.OptionEntry small(
            Options options, OptionInstance<?> leftOption, @Nullable OptionInstance<?> rightOption, OptionsSubScreen screen
        ) {
            AbstractWidget abstractwidget = leftOption.createButton(options);
            return rightOption == null
                ? new OptionsList.OptionEntry(ImmutableMap.of(leftOption, abstractwidget), screen)
                : new OptionsList.OptionEntry(ImmutableMap.of(leftOption, abstractwidget, rightOption, rightOption.createButton(options)), screen);
        }
    }
}
