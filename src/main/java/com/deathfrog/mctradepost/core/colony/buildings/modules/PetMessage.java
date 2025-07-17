package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingPetshop;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.network.messages.server.AbstractBuildingServerMessage;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.slf4j.Logger;

import org.jetbrains.annotations.NotNull;

public class PetMessage extends AbstractBuildingServerMessage<IBuilding>
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final PlayMessageType<?> TYPE = PlayMessageType.forServer(MCTradePostMod.MODID, "pet_message", PetMessage::new);

    public enum PetAction
    {
        ASSIGN, FREE
    }

    private PetAction petAction = PetAction.ASSIGN;
    private BlockPos workBuildingId = BlockPos.ZERO;
    private int entityId = -1;

    public PetMessage(final IBuildingView trainerBuilding, PetAction action, int entityId)
    {
        super(TYPE, trainerBuilding);
        this.petAction = action;
        this.entityId = entityId;
    }

    protected PetMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        this.workBuildingId = buf.readBlockPos();
        this.entityId = buf.readInt();
        this.petAction = PetAction.values()[buf.readInt()];
    }

    @Override
    protected void toBytes(@NotNull final RegistryFriendlyByteBuf buf)
    {
        super.toBytes(buf);
        buf.writeBlockPos(this.workBuildingId);
        buf.writeInt(this.entityId);
        buf.writeInt(this.petAction.ordinal());
    }

    public void setWorkBuilding(final BlockPos workBuildingId)
    {
        this.workBuildingId = workBuildingId;
    }

    @Override
    protected void onExecute(final IPayloadContext ctxIn, final ServerPlayer player, final IColony colony, final IBuilding trainerBuilding)
    {
        Entity entity = colony.getWorld().getEntity(entityId);
        IBuilding workBuilding = null;
        
        if (!BlockPos.ZERO.equals(workBuildingId)) 
        {
            workBuilding = colony.getBuildingManager().getBuilding(workBuildingId);
        }

        MCTradePostMod.LOGGER.info("Server exection of PetMessage. Colony: {}, Trainer Building: {}, Entity: {}, Work Building: {}, Pet Action: {}", colony, trainerBuilding, entity, workBuilding, petAction);

        switch (petAction)
        {
            case ASSIGN:
                if (entity != null && entity instanceof ITradePostPet pet) 
                {
                    MCTradePostMod.LOGGER.info("Setting work building: {}", workBuilding);
                    pet.setWorkBuilding(workBuilding);
                    ((BuildingPetshop) trainerBuilding).markPetsDirty();
                } 
                break;

            case FREE:
                // TODO
                break;

        }
    }
}
