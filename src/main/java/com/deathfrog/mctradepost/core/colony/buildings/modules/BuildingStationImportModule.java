package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.buildings.modules.*;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.Utils;
import com.minecolonies.api.util.constant.NbtTagConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_QUANTITY;

public class BuildingStationImportModule extends AbstractBuildingModule implements IPersistentModule
{
    /**
     * Number of trades it can hold per level.
     */
    private static final int TRADES_PER_LEVEL = 3;

    /**
     * The import tag name.
     */
    private static final String TAG_IMPORTS = "imports";

    /**
     * The list of trades configured.
     */
    protected final Map<ItemStorage, Integer> importMap = new HashMap<>();

    /**
     * Calculate the minimum stock size.
     *
     * @return the size.
     */
    private int allowedTrades()
    {
        // TODO: Implement research to increase this.

        return (int) (building.getBuildingLevel() * TRADES_PER_LEVEL);
    }

    /**
     * Adds a trade to the list of imports if the list is not full, or if the item is already in the list.
     *
     * @param itemStack the itemstack to add.
     * @param quantity  the quantity to add.
     */
    public void addImport(final ItemStack itemStack, final int quantity)
    {
        if (importMap.containsKey(new ItemStorage(itemStack)) || importMap.size() < allowedTrades())
        {
            importMap.put(new ItemStorage(itemStack), quantity);
            markDirty();
        }
    }

    /**
     * Removes a trade associated with the given ItemStack from the trade list.
     * If there is an open request matching the itemStack, it gets cancelled.
     *
     * @param itemStack The ItemStack representing the trade to be removed.
     */
    public void removeImport(final ItemStack itemStack)
    {
        importMap.remove(new ItemStorage(itemStack));

        final Collection<IToken<?>> list = building.getOpenRequestsByRequestableType().getOrDefault(TypeToken.of(Stack.class), new ArrayList<>());
        final IToken<?> token = getMatchingRequest(itemStack, list);
        if (token != null)
        {
            building.getColony().getRequestManager().updateRequestState(token, RequestState.CANCELLED);
        }

        markDirty();
    }

    /**
     * The current number of trades configured in this module.
     *
     * @return the number of trades.
     */
    public int currentImportCount() 
    { 
        return importMap.size(); 
    }

    
    /**
     * Get the request from the list that matches this stack.
     * @param stack the stack to search for in the requests.
     * @param list the list of requests.
     * @return the token of the matching request or null.
     */
    private IToken<?> getMatchingRequest(final ItemStack stack, final Collection<IToken<?>> list)
    {
        for (final IToken<?> token : list)
        {
            final IRequest<?> iRequest = building.getColony().getRequestManager().getRequestForToken(token);
            if (iRequest != null && iRequest.getRequest() instanceof Stack && ItemStackUtils.compareItemStacksIgnoreStackSize(((Stack) iRequest.getRequest()).getStack(), stack))
            {
                return token;
            }
        }
        return null;
    }

    /**
     * Deserializes the NBT data for the trade list, restoring its state from the
     * provided CompoundTag.
     *
     * @param provider The holder lookup provider for item and block references.
     * @param compound The CompoundTag containing the serialized state of the
     *                 trade list.
     */
    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        importMap.clear();
        final ListTag importTagList = compound.getList(TAG_IMPORTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < importTagList.size(); i++)
        {
            final CompoundTag compoundNBT = importTagList.getCompound(i);
            importMap.put(new ItemStorage(ItemStack.parseOptional(provider, compoundNBT.getCompound(NbtTagConstants.STACK))), compoundNBT.getInt(TAG_QUANTITY));
        }
    }

    /**
     * Serializes the NBT data for the trade list, storing its state in the
     * provided CompoundTag.
     *
     * @param provider The holder lookup provider for item and block references.
     * @param compound The CompoundTag containing the serialized state of the
     *                 trade list.
     */
    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, CompoundTag compound)
    {
        @NotNull final ListTag importTagList = new ListTag();
        for (@NotNull final Map.Entry<ItemStorage, Integer> entry : importMap.entrySet())
        {
            final CompoundTag compoundNBT = new CompoundTag();
            compoundNBT.put(NbtTagConstants.STACK, entry.getKey().getItemStack().saveOptional(provider));
            compoundNBT.putInt(TAG_QUANTITY, entry.getValue());
            importTagList.add(compoundNBT);
        }
        compound.put(TAG_IMPORTS, importTagList);
    }

    /**
     * Serializes the trade list to the given RegistryFriendlyByteBuf for
     * transmission to the client. 
     *
     * @param buf the buffer to serialize the trade list to.
     */
    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf)
    {
        buf.writeInt(importMap.size());
        for (final Map.Entry<ItemStorage, Integer> entry : importMap.entrySet())
        {
            Utils.serializeCodecMess(buf, entry.getKey().getItemStack());
            buf.writeInt(entry.getValue());
        }
        buf.writeBoolean(importMap.size() >= allowedTrades());
    }
    
    /**
     * Checks if the given itemStack at a specified cost are part of this modules import list.
     *
     * @param stack the itemStack to check.
     * @param cost  the cost to check.
     * @return true if the itemStack and cost are in the trade list, false otherwise.
     */
    public boolean hasTrade(ItemStack stack, int cost)
    {
        return importMap.containsKey(new ItemStorage(stack)) && importMap.get(new ItemStorage(stack)) == cost;
    }
}
