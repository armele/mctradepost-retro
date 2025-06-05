package com.deathfrog.mctradepost.core.entity.ai.workers;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.CURE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.DECIDE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.GATHERING_REQUIRED_MATERIALS;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.IDLE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.REQUEST_CURE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.START_WORKING;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.WANDER;

import java.util.List;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.StatsUtil;
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
import com.minecolonies.api.util.Tuple;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.mojang.logging.LogUtils;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.items.IItemHandler;

/* Heavily inspired by, and modeled after, EntityAIWorkHealer
 * @see com.minecolonies.core.entity.ai.workers.EntityAIWorkHealer
 */
public class EntityAIWorkGuestServices extends AbstractEntityAIInteract<JobGuestServices, BuildingResort> {
    public static final Logger LOGGER = LogUtils.getLogger();
    
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


    public EntityAIWorkGuestServices(@NotNull final JobGuestServices job)
    {
        super(job);
        super.registerTargets(
          new AITarget<IAIState>(IDLE, START_WORKING, 1),
          new AITarget<IAIState>(START_WORKING, DECIDE, 1),
          new AITarget<IAIState>(DECIDE, this::decide, 20),
          new AITarget<IAIState>(REQUEST_CURE, this::requestCure, 20),
          new AITarget<IAIState>(CURE, this::cure, 20),
          new AITarget<IAIState>(WANDER, this::wander, 20)
        );
        worker.setCanPickUpLoot(true);
    }

    @Override
    public IAIState getStateAfterPickUp()
    {
        LOGGER.trace("I should have picked up what I needed from the building!");
        return CURE;
    }

    /**
     * Decides whether the guest can be served.
     * 
     * @param building the resort.
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
                    LOGGER.trace("Guest services needs cure materials from the building.");

                    needsCurrently = new Tuple<>(Vacationer.hasRemedyItem(cure), cure.getAmount());
                    return GATHERING_REQUIRED_MATERIALS;
                }

                // If it's not in the building, see if we already requested it.
                boolean hasCureRequested = false;
                for (final IRequest<? extends Stack> request : list)
                {
                    if (Vacationer.isRemedyItem(request.getRequest().getStack(), cure))
                    {
                        hasCureRequested = true;
                        guest.setState(Vacationer.VacationState.REQUESTED);
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
                    LOGGER.trace("While decideToServe,request remedies to be delivered.", guest.getCivilianId());
                    worker.getCitizenData().createRequestAsync(new Stack(cure.getItemStack(), REQUEST_COUNT, 1));
                    currentGuest = null;
                    return DECIDE;
                }
            }
        }
        return CURE;
    }

    private IAIState decide()
    {
        if (!walkToBuilding())
        {
            return DECIDE;
        }

        final BuildingResort resort = building;

        // Assume guests already on the guest list are not at the resort, unless we find them there in the next step.
        for (final Vacationer guest : building.getGuests())
        {
            LOGGER.trace("Guest list includes: {} with state: {}", guest.getCivilianId(), guest.getState());
            guest.setCurrentlyAtResort(false);
        }

        for (final AbstractEntityCitizen citizen : WorldUtil.getEntitiesWithinBuilding(world, AbstractEntityCitizen.class, building,
            cit -> cit.getCitizenData() != null))
        {
            Vacationer guest = resort.checkOrCreateGuestFile(citizen.getCivilianID());
            // Guests who made a reservation and are now in the building are set to "CHECKED_IN"
            if (guest.getState() == Vacationer.VacationState.RESERVED) {
                guest.setState(Vacationer.VacationState.CHECKED_IN);
            }

            guest.setCurrentlyAtResort(true);
        }

        // There are no guests on the guest list, even after evaluating the people in the building.
        if (resort.getGuests().isEmpty())
        {
            LOGGER.trace("No guests on the guest list.");
            return WANDER;
        }

        // Update our files for any guests who have disappeared mysteriously or are checked out or have gotten their remedy elsewhere...
        for (final Vacationer guest : resort.getGuests())
        {
            final ICitizenData guestData = resort.getColony().getCitizenManager().getCivilian(guest.getCivilianId());
            if (guestData == null || !guestData.getEntity().isPresent())
            {
                LOGGER.trace("This guest is missing: {}", guest.getCivilianId());
                resort.removeGuestFile(guest.getCivilianId());
                continue;
            }

            final EntityCitizen citizen = (EntityCitizen) guestData.getEntity().get();

            if (guest.getBurntSkill() == null || guest.getState() == Vacationer.VacationState.CHECKED_OUT)
            {
                LOGGER.trace("This guest is already cured: {}", guest.getCivilianId());
                resort.removeGuestFile(guest.getCivilianId());
                continue;
            }
        }

        // Handle any guests in a CHECKED_IN state first.
        List<Vacationer> checkedInGuests = resort.getGuests().stream().filter(g -> g.getState().equals(Vacationer.VacationState.CHECKED_IN) && g.isCurrentlyAtResort()).collect(Collectors.toList());

        for (final Vacationer guest : checkedInGuests)
        {
            LOGGER.trace("At CHECKED_IN Request a cure for guest: {}", guest.getCivilianId());
            this.currentGuest = guest;
            return REQUEST_CURE;
        }

        // Handle any guests in a REQUESTED state next.
        List<Vacationer> requestedGuests = resort.getGuests().stream().filter(g -> g.getState().equals(Vacationer.VacationState.REQUESTED) && g.isCurrentlyAtResort()).collect(Collectors.toList());

        for (final Vacationer guest : requestedGuests)
        {
            final ICitizenData guestData = resort.getColony().getCitizenManager().getCivilian(guest.getCivilianId());
            final EntityCitizen citizen = (EntityCitizen) guestData.getEntity().get();

            // Check the worker inventory, and go to CURE If remedy present.
            if (hasCureInInventory(guest, worker.getInventoryCitizen()))
            {
                LOGGER.trace("At REQUESTED: This cure is on the worker, provide it to guest: {}", guest.getCivilianId());
                this.currentGuest = guest;
                return CURE;
            }

            // Check the building inventory and go to GATHERING_REQUIRED_MATERIALS If remedy present.
            if (hasCureInInventory(guest, building.getItemHandlerCap()))
            {
                LOGGER.trace("At REQUESTED: This cure is in the building, obtain and provide it to guest: {}", guest.getCivilianId());
                this.currentGuest = guest;

                // Loop through and see what we need from the building (and get it).
                for (final ItemStorage remedy : currentGuest.getRemedyItems())
                {
                    LOGGER.trace("Checking on this remedy: {}", remedy);

                    if (InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), Vacationer.hasRemedyItem(remedy)) < remedy.getAmount())
                    {
                        LOGGER.trace("Needs to get these from the building {}", remedy);
                        needsCurrently = new Tuple<>(Vacationer.hasRemedyItem(remedy), 1);
                        return GATHERING_REQUIRED_MATERIALS;
                    }
                }
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


        // Handle any guests in a TREATED state next.
        List<Vacationer> treatedGuests = resort.getGuests().stream().filter(g -> g.getState().equals(Vacationer.VacationState.REQUESTED) && g.isCurrentlyAtResort()).collect(Collectors.toList());
        for (final Vacationer guest : requestedGuests)
        {
            final ICitizenData guestData = resort.getColony().getCitizenManager().getCivilian(guest.getCivilianId());
            final EntityCitizen citizen = (EntityCitizen) guestData.getEntity().get();

            // They are either cured or lost their remedy... If they are cured they will check themselves out.
            // Set them back to REQUESTED in case they need another.
            if (!hasCureInInventory(guest, citizen.getInventoryCitizen()))
            {
                guest.setState(Vacationer.VacationState.REQUESTED);
                return DECIDE;
            }
        }

        // Note that we are intentionally ignoring BROWSER here.

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
            LOGGER.trace("Finding guest: {} to give them their remedy.", currentGuest.getCivilianId());
            currentGuest.setState(Vacationer.VacationState.REQUESTED);
            return REQUEST_CURE;
        }

        final ImmutableList<IRequest<? extends Stack>> list = building.getOpenRequestsOfType(worker.getCitizenData().getId(), TypeToken.of(Stack.class));
        final ImmutableList<IRequest<? extends Stack>> completed = building.getCompletedRequestsOfType(worker.getCitizenData(), TypeToken.of(Stack.class));

        for (final ItemStorage cure : currentGuest.getRemedyItems())
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
                    LOGGER.trace("Request remedies to be delivered.", currentGuest.getCivilianId());
                    worker.getCitizenData().createRequestAsync(new Stack(cure.getItemStack(), REQUEST_COUNT, 1));
                    
                    currentGuest = null;
                    return DECIDE;
                }
            }
            LOGGER.trace("We have this remedy to provide: {}", cure);
        }

        currentGuest.setState(Vacationer.VacationState.REQUESTED);

        return CURE;
    }

    /**
     * Actual action of applying the remedy to the guest.
     *
     * @return the next state to go to, if successful idle.
     */
    private IAIState cure()
    {
        if (currentGuest == null)
        {
            return DECIDE;
        }

        final ICitizenData data = building.getColony().getCitizenManager().getCivilian(currentGuest.getCivilianId());
        if (data == null || !data.getEntity().isPresent())
        {
            currentGuest = null;
            return DECIDE;
        }

        final EntityCitizen citizen = (EntityCitizen) data.getEntity().get();
        if (!walkToSafePos(data.getEntity().get().blockPosition()))
        {
            return CURE;
        }

        if (currentGuest.getBurntSkill() == null)
        {
            currentGuest = null;
            // citizen.heal(10);
            worker.getCitizenExperienceHandler().addExperience(BASE_XP_GAIN);
            return DECIDE;
        }

        if (!hasCureInInventory(currentGuest, worker.getInventoryCitizen()))
        {
            LOGGER.trace("It's not in our inventory, though...");

            if (hasCureInInventory(currentGuest, building.getItemHandlerCap()))
            {
                 LOGGER.trace("It is in the building...");

                for (final ItemStorage remedy : currentGuest.getRemedyItems())
                {
                    LOGGER.trace("Checking on this remedy: {}", remedy);

                    if (InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), Vacationer.hasRemedyItem(remedy)) < remedy.getAmount())
                    {
                        LOGGER.trace("Needs to get these from the building {}", remedy);
                        needsCurrently = new Tuple<>(Vacationer.hasRemedyItem(remedy), 1);
                        return GATHERING_REQUIRED_MATERIALS;
                    }
                }
            }
        }

        LOGGER.trace("At this point I should have a remedy item in hand!");

        if (!hasCureInInventory(currentGuest, citizen.getInventoryCitizen()))
        {
            for (final ItemStorage remedy : currentGuest.getRemedyItems())
            {
                if (InventoryUtils.getItemCountInItemHandler(citizen.getInventoryCitizen(), Vacationer.hasRemedyItem(remedy)) < remedy.getAmount())
                {
                    if (!citizen.getInventoryCitizen().hasSpace())
                    {
                        data.triggerInteraction(new StandardInteraction(Component.translatableEscape(GUEST_FULL_INVENTORY), ChatPriority.BLOCKING));
                        currentGuest = null;
                        return DECIDE;
                    }
                    InventoryUtils.transferXOfFirstSlotInItemHandlerWithIntoNextFreeSlotInItemHandler(
                      worker.getInventoryCitizen(),
                      Vacationer.hasRemedyItem(remedy),
                      remedy.getAmount(), citizen.getInventoryCitizen()
                    );

                    StatsUtil.trackStat(building, BuildingResort.TREATS_SERVED, remedy.getItemStack(), 1);
                }
            }
        }

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
                LOGGER.trace("Item {} NOT in handler {}", cure, handler);
                return false;
            }
            LOGGER.trace("Item {} IS in handler {}", cure, handler);
        }

        return true;
    }
    
    @Override
    public Class<BuildingResort> getExpectedBuildingClass() {
        return BuildingResort.class;
    }
}
