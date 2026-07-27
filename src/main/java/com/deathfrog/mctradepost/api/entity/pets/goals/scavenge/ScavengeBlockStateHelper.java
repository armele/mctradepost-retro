package com.deathfrog.mctradepost.api.entity.pets.goals.scavenge;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Shared block-state preparation used by scavenging data analysis and display.
 */
public final class ScavengeBlockStateHelper
{
    /**
     * Prevents instantiation of this utility class.
     */
    private ScavengeBlockStateHelper()
    {
    }

    /**
     * Produces a representative harvestable state by maturing integer age
     * properties and enabling berry-bearing boolean properties.
     *
     * @param block block whose representative state should be prepared
     * @return representative mature block state
     */
    public static BlockState representativeState(final Block block)
    {
        BlockState state = block.defaultBlockState();
        for (Property<?> property : state.getProperties())
        {
            if (property instanceof IntegerProperty integerProperty)
            {
                @SuppressWarnings("null")
                final int maximum = integerProperty.getPossibleValues().stream()
                    .max(Integer::compareTo)
                    .orElse(state.getValue(integerProperty));
                state = state.setValue(integerProperty, maximum);
            }
            else if (property instanceof BooleanProperty booleanProperty
                && ScavengePropertyNames.BERRIES.equals(booleanProperty.getName()))
            {
                state = state.setValue(booleanProperty, true);
            }
        }
        return state;
    }
}
