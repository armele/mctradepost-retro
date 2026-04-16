package com.deathfrog.mctradepost.compat.recycling;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central registry for optional recycling providers. Providers should be kept
 * small and runtime-safe so they can be consulted during normal recycler
 * operation without introducing hard dependencies. Third-party compatibility
 * mods may register their own providers during setup to extend Trade Post's
 * recycler without patching Trade Post itself.
 */
public final class OptionalRecyclingProviders
{
    private static final CopyOnWriteArrayList<IOptionalRecyclingProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    static
    {
        register(new SilentGearRecyclingProvider());
    }

    private OptionalRecyclingProviders()
    {
    }

    /**
     * Registers a new optional recycling provider. Providers are consulted in
     * registration order, so compatibility mods may choose to register early or
     * late depending on their desired precedence. Duplicate instance
     * registrations are ignored.
     *
     * @param provider the provider to register
     */
    public static void register(final IOptionalRecyclingProvider provider)
    {
        if (provider == null || PROVIDERS.contains(provider))
        {
            return;
        }

        PROVIDERS.add(provider);
    }

    /**
     * Returns a snapshot of the currently registered optional recycling
     * providers. Callers receive a defensive copy so external code cannot modify
     * the registry contents directly.
     *
     * @return the currently registered optional recycling providers
     */
    public static List<IOptionalRecyclingProvider> getProviders()
    {
        return List.copyOf(new ArrayList<>(PROVIDERS));
    }
}
