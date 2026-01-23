package com.deathfrog.mctradepost.core.event.wishingwell.ritual;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.core.event.wishingwell.WishingWellHandler;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public class WeatherRitualProcessor 
{
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Processes a weather ritual at the specified BlockPos within the ServerLevel. Based on the ritual definition, it sets the weather
     * to clear, rain, or storm for the remainder of the day. If the target weather type is unknown, the ritual is ignored.
     *
     * @param level  the ServerLevel where the ritual is taking place
     * @param pos    the BlockPos of the wishing well structure
     * @param ritual the ritual definition containing the target weather type
     * @return true if the ritual was successfully triggered, false otherwise
     */
    public static boolean processRitualWeather(@Nonnull ServerLevel level, @Nonnull BlockPos pos, RitualDefinitionHelper ritual)
    {
        int restOfDay = 24000 - (int) (level.getDayTime() % 24000);
        int clearTime = 0;
        int weatherTime = 0;
        boolean isRaining = false;
        boolean isThundering = false;

        String weather = ritual.target();
        // LOGGER.info("Ritual target {} resolved to {}", ritual.target(), entityType);

        if (weather == null)
        {
            LOGGER.info("No weather target provided during Weather ritual.");
            return false;
        }
        else if (weather.equals("clear"))
        {
            clearTime = restOfDay;
            weatherTime = 0;
            isRaining = false;
            isThundering = false;
        }
        else if (weather.equals("rain"))
        {
            clearTime = 0;
            weatherTime = restOfDay;
            isRaining = true;
            isThundering = false;
        }
        else if (weather.equals("storm"))
        {
            clearTime = 0;
            weatherTime = restOfDay;
            isRaining = true;
            isThundering = true;
        }
        else
        {
            LOGGER.info("Unknown weather ritual of {} ignored at {} ", weather, pos);
            return false;
        }

        level.setWeatherParameters(clearTime, weatherTime, isRaining, isThundering);

        LOGGER.info("Weather ritual of {} completed at {} ", weather, pos);
        WishingWellHandler.showRitualEffect(level, pos);

        return true;
    }
}
