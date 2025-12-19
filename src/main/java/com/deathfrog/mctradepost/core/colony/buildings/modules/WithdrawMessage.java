package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.sounds.MCTPModSoundEvents;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.network.messages.server.AbstractBuildingServerMessage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class WithdrawMessage extends AbstractBuildingServerMessage<IBuilding>
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forServer(MCTradePostMod.MODID, "withdraw_message", WithdrawMessage::new);

    protected WithdrawMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
    }

    public WithdrawMessage(final IBuildingView building) 
    {
        super(TYPE, building);
    }

    /**
     * Handles the Withdraw message on the server side.
     * 
     * @param payload the payload context
     * @param player the player that sent the message
     * @param colony the colony the message is for
     * @param building the building that the message is for
     */
    @Override
    protected void onExecute(IPayloadContext payload, ServerPlayer player, IColony colony, IBuilding building)
    {
        final int mintingLevel = MCTPConfig.mintingLevel.get();

        if (building instanceof BuildingMarketplace marketplace && marketplace.getBuildingLevel() >= mintingLevel) {
            ItemStack coins = marketplace.mintCoins(player, 1);
            if (!coins.isEmpty()) {
                player.addItem(coins);
                withdrawEffects(marketplace);
                MessageUtils.format("mctradepost.marketplace.minted").sendTo(player);
            }  
        }
        else 
        {
            MessageUtils.format("mctradepost.marketplace.invalid").sendTo(player);
        }
    }

    /**
     * Plays a sound and displays particles at the marketplace's location when a player withdraws coins.
     * The sound is a cash register sound and the particles are the happy villager particles.
     * @param marketplace the marketplace building
     */
    protected void withdrawEffects(IBuilding marketplace)
    {
        BlockPos pos = marketplace.getPosition();
        marketplace.getColony().getWorld().playSound(
                null,                         // null = all players tracking this entity
                pos.getX(), pos.getY(), pos.getZ(),
                MCTPModSoundEvents.CASH_REGISTER,
                net.minecraft.sounds.SoundSource.NEUTRAL,
                0.3F,                         // volume
                1.0F);                        // pitch

        ((ServerLevel)marketplace.getColony().getWorld()).sendParticles(
                NullnessBridge.assumeNonnull(ParticleTypes.HAPPY_VILLAGER),
                pos.getX(), pos.getY(), pos.getZ(),
                4,                           // count
                0.3, 0.3, 0.3,               // x,y,z scatter
                0.0);                        // speed
    }
    
}
