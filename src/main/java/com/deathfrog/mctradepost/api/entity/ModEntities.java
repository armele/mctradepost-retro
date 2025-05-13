package com.deathfrog.mctradepost.api.entity;

import com.deathfrog.mctradepost.core.entity.citizen.MCTPEntityCitizen;

import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModEntities {
    public static DeferredHolder<EntityType<?>, EntityType<MCTPEntityCitizen>> MCTP_CITIZEN;
}
