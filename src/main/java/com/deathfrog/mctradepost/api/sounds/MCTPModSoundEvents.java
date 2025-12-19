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
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.*;

import javax.annotation.Nonnull;

/**
 * Registering of sound events for our colony.
 */
public final class MCTPModSoundEvents
{
    public static final @Nonnull SoundEvent CASH_REGISTER = MCTPModSoundEvents.getSoundID("environment.cash_register");

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(NullnessBridge.assumeNonnull(Registries.SOUND_EVENT), MCTradePostMod.MODID);
    public static Map<String, Map<EventType, List<Tuple<SoundEvent, SoundEvent>>>> MCTP_CITIZEN_SOUND_EVENTS = new HashMap<>();     // These will be injected into MineColonies' CITIZEN_SOUND_EVENTS

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
     * Register the {@link SoundEvent}s.  
     * Note that this implementation adds the sound events to the MineColonies list of CITIZEN_SOUND_EVENTS as well.
     * Not preferable, but required.
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
                    // MCTradePostMod.LOGGER.info("Registering sound event: " + ModSoundEvents.CITIZEN_SOUND_EVENT_PREFIX + job.getPath() + ".genderplaceholder." + event.getId());

                    final SoundEvent maleSoundEvent =
                      MCTPModSoundEvents.getSoundID(ModSoundEvents.CITIZEN_SOUND_EVENT_PREFIX + job.getPath() + ".male" + i + "." + event.getId());
                    final SoundEvent femaleSoundEvent =
                      MCTPModSoundEvents.getSoundID(ModSoundEvents.CITIZEN_SOUND_EVENT_PREFIX + job.getPath() + ".female" + i + "." + event.getId());

                    String maleSoundPath = maleSoundEvent.getLocation().getPath();

                    if (maleSoundPath != null)
                    {
                        SOUND_EVENTS.register(maleSoundPath, () -> maleSoundEvent); 
                    }

                    String femaleSoundPath = femaleSoundEvent.getLocation().getPath();

                    if (femaleSoundPath != null)
                    {
                        SOUND_EVENTS.register(femaleSoundPath, () -> maleSoundEvent); 
                    }

                    individualSounds.add(new Tuple<>(maleSoundEvent, femaleSoundEvent));
                }
                map.put(event, individualSounds);
            }
            MCTP_CITIZEN_SOUND_EVENTS.put(job.getPath(), map);
        }
    }

    /**
     * Register a {@link SoundEvent}.
     *
     * @param soundName The SoundEvent's name without the minecolonies prefix
     * @return The SoundEvent
     */
    public static @Nonnull SoundEvent getSoundID(final @Nonnull String soundName)
    {
        final ResourceLocation location = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, soundName);

        if (location == null)
        {
            throw new IllegalArgumentException("No resource location found for sound name: " + soundName);
        }

        SoundEvent sound = SoundEvent.createVariableRangeEvent(location);

        if (sound == null)
        {
            throw new IllegalArgumentException("No sound event found for sound: " + location.toString());
        }

        return sound;
    }

    /**
     * Injects the citizen sound events from MCTradePost into MineColonies' CITIZEN_SOUND_EVENTS.
     * This is a temporary solution until sounds in MineColonies have the flexibility to look up sound events from other modpacks.
     */
    public static void injectSounds() {
        if (MCTP_CITIZEN_SOUND_EVENTS.isEmpty()) {
            MCTradePostMod.LOGGER.info("There are no sounds to inject.");
        } else {
            int size = MCTP_CITIZEN_SOUND_EVENTS.size();
            MCTradePostMod.LOGGER.info("Injecting {} sound events.", size);
            ModSoundEvents.CITIZEN_SOUND_EVENTS.putAll(MCTP_CITIZEN_SOUND_EVENTS);
        }
    }
}
