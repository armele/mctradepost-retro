package com.deathfrog.mctradepost.core.event.wishingwell.ritual;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.MCTPInventoryUtils;
import com.deathfrog.mctradepost.core.event.wishingwell.WishingWellHandler;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualState.RitualResult;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class TransformRitualProcessor 
{
    public static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * Processes a transformation ritual at the specified BlockPos within the ServerLevel. This ritual transforms companion items into
     * a target item, as defined in the ritual. If the number of companion items is insufficient, the ritual cannot proceed.
     *
     * @param level  the ServerLevel where the ritual is taking place
     * @param pos    the BlockPos of the wishing well structure
     * @param ritual the ritual definition containing the target item and companion item count
     * @param state  the current state of the ritual, including companion item count
     * @return RitualResult indicating whether the ritual was completed, needed ingredients, or failed due to an error
     */
    public static RitualResult processRitualTransform(@Nonnull ServerLevel level,
        @Nonnull BlockPos pos,
        RitualDefinitionHelper ritual,
        RitualState state)
    {
        if (state.getCompanionCount() < ritual.companionItemCount())
        {
            return RitualResult.NEEDS_INGREDIENTS;
        }

        try
        {
            Item targetItem = ritual.getTargetAsItem();

            if (targetItem == null)
            {
                return RitualResult.FAILED;
            }

            MCTPInventoryUtils.dropItemsInWorld(level, pos, new ItemStack(targetItem, ritual.companionItemCount()));
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to drop items for ritual", e);
            return RitualResult.FAILED;
        }

        WishingWellHandler.showRitualEffect(level, pos);
        return RitualResult.COMPLETED;
    }    
}
