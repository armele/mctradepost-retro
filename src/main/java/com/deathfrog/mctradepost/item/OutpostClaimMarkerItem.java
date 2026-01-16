package com.deathfrog.mctradepost.item;

import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.api.util.NullnessBridge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class OutpostClaimMarkerItem extends AbstractPositionMarkerItem
{
    public static final String LINKED = "linked";
    
    public OutpostClaimMarkerItem(@Nonnull Properties properties)
    {
        super(properties);
    }

    /**
     * Sends a message to the given player indicating that the outpost claim has been
     * set at the given position.
     * @param player the player to send the message to
     * @param clicked the position at which the outpost claim was set
     */
    @Override
    public void sendPlayerLinkedMessage(Player player, BlockPos clicked)
    {
        Component message = Component.translatable("com.mctradepost.outpost.claim", clicked.toShortString());
        player.displayClientMessage(NullnessBridge.assumeNonnull(message), true);
    }

    /**
     * Gets the translation key for the item's tooltip.
     * If set is true, returns the translation key for when the item is set.
     * If set is false, returns the translation key for when the item is unset.
     * @param set whether the item is set or unset
     * @return the translation key for the item's tooltip
     */
    @Override
    public @Nonnull String hoverMessageId(boolean set)
    {
        if (set) return "com.mctradepost.outpost.marker.set.tooltip";

        return "com.mctradepost.outpost.marker.unset.tooltip";
    }
}