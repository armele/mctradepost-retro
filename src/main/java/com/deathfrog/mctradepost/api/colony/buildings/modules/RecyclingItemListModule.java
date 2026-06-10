package com.deathfrog.mctradepost.api.colony.buildings.modules;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.colony.requestsystem.token.StandardToken;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.colony.buildings.modules.ItemListModule;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import com.minecolonies.api.util.Utils;
import java.util.UUID;

public class RecyclingItemListModule extends ItemListModule
{
    private static final String TAG_LEGACY_PENDING_LIST = "pendingList";
    private static final String TAG_PENDING_WAREHOUSE_REQUESTS = "pendingWarehouseRequests";
    private static final String TAG_ACCEPTED_RECYCLING_INPUTS = "acceptedRecyclingInputs";
    private static final String TAG_TOKEN = "token";
    private static final String TAG_TOKEN_MSB = "msb";
    private static final String TAG_TOKEN_LSB = "lsb";
    private static final String TAG_ITEM = "item";

    /*
     * Items requested from the warehouse. A null token is only used for
     * entries migrated from the legacy pendingList format.
     */
    private final ArrayList<PendingWarehouseRequest> pendingWarehouseRequests = new ArrayList<>();

    /*
     * Items that have arrived from a warehouse request and are now in the
     * recycler's custody, but have not yet been inserted into a processor.
     */
    private final ArrayList<ItemStorage> acceptedRecyclingInputs = new ArrayList<>();

    public RecyclingItemListModule(String id)
    {
        super(id);
    }

    /**
     * Tracks an item request that has been submitted to the MineColonies request system and should still be expected from warehouse
     * delivery.
     *
     * @param token The request token, or null for entries migrated from the old pendingList save format.
     * @param item  The requested recyclable item.
     */
    public record PendingWarehouseRequest(@Nullable IToken<?> token, ItemStorage item)
    {
    }

    /**
     * Returns the live warehouse requests that should suppress duplicate recyclable requests.
     *
     * @return mutable list of pending warehouse requests.
     */
    public List<PendingWarehouseRequest> getPendingWarehouseRequests()
    {
        return pendingWarehouseRequests;
    }

    /**
     * Returns delivered recyclables that are in recycler custody but have not yet entered a recycling processor.
     *
     * @return mutable list of accepted recycling inputs.
     */
    public List<ItemStorage> getAcceptedRecyclingInputs()
    {
        return acceptedRecyclingInputs;
    }

    /**
     * Checks whether the given item already has a live warehouse request.
     *
     * @param item The item to check.
     * @return true if a matching pending warehouse request exists.
     */
    public boolean hasPendingWarehouseRequest(ItemStorage item)
    {
        return findPendingWarehouseRequest(item) != null;
    }

    /**
     * Finds the pending warehouse request matching the given item.
     *
     * @param item The item to match.
     * @return the matching request, or null if no request is pending.
     */
    public @Nullable PendingWarehouseRequest findPendingWarehouseRequest(ItemStorage item)
    {
        for (PendingWarehouseRequest request : pendingWarehouseRequests)
        {
            if (request.item().equals(item))
            {
                return request;
            }
        }

        return null;
    }

    /**
     * Adds a warehouse request to the pending request list.
     *
     * @param token The request token returned by the request system, or null for legacy migrated entries.
     * @param item  The requested item.
     */
    public void addPendingWarehouseRequest(@Nullable IToken<?> token, ItemStorage item)
    {
        if (item == null || item.isEmpty())
        {
            return;
        }

        if (token != null)
        {
            pendingWarehouseRequests.removeIf(request -> token.equals(request.token()));
        }

        pendingWarehouseRequests.add(new PendingWarehouseRequest(token, item.copy()));
    }

    /**
     * Removes a pending warehouse request after delivery, cancellation, or stale-request reconciliation.
     *
     * @param request The request entry to remove.
     */
    public void removePendingWarehouseRequest(PendingWarehouseRequest request)
    {
        pendingWarehouseRequests.remove(request);
    }

    /**
     * Removes all pending warehouse requests matching the given item.
     *
     * @param item The item whose pending requests should be removed.
     */
    public void removePendingWarehouseRequestFor(ItemStorage item)
    {
        pendingWarehouseRequests.removeIf(request -> request.item().equals(item));
    }

    /**
     * Marks a delivered recyclable as accepted into recycler custody.
     *
     * @param item The delivered item now held by the worker, building, or input path.
     */
    public void addAcceptedRecyclingInput(ItemStorage item)
    {
        if (item == null || item.isEmpty())
        {
            return;
        }

        acceptedRecyclingInputs.add(item.copy());
    }

    /**
     * Checks whether a matching item has been delivered and accepted into recycler custody.
     *
     * @param item The item to check.
     * @return true if a matching accepted input exists.
     */
    public boolean hasAcceptedRecyclingInput(ItemStorage item)
    {
        return findAcceptedRecyclingInput(item) != null;
    }

    /**
     * Finds an accepted recycling input matching the given item.
     *
     * @param item The item to match.
     * @return the accepted input, or null if none exists.
     */
    public @Nullable ItemStorage findAcceptedRecyclingInput(ItemStorage item)
    {
        for (ItemStorage accepted : acceptedRecyclingInputs)
        {
            if (accepted.equals(item))
            {
                return accepted;
            }
        }

        return null;
    }

    /**
     * Removes one matching accepted input after it has entered a recycling processor.
     *
     * @param item The accepted input to remove.
     */
    public void removeAcceptedRecyclingInput(ItemStorage item)
    {
        ItemStorage accepted = findAcceptedRecyclingInput(item);
        if (accepted != null)
        {
            acceptedRecyclingInputs.remove(accepted);
        }
    }

    /**
     * Deserializes pending warehouse requests and accepted recycling inputs from NBT.
     *
     * @param provider The provider to use for looking up holders.
     * @param compound The compound tag containing the serialized state.
     */
    @Override
    public void deserializeNBT(@NotNull HolderLookup.@NotNull Provider provider, CompoundTag compound)
    {
        super.deserializeNBT(provider, compound);

        final HolderLookup.Provider nnProvider = Objects.requireNonNull(provider, "provider");

        pendingWarehouseRequests.clear();
        acceptedRecyclingInputs.clear();

        ListTag pendingRequests = compound.getList(TAG_PENDING_WAREHOUSE_REQUESTS, 10);
        for (int i = 0; i < pendingRequests.size(); ++i)
        {
            CompoundTag listItem = pendingRequests.getCompound(i);

            if (listItem == null || listItem.isEmpty() || !listItem.contains(TAG_ITEM))
            {
                continue;
            }

            IToken<?> token = null;
            if (listItem.contains(TAG_TOKEN))
            {
                CompoundTag tokenTag = listItem.getCompound(TAG_TOKEN);
                token = new StandardToken(new UUID(tokenTag.getLong(TAG_TOKEN_MSB), tokenTag.getLong(TAG_TOKEN_LSB)));
            }

            pendingWarehouseRequests.add(new PendingWarehouseRequest(token, new ItemStorage(ItemStack.parseOptional(nnProvider, listItem.getCompound(TAG_ITEM)))));
        }

        ListTag acceptedInputs = compound.getList(TAG_ACCEPTED_RECYCLING_INPUTS, 10);
        for (int i = 0; i < acceptedInputs.size(); ++i)
        {
            CompoundTag listItem = acceptedInputs.getCompound(i);

            if (listItem == null || listItem.isEmpty())
            {
                continue;
            }

            acceptedRecyclingInputs.add(new ItemStorage(ItemStack.parseOptional(nnProvider, listItem)));
        }

        // Migrate older saves. These entries have no request token, so the
        // reconciliation pass will clear them once no matching request exists.
        ListTag filterableList = compound.getList(TAG_LEGACY_PENDING_LIST, 10);

        for (int i = 0; i < filterableList.size(); ++i)
        {
            CompoundTag listItem = filterableList.getCompound(i);

            if (listItem == null || listItem.isEmpty())
            {
                continue;
            }
            
            pendingWarehouseRequests.add(new PendingWarehouseRequest(null, new ItemStorage(ItemStack.parseOptional(nnProvider, listItem))));
        }
    }

    /**
     * Serializes pending warehouse requests and accepted recycling inputs to NBT.
     *
     * @param provider The provider to use for looking up holders.
     * @param compound The compound tag to which the state should be serialized.
     */
    @SuppressWarnings("null")
    public void serializeNBT(@NotNull HolderLookup.Provider provider, CompoundTag compound)
    {
        super.serializeNBT(provider, compound);

        ListTag pendingRequests = new ListTag();
        Iterator<PendingWarehouseRequest> pendingIterator = this.pendingWarehouseRequests.iterator();

        while (pendingIterator.hasNext())
        {
            PendingWarehouseRequest request = pendingIterator.next();
            CompoundTag requestTag = new CompoundTag();

            if (request.token() != null)
            {
                CompoundTag tokenTag = new CompoundTag();
                Object identifier = request.token().getIdentifier();
                if (identifier instanceof UUID uuid)
                {
                    tokenTag.putLong(TAG_TOKEN_MSB, uuid.getMostSignificantBits());
                    tokenTag.putLong(TAG_TOKEN_LSB, uuid.getLeastSignificantBits());
                    requestTag.put(TAG_TOKEN, tokenTag);
                }
            }

            requestTag.put(TAG_ITEM, request.item().getItemStack().saveOptional(provider));
            pendingRequests.add(requestTag);
        }

        compound.put(TAG_PENDING_WAREHOUSE_REQUESTS, pendingRequests);

        ListTag acceptedItems = new ListTag();
        Iterator<ItemStorage> acceptedIterator = this.acceptedRecyclingInputs.iterator();

        while (acceptedIterator.hasNext())
        {
            ItemStorage item = acceptedIterator.next();
            acceptedItems.add(item.getItemStack().saveOptional(provider));
        }

        compound.put(TAG_ACCEPTED_RECYCLING_INPUTS, acceptedItems);
    }

    /**
     * Serializes the legacy client view of pending warehouse items to the given buffer. Accepted inputs are intentionally server-only
     * to keep the view packet compatible with existing clients.
     *
     * @param buf The buffer to serialize the pending item projection into.
     */
    public void serializeToView(@NotNull RegistryFriendlyByteBuf buf)
    {
        super.serializeToView(buf);
        buf.writeInt(this.pendingWarehouseRequests.size());
        Iterator<PendingWarehouseRequest> pendingIterator = this.pendingWarehouseRequests.iterator();

        while (pendingIterator.hasNext())
        {
            PendingWarehouseRequest request = pendingIterator.next();
            Utils.serializeCodecMess(buf, request.item().getItemStack());
        }
    }
}
