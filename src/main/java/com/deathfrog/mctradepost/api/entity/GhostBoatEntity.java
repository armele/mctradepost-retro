package com.deathfrog.mctradepost.api.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;

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
    protected Vec3 pathPosition(@Nonnull BlockPos pos)
    {
        // Water route nodes identify the water block itself. Place the boat's
        // entity origin at that block's surface rather than within its volume.
        return Vec3.atCenterOf(pos).add(0.0D, 0.5D, 0.0D);
    }

    @Override
    protected void spawnTrailParticle()
    {
        if (level() instanceof ServerLevel serverLevel)
        {
            Vec3 offset = NullnessBridge.assumeNonnull(getForward().scale(0.9D));
            Vec3 behind = position().subtract(offset);
            serverLevel.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.SPLASH), behind.x(), behind.y(), behind.z(), 2, 0.25D, 0.05D, 0.25D, 0.0D);
            serverLevel.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.BUBBLE), behind.x(), behind.y(), behind.z(), 3, 0.2D, 0.05D, 0.2D, 0.02D);
        }
    }

    @Override
    protected void playRollingSound()
    {
        BlockPos soundPos = blockPosition();

        if (soundPos == null) return;

        level().playSound(null, soundPos, NullnessBridge.assumeNonnull(SoundEvents.BOAT_PADDLE_WATER), SoundSource.NEUTRAL, 0.25F, 1.0F);
    }
}
