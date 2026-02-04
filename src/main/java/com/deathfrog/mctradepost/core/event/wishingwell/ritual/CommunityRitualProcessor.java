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
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.SoundUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost;
import com.deathfrog.mctradepost.core.event.wishingwell.WishingWellHandler;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualState.RitualResult;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.IAssignsCitizen;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.jobs.IJob;
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
import com.minecolonies.core.colony.jobs.*;
import com.minecolonies.core.datalistener.model.Disease;
import com.minecolonies.core.network.messages.client.CircleParticleEffectMessage;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;

import static com.minecolonies.core.colony.buildings.modules.BuildingModules.LIVING;

public class CommunityRitualProcessor 
{
    final static int MIN_IMPROVEMENT = 15;
    final static int CANDIDATE_HOUSES = 20;
    public static final Logger LOGGER = LogUtils.getLogger();

    protected static final Map<Class<? extends IJob<?>>, Integer> JOB_PRIORITIES = Map.ofEntries(
        Map.entry(JobChef.class, 100),
        Map.entry(JobCook.class, 80),
        Map.entry(JobBuilder.class, 70),
        Map.entry(JobHealer.class, 60),
        Map.entry(JobDeliveryman.class, 50),
        Map.entry(JobFarmer.class, 45),
        Map.entry(JobBaker.class, 40),
        Map.entry(JobUndertaker.class, 35),
        Map.entry(JobBlacksmith.class, 30),
        Map.entry(JobStonemason.class, 25),
        Map.entry(JobSawmill.class, 20)
    );

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


        try
        {
            List<ICitizenData> citizens = marketplace.getColony().getCitizenManager().getCitizens();

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
        class HousingData
        {
            private final IAssignsCitizen livingModule;

            public HousingData(IAssignsCitizen livingModule)
            {
                this.livingModule = livingModule;
            }

            public IAssignsCitizen getLivingModule()
            {
                return livingModule;
            }
        }

        class HomeAssignment
        {
            private final ICitizenData citizen;
            private final int          jobPriority;

            public HomeAssignment(ICitizenData citizen, int jobPriority)
            {
                this.citizen = citizen;
                this.jobPriority = jobPriority;
            }

            public ICitizenData getCitizen()
            {
                return citizen;
            }

            public int getJobPriority()
            {
                return jobPriority;
            }

            /**
             * Returns the BlockPos of the citizen's workplace. If the citizen is a deliveryman, this will be the BlockPos of the warehouse they are assigned to
             * (if any). Otherwise, this will be the BlockPos of the citizen's work building.
             * @return The BlockPos of the citizen's workplace, or null if the citizen does not have a work building or is a deliveryman without a warehouse.
             */
            public BlockPos getWorkPos()
            {
                if (citizen.getWorkBuilding() == null)
                {
                    return null;
                }

                if (citizen.getJob() instanceof JobDeliveryman deliveryJob)
                {
                    IWareHouse warehouse = deliveryJob.findWareHouse();

                    if (warehouse != null)
                    {
                        return warehouse.getPosition();
                    }
                }

                return citizen.getWorkBuilding().getPosition();
            }

            public BlockPos getCurrentHomePos()
            {
                return citizen.getHomePosition();
            }

            public int getDistanceToWork()
            {
                BlockPos workPos = getWorkPos();
                BlockPos currentHomePos = getCurrentHomePos();

                if (workPos == null || currentHomePos == null)
                {
                    return Integer.MAX_VALUE;
                }
                return BlockPosUtil.distManhattan(workPos, currentHomePos);
            }
        }

        ServerLevel currentLevel = (ServerLevel) marketplace.getColony().getWorld();

        if (currentLevel == null)
        {
            return RitualResult.FAILED;
        }

        Map<BlockPos, HousingData> housingMap = new HashMap<>();
        Map<BlockPos,IBuilding> buildingList = marketplace.getColony().getServerBuildingManager().getBuildings();

        final List<HomeAssignment> assignments = new ArrayList<>();

        for (IBuilding building : buildingList.values())
        {
            if (building.hasModule(LIVING) 
                && !(building instanceof BuildingGuardTower)
                && !(building instanceof BuildingOutpost)
                && !(building instanceof BuildingBarracksTower)
                && !(building instanceof BuildingGateHouse)
            ) {
                IAssignsCitizen livingModule = building.getModule(LIVING);
                HousingData houseData = new HousingData(livingModule);
                housingMap.put(building.getPosition(), houseData);
            }
        }

        List<ICitizenData> citizens = marketplace.getColony().getCitizenManager().getCitizens();

        for (ICitizenData citizen : citizens)
        {
            if (!citizen.getEntity().isPresent())
            {
                continue;
            }

            int citizenJobPriority = 0;
            IBuilding workBuilding = citizen.getWorkBuilding();

            if (workBuilding != null)
            {
                citizenJobPriority = JOB_PRIORITIES.getOrDefault(citizen.getJob().getClass(), 10);
            }

            HomeAssignment assignment = new HomeAssignment(citizen, citizenJobPriority);
            assignments.add(assignment);
        }

        int totalAssigned = 0;
        int totalSavings = 0;

        try
        {
            // Prioritize assignments by job priority (higher first)
            assignments.sort(Comparator
                .comparingInt(HomeAssignment::getJobPriority).reversed()
                // tie-breaker: fix worst commute first
                .thenComparingInt(HomeAssignment::getDistanceToWork));

            final Set<ICitizenData> unassignedCitizens = new HashSet<>();
            final List<BlockPos> allHouses = new ArrayList<>(housingMap.keySet());
            
            for (HomeAssignment a : assignments)
            {
                if (a.getWorkPos() == null)
                {
                    // Treat the unemployed as flexible to assign to any free bed after all prioritized workers are assigned
                    unassignedCitizens.add(a.getCitizen());
                    continue;
                }

                // Build shortlist once per worker
                List<BlockPos> candidates = allHouses.stream()
                    .sorted(Comparator.comparingInt(h -> BlockPosUtil.distManhattan(a.getWorkPos(), h)))
                    .limit(CANDIDATE_HOUSES)
                    .toList();

                int currentDist = a.getDistanceToWork();

                for (BlockPos possibleNewHousePos : candidates)
                {
                    HousingData houseData = housingMap.get(possibleNewHousePos);
                    int newDist = BlockPosUtil.distManhattan(a.getWorkPos(), possibleNewHousePos);
                    int savings = currentDist - newDist;

                    // Only consider this house if it is a meaningful improvement over the current house
                    if (savings >= MIN_IMPROVEMENT)
                    {
                        ICitizenData evicted = insertToHouseIfCloser(houseData.getLivingModule(), a.getCitizen(), newDist);

                        // Someone got evicted, add them to unassigned list
                        if (evicted != null)
                        {
                            unassignedCitizens.add(evicted);
                        }

                        if (evicted == null || !evicted.equals(a.getCitizen()))
                        {
                            // Our candidate citizen was successfully assigned to new house
                            if (unassignedCitizens.contains(a.getCitizen()))
                            {
                                totalSavings += savings;
                                totalAssigned++;
                                // If they were in our unassigned list, remove them (they found a home)
                                unassignedCitizens.remove(a.getCitizen());
                            }
                            break;
                        }
                    }
                    else
                    {
                        // No further houses will be better, skip to next worker
                        break;
                    }
                }

            }

            // Try to assign any unassigned citizens to any available housing
            for (ICitizenData unassigned : unassignedCitizens)
            {
                // They got a home somewhere else already - skip.
                if (unassigned.getHomePosition() != null) continue;

                for (BlockPos possibleNewHousePos : allHouses)
                {
                    HousingData houseData = housingMap.get(possibleNewHousePos);
                    IAssignsCitizen livingModule = houseData.getLivingModule();
                    if (!livingModule.isFull())
                    {
                        totalAssigned++;
                        livingModule.assignCitizen(unassigned);
                        break;
                    }
                }
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to process community ritual.", e);
            return RitualResult.FAILED;
        }

        if (totalAssigned > 0)
        {
            MessageUtils.format(totalAssigned + " citizens have been assigned to homes, with an average of " + Math.round((float) totalSavings / (float) totalAssigned) + " commute distance savings.").sendTo(marketplace.getColony()).forAllPlayers();
        }
        else
        {
            MessageUtils.format("No citizens were assigned to closer homes.").sendTo(marketplace.getColony()).forAllPlayers();
        }

        WishingWellHandler.showRitualEffect(currentLevel, pos);
        return RitualResult.COMPLETED;
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

            int dist = BlockPosUtil.distManhattan(houseModule.getBuilding().getPosition(), workBuilding.getPosition());
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
