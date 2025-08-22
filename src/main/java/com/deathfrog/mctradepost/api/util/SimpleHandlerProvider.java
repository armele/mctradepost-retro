package com.deathfrog.mctradepost.api.util;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import com.minecolonies.api.util.IItemHandlerCapProvider;

// --- Minimal adapter to your IItemHandlerCapProvider type ---
public final class SimpleHandlerProvider implements IItemHandlerCapProvider
{
    private final IItemHandler handler;

    private SimpleHandlerProvider(IItemHandler handler)
    {
        this.handler = handler;
    }

    @Override
    public IItemHandler getItemHandlerCap()
    {
        return handler;
    }

    public static IItemHandlerCapProvider of(IItemHandler h)
    {
        return new SimpleHandlerProvider(h);
    }

    @Override
    public @Nullable IItemHandler getItemHandlerCap(@Nullable Direction side) {
        // This provider isn't side-sensitive; always return the same handler.
        return handler;
    }

}
