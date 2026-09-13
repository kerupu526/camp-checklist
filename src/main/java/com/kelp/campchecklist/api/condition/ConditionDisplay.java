package com.kelp.campchecklist.api.condition;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.Objects;
import java.util.Optional;

/** Config-derived metadata. Mutable Minecraft values are copied on ingress and egress. */
public record ConditionDisplay(Component label, Optional<ItemStack> itemIcon) {
    public ConditionDisplay {
        label = Objects.requireNonNull(label).copy();
        itemIcon = Objects.requireNonNull(itemIcon).filter(s -> !s.isEmpty()).map(ItemStack::copy);
    }
    @Override public Component label() { return label.copy(); }
    @Override public Optional<ItemStack> itemIcon() { return itemIcon.map(ItemStack::copy); }
}
