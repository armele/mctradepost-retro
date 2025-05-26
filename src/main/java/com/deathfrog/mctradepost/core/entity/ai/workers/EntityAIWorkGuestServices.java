package com.deathfrog.mctradepost.core.entity.ai.workers;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingResort;
import com.deathfrog.mctradepost.core.colony.jobs.JobGuestServices;
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
import com.minecolonies.core.datalistener.model.Disease;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.ai.workers.util.Patient;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.api.util.Tuple;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.items.IItemHandler;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;

/* Heavily inspired by, and modeled after, EntityAIWorkHealer
 * @see com.minecolonies.core.entity.ai.workers.EntityAIWorkHealer
 */
public class EntityAIWorkGuestServices extends AbstractEntityAIInteract<JobGuestServices, BuildingResort> {
    /**
     * The current patient.
     */
    private Patient currentGuest = null;

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
        // TODO: TEST JobGuestServcies AI
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
     * @param disease  the disease to cure.
     * @return the next state to go to.
     */
    private IAIState decideToServe(BuildingResort building, Patient guest, Disease disease) {
        final ImmutableList<IRequest<? extends Stack>> list = building.getOpenRequestsOfType(worker.getCitizenData().getId(), TypeToken.of(Stack.class));
        final ImmutableList<IRequest<? extends Stack>> completed = building.getCompletedRequestsOfType(worker.getCitizenData(), TypeToken.of(Stack.class));
        
        for (final ItemStorage cure : disease.cureItems())
        {
            if (!InventoryUtils.hasItemInItemHandler(worker.getInventoryCitizen(), Disease.hasCureItem(cure)))
            {
                if (InventoryUtils.getCountFromBuilding(building, Disease.hasCureItem(cure)) >= cure.getAmount())
                {
                    needsCurrently = new Tuple<>(Disease.hasCureItem(cure), cure.getAmount());
                    return GATHERING_REQUIRED_MATERIALS;
                }
                boolean hasCureRequested = false;
                for (final IRequest<? extends Stack> request : list)
                {
                    if (Disease.isCureItem(request.getRequest().getStack(), cure))
                    {
                        hasCureRequested = true;
                        break;
                    }
                }
                for (final IRequest<? extends Stack> request : completed)
                {
                    if (Disease.isCureItem(request.getRequest().getStack(), cure))
                    {
                        hasCureRequested = true;
                        break;
                    }
                }
                if (!hasCureRequested)
                {
                    guest.setState(Patient.PatientState.NEW);
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
        // Walk to each relaxation station.
        // Dispense drinks and hot towels to guests nearby.

        final BuildingResort resort = building;
        for (final AbstractEntityCitizen citizen : WorldUtil.getEntitiesWithinBuilding(world, AbstractEntityCitizen.class, building,
            cit -> cit.getCitizenData() != null && cit.getCitizenData().getCitizenDiseaseHandler().isSick()))
        {
            resort.checkOrCreateGuestFile(citizen.getCivilianID());
        }

        for (final Patient guest : resort.getGuests())
        {
            final ICitizenData guestData = resort.getColony().getCitizenManager().getCivilian(guest.getId());
            if (guestData == null || !guestData.getEntity().isPresent() || (guestData.getEntity().isPresent() && !guestData.getEntity().get().getCitizenData().getCitizenDiseaseHandler().isSick()))
            {
                resort.removeGuestFile(guest);
                continue;
            }
            final EntityCitizen citizen = (EntityCitizen) guestData.getEntity().get();
            final Disease disease = citizen.getCitizenData().getCitizenDiseaseHandler().getDisease();

            if (guest.getState() == Patient.PatientState.NEW)
            {
                this.currentGuest = guest;
                return REQUEST_CURE;
            }

            if (guest.getState() == Patient.PatientState.REQUESTED)
            {
                if (disease == null)
                {
                    this.currentGuest = guest;
                    return CURE;
                }

                if (hasCureInInventory(disease, worker.getInventoryCitizen()) || hasCureInInventory(disease, building.getItemHandlerCap()))
                {
                    this.currentGuest = guest;
                    return CURE;
                }
                
                if (citizen.getInventoryCitizen().hasSpace())
                {
                    IAIState servestate = decideToServe(building, guest, disease);
                    if (!servestate.equals(null))
                    {
                        return servestate;
                    }
                }
                else
                {
                    guestData.triggerInteraction(new StandardInteraction(Component.translatableEscape(GUEST_FULL_INVENTORY), ChatPriority.BLOCKING));
                }
            }

            if (guest.getState() == Patient.PatientState.TREATED)
            {
                if (disease == null)
                {
                    this.currentGuest = guest;
                    return CURE;
                }

                if (!hasCureInInventory(disease, citizen.getInventoryCitizen()))
                {
                    guest.setState(Patient.PatientState.NEW);
                    return DECIDE;
                }
            }
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

        final ICitizenData data = building.getColony().getCitizenManager().getCivilian(currentGuest.getId());
        if (data == null || !data.getEntity().isPresent() || !data.getEntity().get().getCitizenData().getCitizenDiseaseHandler().isSick())
        {
            currentGuest = null;
            return DECIDE;
        }

        final EntityCitizen citizen = (EntityCitizen) data.getEntity().get();
        if (!walkToSafePos(citizen.blockPosition()))
        {
            return REQUEST_CURE;
        }

        final Disease disease = citizen.getCitizenData().getCitizenDiseaseHandler().getDisease();
        if (disease == null)
        {
            currentGuest.setState(Patient.PatientState.REQUESTED);
            currentGuest = null;
            return DECIDE;
        }

        final ImmutableList<IRequest<? extends Stack>> list = building.getOpenRequestsOfType(worker.getCitizenData().getId(), TypeToken.of(Stack.class));
        final ImmutableList<IRequest<? extends Stack>> completed = building.getCompletedRequestsOfType(worker.getCitizenData(), TypeToken.of(Stack.class));

        for (final ItemStorage cure : disease.cureItems())
        {
            if (!InventoryUtils.hasItemInItemHandler(worker.getInventoryCitizen(), Disease.hasCureItem(cure))
                  && InventoryUtils.getCountFromBuilding(building, Disease.hasCureItem(cure)) <= 0)
            {
                boolean hasRequest = false;
                for (final IRequest<? extends Stack> request : list)
                {
                    if (Disease.isCureItem(request.getRequest().getStack(), cure))
                    {
                        hasRequest = true;
                        break;
                    }
                }
                for (final IRequest<? extends Stack> request : completed)
                {
                    if (Disease.isCureItem(request.getRequest().getStack(), cure))
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

        currentGuest.setState(Patient.PatientState.REQUESTED);
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

        final ICitizenData data = building.getColony().getCitizenManager().getCivilian(currentGuest.getId());
        if (data == null || !data.getEntity().isPresent() || !data.getEntity().get().getCitizenData().getCitizenDiseaseHandler().isSick())
        {
            currentGuest = null;
            return DECIDE;
        }

        final EntityCitizen citizen = (EntityCitizen) data.getEntity().get();
        if (!walkToSafePos(data.getEntity().get().blockPosition()))
        {
            return CURE;
        }

        final Disease disease = citizen.getCitizenData().getCitizenDiseaseHandler().getDisease();
        if (disease == null)
        {
            currentGuest = null;
            citizen.heal(10);
            worker.getCitizenExperienceHandler().addExperience(BASE_XP_GAIN);
            return DECIDE;
        }

        if (!hasCureInInventory(disease, worker.getInventoryCitizen()))
        {
            if (hasCureInInventory(disease, building.getItemHandlerCap()))
            {
                for (final ItemStorage cure : disease.cureItems())
                {
                    if (InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), Disease.hasCureItem(cure)) < cure.getAmount())
                    {
                        needsCurrently = new Tuple<>(Disease.hasCureItem(cure), 1);
                        return GATHERING_REQUIRED_MATERIALS;
                    }
                }
            }
            currentGuest = null;
            return DECIDE;
        }

        if (!hasCureInInventory(disease, citizen.getInventoryCitizen()))
        {
            for (final ItemStorage cure : disease.cureItems())
            {
                if (InventoryUtils.getItemCountInItemHandler(citizen.getInventoryCitizen(), Disease.hasCureItem(cure)) < cure.getAmount())
                {
                    if (!citizen.getInventoryCitizen().hasSpace())
                    {
                        data.triggerInteraction(new StandardInteraction(Component.translatableEscape(GUEST_FULL_INVENTORY), ChatPriority.BLOCKING));
                        currentGuest = null;
                        return DECIDE;
                    }
                    InventoryUtils.transferXOfFirstSlotInItemHandlerWithIntoNextFreeSlotInItemHandler(
                      worker.getInventoryCitizen(),
                      Disease.hasCureItem(cure),
                      cure.getAmount(), citizen.getInventoryCitizen()
                    );
                }
            }
        }

        worker.getCitizenExperienceHandler().addExperience(BASE_XP_GAIN);
        currentGuest.setState(Patient.PatientState.TREATED);
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
     * Check if the cure for a certain illness is in the inv.
     *
     * @param disease the disease to check.
     * @param handler the inventory to check.
     * @return true if so.
     */
    private boolean hasCureInInventory(final Disease disease, final IItemHandler handler)
    {
        for (final ItemStorage cure : disease.cureItems())
        {
            if (InventoryUtils.getItemCountInItemHandler(handler, Disease.hasCureItem(cure)) < cure.getAmount())
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
