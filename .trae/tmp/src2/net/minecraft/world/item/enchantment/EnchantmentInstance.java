package net.minecraft.world.item.enchantment;

import net.minecraft.core.Holder;
import net.minecraft.util.random.WeightedEntry;

/**
 * Defines an immutable instance of an enchantment and its level.
 */
public class EnchantmentInstance extends WeightedEntry.IntrusiveBase {
    /**
     * The enchantment being represented.
     */
    public final Holder<Enchantment> enchantment;
    /**
     * The level of the enchantment.
     */
    public final int level;

    public EnchantmentInstance(Holder<Enchantment> enchantment, int level) {
        super(enchantment.value().getWeight());
        this.enchantment = enchantment;
        this.level = level;
    }
}
