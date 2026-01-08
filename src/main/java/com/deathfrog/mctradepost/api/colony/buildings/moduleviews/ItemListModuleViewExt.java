package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.colony.buildings.moduleviews.ItemListModuleView;

import net.minecraft.network.chat.Component;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public abstract class ItemListModuleViewExt extends ItemListModuleView
{
    private final AtomicReference<Function<IBuildingView, Set<ItemStorage>>> allItemsRef;

    protected ItemListModuleViewExt(
        final String id,
        final Component desc,
        final boolean inverted,
        final AtomicReference<Function<IBuildingView, Set<ItemStorage>>> ref)
    {
        super(id, desc, inverted, (view) ->
        {
            final Function<IBuildingView, Set<ItemStorage>> fn = ref.get();
            if (fn == null)
            {
                throw new IllegalStateException("allItems accessed before initialization for module: " + id);
            }
            return fn.apply(view);
        });

        this.allItemsRef = ref;

        // Now it's safe to bind to 'this'
        ref.set(this::computeAllItems);
    }

    protected AtomicReference<Function<IBuildingView, Set<ItemStorage>>> getAllItemsRef()
    {
        return allItemsRef;
    }

    protected abstract Set<ItemStorage> computeAllItems(final IBuildingView buildingView);
}
