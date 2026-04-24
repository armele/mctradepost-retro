package com.deathfrog.mctradepost.api.entity.pets.goals;

import javax.annotation.Nullable;

import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Short-lived defensive melee goal for trade post pets.
 * <p>
 * This goal lets a pet retaliate against a valid hostile target after another
 * target goal assigns one, while keeping that response bounded. Pets will not
 * attack players or other trade post pets, will stop after a brief fight window,
 * and will not chase the attacker too far from the point where the fight began.
 *
 * @param <P> the concrete pet mob type using this goal
 */
public class PetDefensiveAttackGoal<P extends PathfinderMob & ITradePostPet> extends MeleeAttackGoal
{
    private static final int MAX_ATTACK_TICKS = 20 * 8;
    private static final double MAX_CHASE_DISTANCE_SQ = 16.0D * 16.0D;

    private final P pet;
    private int ticksRunning;
    @Nullable
    private Vec3 fightOrigin;

    /**
     * Creates a defensive attack goal for the given pet.
     *
     * @param pet the pet that should perform the melee attack
     * @param speedModifier movement speed modifier used while pursuing the target
     */
    public PetDefensiveAttackGoal(P pet, double speedModifier)
    {
        super(pet, speedModifier, true);
        this.pet = pet;
    }

    /**
     * Starts only when another target goal has selected a target this pet is
     * allowed to attack.
     *
     * @return true if the pet may begin its defensive melee response
     */
    @Override
    public boolean canUse()
    {
        return isAllowedTarget(pet.getTarget()) && super.canUse();
    }

    /**
     * Keeps the fight bounded by time, target validity, and distance from the
     * original attack location.
     *
     * @return true while the pet should continue its current defensive attack
     */
    @Override
    public boolean canContinueToUse()
    {
        LivingEntity target = pet.getTarget();
        return ticksRunning < MAX_ATTACK_TICKS && isAllowedTarget(target) && isWithinLeash(target) && super.canContinueToUse();
    }

    @Override
    public void start()
    {
        ticksRunning = 0;
        fightOrigin = pet.position();
        super.start();
    }

    @Override
    public void tick()
    {
        ticksRunning++;
        super.tick();
    }

    @Override
    public void stop()
    {
        super.stop();
        ticksRunning = 0;
        fightOrigin = null;
        pet.setTarget(null);
    }

    private boolean isAllowedTarget(@Nullable LivingEntity target)
    {
        if (target == null || !target.isAlive())
        {
            return false;
        }

        if (target instanceof Player || target instanceof ITradePostPet)
        {
            return false;
        }

        return pet.canAttack(target);
    }

    private boolean isWithinLeash(@Nullable Entity target)
    {
        if (target == null)
        {
            return false;
        }

        Vec3 origin = fightOrigin;
        if (origin == null)
        {
            return true;
        }

        return target.position().distanceToSqr(origin) <= MAX_CHASE_DISTANCE_SQ;
    }
}
