package com.deathfrog.mctradepost.core.event.wishingwell.ritual;

import java.util.Iterator;
import java.util.List;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.item.CoinItem;
import com.minecolonies.api.crafting.ItemStorage;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class RitualState
{
    public enum RitualResult 
    {
        UNRECOGNIZED,
        NEEDS_INGREDIENTS,
        FAILED,
        COMPLETED
    }

    public List<ItemEntity> baseCoins = null;
    public List<ItemEntity> goldCoins = null;
    public List<ItemEntity> diamondCoins = null;
    public List<ItemEntity> companionItems = null;
    public long lastUsed = 0L;

    /**
     * Returns the total number of items in the given list of ItemEntities
     * 
     * @param list the list of ItemEntities to count
     * @return the total number of items
     */
    public int entityCount(List<ItemEntity> list)
    {
        int count = 0;
        for (ItemEntity entity : list)
        {
            count += entity.getItem().getCount();
        }
        return count;
    }

    /**
     * Returns true if the given requirementStack can be fulfilled by the contents of the wishing well's inventory.
     * 
     * @param requirementStack the stack of items required by the ritual
     * @return whether the well has enough items to fulfill the requirement
     */
    public boolean meetsRequirements(ItemStorage requirementStack)
    {

        CoinItem coinItem = MCTradePostMod.MCTP_COIN_ITEM.get();
        CoinItem goldCoin = MCTradePostMod.MCTP_COIN_GOLD.get();
        CoinItem diamondCoin = MCTradePostMod.MCTP_COIN_DIAMOND.get();

        if (coinItem == null || goldCoin == null || diamondCoin == null) 
        {
            throw new IllegalStateException("Trade Post Coin items not initialized. This should never happen. Please report.");
        }

        if (requirementStack.getItemStack().is(coinItem))
        {
            return entityCount(baseCoins) >= requirementStack.getItemStack().getCount();
        }
        else if (requirementStack.getItemStack().is(goldCoin))
        {
            return entityCount(goldCoins) >= requirementStack.getItemStack().getCount();
        }
        else if (requirementStack.getItemStack().is(diamondCoin))
        {
            return entityCount(diamondCoins) >= requirementStack.getItemStack().getCount();
        }
        else
        {
            return false;
        }
    }

    /**
     * Removes the given number of coins from the wishing well's inventory.
     * 
     * @param requirementStack the stack of items to remove from the well
     */
    public void burnCoins(ItemStorage requirementStack)
    {
        int toBurn = requirementStack.getItemStack().getCount();
        if (toBurn <= 0) return;

        // Select the correct coin pool based on item type
        final Item reqItem = requirementStack.getItemStack().getItem();
        List<ItemEntity> pool = null;
        if (reqItem == MCTradePostMod.MCTP_COIN_ITEM.get())
        {
            pool = this.baseCoins;
        }
        else if (reqItem == MCTradePostMod.MCTP_COIN_GOLD.get())
        {
            pool = this.goldCoins;
        }
        else if (reqItem == MCTradePostMod.MCTP_COIN_DIAMOND.get())
        {
            pool = this.diamondCoins;
        }
        if (pool == null || pool.isEmpty()) return;

        // Consume from only the selected pool
        for (Iterator<ItemEntity> it = pool.iterator(); it.hasNext() && toBurn > 0;)
        {
            ItemEntity entity = it.next();
            ItemStack stack = entity.getItem();
            int inStack = stack.getCount();

            if (inStack > toBurn)
            {
                // Burn part of the stack
                stack.shrink(toBurn);
                toBurn = 0;
                // entity remains with reduced count
            }
            else
            {
                // Burn the whole stack
                toBurn -= inStack;
                entity.discard();   // remove from world
                it.remove();        // keep your cached list in sync
            }
        }
    }

    /**
     * Returns the total number of items in the companion items list.
     * @return the total number of companion items
     */
    public int getCompanionCount()
    {
        if (this.companionItems == null || this.companionItems.isEmpty()) return 0;

        int totalCompanionCount = this.companionItems.stream()
                .mapToInt(e -> e.getItem().getCount())
                .sum();

        return totalCompanionCount;
    }
}
