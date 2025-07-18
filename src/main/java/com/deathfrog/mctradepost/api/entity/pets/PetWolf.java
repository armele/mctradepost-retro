package com.deathfrog.mctradepost.api.entity.pets;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_ANIMALTRAINER;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.entity.pets.goals.EscapeHoleWithLadderGoal;
import com.deathfrog.mctradepost.api.entity.pets.goals.HerdGoal;
import com.deathfrog.mctradepost.api.util.PetRegistryUtil;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate;
import com.minecolonies.core.entity.pathfinding.navigation.PathingStuckHandler;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.BegGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;


public class PetWolf extends Wolf implements ITradePostPet, IHerdingPet
{
    public static final Logger LOGGER = LogUtils.getLogger();
    public int logCooldown = 0;

    protected PetData petData = null;
    
    // After how many nudges without moving do we give the Sheep a pathfinding command to unstick themselves?
    public static final int STUCK_STEPS = 10;   

    protected BlockPos targetPosition = BlockPos.ZERO;
    protected int stuckTicks = 0;

    public PetWolf(EntityType<? extends Wolf> entityType, Level level)
    {
        super(entityType, level);
        petData = new PetData(this);
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
    public IBuilding getWorkBuilding()
    {
        if (petData == null) return null;

        return petData.getWorkBuilding();
    }

    @Override
    public void setWorkBuilding(IBuilding building)
    {
        petData.setWorkBuilding(building);
    }

    @Override
    public String getAnimalType()
    {
        return petData.getAnimalType();
    }

    /**
     * A pet wolf will never want to attack another entity.
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
        this.goalSelector.addGoal(1, new FloatGoal(this));
        // this.goalSelector.addGoal(2, new EscapeHoleWithLadderGoal(this));
        this.goalSelector.addGoal(3, new HerdGoal<>(this));
        this.goalSelector.addGoal(4, new FollowOwnerGoal(this, 1.0, 10.0F, 2.0F));
        this.goalSelector.addGoal(5, new BreedGoal(this, 1.0));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(7, new BegGoal(this, 8.0F));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(4, new ResetUniversalAngerTargetGoal(this, true));
    }

    @Override
    public void addAdditionalSaveData(@Nonnull CompoundTag compound)
    {
        super.addAdditionalSaveData(compound);
        
        if (petData != null)
        {
            petData.toNBT(compound);
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
        super.readAdditionalSaveData(compound);
        petData = new PetData(this, compound);

        boolean registered = PetRegistryUtil.isRegistered(this);
        if (!registered && this.isAlive())
        {
            PetRegistryUtil.register(this);
        }

        // These are called to ensure that the custom goals have the necessary colony information available to them.  
        // At construction, they may not.
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);
        this.registerGoals();
    }

    @Override
    public void tick()
    {
        super.tick();

        logActiveGoals();
    }

    /**
     * Removes the PetWolf from the game. This method unregisters the wolf from the PetRegistry
     * and clears the reference to its associated BaseTradePostPet. This is typically called
     * when the wolf is removed from the world for any reason, such as death or despawning.
     *
     * @param reason the reason for the removal of the PetWolf
     */
    @Override
    public void remove(@Nonnull RemovalReason reason)
    {
        PetRegistryUtil.unregister(this);
        petData = null;
        super.remove(reason);
    }

    /**
     * Creates a path navigation for this wolf. By default, this uses a minecolonies navigation.
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
        pathNavigation.getPathingOptions().setPassDanger(true);

        return pathNavigation;
    }

    /**
     * Determines if this wolf can climb the block at its current position.
     * By default, this returns true if the block is climable (i.e. a ladder or vine).
     * @return true if the wolf can climb the block at its current position, false otherwise
     */
    @Override
    public boolean onClimbable() {
        // TODO: Lock climbing behind research.
        return this.level().getBlockState(this.blockPosition()).is(BlockTags.CLIMBABLE);
    }

    public PetData getPetData()
    {
        return this.petData;
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

    /**
     * Logs the active goals of this wolf every 100 ticks. Used for debugging.
     */
    public void logActiveGoals() 
    {
        if (logCooldown > 0) 
        {
            logCooldown--;
            return;
        }

        logCooldown = 100;

        for (WrappedGoal wrapped : this.goalSelector.getAvailableGoals()) {
            Goal goal = wrapped.getGoal();
            if (wrapped.isRunning()) {
                TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Active Goal: " + goal.getClass().getSimpleName()));
            }
        }
        for (WrappedGoal wrapped : this.targetSelector.getAvailableGoals()) {
            Goal goal = wrapped.getGoal();
            if (wrapped.isRunning()) {
                TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Active Target Goal: " + goal.getClass().getSimpleName()));
            }
        }
    }
}
