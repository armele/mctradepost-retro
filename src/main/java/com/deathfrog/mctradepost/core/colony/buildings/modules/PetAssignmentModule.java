package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.deathfrog.mctradepost.api.entity.pets.PetData;
import com.deathfrog.mctradepost.api.entity.pets.PetRoles;
import com.deathfrog.mctradepost.api.util.PetRegistryUtil;
import com.deathfrog.mctradepost.core.blocks.BlockTrough;
import com.deathfrog.mctradepost.core.blocks.blockentity.PetWorkingBlockEntity;
import com.ldtteam.structurize.api.BlockPosUtil;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;

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
        Level level = building.getColony().getWorld();
        
        Set<BlockPos> animalWorkLocations = gatherWorkLocations(building.getColony());
        // MCTradePostMod.LOGGER.info("Sending {} herding buildings", herdingBuildings.size());

        buf.writeInt(animalWorkLocations.size());

        for (BlockPos workPos : animalWorkLocations)
        {   
            CompoundTag tag = new CompoundTag();
            BlockPosUtil.writeToNBT(tag, "WorkLocation", workPos);

            BlockState state = level.getBlockState(workPos);
            BlockEntity be = level.getBlockEntity(workPos);
            Block block = state.getBlock();

            tag.putInt("Role", PetData.roleFromPosition(level, workPos).ordinal());

            if (be instanceof PetWorkingBlockEntity pwb)
            {
                tag.putString("LocationName", pwb.getDefaultName().getString());
            }
            
            buf.writeNbt(tag);
        }
    }
    

    /**
     * Retrieves the set of all valid work locations for pets in the specified colony.
     *
     * @param colony The colony for which to gather work locations.
     * @return A set of BlockPos representing the work locations for pets.
     */
    protected Set<BlockPos> gatherWorkLocations(IColony colony)
    {
        Set<BlockPos> workLocations = PetRegistryUtil.getWorkLocations(colony);
        return workLocations;
    }

}
