package com.deathfrog.mctradepost.core.colony.buildings.workerbuildings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;
import org.jetbrains.annotations.NotNull;
import com.google.common.collect.ImmutableList;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.core.entity.ai.workers.minimal.EntityAIBurnoutTask;
import com.deathfrog.mctradepost.core.entity.ai.workers.minimal.Vacationer;
import com.deathfrog.mctradepost.core.entity.ai.workers.minimal.Vacationer.VacationState;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/* 
 * Inspired on, and modeled after, a combination of the Hospital and Restaurant
 * 
 */
public class BuildingResort extends AbstractBuilding {
    protected final static String GUEST_TAG_ID = "guests";
    protected final static int ADVERTISING_COOLDOWN_MAX = 3; // In colony ticks (500 regular ticks)
    protected int advertisingCooldown = ADVERTISING_COOLDOWN_MAX;
    protected final static String RELAXATION_STATION_TAG = "relaxation_station";

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

    // Keep a list of who the resort has "advertized" to (who has had the
    // EntityAIBurnoutTask added to their AI)
    // This is deliberately global across all resorts.
    protected static List<EntityCitizen> advertisingList = new ArrayList<>();

    private final Map<Integer, Vacationer> guests = new ConcurrentHashMap<Integer, Vacationer>();

    public BuildingResort(@NotNull IColony colony, BlockPos pos) {
        super(colony, pos);
    }

    @Override
    public String getSchematicName() {
        return ModBuildings.RESORT_ID;
    }

    /**
     * Make a reservation for a given guest
     * @param guest the guest to reserve a room for
     * If the guest is already in the guest list, update their status to reserved
     * If the guest is not in the guest list, add them with a reserved status
     */
    public void makeReservation(Vacationer guest) {
        if (this.guests.containsKey(guest.getCivilianId())) {
            // TODO: RESORT remove after testing.
            MCTradePostMod.LOGGER.info("Updated existing reservation for {} to fix {}.", guest.getCivilianId(), guest.getBurntSkill());
            Vacationer existingGuest = this.guests.get(guest.getCivilianId());
            existingGuest.setState(Vacationer.VacationState.RESERVED);
        } else {
            // TODO: RESORT remove after testing.
            MCTradePostMod.LOGGER.info("New reservation for {} to fix {}.", guest.getCivilianId(), guest.getBurntSkill());
            guest.setState(Vacationer.VacationState.RESERVED);
            this.guests.put(guest.getCivilianId(), guest);
        }

    }

    /**
     * Check if a guest file exists for the given citizen ID, and if not, create it.
     * @param citizenId the ID of the citizen to check/create a guest file for
     * @return the guest file for the given citizen ID
     */
    public Vacationer checkOrCreateGuestFile(int citizenId) {
        if (!this.guests.containsKey(citizenId)) {
            this.guests.put(citizenId, new Vacationer(citizenId));
        }

        return this.guests.get(citizenId);
    }

    /**
     * Called every tick that the colony updates.
     * 
     * @param colony the colony that this building is a part of
     */
    @Override
    public void onColonyTick(IColony colony) {
        super.onColonyTick(colony);
        advertisingCooldown--;

        if (advertisingCooldown > 0)
            return;

        /* Once the resort is built, citizens start thinking about vacations... */
        colony.getCitizenManager().getCitizens().forEach(citizen -> {
            Optional<AbstractEntityCitizen> citizenEntity = citizen.getEntity();
            if (citizenEntity.isPresent() && citizenEntity.get() instanceof EntityCitizen) {
                EntityCitizen advertisingTarget = (EntityCitizen) citizenEntity.get();

                if (citizen.getJob() != null) {
                    if (!advertisingList.contains(advertisingTarget)) {
                        // Citizens who have jobs get the EntityAIBurnoutTask added to their AI
                        @SuppressWarnings("unused")
                        EntityAIBurnoutTask burnoutTask = new EntityAIBurnoutTask(advertisingTarget);  // Note that this adds the task to the AI automatically
                        advertisingList.add(advertisingTarget);

                        // TODO: Remove after testing complete
                        MCTradePostMod.LOGGER.info("Added EntityAIBurnoutTask to " + advertisingTarget.getName());

                        burnoutTask.setVacationStatus(guests.get(citizenEntity.get().getCivilianID()));
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
        ListTag guestTagList = compound.getList(GUEST_TAG_ID, 10);

        for (int i = 0; i < guestTagList.size(); ++i) {
            CompoundTag vacationCompound = guestTagList.getCompound(i);
            int guestId = vacationCompound.getInt("id");
            if (!this.guests.containsKey(guestId)) {
                this.guests.put(guestId, new Vacationer(vacationCompound));
            }
        }
    }

    /**
     * Serializes the NBT data for the building, including the list of patient
     * citizens who have been checked in.
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
            MCTradePostMod.LOGGER.warn("No relaxation stations found in this resort.");
            return null;
        }

        lastSitting++;

        if (lastSitting >= sitPositions.size())
        {
            lastSitting = 0;
        }

        return sitPositions.get(lastSitting);
    }

    public List<Vacationer> getGuests() {
        return ImmutableList.copyOf(this.guests.values());
    }

    public Vacationer getGuestFile(int civilianId) {
        return this.guests.get(civilianId);
    }

    public void removeGuestFile(Vacationer guest) {
        guest.setState(VacationState.CHECKED_OUT);
        this.guests.remove(guest.getCivilianId());
    }

}
