package com.deathfrog.mctradepost.core.entity.ai.workers;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.DECIDE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.GATHERING_REQUIRED_MATERIALS;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.IDLE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.START_WORKING;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.WANDER;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingResort;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.colony.jobs.JobGuestServices;
import com.deathfrog.mctradepost.core.entity.ai.workers.minimal.Vacationer;
import com.deathfrog.mctradepost.core.entity.ai.workers.minimal.Vacationer.VacationState;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.mojang.logging.LogUtils;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_GUESTSERVICES;

/* Heavily inspired by, and modeled after, EntityAIWorkHealer
 * @see com.minecolonies.core.entity.ai.workers.EntityAIWorkHealer
 */
public class EntityAIWorkGuestServices extends AbstractEntityAIInteract<JobGuestServices, BuildingResort> 
{
    public static final Logger LOGGER = LogUtils.getLogger();
    
    public enum GuestservicesState implements IAIState
    {
        REQUEST_REMEDY, APPLY_REMEDY;

        @Override
        public boolean isOkayToEat()
        {
            return true;
        }
    }

    /**
     * The current patient.
     */
    private Vacationer currentGuest = null;

    /**
     * Base xp gain for a guest services worker.
     */
    private static final double BASE_XP_GAIN = 2;

    /**
     * Saturation gained from relaxing at the resort.
     */
    private static final double RELAXATION_SATURATION = 2.0;

    public static final String GUEST_FULL_INVENTORY = "com.mctradepost.resort.guest.full_inventory";

    /**
     * How many of each resort item it should try to request at a time.
     */
    private static final int REQUEST_COUNT = 8;

    /**
     * How many times the AI should attempt to find an allegedly delivered item before giving up on it.
     */
    protected int deliverAcceptanceCounter = 0;
    protected static final int SOFT_DELIVERY_ACCEPTANCE_COUNTER = 10;
    protected static final int HARD_DELIVERY_ACCEPTANCE_COUNTER = 20;

    @SuppressWarnings("unchecked")
    public EntityAIWorkGuestServices(@NotNull final JobGuestServices job)
    {
        super(job);
        super.registerTargets(
          new AITarget<IAIState>(IDLE, START_WORKING, 2),
          new AITarget<IAIState>(START_WORKING, DECIDE, 2),
          new AITarget<IAIState>(DECIDE, this::decide, 10),
          new AITarget<IAIState>(GuestservicesState.REQUEST_REMEDY, this::requestRemedy, 10),
          new AITarget<IAIState>(GuestservicesState.APPLY_REMEDY, this::applyRemedy, 10),
          new AITarget<IAIState>(WANDER, this::wander, 50)
        );
        worker.setCanPickUpLoot(true);
    }

    @Override
    public IAIState getStateAfterPickUp()
    {
        TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Colony {} - Guest Services getStateAfterPickUp: {}", building.getColony().getID(), currentGuest));
        return GuestservicesState.APPLY_REMEDY;
    }

    @Override
    public IAIState afterRequestPickUp() 
    {
        TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Colony {} - Guest Services afterRequestPickUp: {}", building.getColony().getID(), currentGuest));
        return super.afterRequestPickUp();
    }

    /**
     * Waits for the AI to receive new requests from the building. If the AI needs an item, but there are no open requests, the AI will
     * transition to the DECIDE state to decide what to do next. If the AI does not need an item, the AI will transition back to the
     * IDLE state.
     * 
     * @return The next AI state to transition to.
     */
    @Override
    protected @NotNull IAIState waitForRequests() 
    {
        IAIState state = super.waitForRequests();

        TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Colony {} - Guest Services waitForRequests() has open sync request? {} Has completed reqeusts to pick up? {} state: {}. deliverAcceptanceCounter: {}", 
            building.getColony().getID(), building.hasOpenSyncRequest(worker.getCitizenData()), building.hasCitizenCompletedRequestsToPickup(worker.getCitizenData()), state, deliverAcceptanceCounter));

        if (state != AIWorkerState.NEEDS_ITEM) 
        {
            deliverAcceptanceCounter = 0;
            return state;
        }

        if (deliverAcceptanceCounter++ < SOFT_DELIVERY_ACCEPTANCE_COUNTER || building.hasOpenSyncRequest(worker.getCitizenData())) 
        {
            return state;
        }

        boolean clearedSomething = cleanStuckRequests(deliverAcceptanceCounter);

        if (clearedSomething)
        {
            deliverAcceptanceCounter = 0;
        }

        // If we didn't clear anything, staying in NEEDS_ITEM is more honest than DECIDE.
        return clearedSomething ? AIWorkerState.DECIDE : AIWorkerState.NEEDS_ITEM;
    }

    /**
     * Cleans stuck requests from the building's request queue that are not deliverable anymore (for example, if a request is async, but the
     * citizen is not available to pick it up anymore).
     * 
     * @return true if any requests were cleared, false otherwise.
     */
    protected boolean cleanStuckRequests(int tryCounter)
    {
        ICitizenData citizen = worker.getCitizenData();
        Collection<IRequest<?>> completed = building.getCompletedRequestsOfCitizenOrBuilding(citizen);

        boolean cleared = false;

        // Copy IDs to avoid concurrent modification surprises.
        List<IRequest<?>> snapshot = new ArrayList<>(completed);

        for (IRequest<?> request : snapshot)
        {
            IToken<?> id = request.getId();
            if (!request.canBeDelivered() || citizen.isRequestAsync(id) || tryCounter > HARD_DELIVERY_ACCEPTANCE_COUNTER)
            {

                TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Colony {} - Guest Services cleanStuckRequests() clearing stuck request: {}", 
                    building.getColony().getID(), request.getLongDisplayString()));

                building.markRequestAsAccepted(citizen, id);
                cleared = true;
            }
        }

        return cleared;
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
        final ImmutableList<IRequest<? extends Stack>> openRequests = building.getOpenRequestsOfType(worker.getCitizenData().getId(), TypeToken.of(Stack.class));
        
        final ICitizenData data = building.getColony().getCitizenManager().getCivilian(guest.getCivilianId());
        final EntityCitizen vacationingCitizen = (EntityCitizen) data.getEntity().get();

        boolean somethingToHandOut = false;

        for (final ItemStorage remedy : guest.getRemedyItems())
        {
            if (!InventoryUtils.hasItemInItemHandler(worker.getInventoryCitizen(), Vacationer.hasRemedyItem(remedy))
                && !InventoryUtils.hasItemInItemHandler(vacationingCitizen.getInventoryCitizen(), Vacationer.hasRemedyItem(remedy)))
            {
                if (InventoryUtils.getCountFromBuilding(building, Vacationer.hasRemedyItem(remedy)) >= remedy.getAmount())
                {
                    TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Guest services needs remedy materials from the building: {}", remedy));

                    needsCurrently = new Tuple<>(Vacationer.hasRemedyItem(remedy), remedy.getAmount());
                    return GATHERING_REQUIRED_MATERIALS;
                }

                // If it's not in the building, see if we already requested it.
                boolean hasCureRequested = false;
                for (final IRequest<? extends Stack> request : openRequests)
                {
                    if (Vacationer.isRemedyItem(request.getRequest().getStack(), remedy))
                    {
                        TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("We have already ordered a {} that has not arrived at the building.", remedy));
                        hasCureRequested = true;
                    }
                }

                if (!hasCureRequested)
                {
                    guest.setState(Vacationer.VacationState.CHECKED_IN);
                    TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("While decideToServe for {}, request remedy {} to be delivered.", guest.getCivilianId(), remedy));
                    worker.getCitizenData().createRequestAsync(new Stack(remedy.getItemStack(), REQUEST_COUNT, 2));
                }
            }
            else
            {
                if (!InventoryUtils.hasItemInItemHandler(vacationingCitizen.getInventoryCitizen(), Vacationer.hasRemedyItem(remedy)))
                {
                    TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Guest services has something to distribute: {}", remedy));
                    somethingToHandOut = true;
                    guest.setState(VacationState.REQUESTED);
                }
                else
                {
                    TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("While decideToServe for {}, guest already has {}.", guest.getCivilianId(), remedy));
                }
            }
        }

        if (!somethingToHandOut)
        {
            guest.setState(Vacationer.VacationState.PENDING);
            return null;
        }

        return GuestservicesState.APPLY_REMEDY;
    }

    /**
     * Decide what the guest services worker should do.
     * This is the main logic loop for the guest services worker.
     * It will loop through the guests that are at the resort and decide what to do with them.
     * It will try to serve guests who are in a CHECKED_IN, REQUESTED, or TREATED state.
     * If there are no guests on the guest list, it will go into a WANDER state.
     * If there are guests, but none of them are in a CHECKED_IN, REQUESTED, or TREATED state,
     * it will go into a WANDER state as well.
     * If there are guests and at least one of them is in a CHECKED_IN, REQUESTED, or TREATED state,
     * it will go into a DECIDE state and evaluate each guest in the list to see what to do with them.
     * @return the next state to go to.
     */
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
            TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Guest list includes: {} with state: {}", guest.getCivilianId(), guest.getState()));
            guest.setCurrentlyAtResort(false);
        }

        for (final AbstractEntityCitizen citizen : WorldUtil.getEntitiesWithinBuilding(world, AbstractEntityCitizen.class, building,
            cit -> cit.getCitizenData() != null))
        {
            Vacationer guest = resort.getGuestFile(citizen.getCivilianID());

            if (guest == null)
            {
                continue;
            }

            // Guests who made a reservation and are now in the building are set to "CHECKED_IN"
            if (guest.getState() == Vacationer.VacationState.RESERVED) {
                guest.setState(Vacationer.VacationState.CHECKED_IN);
            }

            guest.setCurrentlyAtResort(true);
            citizen.getCitizenData().increaseSaturation(RELAXATION_SATURATION);
        }

        // There are no guests on the guest list, even after evaluating the people in the building.
        if (resort.getGuests().isEmpty())
        {
            TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("No guests on the guest list."));
            return WANDER;
        }

        // Update our files for any guests who have disappeared mysteriously or are checked out or have gotten their remedy elsewhere...
        for (final Vacationer guest : resort.getGuests())
        {
            final ICitizenData guestData = resort.getColony().getCitizenManager().getCivilian(guest.getCivilianId());
            if (guestData == null || !guestData.getEntity().isPresent())
            {
                TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("This guest is missing: {}", guest.getCivilianId()));
                resort.removeGuestFile(guest.getCivilianId());
                continue;
            }

            // final EntityCitizen citizen = (EntityCitizen) guestData.getEntity().get();

            if (guest.getBurntSkill() == null || guest.getState() == Vacationer.VacationState.CHECKED_OUT)
            {
                TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("This guest is already cured: {}", guest.getCivilianId()));
                guest.setState(VacationState.CHECKED_OUT);
                resort.removeGuestFile(guest.getCivilianId());
                continue;
            }
        }

        // Handle any guests in a CHECKED_IN state first.
        List<Vacationer> checkedInGuests = resort.getGuests().stream().filter(g -> g.getState().equals(Vacationer.VacationState.CHECKED_IN) && g.isCurrentlyAtResort()).collect(Collectors.toList());

        for (final Vacationer guest : checkedInGuests)
        {
            TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Colony {} guestservices At CHECKED_IN Request a remedy for guest: {}", building.getColony().getID(), guest.getCivilianId()));
            this.currentGuest = guest;
            return GuestservicesState.REQUEST_REMEDY;
        }

        // Handle any guests in a REQUESTED state next.
        List<Vacationer> requestedGuests = resort.getGuests().stream().filter(g -> g.getState().equals(Vacationer.VacationState.REQUESTED) && g.isCurrentlyAtResort()).collect(Collectors.toList());

        for (final Vacationer guest : requestedGuests)
        {
            // final ICitizenData guestData = resort.getColony().getCitizenManager().getCivilian(guest.getCivilianId());
            // final EntityCitizen citizen = (EntityCitizen) guestData.getEntity().get();

            // Check the worker inventory, and go to GuestservicesState.APPLY_REMEDY If remedy present.
            if (hasCureInInventory(guest, worker.getInventoryCitizen()))
            {
                TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("At REQUESTED: This remedy is on the worker, provide it to guest: {}", guest.getCivilianId()));
                this.currentGuest = guest;
                return GuestservicesState.APPLY_REMEDY;
            }

            // Check the building inventory and go to GATHERING_REQUIRED_MATERIALS If remedy present.
            if (hasCureInInventory(guest, building.getItemHandlerCap()))
            {
                TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("At REQUESTED: This remedy is in the building, obtain and provide it to guest: {}", guest.getCivilianId()));
                this.currentGuest = guest;

                // Loop through and see what we need from the building (and get it).
                for (final ItemStorage remedy : currentGuest.getRemedyItems())
                {
                    TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Checking on this remedy: {}", remedy));

                    if (InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), Vacationer.hasRemedyItem(remedy)) < remedy.getAmount())
                    {
                        TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Needs to get these from the building {}", remedy));
                        needsCurrently = new Tuple<>(Vacationer.hasRemedyItem(remedy), 1);
                        return GATHERING_REQUIRED_MATERIALS;
                    }
                }
            }

            IAIState servestate = decideToServe(building, guest);
            if (servestate != null)
            {
                currentGuest = guest;
                return servestate;
            }
        }


        // Handle any guests in a TREATED state next.
        List<Vacationer> treatedGuests = resort.getGuests().stream().filter(g -> g.getState().equals(Vacationer.VacationState.TREATED) && g.isCurrentlyAtResort()).collect(Collectors.toList());
        for (final Vacationer guest : treatedGuests)
        {
            final ICitizenData guestData = resort.getColony().getCitizenManager().getCivilian(guest.getCivilianId());
            final EntityCitizen citizen = (EntityCitizen) guestData.getEntity().get();

            // They are either cured or lost their remedy... If they are cured they will check themselves out.
            // Set them back to REQUESTED in case they need another.
            if (!hasCureInInventory(guest, citizen.getInventoryCitizen()))
            {
                TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("At TREATED: Marked as treated, but no longer has the remedy. Setting back to CHECKED_IN. Guest: {}", guest.getCivilianId()));
                guest.setState(Vacationer.VacationState.CHECKED_IN);
                this.currentGuest = guest;
                return GuestservicesState.REQUEST_REMEDY;
            } else {
                TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("At TREATED: Marked as treated and has the remedy, but not yet satsified. Guest: {}", guest.getCivilianId()));
            }
        }

        // Handle any guests in a PENDING state next.
        List<Vacationer> pendingGuests = resort.getGuests().stream().filter(g -> g.getState().equals(Vacationer.VacationState.PENDING) && g.isCurrentlyAtResort()).collect(Collectors.toList());
        for (final Vacationer guest : pendingGuests)
        {
            TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Colony {} guestservices At PENDING: Guest: {}", building.getColony().getID(), guest.getCivilianId()));
            IAIState servestate = decideToServe(building, guest);
            if (servestate != null)
            {
                this.currentGuest = guest;
                return servestate;
            }
        }


        // Note that we are intentionally ignoring BROWSER here.

        return getState();
    }


    /**
     * Request the remedy for a given patient.
     *
     * @return the next state to go to.
     */
    private IAIState requestRemedy()
    {
        if (currentGuest == null)
        {
            TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Colony {} guestservices - we have requested a remedy but no current guest is specified.", building.getColony().getID()));
            return DECIDE;
        }

        final ICitizenData data = building.getColony().getCitizenManager().getCivilian(currentGuest.getCivilianId());
        if (data == null)
        {
            TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Colony {} guestservices - we have requested a remedy cannot read the guest data for {}.", building.getColony().getID(), currentGuest.getCivilianId()));
            currentGuest = null;
            return DECIDE;
        }

        if (!currentGuest.isCurrentlyAtResort())
        {
            TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Colony {} guestservices - the guest we are trying to serve is not present: {}", building.getColony().getID(), currentGuest.getCivilianId()));
            currentGuest = null;
            return DECIDE;
        }

        if (currentGuest.getBurntSkill() == null || currentGuest.getState() == Vacationer.VacationState.CHECKED_OUT)
        {
            TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Colony {} guestservices - This guest is already satisfied: {}", building.getColony().getID(), currentGuest.getCivilianId()));
            currentGuest.setState(VacationState.CHECKED_OUT);
            if (currentGuest.getResort() != null)
            {
                currentGuest.getResort().removeGuestFile(currentGuest.getCivilianId());
            }

            return DECIDE;
        }

        final EntityCitizen citizen = (EntityCitizen) data.getEntity().get();
        if (!walkToSafePos(citizen.blockPosition()))
        {
            TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Colony {} guestservices - Finding guest: {} to take their order.", building.getColony().getID(), currentGuest.getCivilianId()));
            currentGuest.setState(Vacationer.VacationState.REQUESTED);
            return GuestservicesState.REQUEST_REMEDY;
        }

        TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Colony {} guestservices - Determining if ready to remedy guest: {}", building.getColony().getID(), currentGuest.getCivilianId()));
        IAIState servestate = decideToServe(building, currentGuest);
        if (servestate != null)
        {
            return servestate;
        }

        return GuestservicesState.APPLY_REMEDY;
    }

    /**
     * Actual action of applying the remedy to the guest.
     *
     * @return the next state to go to, if successful idle.
     */
    private IAIState applyRemedy()
    {
        if (currentGuest == null)
        {
            TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("I forgot who I was talking to...."));
            return DECIDE;
        }

        final ICitizenData data = building.getColony().getCitizenManager().getCivilian(currentGuest.getCivilianId());
        if (data == null || !data.getEntity().isPresent())
        {
            TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("They're gone!"));
            currentGuest = null;
            return DECIDE;
        }

        final EntityCitizen vacationingCitizen = (EntityCitizen) data.getEntity().get();
        if (!walkToSafePos(data.getEntity().get().blockPosition()))
        {
            TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Walking to them..."));
            return GuestservicesState.APPLY_REMEDY;
        }

        if (currentGuest.getBurntSkill() == null)
        {
            currentGuest = null;
            TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("No burnt skills."));
            return DECIDE;
        }

        if (!hasCureInInventory(currentGuest, worker.getInventoryCitizen()))
        {
            TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("The whole thing is not in our inventory, though. Check the building."));

            if (hasAnyCureInInventory(currentGuest, building.getItemHandlerCap()))
            {
                 TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Some of it is in the building..."));

                for (final ItemStorage remedy : currentGuest.getRemedyItems())
                {
                    TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Checking on this remedy: {}", remedy));

                    if (InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), Vacationer.hasRemedyItem(remedy)) < remedy.getAmount()
                        && InventoryUtils.getCountFromBuilding(building, Vacationer.hasRemedyItem(remedy)) > 0
                        && InventoryUtils.getItemCountInItemHandler(vacationingCitizen.getInventoryCitizen(), Vacationer.hasRemedyItem(remedy)) < remedy.getAmount())
                    {
                        TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Colony {} Guest Services: Needs to get these from the building {}", building.getColony().getID(), remedy));
                        needsCurrently = new Tuple<>(Vacationer.hasRemedyItem(remedy), 1);
                        return GATHERING_REQUIRED_MATERIALS;
                    }
                    else
                    {
                        TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Colony {} Guest Services: This remedy is missing while we're trying to serve: {}. Put them in pending.", building.getColony().getID(), remedy));
                        currentGuest.setState(VacationState.PENDING); // We don't have what they need any more - put them into pending.
                    }
                }
            } else {
                TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Colony {} Guest Services: None of it is in the building, either.", building.getColony().getID()));
                currentGuest.setState(VacationState.PENDING); // We don't have what they need any more - put them into pending.
                currentGuest = null;
                return DECIDE;
            }
        } else {
            TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Colony {} Guest Services: I have the remedy needed for {}.", building.getColony().getID(), vacationingCitizen.getName()));
        }

        ItemStack itemToHold = currentGuest.getRemedyItems().get(0).getItemStack();

        if (!itemToHold.isEmpty())
        {
            worker.setItemInHand(InteractionHand.MAIN_HAND, itemToHold);
        }

        TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Colony {} Guest Services: At this point I should have a remedy item in hand! Does the guest already have one?", building.getColony().getID()));

        if (!hasCureInInventory(currentGuest, vacationingCitizen.getInventoryCitizen()))
        {
            for (final ItemStorage remedy : currentGuest.getRemedyItems())
            {
                if (InventoryUtils.getItemCountInItemHandler(vacationingCitizen.getInventoryCitizen(), Vacationer.hasRemedyItem(remedy)) < remedy.getAmount()
                    && InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), Vacationer.hasRemedyItem(remedy)) >= remedy.getAmount())
                {
                    TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Colony {} Guest Services: They need this remedy, and we have it: {}", building.getColony().getID(), remedy));

                    if (!vacationingCitizen.getInventoryCitizen().hasSpace())
                    {
                        TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Colony {} Guest Services: They don't have space for this remedy: {}", building.getColony().getID(), remedy));
                        data.triggerInteraction(new StandardInteraction(Component.translatableEscape(GUEST_FULL_INVENTORY), ChatPriority.BLOCKING));
                        currentGuest = null;
                        return DECIDE;
                    }
                    TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Colony {} Handing over the remedy to {}.", building.getColony().getID(), vacationingCitizen.getName()));
                    InventoryUtils.transferXOfFirstSlotInItemHandlerWithIntoNextFreeSlotInItemHandler(
                      worker.getInventoryCitizen(),
                      Vacationer.hasRemedyItem(remedy),
                      remedy.getAmount(), vacationingCitizen.getInventoryCitizen()
                    );
                    
                    currentGuest.setState(Vacationer.VacationState.TREATED);

                    worker.setItemInHand(InteractionHand.MAIN_HAND, NullnessBridge.assumeNonnull(ItemStack.EMPTY));

                    StatsUtil.trackStatByStack(building, BuildingResort.TREATS_SERVED, remedy.getItemStack(), 1);
                    worker.getCitizenExperienceHandler().addExperience(BASE_XP_GAIN);
                }
                else
                {
                    TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Colony {} Guest Services: They don't need (or we don't have) this remedy: {}", building.getColony().getID(), remedy));
                }
            }
        } 
        else 
        {
            TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Colony {} Guest Services: The guest has the remedy already.  I'm keeping this one.", building.getColony().getID()));
        }

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
     * @return true if the entire remedy is in the inventory (all parts).
     */
    private boolean hasCureInInventory(final Vacationer guest, final IItemHandler handler)
    {
        for (final ItemStorage remedy : guest.getRemedyItems())
        {
            if (InventoryUtils.getItemCountInItemHandler(handler, Vacationer.hasRemedyItem(remedy)) < remedy.getAmount())
            {
                TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("hasCure: Item {} NOT in handler {}", remedy, handler));
                return false;
            }
            TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("hasCure:Item {} IS in handler {}", remedy, handler));
        }

        return true;
    }
    
    /**
     * Check if any parts of the remedy for this guest is in the inventory.
     * 
     * @param handler the inventory to check.
     * @return true if so.
     */
    private boolean hasAnyCureInInventory(final Vacationer guest, final IItemHandler handler)
    {
        int count = 0;
        for (final ItemStorage remedy : guest.getRemedyItems())
        {
            if (InventoryUtils.getItemCountInItemHandler(handler, Vacationer.hasRemedyItem(remedy)) > 0)
            {
                TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("hasAny: Some {} in handler {}", remedy, handler));
                count++;
            }
        }

        return count > 0;
    }

    @Override
    public Class<BuildingResort> getExpectedBuildingClass() {
        return BuildingResort.class;
    }
}
