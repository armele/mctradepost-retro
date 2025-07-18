package com.deathfrog.mctradepost.api.entity.pets;

import net.minecraft.core.BlockPos;

public interface IHerdingPet
{
    public void setTargetPosition(BlockPos targetPosition);
    public void incrementStuckTicks();
    public int getStuckTicks();
    public void clearStuckTicks();
}
