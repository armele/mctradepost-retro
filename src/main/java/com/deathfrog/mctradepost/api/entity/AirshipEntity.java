package com.deathfrog.mctradepost.api.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Non-physical visual vehicle used for the visible endpoint portions of air trade routes.
 *
 * <p>The shared {@link GhostCartEntity} controller supplies path synchronization,
 * cargo data, and client tracking. Air-route construction and selection remain
 * the responsibility of the server-side trade routing layer.</p>
 */
public class AirshipEntity extends GhostCartEntity
{
    public AirshipEntity(EntityType<? extends AirshipEntity> type, Level level)
    {
        super(type, level);
    }

    @Override
    protected double getTrailParticleYOffset()
    {
        // The airship's exhaust is mounted by the raised rear propeller, not at basket level.
        return 1.1D;
    }

    @Override
    protected boolean discardWhenPathless()
    {
        // Keep manually summoned airships available for visual inspection.
        // Completed routed vehicles still use GhostCartEntity's end-of-path removal.
        return false;
    }
}
