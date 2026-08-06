package com.deathfrog.mctradepost.api.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Non-physical shipment visual rendered as an oak boat. */
public class GhostBoatEntity extends GhostCartEntity
{
    public GhostBoatEntity(EntityType<? extends GhostBoatEntity> type, Level level)
    {
        super(type, level);
    }

    @Override
    protected Vec3 pathPosition(BlockPos pos)
    {
        return Vec3.atCenterOf(pos).add(0.0D, 0.15D, 0.0D);
    }

    @Override
    protected void spawnTrailParticle()
    {
        if (level() instanceof ServerLevel serverLevel)
            serverLevel.sendParticles(ParticleTypes.SPLASH, getX(), getY(), getZ(), 2, 0.25D, 0.05D, 0.25D, 0.0D);
    }

    @Override
    protected void playRollingSound()
    {
        level().playSound(null, blockPosition(), SoundEvents.BOAT_PADDLE_WATER, SoundSource.NEUTRAL, 0.25F, 1.0F);
    }
}
