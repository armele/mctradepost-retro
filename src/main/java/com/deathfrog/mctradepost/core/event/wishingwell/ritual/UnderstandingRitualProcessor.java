package com.deathfrog.mctradepost.core.event.wishingwell.ritual;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.advancements.MCTPAdvancementTriggers;
import com.deathfrog.mctradepost.api.util.MCTPInventoryUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.event.wishingwell.WishingWellHandler;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualState.RitualResult;
import com.minecolonies.core.util.AdvancementUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Performs the Wish of Understanding and awards its guide book. */
public final class UnderstandingRitualProcessor
{
    private UnderstandingRitualProcessor()
    {
    }

    /**
     * Creates the guide book and awards the associated colony advancement.
     *
     * @param marketplace marketplace whose wishing well performed the ritual
     * @param pos wishing-well position
     * @param ritual ritual definition
     * @param state current detected ritual ingredients
     * @return completion state; ingredients remain untouched when processing fails
     */
    @SuppressWarnings("null")
    public static RitualResult process(@Nonnull BuildingMarketplace marketplace,
        @Nonnull BlockPos pos,
        @Nonnull RitualDefinitionHelper ritual,
        @Nonnull RitualState state)
    {
        if (state.getCompanionCount() < ritual.companionItemCount())
        {
            return RitualResult.NEEDS_INGREDIENTS;
        }

        ServerLevel level = (ServerLevel) marketplace.getColony().getWorld();
        if (level == null)
        {
            return RitualResult.FAILED;
        }

        MCTPInventoryUtils.dropItemsInWorld(level, pos, UnderstandingGuideBook.create());
        WishingWellHandler.showRitualEffect(level, pos);
        AdvancementUtils.TriggerAdvancementPlayersForColony(marketplace.getColony(), player -> {
            if (player != null)
            {
                MCTPAdvancementTriggers.MAKE_WISH.get().trigger(player);
            }
        });
        return RitualResult.COMPLETED;
    }
}
