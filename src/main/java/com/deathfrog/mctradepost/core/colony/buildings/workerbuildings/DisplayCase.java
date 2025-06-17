package com.deathfrog.mctradepost.core.colony.buildings.workerbuildings;

import java.util.UUID;

import com.deathfrog.mctradepost.item.SouvenirItem;
import com.minecolonies.api.util.NBTUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;

public class DisplayCase 
{
    public enum SaleState
    {
        NONE,
        FOR_SALE,
        ORDER_PLACED,
        ORDER_FULFILLED
    }

    private BlockPos pos = null;
    private ItemStack stack = ItemStack.EMPTY;
    private int tickcount = -1;
    private UUID frameId = null;
    private SaleState saleState = SaleState.NONE;

    public DisplayCase(BlockPos pos, UUID frameId) 
    {
        this.pos = pos;
        this.frameId = frameId;
        saleState = SaleState.NONE;
    }
    
    public DisplayCase(BlockPos pos, UUID frameId, ItemStack stack, int tickcount) 
    {
        this.pos = pos;
        this.frameId = frameId;
        this.stack = stack; 
        this.tickcount = tickcount;
        saleState = SaleState.NONE;
    }

    public BlockPos getPos() 
    {
        return pos;
    }

    public ItemStack getStack() 
    {
        return stack;
    }

    public void setStack(ItemStack stack) 
    {
        this.stack = stack;
    }

    public void setPos(BlockPos pos) 
    {
        this.pos = pos;
    }

    public int getTickcount() 
    {
        return tickcount;
    }

    public void setTickcount(int tickcount) 
    {
        this.tickcount = tickcount;
    }

    public void setFrameId(UUID frameId) 
    {
        this.frameId = frameId;
    }

    public UUID getFrameId() 
    {
        return frameId;
    }

    public void setSaleState(SaleState saleState) 
    {
        this.saleState = saleState;
    }

    public SaleState getSaleState() 
    {
        return saleState;
    }

    /**
     * Serializes the current state of the DisplayCase into an NBT tag.
     *
     * @param provider The holder lookup provider for saving the item stack.
     * @return A CompoundTag representing the serialized state of the DisplayCase, including
     *         position, item contents, tick count, and frame ID if present.
     */

    public CompoundTag toNBT(HolderLookup.Provider provider) 
    {
        CompoundTag tag = new CompoundTag();
        tag.put("Pos", NBTUtils.writeBlockPos(pos));
        if (!stack.isEmpty()) 
        {
            tag.put("Contents", stack.save(provider));
        }
        tag.putInt("TickCount", tickcount);
        if (frameId != null) 
        {
            tag.putLong("FrameIdMost", frameId.getMostSignificantBits());
            tag.putLong("FrameIdLeast", frameId.getLeastSignificantBits());
        }
        tag.putInt("SaleState", saleState.ordinal());
        return tag;
    }

    /**
     * Reads a display case from an NBT tag.
     *
     * @param tag The tag to read from.
     * @param provider The holder lookup provider.
     * @return The display case read from the tag.
     */
    public static DisplayCase fromNBT(CompoundTag tag, HolderLookup.Provider provider) 
    {
        BlockPos pos = NBTUtils.readBlockPos(tag.get("Pos"));
        ItemStack stack = ItemStack.CODEC.parse(NbtOps.INSTANCE, tag.get("Contents"))
                            .result()
                            .orElse(ItemStack.EMPTY);
        int tickCount = tag.getInt("TickCount");
        UUID frameID = null;
        if (tag.contains("FrameIdMost") && tag.contains("FrameIdLeast")) {
            frameID = new UUID(tag.getLong("FrameIdMost"), tag.getLong("FrameIdLeast"));
        }
        DisplayCase fromCase = new DisplayCase(pos, frameID, stack, tickCount);
        int order = tag.getInt("SaleState");

        if (order <= 0 || order >= SaleState.values().length) 
        {
            if (!stack.isEmpty())
            {
                fromCase.setSaleState(SaleState.FOR_SALE);
            }
            else
            {   
                fromCase.setSaleState(SaleState.NONE);
            }
        } 
        else 
        {
            fromCase.setSaleState(SaleState.values()[order]);
        }

        return fromCase;
    }

    @Override
    public String toString()
    {
        return String.format("DisplayCase at %s\n- Item: %s\n- Ticks: %d\n- SaleState: %s",
                pos,
                stack.isEmpty() ? "empty" : stack.toString() + (stack.getItem() instanceof SouvenirItem ? SouvenirItem.toString(stack) : " <not a souvenir>"), 
                tickcount,
                saleState);
    }

}

