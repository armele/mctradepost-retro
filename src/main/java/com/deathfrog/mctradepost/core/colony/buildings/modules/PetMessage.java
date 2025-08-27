package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.util.PetRegistryUtil;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingPetshop;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.network.messages.server.AbstractBuildingServerMessage;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.slf4j.Logger;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_ANIMALTRAINER;

import java.util.UUID;

import org.jetbrains.annotations.NotNull;

public class PetMessage extends AbstractBuildingServerMessage<IBuilding>
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final PlayMessageType<?> TYPE = PlayMessageType.forServer(MCTradePostMod.MODID, "pet_message", PetMessage::new);

    public enum PetAction
    {
        ASSIGN, FREE, QUERY
    }

    private PetAction petAction = PetAction.ASSIGN;
    private BlockPos workLocation = BlockPos.ZERO;
    private UUID entityUuid = null;

    public PetMessage(final IBuildingView trainerBuilding, PetAction action, UUID entityUuid)
    {
        super(TYPE, trainerBuilding);
        this.petAction = action;
        this.entityUuid = entityUuid;
    }

    protected PetMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        this.workLocation = buf.readBlockPos();
        this.petAction = PetAction.values()[buf.readInt()];

        if (!petAction.equals(PetAction.QUERY))
        {
            this.entityUuid = buf.readUUID();
        }

    }

    /**
     * Serializes the current state of this message into the given RegistryFriendlyByteBuf.
     * 
     * @param buf The buffer to serialize the message into.
     */
    @Override
    protected void toBytes(@NotNull final RegistryFriendlyByteBuf buf)
    {
        super.toBytes(buf);
        buf.writeBlockPos(this.workLocation);
        buf.writeInt(this.petAction.ordinal());

        if (this.entityUuid != null)
        {
            buf.writeUUID(this.entityUuid); 
        }
    }
    
    public void setWorkLocation(final BlockPos workLocation)
    {
        this.workLocation = workLocation;
    }

    /**
     * Server-side handler for the PetMessage.
     * This method will be called on the server when the client sends a PetMessage.
     * The method will take the appropriate action based on the value of the petAction field.
     * If the petAction is ASSIGN, the method will set the work location of the entity at entityId to the value of the workLocation field.
     * If the petAction is FREE, the method will free the entity at entityId from its current work location.
     * If the petAction is QUERY, the method will gather all valid work locations for pets in the colony and send them to the client.
     * @param ctxIn the payload context for this message
     * @param player the player who sent the message
     * @param colony the colony the message is for
     * @param trainerBuilding the trainer building that the message is for
     */
    @Override
    protected void onExecute(final IPayloadContext ctxIn, final ServerPlayer player, final IColony colony, final IBuilding trainerBuilding)
    {
        Entity entity = (Entity) PetRegistryUtil.resolve((ServerLevel) colony.getWorld(), entityUuid);

        TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, 
            () -> MCTradePostMod.LOGGER.info("Server execution of PetMessage. Colony: {}, Trainer Building: {}, Entity: {}, Work Location: {}, Pet Action: {}", 
            colony, trainerBuilding, entity, workLocation, petAction));

        switch (petAction)
        {
            case ASSIGN:
                if (entity != null && entity instanceof ITradePostPet pet) 
                {
                    MCTradePostMod.LOGGER.info("Setting work location: {}", workLocation);
                    pet.setWorkLocation(workLocation);
                    ((BuildingPetshop) trainerBuilding).markPetsDirty();
                } 
                break;

            case FREE:
                // TODO
                break;

            case QUERY:
                PetAssignmentModule petModule = ((BuildingPetshop) trainerBuilding).getModule(MCTPBuildingModules.PET_ASSIGNMENT);
                petModule.gatherWorkLocations(colony);
                ((BuildingPetshop) trainerBuilding).markPetsDirty();
                colony.getPackageManager().addCloseSubscriber(player);
                break;

        }
    }
}
