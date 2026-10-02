package com.deathfrog.mctradepost.api.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Non-physical visual vehicle reserved for future air trade-route segments.
 *
 * <p>The shared {@link GhostCartEntity} controller supplies path synchronization,
 * cargo data, and client tracking. Air-route construction and selection are
 * intentionally not part of this entity.</p>
 */
public class AirshipEntity extends GhostCartEntity
{
    public AirshipEntity(EntityType<? extends AirshipEntity> type, Level level)
    {
        super(type, level);
    }
}
