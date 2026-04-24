package com.deathfrog.mctradepost.api.entity.pets.goals;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.util.PetGuardAlertUtil;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * Target-selection goal that lets trade post pets respond when attacked.
 * <p>
 * This goal filters out players and other trade post pets before delegating to
 * Minecraft's normal hurt-by-target behavior. When the target is accepted, it
 * also gives the pet one chance per cooldown window to alert nearby MineColonies
 * guards through {@link PetGuardAlertUtil}.
 *
 * @param <P> the concrete pet mob type using this goal
 */
public class PetHurtByTargetGoal<P extends PathfinderMob & ITradePostPet> extends HurtByTargetGoal
{
    private static final long GUARD_ALERT_COOLDOWN_TICKS = 30L * 20L;

    private final P pet;
    private long nextGuardAlertGameTime;

    /**
     * Creates a hurt-by-target goal for a trade post pet.
     *
     * @param pet the pet that should retaliate after being attacked
     * @param alertOthers true when this pet should alert other nearby pets through
     * Minecraft's normal {@link HurtByTargetGoal#setAlertOthers()} behavior
     */
    public PetHurtByTargetGoal(P pet, boolean alertOthers)
    {
        super(pet);
        this.pet = pet;

        if (alertOthers)
        {
            this.setAlertOthers();
        }
    }

    /**
     * Rejects friendly-fire style targets before allowing the vanilla hurt target
     * logic to choose the attacker.
     *
     * @return true if the pet may target its most recent attacker
     */
    @Override
    public boolean canUse()
    {
        LivingEntity attacker = pet.getLastHurtByMob();

        if (attacker instanceof Player || attacker instanceof ITradePostPet)
        {
            return false;
        }

        return super.canUse();
    }

    /**
     * Starts retaliation and, if the guard-alert cooldown has elapsed, asks
     * nearby MineColonies guards to help against the accepted attacker.
     */
    @Override
    public void start()
    {
        super.start();

        P localPet = pet;
        if (localPet == null) return;

        final LivingEntity attacker = localPet.getTarget();
        if (attacker != null && localPet.level().getGameTime() >= nextGuardAlertGameTime)
        {
            PetGuardAlertUtil.alertNearbyGuards(localPet, attacker);
            nextGuardAlertGameTime = localPet.level().getGameTime() + GUARD_ALERT_COOLDOWN_TICKS;
        }
    }

    /**
     * Restricts the vanilla alert-others behavior so it only spreads among trade
     * post pets and never propagates player or pet targets.
     *
     * @param mob nearby mob being considered for alerting
     * @param target target the nearby mob would be assigned
     */
    @Override
    protected void alertOther(@Nonnull Mob mob, @Nonnull LivingEntity target)
    {
        if (mob instanceof ITradePostPet && !(target instanceof Player) && !(target instanceof ITradePostPet))
        {
            super.alertOther(mob, target);
        }
    }
}
