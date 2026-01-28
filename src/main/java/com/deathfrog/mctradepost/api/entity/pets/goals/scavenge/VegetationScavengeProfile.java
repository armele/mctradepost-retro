package com.deathfrog.mctradepost.api.entity.pets.goals.scavenge;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.entity.pets.PetRoles;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.server.level.ServerLevel;

public class VegetationScavengeProfile<P extends Animal & ITradePostPet> implements IScavengeProfile<P>
{
    protected static final String LOOT_BASE = "vegetation_scavenge";

    // --- tuning ---
    private final int maxVerticalSearch;         // how high above pet to sample for canopy targets
    private final int downSearchForGroundAnchor; // how far down to find standable ground under leaves
    private final int samples;                   // random samples per canUse

    public VegetationScavengeProfile()
    {
        this(10, 14, 140);
    }

    public VegetationScavengeProfile(final int maxVerticalSearch, final int downSearchForGroundAnchor, final int samples)
    {
        this.maxVerticalSearch = Math.max(2, maxVerticalSearch);
        this.downSearchForGroundAnchor = Math.max(2, downSearchForGroundAnchor);
        this.samples = Mth.clamp(samples, 40, 240);
    }

    @Override
    public PetRoles requiredRole()
    {
        return PetRoles.SCAVENGE_VEGETATION;
    }

    @Override
    public double navigationYOffset()
    {
        return 0.0;
    }

    @Override
    public @Nullable BlockPos findTarget(final P pet, final int searchRadius)
    {
        if (!(pet.level() instanceof ServerLevel level)) return null;

        final BlockPos origin = pet.blockPosition();
        final @Nonnull RandomSource rng = NullnessBridge.assumeNonnull(pet.getRandom());

        final int r = Math.max(3, searchRadius);
        final int yMin = origin.getY() - 2;
        final int yMax = origin.getY() + this.maxVerticalSearch;

        final List<BlockPos> candidates = new ArrayList<>(24);

        for (int i = 0; i < this.samples; i++)
        {
            final int dx = Mth.nextInt(rng, -r, r);
            final int dz = Mth.nextInt(rng, -r, r);
            final int dy = Mth.nextInt(rng, -2, this.maxVerticalSearch);

            final BlockPos pos = origin.offset(dx, dy, dz);
            if (pos.getY() < yMin || pos.getY() > yMax) continue;
            if (!level.isLoaded(pos)) continue;

            final BlockState state = level.getBlockState(pos);
            if (!isHarvestable(level, pos, state)) continue;

            candidates.add(pos);
            if (candidates.size() >= 24) break;
        }

        if (candidates.isEmpty()) return null;
        return candidates.get(rng.nextInt(candidates.size()));
    }

    @Override
    public @Nonnull BlockPos navigationAnchor(final P pet, final @Nonnull BlockPos target)
    {
        if (!(pet.level() instanceof ServerLevel level)) return target;

        final BlockState state = level.getBlockState(target);
        final boolean flyer = isFlyer(pet);

        // Fruit-bearing blocks: for walkers, approach from ground adjacent;
        // for flyers, pick a nearby hover point (air) adjacent to the target.
        if (state.is(ModTags.TAG_FRUIT))
        {
            if (flyer)
            {
                final BlockPos hover = findHoverSpotNear(level, target, 2, 2);
                return hover != null ? hover : target;
            }

            final BlockPos ground = findNearbyStandable(level, target, 2, 5);
            return ground != null ? ground : target;
        }

        // Apple leaves: for walkers, find ground under/near the canopy;
        // for flyers, hover adjacent to the leaf block.
        if (state.is(ModTags.TAG_APPLE_LEAVES))
        {
            if (flyer)
            {
                final BlockPos hover = findHoverSpotNear(level, target, 2, 3);
                return hover != null ? hover : target;
            }

            final BlockPos ground = findGroundUnderCanopy(level, target, 2, this.downSearchForGroundAnchor);
            return ground != null ? ground : target;
        }

        return target;
    }

    @Override
    public boolean isHarvestable(final ServerLevel level, final BlockPos pos, final BlockState state)
    {
        // Tag-driven selection
        if (state.is(ModTags.TAG_FRUIT))
        {
            // "Smart" maturity: only gate when a known property exists.
            return passesGenericMaturityGate(state);
        }

        if (state.is(ModTags.TAG_APPLE_LEAVES) || state.is(ModTags.TAG_OTHER_LEAVES))
        {
            return true;
        }

        return false;
    }

    /**
     * Maps a given block state to a loot table location.
     * The mapping is as follows:
     * - Fruit blocks map to pet/vegetation_scavenge/fruit/<block_path>
     * - Apple leaves map to pet/vegetation_scavenge/apple_leaves
     * - Other leaves map to pet/vegetation_scavenge/other_leaves
     * If the block is not harvestable, or if the block does not have a tag, returns null.
     * @param level the level containing the block
     * @param pos the position of the block
     * @param state the block state at pos
     * @return the loot table location for the given block state, or null if none
     */
    @Override
    public @Nullable ResourceLocation lootTableFor(final ServerLevel level, final BlockPos pos, @Nonnull final BlockState state)
    {

        Block block = state.getBlock();

        if (block == null || !isHarvestable(level, pos, state))
        {
            return null;
        }

        final ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);

        if (state.is(ModTags.TAG_FRUIT))
        {
            return ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "pet/" + LOOT_BASE + "/fruit/" + key.getPath());
        }

        if (state.is(ModTags.TAG_APPLE_LEAVES))
        {
            return ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "pet/" + LOOT_BASE + "/apple_leaves/" + key.getPath());
        }

        if (state.is(ModTags.TAG_OTHER_LEAVES))
        {
            return ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "pet/" + LOOT_BASE + "/other_leaves/" + key.getPath());
        }

        return null;
    }

    /**
     * Called when the pet successfully harvests at the given position.
     * This method is responsible for any visual effects or sounds that should be played when the pet harvests.
     * 
     * @param level the level containing the block that was harvested
     * @param pos the position of the block that was harvested
     * @param pet the pet that did the harvesting
     */
    @SuppressWarnings("null")
    @Override
    public void onSuccessfulHarvest(final ServerLevel level, final BlockPos pos, final P pet)
    {
        if (pos == null || pet == null) return;

        final BlockState state = level.getBlockState(pos);

        if (state.is(ModTags.TAG_FRUIT))
        {
            onHarvestApplied(level, pos, state);

            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.NEUTRAL, 0.8f, 1.0f);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                6, 0.2, 0.2, 0.2, 0.01);
            return;
        }

        if (state.is(ModTags.TAG_APPLE_LEAVES) || state.is(ModTags.TAG_OTHER_LEAVES))
        {
            level.playSound(null, pos, SoundEvents.GRASS_BREAK, SoundSource.NEUTRAL, 0.8f, 0.95f);
            level.sendParticles(ParticleTypes.COMPOSTER,
                pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                8, 0.3, 0.3, 0.3, 0.01);
        }
    }


    /**
     * Called after the pet successfully harvests a block at the given position.
     * This method is responsible for modifying the block state of the harvested block to reflect the post-harvest state.
     * In this case, we apply a heuristic to set the age of the block to a reasonable post-harvest value, and set the berries flag to false.
     * @param level the level containing the block that was harvested
     * @param pos the position of the block that was harvested
     * @param state the block state of the block that was harvested
     */
    protected void onHarvestApplied(ServerLevel level, @Nonnull BlockPos pos, @Nonnull BlockState state)
    {
        if (!state.is(ModTags.TAG_FRUIT)) return;

        // AGE property: drop to a reasonable post-harvest stage
        IntegerProperty age = findIntProp(state, "age");
        if (age != null)
        {
            int min = age.getPossibleValues().stream().min(Integer::compareTo).orElse(0);
            int max = age.getPossibleValues().stream().max(Integer::compareTo).orElse(min);

            // Heuristic:
            // - berries (0..3): set to 1
            // - cocoa (0..2): set to 0
            int newAge = (max >= 3) ? Math.min(1, max) : min;

            BlockState newState = state.setValue(age, newAge);

            if (newState == null)
            {
                return;
            }

            if (newState != state)
            {
                level.setBlock(pos, newState, 3);
            }
            return;
        }

        // BERRIES boolean: set false
        BooleanProperty berries = findBoolProp(state, "berries");
        if (berries != null && state.getValue(berries))
        {
            BlockState newState = state.setValue(berries, false);

            if (newState == null)
            {
                return;
            }

            level.setBlock(pos, newState, 3);
        }
    }


    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------


    /**
     * Returns true if the given pet is a flyer (i.e. it uses FlyingPathNavigation), false otherwise.
     * This is used to determine if a pet should navigate to the ground or hover above when scavenging.
     * @param pet the pet to check
     * @return true if the pet is a flyer, false otherwise
     */
    private static boolean isFlyer(final Animal pet)
    {
        // Works for bats/parrots if they use FlyingPathNavigation.
        return pet.getNavigation() instanceof FlyingPathNavigation;
    }

    /**
     * "Smart maturity" for tag entries:
     * - If an 'age' IntegerProperty exists -> require max age.
     * - Else if a 'berries' BooleanProperty exists -> require true.
     * - Else -> no gating.
     */
    private static boolean passesGenericMaturityGate(final BlockState state)
    {
        IntegerProperty age = findIntProp(state, "age");
        if (age != null)
        {
            int cur = state.getValue(age);
            int max = age.getPossibleValues().stream().max(Integer::compareTo).orElse(cur);
            return cur >= max;
        }

        BooleanProperty berries = findBoolProp(state, "berries");
        if (berries != null)
        {
            return state.getValue(berries);
        }

        return true;
    }

    private static @Nullable IntegerProperty findIntProp(final BlockState state, final String name)
    {
        for (Property<?> prop : state.getProperties())
        {
            if (prop instanceof IntegerProperty ip && name.equals(prop.getName()))
                return ip;
        }
        return null;
    }

    private static @Nullable BooleanProperty findBoolProp(final BlockState state, final String name)
    {
        for (Property<?> prop : state.getProperties())
        {
            if (prop instanceof BooleanProperty bp && name.equals(prop.getName()))
                return bp;
        }
        return null;
    }

    /**
     * For walkers: find a nearby standable position near target by scanning a small square, then walking downward.
     */
    @SuppressWarnings("null")
    private static @Nullable BlockPos findNearbyStandable(final ServerLevel level, final BlockPos target, final int horizRadius, final int maxDown)
    {
        BlockPos best = null;

        for (final BlockPos p : BlockPos.betweenClosed(
            target.offset(-horizRadius, 0, -horizRadius),
            target.offset( horizRadius, 0,  horizRadius)))
        {
            final BlockPos top = new BlockPos(p.getX(), target.getY(), p.getZ());
            final BlockPos stand = descendToStandable(level, top, maxDown);
            if (stand == null) continue;

            if (best == null || stand.distSqr(target) < best.distSqr(target)) best = stand;
        }

        return best;
    }

    /**
     * For canopy leaves: try a small ring around the target, then descend to ground.
     */
    @SuppressWarnings("null")
    private static @Nullable BlockPos findGroundUnderCanopy(final ServerLevel level, final BlockPos leavesPos, final int horizRadius, final int maxDown)
    {
        BlockPos best = null;

        for (final BlockPos p : BlockPos.betweenClosed(
            leavesPos.offset(-horizRadius, 0, -horizRadius),
            leavesPos.offset( horizRadius, 0,  horizRadius)))
        {
            final BlockPos top = new BlockPos(p.getX(), leavesPos.getY(), p.getZ());
            final BlockPos stand = descendToStandable(level, top, maxDown);
            if (stand == null) continue;

            if (best == null || stand.distSqr(leavesPos) < best.distSqr(leavesPos)) best = stand;
        }

        return best;
    }

    /**
     * Finds a hover spot (air) near the target for flyers.
     * Returns an air block position that should be safe to navigate to.
     */
    private static @Nullable BlockPos findHoverSpotNear(final ServerLevel level, final BlockPos target, final int horizRadius, final int vertRadius)
    {
        // Prefer positions adjacent to the target at similar height, then slightly above.
        for (int dy = 0; dy <= vertRadius; dy++)
        {
            final int y = target.getY() + dy;
            for (final BlockPos p : BlockPos.betweenClosed(
                new BlockPos(target.getX() - horizRadius, y, target.getZ() - horizRadius),
                new BlockPos(target.getX() + horizRadius, y, target.getZ() + horizRadius)))
            {
                // skip inside target block itself
                if (p.equals(target)) continue;

                if (isAirForHover(level, p))
                    return p;
            }
        }

        // Fallback: try just above target
        BlockPos above = target.above();
        return isAirForHover(level, above) ? above : null;
    }

    @SuppressWarnings("null")
    private static boolean isAirForHover(final ServerLevel level, final BlockPos p)
    {
        // Require enough space for the mob: p and p.above() should be non-colliding.
        final BlockState a = level.getBlockState(p);
        final BlockState b = level.getBlockState(p.above());

        return a.getCollisionShape(level, p).isEmpty()
            && b.getCollisionShape(level, p.above()).isEmpty();
    }

    @SuppressWarnings("null")
    private static @Nullable BlockPos descendToStandable(final ServerLevel level, final BlockPos start, final int maxDown)
    {
        BlockPos p = start;

        for (int i = 0; i <= maxDown; i++)
        {
            final BlockPos below = p.below();
            final BlockState at = level.getBlockState(p);
            final BlockState above = level.getBlockState(p.above());
            final BlockState belowState = level.getBlockState(below);

            final boolean spaceHere = at.getCollisionShape(level, p).isEmpty();
            final boolean spaceAbove = above.getCollisionShape(level, p.above()).isEmpty();
            final boolean solidBelow = belowState.isFaceSturdy(level, below, Direction.UP);

            if (spaceHere && spaceAbove && solidBelow)
                return p;

            p = p.below();
        }

        return null;
    }
}
