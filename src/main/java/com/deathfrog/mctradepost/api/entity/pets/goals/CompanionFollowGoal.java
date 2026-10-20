package com.deathfrog.mctradepost.api.entity.pets.goals;

import java.util.EnumSet;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.entity.pets.PetDragon;
import com.deathfrog.mctradepost.api.util.NullnessBridge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;

/** Keeps a summoned staff companion near the player who most recently summoned or recalled it. */
public class CompanionFollowGoal extends Goal
{
    private static final double START_DISTANCE_SQ = 8.0D * 8.0D;
    private static final double STOP_DISTANCE_SQ = 3.0D * 3.0D;
    private static final double TELEPORT_DISTANCE_SQ = 24.0D * 24.0D;
    private final PetDragon dragon;
    private ServerPlayer player;

    /**
     * Creates a follow goal for a companion dragon.
     *
     * @param dragon companion controlled by this goal
     */
    public CompanionFollowGoal(@Nonnull PetDragon dragon)
    {
        this.dragon = dragon;
        setFlags(NullnessBridge.assumeNonnull(EnumSet.of(Flag.MOVE, Flag.LOOK)));
    }

    /** {@inheritDoc} */
    @Override
    public boolean canUse()
    {
        UUID controller = dragon.getCompanionController();
        if (controller == null || dragon.level().isClientSide)
        {
            return false;
        }

        net.minecraft.world.entity.player.Player resolvedPlayer = dragon.level().getPlayerByUUID(controller);
        player = resolvedPlayer instanceof ServerPlayer serverPlayer ? serverPlayer : null;

        final ServerPlayer localPlayer = player;

        return localPlayer != null && !localPlayer.isSpectator() && dragon.distanceToSqr(localPlayer) > START_DISTANCE_SQ;
    }

    /** {@inheritDoc} */
    @Override
    public boolean canContinueToUse()
    {
        final ServerPlayer localPlayer = player;

        return localPlayer != null && localPlayer.isAlive() && dragon.distanceToSqr(localPlayer) > STOP_DISTANCE_SQ;
    }

    /** {@inheritDoc} */
    @Override
    public void tick()
    {
        final ServerPlayer localPlayer = player;

        if (localPlayer == null)
        {
            return;
        }

        dragon.getLookControl().setLookAt(localPlayer, 10.0F, dragon.getMaxHeadXRot());
        
        if (dragon.distanceToSqr(localPlayer) > TELEPORT_DISTANCE_SQ)
        {
            dragon.teleportTo(localPlayer.getX(), localPlayer.getY(), localPlayer.getZ());
        }
        else
        {
            dragon.getNavigation().moveTo(localPlayer, 1.15D);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void stop()
    {
        dragon.getNavigation().stop();
        player = null;
    }
}
