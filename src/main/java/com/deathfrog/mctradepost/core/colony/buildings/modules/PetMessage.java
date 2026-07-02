package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.entity.pets.PetHelper;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.PetAnimalManagerUtil;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.slf4j.Logger;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_ANIMALTRAINER;

import java.util.Optional;
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
                boolean validWorkLocation = BlockPos.ZERO.equals(workLocation) || workLocations.contains(workLocation);

                if (entity != null && entity instanceof ITradePostPet pet && validWorkLocation)
                {
                    if (!pet.getTrainerBuilding().equals(trainerBuilding))
                    {
                        MCTradePostMod.LOGGER.error("Attempt to assign pet {} to work location {} in building {}, but its trainer building is {}. Aborting assignment.",
                            pet, workLocation, trainerBuilding, pet.getTrainerBuilding());
                        return;
                    }

                    TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> MCTradePostMod.LOGGER.info("Setting work location: {}", workLocation));
                    pet.setWorkLocation(workLocation);
                    petshop.rememberPetData(pet);
                    petshop.markPetsDirty();
                } 
                else if (entity == null && validWorkLocation && petshop.updatePersistedPetWorkLocation(localUuid, workLocation))
                {
                    TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> MCTradePostMod.LOGGER.info("Persisted unloaded pet {} work location: {}", localUuid, workLocation));
                }
                break;

            case FREE:
                if (localUuid == null)
                {
                    return;
                }

                entity = (Entity) resolvePet(level, colony, petshop, localUuid);

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
                else
                {
                    purgeMissingPet(player, petshop, localUuid);
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

                entity = (Entity) resolvePet(level, colony, petshop, localUuid);

                if (entity != null && entity instanceof ITradePostPet pet) 
                {
                    if (!pet.getTrainerBuilding().equals(trainerBuilding))
                    {
                        MCTradePostMod.LOGGER.error("Attempt to summon pet {} to building {}, but its trainer building is {}. Aborting assignment.",
                            pet, trainerBuilding, pet.getTrainerBuilding());
                        return;
                    }

                    TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> MCTradePostMod.LOGGER.info("Summoning pet: {}", pet));
                    Optional<BlockPos> targetPos = PetHelper.findNearbyValidSpawn(level, entity, trainerBuilding.getPosition(), 3);
                    if (targetPos.isEmpty())
                    {
                        MCTradePostMod.LOGGER.warn("Unable to find a safe summon position near pet shop {} for pet {}.",
                            trainerBuilding.getPosition(), entity);
                        MessageUtils.format(Component.translatable("com.minecolonies.coremod.gui.petstore.summonfailed")).sendTo(player);
                        return;
                    }

                    BlockPos safeTargetPos = targetPos.get();
                    entity = teleportPetHome(entity, level, safeTargetPos);
                    if (entity instanceof ITradePostPet summonedPet)
                    {
                        PetRegistryUtil.register(summonedPet);
                        petshop.rememberPetData(summonedPet);
                    }
                    MessageUtils.format(Component.translatable("com.minecolonies.coremod.gui.petstore.summoned")).sendTo(player);
                    petshop.markPetsDirty();
                } 
                else
                {
                    purgeMissingPet(player, petshop, localUuid);
                }
                break;

        }
    }

    /**
     * Resolve a pet from every authoritative source that can point to a loaded entity.
     */
    private ITradePostPet resolvePet(final @Nonnull ServerLevel level, final @Nonnull IColony colony, final @Nonnull BuildingPetshop petshop, final @Nonnull UUID uuid)
    {
        ITradePostPet pet = PetRegistryUtil.resolve(level, uuid);
        if (pet != null)
        {
            return pet;
        }

        MinecraftServer server = level.getServer();
        if (server != null)
        {
            pet = PetRegistryUtil.findHandle(petshop, uuid)
                .map(handle -> PetRegistryUtil.resolve(server, handle))
                .orElse(null);
            if (pet != null)
            {
                return pet;
            }

            pet = PetRegistryUtil.resolve(server, uuid);
            if (pet != null)
            {
                return pet;
            }
        }

        return PetAnimalManagerUtil.resolveManagedPet(colony, uuid);
    }

    /**
     * Move a pet to the target server level and position.
     */
    private Entity teleportPetHome(final Entity entity, final @Nonnull ServerLevel targetLevel, final BlockPos safeTargetPos)
    {
        double x = safeTargetPos.getX() + 0.5D;
        double y = safeTargetPos.getY();
        double z = safeTargetPos.getZ() + 0.5D;

        if (entity.level() == targetLevel)
        {
            entity.teleportTo(x, y, z);
            return entity;
        }

        @SuppressWarnings("null")
        Entity changedEntity = entity.changeDimension(new DimensionTransition(
            targetLevel,
            new Vec3(x, y, z),
            Vec3.ZERO,
            entity.getYRot(),
            entity.getXRot(),
            DimensionTransition.DO_NOTHING));
        return changedEntity == null ? entity : changedEntity;
    }

    /**
     * Remove an unreachable pet from persisted pet shop state and tell the player what happened.
     */
    @SuppressWarnings("null")
    private void purgeMissingPet(final ServerPlayer player, final BuildingPetshop petshop, final UUID uuid)
    {
        MCTradePostMod.LOGGER.warn("Pet {} was listed in pet shop {} but no loaded entity could be resolved. Purging stale pet record.",
            uuid, petshop.getPosition());
        PetAnimalManagerUtil.purgePetshopRecord(petshop, uuid);
        MessageUtils.format(Component.translatable("com.minecolonies.coremod.gui.petstore.missing")).sendTo(player);
    }
}
