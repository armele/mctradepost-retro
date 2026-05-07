package com.deathfrog.mctradepost.core.event.wishingwell.ritual;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.modules.OutpostLivingBuildingModule;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.SoundUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost;
import com.deathfrog.mctradepost.core.event.wishingwell.WishingWellHandler;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualState.RitualResult;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.IAssignsCitizen;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenDiseaseHandler;
import com.minecolonies.api.entity.citizen.happiness.ExpirationBasedHappinessModifier;
import com.minecolonies.api.entity.citizen.happiness.StaticHappinessSupplier;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.constant.HappinessConstants;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBarracksTower;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingGateHouse;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingGuardTower;
import com.minecolonies.core.datalistener.model.Disease;
import com.minecolonies.core.network.messages.client.CircleParticleEffectMessage;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;

import com.minecolonies.core.colony.buildings.modules.LivingBuildingModule;

public class CommunityRitualProcessor 
{
    final static int MIN_IMPROVEMENT = 10;
    final static int CANDIDATE_HOUSES = 50;
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Tracks assignment and savings totals while the shelter ritual is processing.
     */
    protected static class ShelterTotals
    {
        private int totalAssigned = 0;
        private int totalSavings = 0;
        private int totalSavingsAssignments = 0;

        public void recordHomelessAssignment()
        {
            totalAssigned++;
        }

        public void recordRelocation(int savings)
        {
            totalAssigned++;
            totalSavings += savings;
            totalSavingsAssignments++;
        }

        public int getTotalAssigned()
        {
            return totalAssigned;
        }

        public int getTotalSavings()
        {
            return totalSavings;
        }

        public int getTotalSavingsAssignments()
        {
            return totalSavingsAssignments;
        }
    }

    /**
     * Processes a community ritual at the specified BlockPos within the ServerLevel. This ritual gives a random effect to each citizen
     * in the colony, based on the companion item used. If the number of companion items is insufficient, the ritual cannot proceed.
     * 
     * @param marketplace the marketplace where the ritual is taking place
     * @param pos         the BlockPos of the wishing well structure
     * @param ritual      the ritual definition containing the target item and companion item count
     * @param state       the current state of the ritual, including companion item count
     * @return RitualResult indicating whether the ritual was completed, needed ingredients, or failed due to an error
     */
    public static RitualResult processRitualCommunity(@Nonnull BuildingMarketplace marketplace,
        @Nonnull BlockPos pos,
        RitualDefinitionHelper ritual,
        RitualState state)
    {
        ServerLevel currentLevel = (ServerLevel) marketplace.getColony().getWorld();

        if (currentLevel == null)
        {
            return RitualResult.FAILED;
        }

        if (state.getCompanionCount() < ritual.companionItemCount())
        {
            return RitualResult.NEEDS_INGREDIENTS;
        }

        Item companionItem = BuiltInRegistries.ITEM.get(ritual.companionItem());
        if (companionItem.equals(MCTradePostMod.WISH_SHELTER.get()))
        {
            return processRitualShelter(marketplace, pos, ritual, state);
        }

        int helpedCount = 0;
        String message = null;

        try
        {
            List<ICitizenData> citizens = marketplace.getColony().getCitizenManager().getCitizens();

            if (companionItem.equals(MCTradePostMod.WISH_PLENTY.get()))
            {
                message = "citizens fed.";
            }
            else 
            {
                message = "citizens cured.";
            }

            for (ICitizenData citizen : citizens)
            {
                if (!citizen.getEntity().isPresent())
                {
                    continue;
                }

                AbstractEntityCitizen entity = citizen.getEntity().get();

                if (companionItem.equals(MCTradePostMod.WISH_PLENTY.get()))
                {
                    if (citizen.getEntity().isPresent())
                    {
                        entity.setHealth(entity.getMaxHealth());
                        citizen.setSaturation(ICitizenData.MAX_SATURATION);
                        citizen.setJustAte(true);
                        citizen.getCitizenHappinessHandler()
                            .addModifier(new ExpirationBasedHappinessModifier(HappinessConstants.HADGREATFOOD,
                                2.0,
                                new StaticHappinessSupplier(2.0),
                                5));

                        entity.playSound(NullnessBridge.assumeNonnull(SoundEvents.NOTE_BLOCK_HARP.value()),
                            (float) SoundUtils.BASIC_VOLUME,
                            (float) com.minecolonies.api.util.SoundUtils.getRandomPentatonic(entity.getRandom()));
                        new CircleParticleEffectMessage(entity.position().add(0, 2, 0), ParticleTypes.HAPPY_VILLAGER, 1)
                            .sendToTrackingEntity(entity);
                        helpedCount++;
                    }
                }
                else if (companionItem.equals(MCTradePostMod.WISH_HEALTH.get()))
                {
                    ICitizenDiseaseHandler diseaseHandler = citizen.getCitizenDiseaseHandler();
                    final Disease disease = diseaseHandler.getDisease();
                    if (disease != null)
                    {
                        diseaseHandler.cure();
                        entity.playSound(NullnessBridge.assumeNonnull(SoundEvents.NOTE_BLOCK_HARP.value()),
                            (float) SoundUtils.BASIC_VOLUME,
                            (float) com.minecolonies.api.util.SoundUtils.getRandomPentatonic(entity.getRandom()));
                        new CircleParticleEffectMessage(entity.position().add(0, 2, 0), ParticleTypes.HAPPY_VILLAGER, 1)
                            .sendToTrackingEntity(entity);
                        helpedCount++;
                    }
                }
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to process community ritual.", e);
            return RitualResult.FAILED;
        }

        WishingWellHandler.showRitualEffect(currentLevel, pos);
        
        MessageUtils.format(helpedCount + message)
            .sendTo(marketplace.getColony())
            .forAllPlayers();

        return RitualResult.COMPLETED;
    }


    /**
     * Processes a shelter ritual at the specified BlockPos within the ServerLevel. This ritual assigns the nearest available houses to workers
     * with the highest job priority first. If a worker already has a house assigned, it will only be reassigned if the new house is a
     * meaningful improvement over the current one. If no houses can be found that are closer to the worker's workplace than their current
     * house, the worker is skipped. After all workers have been processed, any unassigned citizens are tried to be assigned to any remaining
     * available housing. If no housing is available, the citizen is left unassigned. Finally, the ritual will display a visual effect at the
     * BlockPos specified and return a RitualResult indicating whether the ritual was completed, needed ingredients, or failed due to an error.
     */
    public static RitualResult processRitualShelter(@Nonnull BuildingMarketplace marketplace,
        @Nonnull BlockPos pos,
        RitualDefinitionHelper ritual,
        RitualState state)
    {
        ServerLevel currentLevel = (ServerLevel) marketplace.getColony().getWorld();

        if (currentLevel == null)
        {
            return RitualResult.FAILED;
        }

        Map<BlockPos, IAssignsCitizen> housingMap = collectEligibleHousing(marketplace);

        final List<HomeAssignment> assignments = new ArrayList<>();

        List<ICitizenData> citizens = marketplace.getColony().getCitizenManager().getCitizens();
        final Set<ICitizenData> initiallyHomelessCitizens = new HashSet<>();
        final Map<ICitizenData, BlockPos> initialHomePositions = new HashMap<>();
        final Map<ICitizenData, Integer> initialDistances = new HashMap<>();
        int initialDistanceTotal = 0;
        int initialDistanceAssignments = 0;

        for (ICitizenData citizen : citizens)
        {
            if (!citizen.getEntity().isPresent())
            {
                continue;
            }

            if (citizen.getHomeBuilding() == null)
            {
                initiallyHomelessCitizens.add(citizen);
            }

            HomeAssignment assignment = new HomeAssignment(citizen);
            assignments.add(assignment);
            if (citizen.getHomeBuilding() != null)
            {
                initialHomePositions.put(citizen, assignment.getCurrentHome());
                initialDistances.put(citizen, assignment.getDistanceToWork());
                initialDistanceTotal += assignment.getDistanceToWork();
                initialDistanceAssignments++;
            }
        }

        final ShelterTotals totals = new ShelterTotals();

        try
        {
            sortAssignmentsByPriority(assignments);

            final Set<ICitizenData> unassignedCitizens = new HashSet<>();
            final Set<ICitizenData> excludedCitizens = new HashSet<>();
            final List<BlockPos> allHouses = new ArrayList<>(housingMap.keySet());

            assignOutpostResidents(marketplace, assignments, excludedCitizens, totals);
            collectCurrentlyUnassigned(assignments, excludedCitizens, unassignedCitizens);
            assignByJobPriority(assignments, excludedCitizens, unassignedCitizens, allHouses, housingMap, totals);
            resolveUnassignedCitizens(unassignedCitizens, allHouses, housingMap, totals);
            cleanupRemainingCommutes(assignments, excludedCitizens, allHouses, housingMap, totals);
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to process community ritual.", e);
            return RitualResult.FAILED;
        }

        int homelessAssigned = 0;
        int remainingHomeless = 0;
        int relocatedCitizens = 0;
        int relocatedSavingsTotal = 0;
        int finalDistanceTotal = 0;
        int finalDistanceAssignments = 0;

        for (ICitizenData citizen : citizens)
        {
            if (!citizen.getEntity().isPresent())
            {
                continue;
            }

            if (citizen.getHomeBuilding() == null)
            {
                remainingHomeless++;
            }
            else if (initiallyHomelessCitizens.contains(citizen))
            {
                homelessAssigned++;
            }

            if (citizen.getHomeBuilding() != null)
            {
                HomeAssignment assignment = new HomeAssignment(citizen);
                finalDistanceTotal += assignment.getDistanceToWork();
                finalDistanceAssignments++;

                if (!initiallyHomelessCitizens.contains(citizen))
                {
                    BlockPos initialHomePos = initialHomePositions.get(citizen);
                    BlockPos finalHomePos = assignment.getCurrentHome();

                    if (initialHomePos != null && finalHomePos != null && !initialHomePos.equals(finalHomePos))
                    {
                        relocatedCitizens++;
                        relocatedSavingsTotal += initialDistances.get(citizen) - assignment.getDistanceToWork();
                    }
                }
            }
        }

        int totalAssigned = homelessAssigned + relocatedCitizens;
        int averageInitialDistance = initialDistanceAssignments > 0 ? Math.round((float) initialDistanceTotal / (float) initialDistanceAssignments) : 0;
        int averageFinalDistance = finalDistanceAssignments > 0 ? Math.round((float) finalDistanceTotal / (float) finalDistanceAssignments) : 0;
        int averageSavings = relocatedCitizens > 0 ? Math.round((float) relocatedSavingsTotal / (float) relocatedCitizens) : 0;

        if (totalAssigned > 0)
        {
            MessageUtils.format("The average commute distance has been improved from " + averageInitialDistance + " to " + averageFinalDistance + ". "
                + totalAssigned + " citizens have been assigned to homes. " + homelessAssigned + (homelessAssigned == 1 ? " was " : " were ") + "previously homeless, and "
                + relocatedCitizens + (relocatedCitizens == 1 ? " was " : " were ") + "relocated with an average commute distance savings of " + averageSavings + ". "
                + remainingHomeless + (remainingHomeless == 1 ? " citizen remains " : " citizens remain ") + "homeless.").sendTo(marketplace.getColony()).forAllPlayers();
        }
        else
        {
            MessageUtils.format("The average commute distance remained at " + averageInitialDistance + ". " + totalAssigned
                + " citizens have been assigned to homes. " + homelessAssigned + (homelessAssigned == 1 ? " was " : " were ") + "previously homeless, and "
                + relocatedCitizens + (relocatedCitizens == 1 ? " was " : " were ") + "relocated with an average commute distance savings of " + averageSavings + ". "
                + remainingHomeless + (remainingHomeless == 1 ? " citizen remains " : " citizens remain ") + "homeless.").sendTo(marketplace.getColony()).forAllPlayers();
        }

        WishingWellHandler.showRitualEffect(currentLevel, pos);
        return RitualResult.COMPLETED;
    }

    /**
     * Collects the houses that are eligible for normal colony housing assignment.
     */
    protected static Map<BlockPos, IAssignsCitizen> collectEligibleHousing(@Nonnull BuildingMarketplace marketplace)
    {
        Map<BlockPos, IAssignsCitizen> housingMap = new HashMap<>();
        Map<BlockPos, IBuilding> buildingList = marketplace.getColony().getServerBuildingManager().getBuildings();

        for (IBuilding building : buildingList.values())
        {
            if (building.hasModule(LivingBuildingModule.class)
                && !(building instanceof BuildingGuardTower)
                && !(building instanceof BuildingOutpost)
                && !(building instanceof BuildingBarracksTower)
                && !(building instanceof BuildingGateHouse))
            {
                housingMap.put(building.getPosition(), building.getModule(LivingBuildingModule.class));
            }
        }

        return housingMap;
    }

    /**
     * Sorts citizens so higher-priority jobs are handled first, and among those, the worst commute is handled first.
     */
    protected static void sortAssignmentsByPriority(@Nonnull List<HomeAssignment> assignments)
    {
        assignments.sort(Comparator
            .comparingInt(HomeAssignment::getJobPriority).reversed()
            .thenComparing(Comparator.comparingInt(HomeAssignment::getDistanceToWork).reversed()));
    }

    /**
     * Moves outpost workers into their outpost housing before general colony housing is considered.
     */
    protected static void assignOutpostResidents(@Nonnull BuildingMarketplace marketplace,
        @Nonnull List<HomeAssignment> assignments,
        @Nonnull Set<ICitizenData> excludedCitizens,
        @Nonnull ShelterTotals totals)
    {
        for (HomeAssignment assignment : assignments)
        {
            ICitizenData citizen = assignment.getCitizen();
            IBuilding workBuilding = citizen.getWorkBuilding();
            IBuilding workBuildingParent = workBuilding != null && workBuilding.hasParent()
                ? marketplace.getColony().getServerBuildingManager().getBuilding(workBuilding.getParent())
                : null;
            IBuilding outpost = null;

            if (workBuilding instanceof BuildingOutpost)
            {
                outpost = workBuilding;
            }
            else if (workBuildingParent instanceof BuildingOutpost)
            {
                outpost = workBuildingParent;
            }

            if (outpost == null)
            {
                continue;
            }

            OutpostLivingBuildingModule outpostLivingModule = outpost.getModule(OutpostLivingBuildingModule.class);
            if (outpostLivingModule == null)
            {
                continue;
            }

            BlockPos previousHome = assignment.getCurrentHome();
            if (outpost.equals(citizen.getHomeBuilding()))
            {
                excludedCitizens.add(citizen);
                continue;
            }

            if (outpostLivingModule.assignCitizen(citizen))
            {
                if (previousHome == null)
                {
                    totals.recordHomelessAssignment();
                }
                else
                {
                    int oldDistance = assignment.getDistanceToWork();
                    int newDistance = BlockPosUtil.distManhattan(outpost.getPosition(), assignment.getWorkPos());
                    totals.recordRelocation(Math.max(0, oldDistance - newDistance));
                }
                excludedCitizens.add(citizen);
            }
        }
    }

    /**
     * Records citizens who should later be handled by the free-bed resolution passes.
     */
    protected static void collectCurrentlyUnassigned(@Nonnull List<HomeAssignment> assignments,
        @Nonnull Set<ICitizenData> excludedCitizens,
        @Nonnull Set<ICitizenData> unassignedCitizens)
    {
        for (HomeAssignment assignment : assignments)
        {
            if (excludedCitizens.contains(assignment.getCitizen()))
            {
                continue;
            }

            if (assignment.getCitizen().getHomeBuilding() == null)
            {
                unassignedCitizens.add(assignment.getCitizen());
            }
        }
    }

    /**
     * Runs the main housing optimization pass using job priority, limited candidate houses, and eviction when helpful.
     */
    protected static void assignByJobPriority(@Nonnull List<HomeAssignment> assignments,
        @Nonnull Set<ICitizenData> excludedCitizens,
        @Nonnull Set<ICitizenData> unassignedCitizens,
        @Nonnull List<BlockPos> allHouses,
        @Nonnull Map<BlockPos, IAssignsCitizen> housingMap,
        @Nonnull ShelterTotals totals)
    {
        for (HomeAssignment assignment : assignments)
        {
            if (excludedCitizens.contains(assignment.getCitizen()))
            {
                continue;
            }

            HomeAssignment currentAssignment = new HomeAssignment(assignment.getCitizen());

            if (currentAssignment.getWorkPos() == null)
            {
                // The unemployed are handled after workers so the priority pass focuses on commute optimization.
                unassignedCitizens.add(currentAssignment.getCitizen());
                continue;
            }

            List<BlockPos> candidates = allHouses.stream()
                .sorted(Comparator.comparingInt(h -> BlockPosUtil.distManhattan(currentAssignment.getWorkPos(), h)))
                .limit(CANDIDATE_HOUSES)
                .toList();

            int currentDist = currentAssignment.getDistanceToWork();
            boolean isHomeless = (currentAssignment.getCurrentHome() == null);
            int requiredSavings = isHomeless ? 0 : MIN_IMPROVEMENT;

            for (BlockPos possibleNewHousePos : candidates)
            {
                IAssignsCitizen livingModule = housingMap.get(possibleNewHousePos);
                int newDist = BlockPosUtil.distManhattan(currentAssignment.getWorkPos(), possibleNewHousePos);
                int savings = currentDist - newDist;

                if (savings < requiredSavings)
                {
                    // Houses are sorted nearest-first, so later candidates cannot improve the savings.
                    break;
                }

                ICitizenData evicted = insertToHouseIfCloser(livingModule, assignment.getCitizen(), newDist);

                if (evicted != null)
                {
                    if (evicted.equals(assignment.getCitizen()))
                    {
                        unassignedCitizens.add(assignment.getCitizen());
                    }
                    else
                    {
                        unassignedCitizens.add(evicted);
                    }
                }

                if (evicted == null || !evicted.equals(assignment.getCitizen()))
                {
                    if (isHomeless)
                    {
                        totals.recordHomelessAssignment();
                        unassignedCitizens.remove(assignment.getCitizen());
                    }
                    else
                    {
                        totals.recordRelocation(savings);
                    }
                    break;
                }
            }
        }
    }

    /**
     * Gives any still-unhoused citizen the first currently open bed that can accept them.
     */
    protected static void resolveUnassignedCitizens(@Nonnull Set<ICitizenData> unassignedCitizens,
        @Nonnull List<BlockPos> allHouses,
        @Nonnull Map<BlockPos, IAssignsCitizen> housingMap,
        @Nonnull ShelterTotals totals)
    {
        for (ICitizenData unassigned : unassignedCitizens)
        {
            if (unassigned.getHomeBuilding() != null)
            {
                continue;
            }

            for (BlockPos possibleNewHousePos : allHouses)
            {
                IAssignsCitizen livingModule = housingMap.get(possibleNewHousePos);
                if (!livingModule.isFull())
                {
                    livingModule.assignCitizen(unassigned);
                    totals.recordHomelessAssignment();
                    break;
                }
            }
        }
    }

    /**
     * Uses any remaining open beds to take significant commute wins for already-housed workers, without evictions.
     */
    protected static void cleanupRemainingCommutes(@Nonnull List<HomeAssignment> assignments,
        @Nonnull Set<ICitizenData> excludedCitizens,
        @Nonnull List<BlockPos> allHouses,
        @Nonnull Map<BlockPos, IAssignsCitizen> housingMap,
        @Nonnull ShelterTotals totals)
    {
        List<HomeAssignment> cleanupCandidates = assignments.stream()
            .map(assignment -> new HomeAssignment(assignment.getCitizen()))
            .filter(assignment -> !excludedCitizens.contains(assignment.getCitizen()))
            .filter(assignment -> assignment.getWorkPos() != null)
            .filter(assignment -> assignment.getCurrentHome() != null)
            .sorted(Comparator.comparingInt(HomeAssignment::getDistanceToWork).reversed())
            .toList();

        for (HomeAssignment assignment : cleanupCandidates)
        {
            int currentDist = assignment.getDistanceToWork();

            for (BlockPos possibleNewHousePos : allHouses)
            {
                IAssignsCitizen livingModule = housingMap.get(possibleNewHousePos);
                if (livingModule.isFull())
                {
                    continue;
                }

                int newDist = BlockPosUtil.distManhattan(assignment.getWorkPos(), possibleNewHousePos);
                int savings = currentDist - newDist;

                if (savings >= MIN_IMPROVEMENT && livingModule.assignCitizen(assignment.getCitizen()))
                {
                    totals.recordRelocation(savings);
                    break;
                }
            }
        }
    }


    /**
     * Insert a citizen into a house if they are closer to the house than the citizens currently living there.
     * If there is room in the house, just assign the citizen (don't kick anyone out).
     * If there is no room in the house, determine which citizen currently living there has the longest commute and evict them.
     * If the new resident has a longer commute than the evicted citizen, do not change the housing, and return the new resident as not housed.
     * @param houseModule The house to insert the citizen into.
     * @param newResident The citizen to insert into the house.
     * @param distance new resident commute to work from this house.
     * @return The citizen that was evicted from the house, or the new resident if no citizen was evicted.
     */
    protected static ICitizenData insertToHouseIfCloser(IAssignsCitizen houseModule, ICitizenData newResident, int newResidentDistance)
    {
        // If there is room, just assign the new resident
        if (!houseModule.isFull())
        {
            houseModule.assignCitizen(newResident);
            return null;
        }

        ICitizenData evictedCitizen = null;
        int worstDistance = Integer.MIN_VALUE;

        for (ICitizenData citizen : houseModule.getAssignedCitizen())
        {
            IBuilding workBuilding = citizen.getWorkBuilding();

            // If the citizen has no workplace, evict them immediately and assign the new resident
            if (workBuilding == null)
            {
                houseModule.removeCitizen(citizen);
                houseModule.assignCitizen(newResident);
                return citizen;
            }

            HomeAssignment testAssignment = new HomeAssignment(citizen);
            int dist = BlockPosUtil.distManhattan(houseModule.getBuilding().getPosition(), testAssignment.getWorkPos());
            if (dist > newResidentDistance && dist > worstDistance)
            {
                worstDistance = dist;
                evictedCitizen = citizen;
            }
        }

        if (evictedCitizen == null)
        {
            // No one to evict, new resident cannot be housed
            return newResident;
        }

        // Evict the citizen with the worst commute and assign the new resident
        houseModule.removeCitizen(evictedCitizen);
        houseModule.assignCitizen(newResident);
        return evictedCitizen;
    }
}
