package com.deathfrog.mctradepost.api.entity.pets.goals.scavenge;

import java.util.Set;

import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.entity.pets.PetRoles;
import com.deathfrog.mctradepost.core.blocks.blockentity.PetWorkingBlockEntity;
import com.deathfrog.mctradepost.core.entity.pets.scavenge.FocusedForagingIndex;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Shared focused-target selection for vegetation and water profiles. */
public final class FocusedForaging
{
    private static final int CACHE_ATTEMPTS = 12;
    private static final int FOCUSED_SAMPLES = 180;

    /**
     * Prevents instantiation of this utility class.
     */
    private FocusedForaging() { }

    /**
     * Finds a currently harvestable source capable of producing the working
     * block's configured focus item.
     * <p>
     * Cached positions are checked first. If none are ready, the method samples
     * loaded positions around the pet's work location and remembers compatible
     * sources for later attempts. No chunks are loaded by this search.
     * </p>
     *
     * @param pet pet requesting a focused target
     * @param profile active scavenge profile used to validate source readiness
     * @param searchRadius horizontal distance sampled around the work location
     * @param verticalDown lowest relative Y offset included in sampling
     * @param verticalUp highest relative Y offset included in sampling
     * @param <P> concrete trade-post pet type
     * @return a harvestable focused source, or {@code null} when none is found
     */
    public static <P extends Animal & ITradePostPet> BlockPos findTarget(
        final P pet, final IScavengeProfile<P> profile, final int searchRadius, final int verticalDown, final int verticalUp)
    {
        if (!(pet.level() instanceof ServerLevel level)) return null;
        final BlockPos workPos = pet.getWorkLocation();
        if (workPos == null || !level.isLoaded(workPos)) return null;
        final BlockEntity blockEntity = level.getBlockEntity(workPos);
        if (!(blockEntity instanceof PetWorkingBlockEntity working) || !working.isFocusedForagingEnabled()) return null;
        final ItemStack focus = working.getFocusStack();
        if (focus.isEmpty()) return null;

        final PetRoles role = profile.requiredRole();
        final Set<Block> sources = FocusedForagingIndex.sourcesFor(level.getServer(), role, focus.getItem());
        if (sources.isEmpty()) return null;

        for (int i = 0; i < CACHE_ATTEMPTS; i++)
        {
            final BlockPos cached = working.pollFocusedTarget();
            if (cached == null) break;
            if (!level.isLoaded(cached))
            {
                working.rememberFocusedTarget(cached);
                continue;
            }
            final BlockState state = level.getBlockState(cached);
            if (!sources.contains(state.getBlock())) continue;
            working.rememberFocusedTarget(cached);
            if (profile.isHarvestable(level, cached, state)) return cached;
        }

        final int radius = Math.max(3, searchRadius);
        final RandomSource rnd = pet.getRandom();

        if (rnd == null) return null;

        for (int i = 0; i < FOCUSED_SAMPLES; i++)
        {
            final BlockPos pos = workPos.offset(
                Mth.nextInt(rnd, -radius, radius),
                Mth.nextInt(rnd, verticalDown, verticalUp),
                Mth.nextInt(rnd, -radius, radius));

            if (pos == null) continue;

            if (!level.isLoaded(pos)) continue;
            final BlockState state = level.getBlockState(pos);
            if (!sources.contains(state.getBlock())) continue;
            working.rememberFocusedTarget(pos);
            if (profile.isHarvestable(level, pos, state)) return pos;
        }
        return null;
    }
}
