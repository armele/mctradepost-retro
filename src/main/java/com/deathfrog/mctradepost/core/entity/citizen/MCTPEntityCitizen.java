package com.deathfrog.mctradepost.core.entity.citizen;

import com.minecolonies.core.entity.citizen.EntityCitizen;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

public class MCTPEntityCitizen extends EntityCitizen {

    public MCTPEntityCitizen(EntityType<? extends PathfinderMob> type, Level world) {
        super(type, world);
    }
    
}
