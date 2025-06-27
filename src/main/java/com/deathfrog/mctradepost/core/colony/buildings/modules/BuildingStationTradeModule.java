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

public class BuildingStationTradeModule extends AbstractBuildingModule implements IPersistentModule
{
    /**
     * Number of trades it can hold per level.
     */
    private static final int TRADES_PER_LEVEL = 3;

    /**
     * The trade tag tag.
     */
    private static final String TAG_TRADES = "trades";

    /**
     * The list of trades configured.
     */
    protected final Map<ItemStorage, Integer> tradeList = new HashMap<>();

    /**
     * Calculate the minimum stock size.
     *
     * @return the size.
     */
    private int numberOfTrades()
    {
        // TODO: Implement research to increase this.

        return (int) (building.getBuildingLevel() * TRADES_PER_LEVEL);
    }

    public void addTrade(final ItemStack itemStack, final int quantity)
    {
        if (tradeList.containsKey(new ItemStorage(itemStack)) || tradeList.size() < numberOfTrades())
        {
            tradeList.put(new ItemStorage(itemStack), quantity);
            markDirty();
        }
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

    public void removeTrade(final ItemStack itemStack)
    {
        tradeList.remove(new ItemStorage(itemStack));

        final Collection<IToken<?>> list = building.getOpenRequestsByRequestableType().getOrDefault(TypeToken.of(Stack.class), new ArrayList<>());
        final IToken<?> token = getMatchingRequest(itemStack, list);
        if (token != null)
        {
            building.getColony().getRequestManager().updateRequestState(token, RequestState.CANCELLED);
        }

        markDirty();
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        tradeList.clear();
        final ListTag minimumStockTagList = compound.getList(TAG_TRADES, Tag.TAG_COMPOUND);
        for (int i = 0; i < minimumStockTagList.size(); i++)
        {
            final CompoundTag compoundNBT = minimumStockTagList.getCompound(i);
            tradeList.put(new ItemStorage(ItemStack.parseOptional(provider, compoundNBT.getCompound(NbtTagConstants.STACK))), compoundNBT.getInt(TAG_QUANTITY));
        }
    }

    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, CompoundTag compound)
    {
        @NotNull final ListTag minimumStockTagList = new ListTag();
        for (@NotNull final Map.Entry<ItemStorage, Integer> entry : tradeList.entrySet())
        {
            final CompoundTag compoundNBT = new CompoundTag();
            compoundNBT.put(NbtTagConstants.STACK, entry.getKey().getItemStack().saveOptional(provider));
            compoundNBT.putInt(TAG_QUANTITY, entry.getValue());
            minimumStockTagList.add(compoundNBT);
        }
        compound.put(TAG_TRADES, minimumStockTagList);
    }

    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf)
    {
        buf.writeInt(tradeList.size());
        for (final Map.Entry<ItemStorage, Integer> entry : tradeList.entrySet())
        {
            Utils.serializeCodecMess(buf, entry.getKey().getItemStack());
            buf.writeInt(entry.getValue());
        }
        buf.writeBoolean(tradeList.size() >= numberOfTrades());
    }
}
