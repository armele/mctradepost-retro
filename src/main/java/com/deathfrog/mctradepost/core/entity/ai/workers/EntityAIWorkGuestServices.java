package com.deathfrog.mctradepost.core.entity.ai.workers;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingResort;
import com.deathfrog.mctradepost.core.colony.jobs.JobGuestServices;
import com.deathfrog.mctradepost.core.entity.ai.workers.minimal.Vacationer;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.api.util.Tuple;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.minecolonies.api.util.constant.StatisticsConstants.ITEM_USED;
import static com.minecolonies.core.colony.buildings.modules.BuildingModules.STATS_MODULE;

/* Heavily inspired by, and modeled after, EntityAIWorkHealer
 * @see com.minecolonies.core.entity.ai.workers.EntityAIWorkHealer
 */
public class EntityAIWorkGuestServices extends AbstractEntityAIInteract<JobGuestServices, BuildingResort> {
    /**
     * The current patient.
     */
    private Vacationer currentGuest = null;

    /**
     * Base xp gain for a guest services worker.
     */
    private static final double BASE_XP_GAIN = 2;

    public static final String GUEST_FULL_INVENTORY = "com.mctradepost.resort.guest.full_inventory";

    /**
     * How many of each resort item it should try to request at a time.
     */
    private static final int REQUEST_COUNT = 16;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public EntityAIWorkGuestServices(@NotNull final JobGuestServices job)
    {
        // TODO: TEST JobGuestServices AI
        super(job);
        super.registerTargets(
          new AITarget(IDLE, START_WORKING, 1),
          new AITarget(START_WORKING, DECIDE, 1),
          new AITarget(DECIDE, this::decide, 20),
          new AITarget(REQUEST_CURE, this::requestCure, 20),
          new AITarget(CURE, this::cure, 20),
          new AITarget(WANDER, this::wander, 20)
        );
        worker.setCanPickUpLoot(true);
    }


    /**
     * Decides whether the guest should be served.
     * 
     * @param building the building of the guest.
     * @param guest    the patient to serve.
     * 
     * @return the next state to go to.
     */
    private IAIState decideToServe(BuildingResort building, Vacationer guest) {
        final ImmutableList<IRequest<? extends Stack>> list = building.getOpenRequestsOfType(worker.getCitizenData().getId(), TypeToken.of(Stack.class));
        final ImmutableList<IRequest<? extends Stack>> completed = building.getCompletedRequestsOfType(worker.getCitizenData(), TypeToken.of(Stack.class));
        
        for (final ItemStorage cure : guest.getRemedyItems())
        {
            if (!InventoryUtils.hasItemInItemHandler(worker.getInventoryCitizen(), Vacationer.hasRemedyItem(cure)))
            {
                if (InventoryUtils.getCountFromBuilding(building, Vacationer.hasRemedyItem(cure)) >= cure.getAmount())
                {
                    // MCTradePostMod.LOGGER.info("Guest services needs cure materials.");

                    needsCurrently = new Tuple<>(Vacationer.hasRemedyItem(cure), cure.getAmount());
                    return GATHERING_REQUIRED_MATERIALS;
                }
                boolean hasCureRequested = false;
                for (final IRequest<? extends Stack> request : list)
                {
                    if (Vacationer.isRemedyItem(request.getRequest().getStack(), cure))
                    {
                        hasCureRequested = true;
                        break;
                    }
                }
                for (final IRequest<? extends Stack> request : completed)
                {
                    if (Vacationer.isRemedyItem(request.getRequest().getStack(), cure))
                    {
                        hasCureRequested = true;
                        break;
                    }
                }
                if (!hasCureRequested)
                {
                    guest.setState(Vacationer.VacationState.CHECKED_IN);
                    break;
                }
            }
        }
        return null;
    }

    private IAIState decide()
    {
        if (!walkToBuilding())
        {
            return DECIDE;
        }

        // TODO: Implement stat-specific "cures"

        final BuildingResort resort = building;

        // Assume guests already on the guest list are not at the resort, unless we find them there in the next step.
        for (final Vacationer guest : building.getGuests())
        {
            // MCTradePostMod.LOGGER.info("Guest list includes: {}", guest.getCivilianId());
            guest.setCurrentlyAtResort(false);
        }

        for (final AbstractEntityCitizen citizen : WorldUtil.getEntitiesWithinBuilding(world, AbstractEntityCitizen.class, building,
            cit -> cit.getCitizenData() != null))
        {
            Vacationer guest = resort.checkOrCreateGuestFile(citizen.getCivilianID());
            // Guests who made a reservation and are now in the building are set to "PRESENT"
            if (guest.getState() == Vacationer.VacationState.RESERVED) {
                guest.setState(Vacationer.VacationState.CHECKED_IN);
            }

            guest.setCurrentlyAtResort(true);
        }

        // There are no guests on the guest list, even after evaluating the people in the building.
        if (resort.getGuests().isEmpty())
        {
            // MCTradePostMod.LOGGER.info("No guests on the guest list.");
            return WANDER;
        }

        for (final Vacationer guest : resort.getGuests())
        {
            final ICitizenData guestData = resort.getColony().getCitizenManager().getCivilian(guest.getCivilianId());
            if (guestData == null || !guestData.getEntity().isPresent())
            {
                resort.removeGuestFile(guest);
                continue;
            }

            final EntityCitizen citizen = (EntityCitizen) guestData.getEntity().get();

            if (guest.getBurntSkill() == null || guest.getState() == Vacationer.VacationState.CHECKED_OUT)
            {
                // MCTradePostMod.LOGGER.info("Checking out guest {} ({}), who has no burnt skill.", citizen.getName(), citizen.getCivilianID());

                resort.removeGuestFile(guest);
                continue;
            }

            // Guest services does not make house calls! If they aren't currently at the resort, ignore them.
            if (!guest.isCurrentlyAtResort()) {
                // MCTradePostMod.LOGGER.info("Ignoring guest {} ({}), who is not currently at the resort.", citizen.getName(), citizen.getCivilianID());

                continue;
            }

            /*
            MCTradePostMod.LOGGER.info("Worker {} is figuring out what to do with guest {}, who is {}.", 
                worker.getName(), 
                citizen.getName(), 
                guest.isCurrentlyAtResort() ? guest.getState() : "ABSENT");
            */

            if (guest.getState() == Vacationer.VacationState.CHECKED_IN)
            {
                this.currentGuest = guest;
                return REQUEST_CURE;
            }

            if (guest.getState() == Vacationer.VacationState.REQUESTED)
            {
                if (guest.getBurntSkill() == null)
                {
                    this.currentGuest = null;
                    return DECIDE;
                }

                if (hasCureInInventory(guest, worker.getInventoryCitizen()) || hasCureInInventory(guest, building.getItemHandlerCap()))
                {
                    this.currentGuest = guest;
                    return CURE;
                }
                
                if (citizen.getInventoryCitizen().hasSpace())
                {
                    IAIState servestate = decideToServe(building, guest);
                    if (servestate != null)
                    {
                        return servestate;
                    }
                }
                else
                {
                    guestData.triggerInteraction(new StandardInteraction(Component.translatableEscape(GUEST_FULL_INVENTORY), ChatPriority.BLOCKING));
                }
            }

            if (guest.getState() == Vacationer.VacationState.TREATED)
            {
                if (guest.getBurntSkill() == null)
                {
                    this.currentGuest = null;
                    return DECIDE;
                }

                if (!hasCureInInventory(guest, citizen.getInventoryCitizen()))
                {
                    guest.setState(Vacationer.VacationState.REQUESTED);
                    return DECIDE;
                }
            }

            // Note that we are intentionally ignoring BROWSER here.
        }

        return getState();
    }


    /**
     * Request the cure for a given patient.
     *
     * @return the next state to go to.
     */
    private IAIState requestCure()
    {
        if (currentGuest == null)
        {
            return DECIDE;
        }

        final ICitizenData data = building.getColony().getCitizenManager().getCivilian(currentGuest.getCivilianId());
        if (data == null || !data.getEntity().isPresent() || !currentGuest.isCurrentlyAtResort())
        {
            currentGuest = null;
            return DECIDE;
        }

        final EntityCitizen citizen = (EntityCitizen) data.getEntity().get();
        if (!walkToSafePos(citizen.blockPosition()))
        {
            currentGuest.setState(Vacationer.VacationState.REQUESTED);
            return REQUEST_CURE;
        }

        final BuildingResort resort = building;
        final Vacationer guest = resort.getGuestFile(currentGuest.getCivilianId());
        if (guest == null)
        {
            currentGuest.setState(Vacationer.VacationState.BROWSING);
            currentGuest = null;
            return DECIDE;
        }

        final ImmutableList<IRequest<? extends Stack>> list = building.getOpenRequestsOfType(worker.getCitizenData().getId(), TypeToken.of(Stack.class));
        final ImmutableList<IRequest<? extends Stack>> completed = building.getCompletedRequestsOfType(worker.getCitizenData(), TypeToken.of(Stack.class));

        for (final ItemStorage cure : guest.getRemedyItems())
        {
            if (!InventoryUtils.hasItemInItemHandler(worker.getInventoryCitizen(), Vacationer.hasRemedyItem(cure))
                  && InventoryUtils.getCountFromBuilding(building, Vacationer.hasRemedyItem(cure)) <= 0)
            {
                boolean hasRequest = false;
                for (final IRequest<? extends Stack> request : list)
                {
                    if (Vacationer.isRemedyItem(request.getRequest().getStack(), cure))
                    {
                        hasRequest = true;
                        break;
                    }
                }
                for (final IRequest<? extends Stack> request : completed)
                {
                    if (Vacationer.isRemedyItem(request.getRequest().getStack(), cure))
                    {
                        hasRequest = true;
                        break;
                    }
                }
                if (!hasRequest)
                {
                    worker.getCitizenData().createRequestAsync(new Stack(cure.getItemStack(), REQUEST_COUNT, 1));
                }
            }
        }

        currentGuest.setState(Vacationer.VacationState.REQUESTED);
        currentGuest = null;
        return DECIDE;
    }


    /**
     * Give a citizen the cure.
     *
     * @return the next state to go to.
     */
    private IAIState cure()
    {
        if (currentGuest == null)
        {
            return DECIDE;
        }

        final ICitizenData data = building.getColony().getCitizenManager().getCivilian(currentGuest.getCivilianId());
        if (data == null || !data.getEntity().isPresent() || !currentGuest.isCurrentlyAtResort())
        {
            currentGuest = null;
            return DECIDE;
        }

        final EntityCitizen citizen = (EntityCitizen) data.getEntity().get();
        if (!walkToSafePos(data.getEntity().get().blockPosition()))
        {
            return CURE;
        }

        BuildingResort resort = building;
        final Vacationer guest = resort.getGuestFile(currentGuest.getCivilianId());
        if (guest == null)
        {
            currentGuest = null;
            return DECIDE;
        }

        if (!hasCureInInventory(guest, worker.getInventoryCitizen()))
        {
            if (hasCureInInventory(guest, building.getItemHandlerCap()))
            {
                for (final ItemStorage cure : guest.getRemedyItems())
                {
                    if (InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), Vacationer.hasRemedyItem(cure)) < cure.getAmount())
                    {
                        // MCTradePostMod.LOGGER.info("Guest services needs cure materials.");

                        needsCurrently = new Tuple<>(Vacationer.hasRemedyItem(cure), 1);
                        return GATHERING_REQUIRED_MATERIALS;
                    }
                }
            }
            currentGuest = null;
            return DECIDE;
        }

        if (!hasCureInInventory(guest, citizen.getInventoryCitizen()))
        {
            for (final ItemStorage cure : guest.getRemedyItems())
            {
                if (InventoryUtils.getItemCountInItemHandler(citizen.getInventoryCitizen(), Vacationer.hasRemedyItem(cure)) < cure.getAmount())
                {
                    if (!citizen.getInventoryCitizen().hasSpace())
                    {
                        data.triggerInteraction(new StandardInteraction(Component.translatableEscape(GUEST_FULL_INVENTORY), ChatPriority.BLOCKING));
                        currentGuest = null;
                        return DECIDE;
                    }
                    InventoryUtils.transferXOfFirstSlotInItemHandlerWithIntoNextFreeSlotInItemHandler(
                      worker.getInventoryCitizen(),
                      Vacationer.hasRemedyItem(cure),
                      cure.getAmount(), citizen.getInventoryCitizen()
                    );

                    building.getModule(STATS_MODULE).incrementBy(ITEM_USED + ";" + cure.getItemStack().getDescriptionId(), 1);

                    // MCTradePostMod.LOGGER.info("Vacationer {} has been given a cure.", citizen.getName());
                }
            }
        }

        // MCTradePostMod.LOGGER.info("Worker {} has treated guest {}.", worker.getName(), citizen.getName());

        worker.getCitizenExperienceHandler().addExperience(BASE_XP_GAIN);
        currentGuest.setState(Vacationer.VacationState.TREATED);
        currentGuest = null;
        return DECIDE;
    }


    /**
     * Wander around in the colony.
     *
     * @return the next state to go to.
     */
    private IAIState wander()
    {
        IBuilding bestMarketplace = null;
        
        for (final IBuilding building : worker.getCitizenColonyHandler().getColony().getBuildingManager().getBuildings().values()) {
            if (building instanceof BuildingMarketplace) {
                bestMarketplace = building;
                break;
            }
        }

        // Wander over to the marketplace, if one exists.
        if (bestMarketplace != null && !walkToSafePos(bestMarketplace.getPosition()))
        {
            return getState();
        }

        return START_WORKING;
    }


    /**
     * Check if the remedy for this guest is in the inventory.
     * 
     * @param handler the inventory to check.
     * @return true if so.
     */
    private boolean hasCureInInventory(final Vacationer guest, final IItemHandler handler)
    {
        for (final ItemStorage cure : guest.getRemedyItems())
        {
            if (InventoryUtils.getItemCountInItemHandler(handler, Vacationer.hasRemedyItem(cure)) < cure.getAmount())
            {
                return false;
            }
        }
        return true;
    }
    
    @Override
    public Class<BuildingResort> getExpectedBuildingClass() {
        return BuildingResort.class;
    }
}
