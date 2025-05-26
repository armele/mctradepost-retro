package com.deathfrog.mctradepost.core.colony.buildings.workerbuildings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Iterator;
import org.jetbrains.annotations.NotNull;
import com.google.common.collect.ImmutableList;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.core.entity.ai.workers.minimal.EntityAIBurnoutTask;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.entity.ai.workers.util.Patient;
import com.minecolonies.core.entity.citizen.EntityCitizen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

public class BuildingResort extends AbstractBuilding {
    protected final static String GUEST_TAG_ID = "guests";
    protected final static int ADVERTISING_COOLDOWN_MAX = 60; // In colony ticks
    protected int advertisingCooldown = ADVERTISING_COOLDOWN_MAX;

    // Keep a list of who the resort has "advertized" to (who has had the
    // EntityAIBurnoutTask added to their AI)
    protected List<EntityCitizen> advertisingList = new ArrayList<>();

    private final Map<Integer, Patient> guests = new HashMap<Integer, Patient>();

    public BuildingResort(@NotNull IColony colony, BlockPos pos) {
        super(colony, pos);
    }

    @Override
    public String getSchematicName() {
        return ModBuildings.RESORT_ID;
    }

    public void checkOrCreateGuestFile(int citizenId) {
        if (!this.guests.containsKey(citizenId)) {
            this.guests.put(citizenId, new Patient(citizenId));
        }

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

        /* Once the marketplace is built, citizens start thinking about vacations... */
        colony.getCitizenManager().getCitizens().forEach(citizen -> {
            Optional<AbstractEntityCitizen> citizenEntity = citizen.getEntity();
            if (citizenEntity.isPresent() && citizenEntity.get() instanceof EntityCitizen) {
                EntityCitizen advertisingTarget = (EntityCitizen) citizenEntity.get();

                if (citizen.getJob() != null) {
                    if (!advertisingList.contains(advertisingTarget)) {
                        // Citizens who have jobs get the EntityAIBurnoutTask added to their AI
                        @SuppressWarnings("unused")
                        EntityAIBurnoutTask burnoutTask = new EntityAIBurnoutTask(advertisingTarget);
                        advertisingList.add(advertisingTarget);

                        // TODO: Remove after testing complete
                        MCTradePostMod.LOGGER.info("Added EntityAIBurnoutTask to " + advertisingTarget.getName());
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
            CompoundTag patientCompound = guestTagList.getCompound(i);
            int guestId = patientCompound.getInt("id");
            if (!this.guests.containsKey(guestId)) {
                this.guests.put(guestId, new Patient(patientCompound));
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
      Iterator<Patient> guestIterator;
      CompoundTag guestCompound;

      if (!this.guests.isEmpty()) {
         guestTagList = new ListTag();
         guestIterator = this.guests.values().iterator();

         while(guestIterator.hasNext()) {
            Patient guest = guestIterator.next();
            guestCompound = new CompoundTag();
            guest.write(guestCompound);
            guestTagList.add(guestCompound);
         }

         compound.put(GUEST_TAG_ID, guestTagList);
      }

      return compound;
    }

    public List<Patient> getGuests() {
        return ImmutableList.copyOf(this.guests.values());
    }

    public void removeGuestFile(Patient guest) {
        this.guests.remove(guest.getId());
    }

    // TODO: Set the visible status icon appropriately:
    // citizen.getCitizenData().setVisibleStatus(VisibleCitizenStatus.SICK);

}
