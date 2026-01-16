package com.deathfrog.mctradepost.item;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.entity.pets.PetTypes;
import com.deathfrog.mctradepost.api.util.NullnessBridge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class WishGatheringItem extends AbstractPositionMarkerItem
{
    final protected PetTypes gatheredAnimalType;

    public WishGatheringItem(@Nonnull Properties properties, PetTypes petType)
    {
        super(properties);
        this.gatheredAnimalType = petType;
    }

    @Override
    public void sendPlayerLinkedMessage(Player player, BlockPos clicked)
    {
        Component message = Component.translatable("com.mctradepost.wishgather.clicked", clicked.toShortString());
        player.displayClientMessage(NullnessBridge.assumeNonnull(message), true);
    }

    @Override
    public @Nonnull String hoverMessageId(boolean set)
    {
        if (set) return "com.mctradepost.wishgather.marker.set.tooltip";

        return "com.mctradepost.wishgather.marker.unset.tooltip";
    }
    
}
