package com.deathfrog.mctradepost.api.entity.pets.goals.scavenge;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.entity.pets.PetRoles;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.state.BlockState;

public interface IScavengeProfile<P extends Animal & ITradePostPet> 
{
    PetRoles requiredRole();

    /** Pick a target position (usually near work location). Return null if none. */
    @Nullable BlockPos findTarget(P pet, int searchRadius);

    /** Where should the pet navigate to for this target? */
    @Nonnull BlockPos navigationAnchor(P pet, @Nonnull BlockPos target);

    /** Y offset to apply when navigating to the anchor position. */
    double navigationYOffset();

    /** Is this block at pos a valid “harvest trigger” and how do we map it to loot? */
    boolean isHarvestable(ServerLevel level, BlockPos pos, BlockState state);

    /** Loot table key for a harvestable spot (or null if none). */
    @Nullable ResourceLocation lootTableFor(ServerLevel level, BlockPos pos, @Nonnull BlockState state);

    /** cosmetics on success. */
    void onSuccessfulHarvest(ServerLevel level, BlockPos pos, P pet);
}