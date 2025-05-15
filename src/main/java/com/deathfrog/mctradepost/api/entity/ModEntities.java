package com.deathfrog.mctradepost.api.entity;

import com.deathfrog.mctradepost.core.entity.citizen.MCTPEntityCitizen;

import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.registries.DeferredHolder;

// TODO: Validate class unnecessary and remove.

public class ModEntities {
    public static DeferredHolder<EntityType<?>, EntityType<MCTPEntityCitizen>> MCTP_CITIZEN;
}
