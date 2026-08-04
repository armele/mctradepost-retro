package com.deathfrog.mctradepost.api.sounds;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.jobs.MCTPModJobs;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.minecolonies.api.sounds.EventType;
import com.minecolonies.api.sounds.ModSoundEvents;
import com.minecolonies.api.util.Tuple;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.*;

/**
 * Registering of sound events for our colony.
 */
public final class MCTPModSoundEvents
{
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
        DeferredRegister.create(NullnessBridge.assumeNonnull(Registries.SOUND_EVENT), MCTradePostMod.MODID);
    public static final DeferredHolder<SoundEvent, SoundEvent> CASH_REGISTER =
        SOUND_EVENTS.register("environment.cash_register", SoundEvent::createVariableRangeEvent);

    private static final Map<String, Map<EventType, List<Tuple<DeferredHolder<SoundEvent, SoundEvent>, DeferredHolder<SoundEvent, SoundEvent>>>>> DEFERRED_CITIZEN_SOUND_EVENTS = new HashMap<>();

    /**
     * Private constructor to hide the implicit public one.
     */
    private MCTPModSoundEvents()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Register the {@link SoundEvent}s. Note that this implementation adds the sound events to the MineColonies list of
     * CITIZEN_SOUND_EVENTS as well. Not preferable, but required.
     *
     * @param registry the registry to register at.
     */
    static
    {
        final List<ResourceLocation> jobList = new ArrayList<>(MCTPModJobs.getJobs());

        registerSoundsForJobs(jobList, SOUND_EVENTS, DEFERRED_CITIZEN_SOUND_EVENTS);
    }

    /**
     * Registers the sound events for the given jobs.
     *
     * @param jobs a list of {@link ResourceLocation}s, which represent the jobs for which the sound events should be registered.
     */
    private static void registerSoundsForJobs(final List<ResourceLocation> jobs,
        final DeferredRegister<SoundEvent> soundEventRegister,
        final Map<String, Map<EventType, List<Tuple<DeferredHolder<SoundEvent, SoundEvent>, DeferredHolder<SoundEvent, SoundEvent>>>>> soundMap)
    {
        for (final ResourceLocation job : jobs)
        {
            final Map<EventType, List<Tuple<DeferredHolder<SoundEvent, SoundEvent>, DeferredHolder<SoundEvent, SoundEvent>>>> map = new HashMap<>();
            for (final EventType event : EventType.values())
            {
                final List<Tuple<DeferredHolder<SoundEvent, SoundEvent>, DeferredHolder<SoundEvent, SoundEvent>>> individualSounds = new ArrayList<>();
                for (int i = 1; i <= 4; i++)
                {
                    // MCTradePostMod.LOGGER.info("Registering sound event: " + ModSoundEvents.CITIZEN_SOUND_EVENT_PREFIX +
                    // job.getPath() + ".genderplaceholder." + event.getId());

                    final String maleSoundPath = ModSoundEvents.CITIZEN_SOUND_EVENT_PREFIX
                        + job.getPath() + ".male" + i + "." + event.getId();
                    final String femaleSoundPath = ModSoundEvents.CITIZEN_SOUND_EVENT_PREFIX
                        + job.getPath() + ".female" + i + "." + event.getId();
                    final DeferredHolder<SoundEvent, SoundEvent> maleSoundEvent =
                        soundEventRegister.register(maleSoundPath, SoundEvent::createVariableRangeEvent);
                    final DeferredHolder<SoundEvent, SoundEvent> femaleSoundEvent =
                        soundEventRegister.register(femaleSoundPath, SoundEvent::createVariableRangeEvent);

                    individualSounds.add(new Tuple<>(maleSoundEvent, femaleSoundEvent));
                }
                map.put(event, individualSounds);
            }
            soundMap.put(job.getPath(), map);
        }
    }

    /**
     * Injects the citizen sound events from MCTradePost into MineColonies' CITIZEN_SOUND_EVENTS. This is a temporary solution until
     * sounds in MineColonies have the flexibility to look up sound events from other modpacks.
     */
    public static void injectSounds()
    {
        if (DEFERRED_CITIZEN_SOUND_EVENTS.isEmpty())
        {
            MCTradePostMod.LOGGER.info("There are no sounds to inject.");
        }
        else
        {
            final Map<String, Map<EventType, List<Tuple<SoundEvent, SoundEvent>>>> citizenSoundEvents = new HashMap<>();
            DEFERRED_CITIZEN_SOUND_EVENTS.forEach((job, events) -> {
                final Map<EventType, List<Tuple<SoundEvent, SoundEvent>>> resolvedEvents = new HashMap<>();
                events.forEach((event, sounds) -> resolvedEvents.put(event, sounds.stream()
                    .map(sound -> new Tuple<>(sound.getA().get(), sound.getB().get()))
                    .toList()));
                citizenSoundEvents.put(job, resolvedEvents);
            });

            int size = citizenSoundEvents.size();
            MCTradePostMod.LOGGER.info("Injecting {} sound events.", size);
            ModSoundEvents.CITIZEN_SOUND_EVENTS.putAll(citizenSoundEvents);
        }
    }
}
