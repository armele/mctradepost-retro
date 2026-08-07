package com.deathfrog.mctradepost.api.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Road shipment entity with distance-driven wheel animation state. */
public class WagonEntity extends GhostCartEntity
{
    private double lastWheelX;
    private double lastWheelZ;
    private float wheelRotation;
    private boolean wheelPositionInitialized;

    public WagonEntity(EntityType<? extends WagonEntity> type, Level level)
    {
        super(type, level);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (wheelPositionInitialized)
        {
            double dx = getX() - lastWheelX;
            double dz = getZ() - lastWheelZ;
            wheelRotation += (float) (Math.sqrt(dx * dx + dz * dz) * 2.7D);
        }
        lastWheelX = getX();
        lastWheelZ = getZ();
        wheelPositionInitialized = true;
    }

    public float getWheelRotation()
    {
        return wheelRotation;
    }

    @Override
    protected Vec3 pathPosition(@Nonnull BlockPos pos)
    {
        // Road nodes identify the supporting block. The model's wheel bottoms
        // sit at the entity origin, so place that origin on the block surface.
        return Vec3.atCenterOf(pos).add(0.0D, 0.5D, 0.0D);
    }
}
