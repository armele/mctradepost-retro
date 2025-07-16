package com.deathfrog.mctradepost.api.entity.pets;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_ANIMALTRAINER;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.entity.pets.goals.HerdGoal;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.mojang.logging.LogUtils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.BegGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class PetWolf extends Wolf implements ITradePostPet
{
    public static final Logger LOGGER = LogUtils.getLogger();
    public int logCooldown = 0;

    protected BaseTradePostPet colonyPet = null;

    public PetWolf(EntityType<? extends Wolf> entityType, Level level)
    {
        super(entityType, level);
        colonyPet = new BaseTradePostPet(this);
    }


    @Override
    public int getColonyId()
    {
        if (colonyPet == null) return -1;

        return colonyPet.getColonyId();
    }

    @Override
    public IBuilding getBuilding()
    {
        if (colonyPet == null) return null;

        return colonyPet.getBuilding();
    }

    @Override
    public void setColonyId(int colonyId)
    {
        colonyPet.setColonyId(colonyId);
    }

    @Override
    public void setBuilding(IBuilding building)
    {
        colonyPet.setBuilding(building);
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
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(3, new HerdGoal(this));
        this.goalSelector.addGoal(4, new LeapAtTargetGoal(this, 0.4F));
        this.goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0, 10.0F, 2.0F));
        this.goalSelector.addGoal(7, new BreedGoal(this, 1.0));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(9, new BegGoal(this, 8.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, (new HurtByTargetGoal(this, new Class[0])).setAlertOthers(new Class[0]));
        this.targetSelector.addGoal(7, new NearestAttackableTargetGoal(this, AbstractSkeleton.class, false));
        this.targetSelector.addGoal(8, new ResetUniversalAngerTargetGoal(this, true));
    }

    @Override
    public void addAdditionalSaveData(@Nonnull CompoundTag compound)
    {
        super.addAdditionalSaveData(compound);
        colonyPet.toNBT(compound);
    }

    @Override
    public void readAdditionalSaveData(@Nonnull CompoundTag compound)
    {
        super.readAdditionalSaveData(compound);

        if (colonyPet == null)
        {
            colonyPet = new BaseTradePostPet(this);
        }

        colonyPet.fromNBT(compound);
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
