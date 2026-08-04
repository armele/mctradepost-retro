package com.deathfrog.mctradepost.api.entity.pets.goals.scavenge;

import com.deathfrog.mctradepost.core.ModTags;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/** Side-neutral readiness checks shared by server selection and client snapshots. */
public final class ScavengeHarvestability
{
    private ScavengeHarvestability() { }

    /** Returns whether a vegetation state is currently eligible for harvesting. */
    @SuppressWarnings("null")
    public static boolean isVegetationHarvestable(final BlockState state)
    {
        if (state.is(ModTags.BLOCKS.TAG_FRUIT))
        {
            final IntegerProperty age = findIntegerProperty(state, ScavengePropertyNames.AGE);
            if (age != null)
            {
                final int current = state.getValue(age);
                final int maximum = age.getPossibleValues().stream().max(Integer::compareTo).orElse(current);
                return current >= maximum;
            }

            final BooleanProperty berries = findBooleanProperty(state, ScavengePropertyNames.BERRIES);
            return berries == null || state.getValue(berries);
        }

        return state.is(ModTags.BLOCKS.TAG_SCAVENGE_LEAVES) || state.is(ModTags.BLOCKS.TAG_GROUNDCOVER);
    }

    /** Returns whether a state is an eligible dredger forage source. */
    @SuppressWarnings("null")
    public static boolean isDredgerHarvestable(final BlockState state)
    {
        return state.is(ModTags.BLOCKS.WATER_SCAVENGE_BLOCK_TAG);
    }

    private static IntegerProperty findIntegerProperty(final BlockState state, final String name)
    {
        for (Property<?> property : state.getProperties())
        {
            if (property instanceof IntegerProperty integerProperty && name.equals(property.getName()))
            {
                return integerProperty;
            }
        }
        return null;
    }

    private static BooleanProperty findBooleanProperty(final BlockState state, final String name)
    {
        for (Property<?> property : state.getProperties())
        {
            if (property instanceof BooleanProperty booleanProperty && name.equals(property.getName()))
            {
                return booleanProperty;
            }
        }
        return null;
    }
}
