package com.deathfrog.mctradepost.core.event.wishingwell.ritual;

import java.util.List;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.event.wishingwell.WishingWellHandler;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualState.RitualResult;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.research.ILocalResearch;
import com.minecolonies.api.research.ILocalResearchTree;
import com.minecolonies.api.research.IResearchManager;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingUniversity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Applies wishing-well research-time credit to a colony. */
public final class KnowledgeRitualProcessor
{
    /** MineColonies estimates one research progress unit as 25 seconds. */
    private static final int ONE_HOUR_RESEARCH_CREDIT = 60 * 60 / 25;

    private KnowledgeRitualProcessor()
    {
    }

    /**
     * Advances every research which is in progress when the ritual begins.
     */
    public static RitualResult processRitualKnowledge(@Nonnull final BuildingMarketplace marketplace,
        @Nonnull final BlockPos pos,
        @Nonnull final RitualDefinitionHelper ritual,
        @Nonnull final RitualState state)
    {
        if (state.getCompanionCount() < ritual.companionItemCount())
        {
            return RitualResult.NEEDS_INGREDIENTS;
        }

        final IColony colony = marketplace.getColony();
        final ServerLevel serverLevel = (ServerLevel) colony.getWorld();

        if (serverLevel == null) 
        {
            MessageUtils.format("No server level associated with colony.")
                .sendTo(colony)
                .forAllPlayers();
                
            return RitualResult.FAILED;    
        }

        final IBuilding universityBuilding = colony.getServerBuildingManager()
            .getFirstBuildingMatching(building -> building instanceof BuildingUniversity);

        if (!(universityBuilding instanceof BuildingUniversity university))
        {
            MessageUtils.format("message.mctradepost.wish_knowledge.university_required")
                .sendTo(colony)
                .forAllPlayers();
            return RitualResult.FAILED;
        }

        final IResearchManager researchManager = colony.getResearchManager();
        final ILocalResearchTree researchTree = researchManager.getResearchTree();
        final List<ILocalResearch> inProgress = researchTree.getResearchInProgress();

        if (inProgress.isEmpty())
        {
            MessageUtils.format("message.mctradepost.wish_knowledge.no_research")
                .sendTo(colony)
                .forAllPlayers();
            return RitualResult.FAILED;
        }

        for (final ILocalResearch research : inProgress)
        {
            for (int progress = 0; progress < ONE_HOUR_RESEARCH_CREDIT; progress++)
            {
                if (research.research(researchManager.getResearchEffects(), researchTree))
                {
                    university.onSuccess(research);
                    break;
                }
            }
        }

        researchManager.markDirty();
        colony.markDirty();
        WishingWellHandler.showRitualEffect(serverLevel, pos);
        return RitualResult.COMPLETED;
    }
}
