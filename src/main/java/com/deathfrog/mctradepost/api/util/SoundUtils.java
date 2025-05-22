package com.deathfrog.mctradepost.api.util;

import java.util.Random;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.ICivilianData;
import com.minecolonies.api.colony.IVisitorData;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.sounds.EventType;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class SoundUtils {
    /**
     * Plays a sound with a certain chance at a certain position.
     *
     * @param worldIn     the world to play the sound in.
     * @param position    position to play the sound at.
     * @param type        sound to play.
     * @param chance      chance to play the sound.
     * @param volume      volume of the sound.
     */

    /**
     * Get a random between 1 and 100.
     */
    private static final int ONE_HUNDRED = 100;
    private static final Random rand = new Random();
    
    public static void playSoundWithChance(
        @NotNull final Level worldIn,
        @Nullable Player player,    
        @NotNull final BlockPos position,
        @NotNull SoundEvent sound,
        @NotNull SoundSource category,
        final double chance, 
        final double volume,
        final double pitch)
    {
        if (worldIn.isClientSide) {
            return;
        }

        if (chance > rand.nextDouble() * ONE_HUNDRED) {
            worldIn.playSound(null,
                position,
                sound,
                category,
                (float) volume,
                (float) pitch);

        }
    }
}
