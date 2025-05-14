package com.deathfrog.mctradepost.api.sounds;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.jobs.MCTPModJobs;
import com.minecolonies.api.sounds.EventType;
import com.minecolonies.api.util.Tuple;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.*;

/**
 * Registering of sound events for our colony.
 */
public final class ModSoundEvents
{
    /**
     * Citizen sound prefix.
     */
    public static final String CITIZEN_SOUND_EVENT_PREFIX = "citizen.";

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, MCTradePostMod.MODID);

    /**
     * Map of sound events.
     */
    public static Map<String, Map<EventType, List<Tuple<SoundEvent, SoundEvent>>>> CITIZEN_SOUND_EVENTS = new HashMap<>();

    /**
     * Private constructor to hide the implicit public one.
     */
    private ModSoundEvents()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Register the {@link SoundEvent}s.
     *
     * @param registry the registry to register at.
     */
    static
    {
        final List<ResourceLocation> mainTypes = new ArrayList<>(MCTPModJobs.getJobs());

        for (final ResourceLocation job : mainTypes)
        {
            final Map<EventType, List<Tuple<SoundEvent, SoundEvent>>> map = new HashMap<>();
            for (final EventType event : EventType.values())
            {
                final List<Tuple<SoundEvent, SoundEvent>> individualSounds = new ArrayList<>();
                for (int i = 1; i <= 4; i++)
                {
                    final SoundEvent maleSoundEvent =
                      ModSoundEvents.getSoundID(CITIZEN_SOUND_EVENT_PREFIX + job.getPath() + ".male" + i + "." + event.getId());
                    final SoundEvent femaleSoundEvent =
                      ModSoundEvents.getSoundID(CITIZEN_SOUND_EVENT_PREFIX + job.getPath() + ".female" + i + "." + event.getId());

                    SOUND_EVENTS.register(maleSoundEvent.getLocation().getPath(), () -> maleSoundEvent);
                    SOUND_EVENTS.register(femaleSoundEvent.getLocation().getPath(), () -> femaleSoundEvent);
                    individualSounds.add(new Tuple<>(maleSoundEvent, femaleSoundEvent));
                }
                map.put(event, individualSounds);
            }
            CITIZEN_SOUND_EVENTS.put(job.getPath(), map);
        }

        final Map<EventType, List<Tuple<SoundEvent, SoundEvent>>> map = new HashMap<>();
        for (final EventType event : EventType.values())
        {
            final List<Tuple<SoundEvent, SoundEvent>> individualSounds = new ArrayList<>();
            for (int i = 1; i <= 2; i++)
            {
                final SoundEvent maleSoundEvent =
                        ModSoundEvents.getSoundID(CITIZEN_SOUND_EVENT_PREFIX + "child.male" + i + "." + event.getId());
                final SoundEvent femaleSoundEvent =
                        ModSoundEvents.getSoundID(CITIZEN_SOUND_EVENT_PREFIX + "child.female" + i + "." + event.getId());

                individualSounds.add(new Tuple<>(maleSoundEvent, femaleSoundEvent));
                individualSounds.add(new Tuple<>(maleSoundEvent, femaleSoundEvent));
            }
            map.put(event, individualSounds);
        }
        CITIZEN_SOUND_EVENTS.put("child", map);

    }

    /**
     * Register a {@link SoundEvent}.
     *
     * @param soundName The SoundEvent's name without the minecolonies prefix
     * @return The SoundEvent
     */
    public static SoundEvent getSoundID(final String soundName)
    {
        return SoundEvent.createVariableRangeEvent(ResourceLocation.parse(MCTradePostMod.MODID + ":" + soundName));
    }
}
