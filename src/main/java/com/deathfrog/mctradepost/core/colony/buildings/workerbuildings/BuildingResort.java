package com.deathfrog.mctradepost.core.colony.buildings.workerbuildings;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_BURNOUT;

import java.util.HashMap;
import java.util.Iterator;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.google.common.collect.ImmutableList;
import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.api.colony.buildings.jobs.MCTPModJobs;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.entity.ai.workers.minimal.EntityAIBurnoutTask;
import com.deathfrog.mctradepost.core.entity.ai.workers.minimal.Vacationer;
import com.deathfrog.mctradepost.core.entity.ai.workers.minimal.Vacationer.VacationState;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.crafting.IGenericRecipe;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.CraftingUtils;
import com.minecolonies.api.util.OptionalPredicate;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

/* 
 * Inspired on, and modeled after, a combination of the Hospital and Restaurant
 * 
 */
public class BuildingResort extends AbstractBuilding {
    public static final Logger LOGGER = LogUtils.getLogger();

    protected final static String GUEST_TAG_ID = "guests";
    protected final static int ADVERTISING_COOLDOWN_MAX = MCTPConfig.advertisingCooldown.get();      // In colony ticks (500 regular ticks)
    protected int advertisingCooldown = ADVERTISING_COOLDOWN_MAX;
    protected final static String RELAXATION_STATION_TAG = "relaxation_station";

    public final static String VACATIONS_COMPLETED = "vacations.completed";  // Used for stats.
    public final static String TREATS_SERVED = "treats.served";              // Used for stats.
    
    public final static int GUESTS_PER_LEVEL = MCTPConfig.guestsPerResortLevel.get();

    /**
     * Whether we did init tags
     */
    private boolean initTags = false;

    /**
     * Sitting positions
     */
    private List<BlockPos> sitPositions;

    /**
     * Current sitting index
     */
    private int lastSitting = 0;

    // Keep a map of who the resort has "advertized" to (who has had the
    // EntityAIBurnoutTask added to their AI)
    // This is deliberately global across all resorts.
    protected static Map<EntityCitizen, EntityAIBurnoutTask> advertisingMap = new HashMap<EntityCitizen, EntityAIBurnoutTask>();

    private final Map<Integer, Vacationer> guests = new ConcurrentHashMap<Integer, Vacationer>();

    public BuildingResort(@NotNull IColony colony, BlockPos pos) {
        super(colony, pos);
    }

    @Override
    public String getSchematicName() {
        return ModBuildings.RESORT_ID;
    }


    /**
     * Makes a reservation for the given guest at the resort.
     * <p>
     * If the resort has enough capacity (i.e., the guest count is less than the maximum allowed), the guest is added to the list of guests, the guest's state is set to RESERVED, and the guest is linked to this resort.
     * <p>
     * @param guest the guest to make a reservation for
     * @return true if the reservation was successful, false otherwise
     */
    public boolean makeReservation(Vacationer guest) {
        if (guests.size() < (getBuildingLevel() * GUESTS_PER_LEVEL)) {
            TraceUtils.dynamicTrace(TRACE_BURNOUT, () -> LOGGER.info("New reservation for {} to fix {}.", guest.getCivilianId(), guest.getBurntSkill()));
            guest.setState(Vacationer.VacationState.RESERVED);
            guest.setResort(this);
            this.guests.put(guest.getCivilianId(), guest);
            return true;
        }

        return false;
    }

    /**
     * Determines if the resort has reached maximum capacity.
     * 
     * @return true if the resort is full, false otherwise
     */
    public boolean isFull() {
        return guests.size() >= (getBuildingLevel() * GUESTS_PER_LEVEL);
    }


    /**
     * Retrieves the level of the primary skill of the guest services worker assigned to this resort.
     * If there is no guest services worker, or the worker is not assigned to a module, or the module does not have a primary skill, this method returns 0.
     * @return the level of the primary skill of the guest services worker, or 0 if no suitable worker is found.
     */
    public int getGuestServicesSkillLevel() 
    {
        WorkerBuildingModule module = this.getModuleMatching(WorkerBuildingModule.class, m -> m.getJobEntry() == MCTPModJobs.guestservices.get());

        if (module == null) 
        {
            return 0;    
        }

        ICitizenData worker = module.getAssignedCitizen().get(0);

        if (worker == null) 
        {
            return 0;
        }

        int skill = worker.getCitizenSkillHandler().getLevel(module.getPrimarySkill());

        return skill;
    }

    /**
     * Called every tick that the colony updates.
     * 
     * @param colony the colony that this building is a part of
     */
    @Override
    public void onColonyTick(IColony colony) {
        super.onColonyTick(colony);

        // Clear out the guests list when there is no worker here, or the building is destroyed, etc. and don't do any of the advertising logic.
        if (this.getBuildingLevel() <= 0
              || !this.hasModule(WorkerBuildingModule.class)
              || this.getModuleMatching(WorkerBuildingModule.class, m -> m.getJobEntry() == MCTPModJobs.guestservices.get()).getAssignedCitizen().isEmpty())
        {
            for (Vacationer guest : guests.values()) {
                removeGuestFile(guest.getCivilianId());
            }
            return;
        }

        advertisingCooldown--;

        if (advertisingCooldown > 0)
            return;


        /* Once the resort is built, citizens start thinking about vacations... */
        colony.getCitizenManager().getCitizens().forEach(citizen -> {
            Optional<AbstractEntityCitizen> citizenEntity = citizen.getEntity();
            if (citizenEntity.isPresent() && citizenEntity.get() instanceof EntityCitizen && !citizenEntity.get().isDead()) {
                EntityCitizen advertisingTarget = (EntityCitizen) citizenEntity.get();

                if (citizen.getJob() != null) {
                    if (!advertisingMap.containsKey(advertisingTarget)) {
                        // Citizens who have jobs get the EntityAIBurnoutTask added to their AI

                        EntityAIBurnoutTask burnoutTask = new EntityAIBurnoutTask(advertisingTarget);  // Note that this adds the task to the AI automatically
                        advertisingMap.put(advertisingTarget, burnoutTask);

                        Vacationer guest = guests.get(advertisingTarget.getCivilianID());
                        if (guest != null) {
                            burnoutTask.setVacationTracker(guest);
                        }

                        TraceUtils.dynamicTrace(TRACE_BURNOUT, () -> LOGGER.info("Added EntityAIBurnoutTask to " + advertisingTarget.getName()));
                    }
                }

            }
        });

        advertisingCooldown = ADVERTISING_COOLDOWN_MAX;
    }

    /**
     * Deserializes the NBT data for the building, restoring its state from the
     * provided CompoundTag.
     * Iterates through the list of patient citizens stored in the NBT, and
     * populates the guests map
     * with their data, if not already present.
     *
     * @param provider The holder lookup provider for item and block references.
     * @param compound The CompoundTag containing the serialized state of the
     *                 building.
     */
    @Override
    public void deserializeNBT(@NotNull HolderLookup.@NotNull Provider provider, CompoundTag compound) {
        super.deserializeNBT(provider, compound);
        guests.clear();
        
        ListTag guestTagList = compound.getList(GUEST_TAG_ID, 10);

        for (int i = 0; i < guestTagList.size(); ++i) {
            CompoundTag vacationCompound = guestTagList.getCompound(i);
            int guestId = vacationCompound.getInt("id");
            if (!this.guests.containsKey(guestId)) {
                Vacationer guestFile = new Vacationer(vacationCompound);
                guestFile.setResort(this);
                this.guests.put(guestId, guestFile);
            }
        }

        /* Deliberate choice to forgoe attempting to serialize injected burnout tasks 
        *  and their corresponding vacations, and instead let advertising cooldown start 
        *  at 0 and allow citizens to re-evaluate their need for a vacation immedately upon restart.
        */
        advertisingCooldown = 0;   
    }

    /**
     * Serializes the NBT data for the building, including the list of vacationers
     * who have been checked in.
     * 
     * @param provider The holder lookup provider for item and block references.
     * @return The serialized CompoundTag containing the state of the building.
     */
   public CompoundTag serializeNBT(@NotNull HolderLookup.Provider provider) {
      CompoundTag compound = super.serializeNBT(provider);
      ListTag guestTagList;
      Iterator<Vacationer> guestIterator;
      CompoundTag guestCompound;

      if (!this.guests.isEmpty()) {
         guestTagList = new ListTag();
         guestIterator = this.guests.values().iterator();

         while(guestIterator.hasNext()) {
            Vacationer guest = guestIterator.next();
            guestCompound = new CompoundTag();
            guest.write(guestCompound);
            guestTagList.add(guestCompound);
         }

         compound.put(GUEST_TAG_ID, guestTagList);
      }

      return compound;
    }


    /**
     * Called when the building has finished upgrading. Resets the flag to re-check for tagged positions.
     * <p>
     * This is necessary because the when the building is upgraded, we need to re-read the
     * positions to ensure that any new ones in the new level are known.
     */
    @Override
    public void onUpgradeComplete(final int newlevel)
    {
        initTags = false;
        super.onUpgradeComplete(newlevel);
    }

    /**
     * Reads the tag positions
     */
    public void initTagPositions()
    {
        if (initTags)
        {
            return;
        }

        sitPositions = getLocationsFromTag(RELAXATION_STATION_TAG);
        initTags = !sitPositions.isEmpty();
    }

    /**
     * Gets the next sitting position to use for eating, just keeps iterating the aviable positions, so we do not have to keep track of who is where.
     *
     * @return eating position to sit at
     */
    public BlockPos getNextSittingPosition()
    {
        initTagPositions();

        if (sitPositions.isEmpty())
        {
            LOGGER.warn("No relaxation stations found in this resort.");
            return null;
        }

        lastSitting++;

        if (lastSitting >= sitPositions.size())
        {
            lastSitting = 0;
        }

        return sitPositions.get(lastSitting);
    }

    /**
     * Returns a list of all guests currently checked in to this resort.
     * 
     * @return a list of all guests currently checked in to this resort.
     */
    public List<Vacationer> getGuests() {
        return ImmutableList.copyOf(this.guests.values());
    }

    /**
     * Retrieves the guest information for the specified citizen ID.
     * 
     * @param civilianId the ID of the citizen whose guest information is to be retrieved.
     * @return the Vacationer object corresponding to the given citizen ID, or null if not found.
     */
    public Vacationer getGuestFile(int civilianId) {
        return this.guests.get(civilianId);
    }

    /**
     * Removes all guest information from the resort's records, effectively clearing
     * out all of the guests at once.  This is intended for use when the resort is
     * being removed from the colony, or to resolve unfinished vacations.
     */
    public void clearGuests() {
        for (Vacationer guest : this.guests.values()) {
            guest.reset();
        }

        this.guests.clear();
    }

    /**
     * Removes the guest file for the given citizen ID from the resort's
     * records.  If the guest is currently checked in, this will "check them out"
     * and remove their information from the map.
     *
     * @param civilianId the ID of the citizen to check out
     */
    public void removeGuestFile(int civilianId) {
        Vacationer guest = guests.get(civilianId);

        if (guest != null) {
            TraceUtils.dynamicTrace(TRACE_BURNOUT, () -> LOGGER.info("Checking out guest {}.", civilianId));
            guest.setBurntSkill(null);
            guest.setState(VacationState.CHECKED_OUT);
            guest.setResort(null);
            guests.remove(civilianId);            
        }
    }
    public static class CraftingModule extends AbstractCraftingBuildingModule.Crafting
    {
        /**
         * Create a new module.
         *
         * @param jobEntry the entry of the job.
         */
        public CraftingModule(final JobEntry jobEntry)
        {
            super(jobEntry);
        }

        @NotNull
        @Override
        public OptionalPredicate<ItemStack> getIngredientValidator()
        {
            // How do we populate the ingredient validator.
            return CraftingUtils.getIngredientValidatorBasedOnTags(MCTPModJobs.BARTENDER_TAG)
                    .combine(super.getIngredientValidator());
        }

        @Override
        public boolean isRecipeCompatible(@NotNull final IGenericRecipe recipe)
        {
            if (!super.isRecipeCompatible(recipe)) return false;

            final Optional<Boolean> isRecipeAllowed = CraftingUtils.isRecipeCompatibleBasedOnTags(recipe, MCTPModJobs.BARTENDER_TAG);
            if (isRecipeAllowed.isPresent()) return isRecipeAllowed.get();

            return false;
        }
    }
}
