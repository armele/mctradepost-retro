package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.BuildingUtil;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.*;
import com.minecolonies.core.colony.buildings.modules.AnimalHerdingModule;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;

import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

public class PetAssignmentModule extends AbstractBuildingModule implements IPersistentModule
{

    /**
     * Deserializes the NBT data for the module.
     *
     * @param provider The holder lookup provider for item and block references.
     * @param compound The CompoundTag containing the serialized state of the
     *                 trade list.
     */
    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {

    }

    /**
     * Serializes the NBT data for the module.
     *
     * @param provider The holder lookup provider for item and block references.
     * @param compound The CompoundTag containing the serialized state of the
     *                 trade list.
     */
    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, CompoundTag compound)
    {

    }

    /**
     * Serializes the trade list to the given RegistryFriendlyByteBuf for
     * transmission to the client. 
     *
     * @param buf the buffer to serialize the trade list to.
     */
    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf)
    {
        Set<IBuilding> herdingBuildings = gatherHerdingBuildings();
        MCTradePostMod.LOGGER.info("Sending {} herding buildings", herdingBuildings.size());

        buf.writeInt(herdingBuildings.size());

        for (IBuilding herdBuilding : herdingBuildings)
        {   
            CompoundTag tag = BuildingUtil.uniqueBuildingNBT(herdBuilding);
            MCTradePostMod.LOGGER.info("Sending herding building: {}", tag);
            
            buf.writeNbt(tag);
        }
    }
    
    /**
     * Gathers a set of all herding buildings in the colony. This is done by iterating over all buildings in the colony, and
     * for each one, iterating over all AnimalHerdingModules in the building. If such a module is found, the building is added
     * to the set of herding buildings, and its dirty flag is set. This is done so that the herding buildings are serialized and
     * sent to the client when the PetAssignmentModule is updated. Finally, the dirty flag of the building itself is set so that
     * the entire building is serialized and sent to the client.
     *
     * @return a set of all herding buildings in the colony.
     */
    protected Set<IBuilding> gatherHerdingBuildings()
    {
        Set<IBuilding> herdingBuildings = new HashSet<>();

        for (final IBuilding building : building.getColony().getBuildingManager().getBuildings().values())
        {
            for (final AnimalHerdingModule module : building.getModulesByType(AnimalHerdingModule.class))
            {
                MCTradePostMod.LOGGER.info("Found herding building: {}", building);
                herdingBuildings.add(building);
                module.markDirty();
                
            }
            building.markDirty();
        }

        return herdingBuildings;
    }

}
