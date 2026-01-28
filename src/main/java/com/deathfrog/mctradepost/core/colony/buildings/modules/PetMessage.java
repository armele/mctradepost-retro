package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.entity.pets.PetHelper;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.PetRegistryUtil;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingPetshop;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.network.messages.server.AbstractBuildingServerMessage;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.slf4j.Logger;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_ANIMALTRAINER;

import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;

public class PetMessage extends AbstractBuildingServerMessage<IBuilding>
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final PlayMessageType<?> TYPE = PlayMessageType.forServer(MCTradePostMod.MODID, "pet_message", PetMessage::new);

    public enum PetAction
    {
        ASSIGN, FREE, QUERY, SUMMON
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
        @Nonnull BlockPos localWorkLoc = this.workLocation == null ? NullnessBridge.assumeNonnull(BlockPos.ZERO) : this.workLocation;
        super.toBytes(buf);
        buf.writeBlockPos(localWorkLoc);
        buf.writeInt(this.petAction.ordinal());

        if (petAction != PetAction.QUERY)
        {
            buf.writeUUID(NullnessBridge.assumeNonnull(entityUuid));
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
        ServerLevel level = (ServerLevel) colony.getWorld();

        if (level == null || level.isClientSide)
        {
            return;
        }

        if (!(trainerBuilding instanceof BuildingPetshop petshop))
        {
            MCTradePostMod.LOGGER.error("Invalid trainer building: {} - this building is not a petshop.", trainerBuilding);
            return;
        }

        if (!colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS))
        {
            MessageUtils.format(Component.translatable("com.minecolonies.coremod.gui.petstore.noperms")).sendTo(player);
            return;
        }

        PetAssignmentModule petModule = petshop.getModule(MCTPBuildingModules.PET_ASSIGNMENT);
        UUID localUuid = entityUuid;
        Entity entity = null;

        TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, 
            () -> MCTradePostMod.LOGGER.info("Server execution of PetMessage. Colony: {}, Trainer Building: {}, Entity: {}, Work Location: {}, Pet Action: {}", 
            colony, trainerBuilding, localUuid, workLocation, petAction));

        switch (petAction)
        {
            case ASSIGN:
                if (localUuid == null)
                {
                    return;
                }

                entity = (Entity) PetRegistryUtil.resolve(level, localUuid);

                Set<BlockPos> workLocations = petModule.gatherWorkLocations(colony);

                if (entity != null && entity instanceof ITradePostPet pet && workLocations.contains(workLocation)) 
                {
                    if (!pet.getTrainerBuilding().equals(trainerBuilding))
                    {
                        MCTradePostMod.LOGGER.error("Attempt to assign pet {} to work location {} in building {}, but its trainer building is {}. Aborting assignment.",
                            pet, workLocation, trainerBuilding, pet.getTrainerBuilding());
                        return;
                    }

                    TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> MCTradePostMod.LOGGER.info("Setting work location: {}", workLocation));
                    pet.setWorkLocation(workLocation);
                    petshop.markPetsDirty();
                } 
                break;

            case FREE:
                if (localUuid == null)
                {
                    return;
                }

                entity = (Entity) PetRegistryUtil.resolve(level, localUuid);

                if (entity != null && entity instanceof ITradePostPet pet) 
                {
                    if (!pet.getTrainerBuilding().equals(trainerBuilding))
                    {
                        MCTradePostMod.LOGGER.error("Attempt to free pet {} from building {}, but its trainer building is {}. Aborting assignment.",
                            pet, trainerBuilding, pet.getTrainerBuilding());
                        return;
                    }

                    entity.discard();
                    MessageUtils.format(Component.translatable("com.minecolonies.coremod.gui.petstore.freed")).sendTo(player);
                    petshop.markPetsDirty();
                } 
                break;

            case QUERY:
                petModule.gatherWorkLocations(colony);
                petshop.markPetsDirty();
                colony.getPackageManager().addCloseSubscriber(player);
                break;

            case SUMMON:
                if (localUuid == null)
                {
                    return;
                }

                entity = (Entity) PetRegistryUtil.resolve(level, localUuid);

                if (entity != null && entity instanceof ITradePostPet pet) 
                {
                    if (!pet.getTrainerBuilding().equals(trainerBuilding))
                    {
                        MCTradePostMod.LOGGER.error("Attempt to summon pet {} to building {}, but its trainer building is {}. Aborting assignment.",
                            pet, trainerBuilding, pet.getTrainerBuilding());
                        return;
                    }

                    TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> MCTradePostMod.LOGGER.info("Summoning pet: {}", pet));
                    BlockPos targetPos = PetHelper.findNearbyValidSpawn(trainerBuilding.getColony().getWorld(), trainerBuilding.getPosition(), 3);
                    entity.teleportTo(targetPos.getX(), targetPos.getY(), targetPos.getZ());
                    MessageUtils.format(Component.translatable("com.minecolonies.coremod.gui.petstore.summoned")).sendTo(player);
                    petshop.markPetsDirty();
                } 
                break;

        }
    }
}
