package com.deathfrog.mctradepost.api.entity.pets;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.ItemStackHandlerContainerWrapper;
import com.deathfrog.mctradepost.api.util.PetRegistryUtil;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;


public class PetWolf extends Wolf implements ITradePostPet, IHerdingPet
{
    public static final Logger LOGGER = LogUtils.getLogger();
    public int logCooldown = 0;

    protected PetData<PetWolf> petData = null;
    protected boolean goalsInitialized = false;

    protected BlockPos targetPosition = BlockPos.ZERO;
    protected int stuckTicks = 0;

    public PetWolf(EntityType<? extends Wolf> entityType, Level level)
    {
        super(entityType, level);
        petData = new PetData<>(this);
        registerGoals();
    }

    @Override
    public IBuilding getTrainerBuilding()
    {
        if (petData == null) return null;

        return petData.getTrainerBuilding();
    }

    @Override
    public void setTrainerBuilding(IBuilding building)
    {
        petData.setTrainerBuilding(building);
    }

    @Override
    public String getAnimalType()
    {
        if (petData == null) return null;

        return petData.getAnimalType();
    }

    /**
     * A pet will never want to attack another entity.
     * 
     * @param target the target to check
     * @param owner  the owner of the wolf
     * @return false always
     */
    @Override
    public boolean wantsToAttack(@Nonnull LivingEntity target, @Nonnull LivingEntity owner)
    {
        return false;
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(16, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(18, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(19, new RandomLookAroundGoal(this));


        this.targetSelector.addGoal(23, new HurtByTargetGoal(this).setAlertOthers());

        if (this.petData == null)
        {
            // LOGGER.warn("Skipping pet goal registration: petData is null");
            return;
        }
        petData.assignPetGoals();

    }

    @Override
    public void addAdditionalSaveData(@Nonnull CompoundTag compound)
    {
        super.addAdditionalSaveData(compound);
        
        if (petData != null)
        {
            petData.toNBT(compound);
        }
        else
        {
            LOGGER.warn("Failed to serialize pet data: petData is null");
        }

    }

    /**
     * Deserializes the NBT data for this pet from the given CompoundTag.
     * Restores the colony ID and, if available, the position and dimension of the associated building.
     * If the building is found in the world, sets the trainer building for this pet.
     *
     * @param compound the CompoundTag containing the serialized state of the BaseTradePostPet.
     */
    @Override
    public void readAdditionalSaveData(@Nonnull CompoundTag compound)
    {   

        try
        {
            super.readAdditionalSaveData(compound);
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to deserialize parent entity data from tag: {}", compound, e);
        }

        petData = new PetData<PetWolf>(this, compound);

        // Reset and safely re-register goals
        this.goalSelector.removeAllGoals(g -> true);
        this.targetSelector.removeAllGoals(g -> true);
        if (petData != null) {
            registerGoals(); // only register once we have all colony context
        }
        else 
        {
            LOGGER.warn("Failed to deserialize pet data from {}: petData is null", compound);
        }

        boolean registered = PetRegistryUtil.isRegistered(this);
        if (!registered && this.isAlive() && this.getTrainerBuilding() != null)
        {
            PetRegistryUtil.register(this);
        }
        else
        {
            if (!this.isAlive() || this.getTrainerBuilding() == null)
            {
                PetRegistryUtil.unregister(this);
                this.discard();
            }
        }
    }

    /**
     * Resets the state of this pet's AI goals and targets, by clearing all existing goals and targets and re-registering them.
     * A change of work location may necessitate a change of goals.
     * The goals and targets are only registered once the pet has a valid colony context, which is not available until the pet is loaded into a world.
     */
    @Override
    public void resetGoals() 
    {
        this.goalSelector.removeAllGoals(g -> true);
        this.targetSelector.removeAllGoals(g -> true);
        registerGoals();
        goalsInitialized = true;
    }

    @Override
    public void tick()
    {
        super.tick();
        
        if (!goalsInitialized && petData != null) {
            resetGoals();
        }

        if (petData != null)
        {
            petData.tick(this.level());
            petData.aiWatchdogTick();
            petData.logActiveGoals();
        }
        else
        {
            LOGGER.warn("Failed to tick pet {}: petData is null", this);
        }
    }

    /**
     * Removes the Pet from the game. This method unregisters the pet from the PetRegistry
     * and clears the reference to its associated BaseTradePostPet. This is typically called
     * when the pet is removed from the world for any reason, such as death or despawning.
     *
     * @param reason the reason for the removal of the pet
     */
    @Override
    public void remove(@Nonnull RemovalReason reason)
    {
        if (petData != null)
        {
            petData.onRemoval(reason);
        }

        switch (reason) {
            case KILLED, DISCARDED -> {
                PetRegistryUtil.unregister(this);
                this.setOwnerUUID(null);
                
                petData = null;

                if (this.isLeashed())
                {
                    this.dropLeash(true, false);
                }
            }
            case UNLOADED_TO_CHUNK, CHANGED_DIMENSION -> {
                // benign; don’t clear persistent state
            }
            default -> {
                // benign; don’t clear persistent state
            }
        }
        
        super.remove(reason);
    }

    /**
     * Creates a path navigation for this pet. By default, this uses a minecolonies navigation.
     * @param level the level in which the navigation is being created
     * @return a path navigation for this pet
     */
    @Override   
    protected PathNavigation createNavigation(@Nonnull Level level) {
        MinecoloniesAdvancedPathNavigate pathNavigation = new MinecoloniesAdvancedPathNavigate(this, level);
        pathNavigation.getPathingOptions().setEnterDoors(true);
        pathNavigation.getPathingOptions().setCanOpenDoors(true);
        pathNavigation.getPathingOptions().withDropCost(1D);
        pathNavigation.getPathingOptions().withJumpCost(1D);
        pathNavigation.getPathingOptions().setPassDanger(false);
        pathNavigation.getPathingOptions().setCanSwim(true);
        pathNavigation.setCanFloat(true);
        
        return pathNavigation;
    }

    /**
     * Determines if this pet can climb the block at its current position.
     * By default, this returns true if the block is climable (i.e. a ladder or vine).
     * @return true if the pet can climb the block at its current position, false otherwise
     */
    @Override
    public boolean onClimbable() {
        // TODO: Lock climbing behind research.
        return this.level().getBlockState(this.blockPosition()).is(BlockTags.CLIMBABLE);
    }

    @SuppressWarnings("unchecked")
    public PetData<PetWolf> getPetData()
    {
        return this.petData;
    }

    /**
     * Gets the dimension of this pet's current level. If the pet is not currently in a level (i.e. it is not in the world), this
     * will return null.
     *
     * @return the dimension of the pet's level, or null
     */
    @Override
    public ResourceKey<Level> getDimension()
    {
        Level level = this.level();

        if (level != null)
        {
            return level.dimension();
        }
        
        return null;
    }

    public BlockPos getTargetPosition()
    {
        return this.targetPosition;
    }

    public void setTargetPosition(BlockPos targetPosition)
    {
        this.targetPosition = targetPosition;
    }

    public void incrementStuckTicks()
    {
        this.stuckTicks++;
    }

    public int getStuckTicks()
    {
        return this.stuckTicks;
    }

    public void clearStuckTicks()
    {
        this.stuckTicks = 0;
    }

    @Override
    public ItemStackHandler getInventory()
    {
        if (petData == null) {
            return null;
        }

        return petData.getInventory();
    }

    @Override
    public void setWorkLocation(BlockPos location)
    {
        if (petData == null) {
            petData = new PetData<PetWolf>(this);
            return;
        }

        resetGoals();

        petData.setWorkLocation(location);
    }

    @Override
    public BlockPos getWorkLocation()
    {
        if (petData == null) {
            return null;
        }

        return petData.getWorkLocation();
    }

    /**
     * Handles interaction with this entity.
     * 
     * @param player the player that is interacting
     * @param hand   the hand that is interacting
     * @return the result of the interaction
     */
    @Override
    public InteractionResult mobInteract(@Nonnull Player player, @Nonnull InteractionHand hand) {
        if (!level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            ItemStackHandlerContainerWrapper inventoryWrapper = new ItemStackHandlerContainerWrapper(this.getInventory());

            player.openMenu(new SimpleMenuProvider(
                (windowId, playerInventory, p) -> new ChestMenu(
                    MenuType.GENERIC_9x1, // 1-row menu
                    windowId,
                    playerInventory,
                    inventoryWrapper,
                    1 // number of rows
                ),
                this.getDisplayName()
            ));
            return InteractionResult.CONSUME;
        }

        return super.mobInteract(player, hand);
    }

    /**
     * Drops the pet's entire inventory on death. This is not a conventional override of
     * {@link Animal#dropCustomDeathLoot(ServerLevel, DamageSource, boolean)}, as the superclass does not
     * provide a way to drop the entire inventory. This is a workaround to drop the pet's inventory
     * when it dies.
     */
    @Override
    protected void dropCustomDeathLoot(@Nonnull ServerLevel level, @Nonnull DamageSource source, boolean recentlyHit)
    {
        super.dropCustomDeathLoot(level, source, recentlyHit);

        // Drop the pet’s inventory
        ItemStackHandler inv = this.getInventory(); // however you expose it
        if (inv == null) return;

        for (int i = 0; i < inv.getSlots(); i++)
        {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty())
            {
                net.minecraft.world.Containers.dropItemStack(level, getX(), getY(), getZ(), stack.copy());
                inv.setStackInSlot(i, ItemStack.EMPTY); // prevent dupes
            }
        }
    }

    /**
     * Sets the custom name of this pet. This will update the pet's display name,
     * and if not in debug mode, will also update the pet's original name,
     * which is not persisted between game sessions.
     * 
     * @param newName the new custom name to set, or null to clear the custom name
     */

    @Override
    public void setCustomName(@Nullable Component newName) 
    {
        super.setCustomName(newName);

        // Only update originalName if we are NOT in debug mode
        // (i.e., a real in-game name/tag or command)
        if (this.getPetData() != null && !TraceUtils.isTracing(TraceUtils.TRACE_PETGOALS)) 
        {
            this.getPetData().setOriginalName(newName);
        }
    }
}
