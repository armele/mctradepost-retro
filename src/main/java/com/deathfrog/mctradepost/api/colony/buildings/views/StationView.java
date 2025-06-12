package com.deathfrog.mctradepost.api.colony.buildings.views;


import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;

/*
 * ✅ Best Practice
* If a field should:
* Be visible on the client UI → put it in serializeNBT() in the building and deserializeNBT() both in the building and deserialize here in the view.
* 
*/

@OnlyIn(Dist.CLIENT)
public class StationView extends AbstractBuildingView 
{

    public StationView(final IColonyView colony, final BlockPos location) 
    {
        super(colony, location);
    }

    @Override
    public void deserialize(@NotNull final RegistryFriendlyByteBuf buf) 
    {
        super.deserialize(buf);
    }
}
