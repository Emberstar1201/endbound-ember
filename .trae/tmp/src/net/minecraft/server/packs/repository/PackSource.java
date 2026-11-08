package net.minecraft.server.packs.repository;

import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public interface PackSource {
    UnaryOperator<Component> NO_DECORATION = UnaryOperator.identity();
    PackSource DEFAULT = create(NO_DECORATION, true);
    PackSource BUILT_IN = create(decorateWithSource("pack.source.builtin"), true);
    PackSource FEATURE = create(decorateWithSource("pack.source.feature"), false);
    PackSource WORLD = create(decorateWithSource("pack.source.world"), true);
    PackSource SERVER = create(decorateWithSource("pack.source.server"), true);

    Component decorate(Component name);

    boolean shouldAddAutomatically();

    static PackSource create(final UnaryOperator<Component> decorator, final boolean shouldAddAutomatically) {
        return new PackSource() {
            @Override
            public Component decorate(Component p_251609_) {
                return decorator.apply(p_251609_);
            }

            @Override
            public boolean shouldAddAutomatically() {
                return shouldAddAutomatically;
            }
        };
    }

    private static UnaryOperator<Component> decorateWithSource(String translationKey) {
        Component component = Component.translatable(translationKey);
        return p_10539_ -> Component.translatable("pack.nameAndSource", p_10539_, component).withStyle(ChatFormatting.GRAY);
    }
}
