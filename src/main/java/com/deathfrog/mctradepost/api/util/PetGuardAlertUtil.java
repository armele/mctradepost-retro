package com.deathfrog.mctradepost.api.util;

import java.util.Comparator;
import java.util.List;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIGuard;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_PETOTHERGOALS;

public final class PetGuardAlertUtil
{
    private static final double ALERT_RANGE = 48.0D;
    private static final double ALERT_RANGE_SQR = ALERT_RANGE * ALERT_RANGE;
    private static final int MAX_GUARDS_TO_ALERT = 2;

    private PetGuardAlertUtil()
    {
    }

    public static <P extends PathfinderMob & ITradePostPet> int alertNearbyGuards(@Nonnull final P pet, @Nonnull final LivingEntity attacker)
    {
        if (pet.level().isClientSide || !isValidAlertTarget(attacker))
        {
            return 0;
        }

        final IColony colony = resolveColony(pet);
        if (colony == null)
        {
            return 0;
        }

        final List<GuardAlertCandidate> candidates = colony.getCitizenManager().getCitizens().stream()
            .filter(PetGuardAlertUtil::isGuardWithLoadedAI)
            .filter(citizen -> citizen.getEntity().get().distanceToSqr(pet) <= ALERT_RANGE_SQR)
            .map(citizen -> new GuardAlertCandidate(citizen, citizen.getEntity().get().distanceToSqr(pet)))
            .sorted(Comparator.comparingDouble(GuardAlertCandidate::distanceToPetSqr))
            .toList();

        int alerted = 0;
        for (final GuardAlertCandidate candidate : candidates)
        {
            final AbstractEntityAIGuard<?, ?> guardAI = (AbstractEntityAIGuard<?, ?>) candidate.citizen().getJob().getWorkerAI();
            if (guardAI.canHelp(attacker.blockPosition()))
            {
                guardAI.startHelpCitizen(attacker);
                alerted++;

                if (alerted >= MAX_GUARDS_TO_ALERT)
                {
                    break;
                }
            }
        }

        final int alertedGuards = alerted;
        TraceUtils.dynamicTrace(TRACE_PETOTHERGOALS, () -> TraceUtils.LOGGER.info(
            "Pet {} alerted {} guard(s) in colony {} about attacker {}.",
            pet.getUUID(),
            alertedGuards,
            colony.getID(),
            attacker.getType()));

        return alerted;
    }

    private static boolean isValidAlertTarget(@Nonnull final LivingEntity attacker)
    {
        return attacker.isAlive() && attacker instanceof Enemy && !(attacker instanceof Player) && !(attacker instanceof ITradePostPet);
    }

    private static <P extends PathfinderMob & ITradePostPet> IColony resolveColony(@Nonnull final P pet)
    {
        final IBuilding trainerBuilding = pet.getTrainerBuilding();
        if (trainerBuilding != null)
        {
            return trainerBuilding.getColony();
        }

        return IColonyManager.getInstance().getColonyByPosFromWorld(pet.level(), pet.blockPosition());
    }

    private static boolean isGuardWithLoadedAI(@Nonnull final ICitizenData citizen)
    {
        return citizen.getEntity().isPresent()
            && citizen.getJob() instanceof AbstractJobGuard
            && citizen.getJob().getWorkerAI() instanceof AbstractEntityAIGuard;
    }

    private record GuardAlertCandidate(ICitizenData citizen, double distanceToPetSqr)
    {
    }
}
