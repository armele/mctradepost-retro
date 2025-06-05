package com.deathfrog.mctradepost.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ImmersionBlenderItem extends Item {
    public ImmersionBlenderItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public boolean hasCraftingRemainingItem() {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setDamageValue(stack.getDamageValue() + 2);
        return copy;
    }
    
}
