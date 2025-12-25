package com.deathfrog.mctradepost.core.colony.buildings.modules;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.blocks.StewpotBlock;
import com.ldtteam.structurize.api.BlockPosUtil;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.Utils;
import com.minecolonies.api.util.constant.NbtTagConstants;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingKitchen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class StewmelierIngredientModule extends AbstractBuildingModule implements IPersistentModule 
{
    /**
     * NBT tags for the ingredient list.
     */
    private static final String TAG_INGREDIENTS = "ingredients";
    private static final String TAG_PROTECTED_QUANTITY = "protectedQuantity";
    private static final String TAG_STEWPOT_LOCATION = "stewpotLocation";
    private static final String TAG_STEW_QUANTITY = "stewQuantity";
    private static final String TAG_SEASONING_LEVEL = "seasoningLevel";

    private static final float STEW_SEASONING_LEVEL = 8.0f;

    public static final int STEW_EMPTY = 0;
    public static final int STEW_LEVEL_1 = 25;
    public static final int STEW_LEVEL_2 = 50;

    protected BlockPos stewpotLocation = BlockPos.ZERO;
    protected float unseasonedQuantity = 0.0f;
    protected float stewQuantity = 0.0f;
    protected int seasoningLevel = 0;
    
    protected Set<ItemStorage> ingredientSet = new HashSet<>();

    public StewmelierIngredientModule() 
    {
        // Constructor implementation
    }

    public Set<ItemStorage> getIngredients() 
    {
        return ingredientSet;
    }

    public int ingredientCount() 
    {
        return ingredientSet.size();
    }

    public BlockPos getStewpotLocation() 
    {
        return stewpotLocation;
    }

    public void setStewpotLocation(BlockPos location) 
    {
        stewpotLocation = location;
        markDirty();
    }

    public float getStewQuantity() 
    {
        return stewQuantity;
    }

    public void setStewQuantity(float quantity) 
    {
        float oldQuantity = stewQuantity;
        stewQuantity = quantity;

        useSeasoning(stewQuantity - oldQuantity);

        markDirty();
    }

    public void addStew(float adjustBy) 
    {
        useSeasoning(adjustBy);
        stewQuantity += adjustBy;
        markDirty();
    }

    /**
     * Adjusts the amount of stewing in the stewpot by the given amount.
     * If the amount of stewing exceeds the seasoning level, it will
     * decrement the seasoning level and subtract the seasoning level from
     * the amount of stewing.
     * 
     * @param stewAmount the amount of stewing to add
     */
    public void useSeasoning(float stewAmount)
    {
        if (stewAmount > 0)
        {
            unseasonedQuantity = unseasonedQuantity + stewAmount;

            if (unseasonedQuantity >= STEW_SEASONING_LEVEL)
            {
                seasoningLevel--;
                unseasonedQuantity -= STEW_SEASONING_LEVEL;
            }
        }

        markDirty();
    }

    public int getSeasoningLevel()
    {
        return seasoningLevel;
    }

    public void setSeasoningLevel(int level)
    {
        seasoningLevel = level;
        markDirty();
    }

    /**
     * Adds an ingredient to the list of ingredients in the module.
     * 
     * @param ingredient the ingredient to add.
     */
    public void addIngredient(ItemStorage ingredient) 
    {
        ingredientSet.add(ingredient);
        markDirty();
    }

    /**
     * Removes an ingredient from the list of ingredients in the module.
     * 
     * @param ingredient the ingredient to remove.
     */
    public void removeIngredient(ItemStorage ingredient) 
    {
        ingredientSet.remove(ingredient);
        markDirty();
    }


    /**
     * Marks the module as dirty and updates the stew level.
     * This method is called when the module's state changes in a way that should be reflected on the client.
     * It will call the parent class's markDirty method, then update the stew level based on the current stew quantity.
     * If the stew quantity is greater than 50, the stew level will be set to 3.
     * If the stew quantity is greater than 25, the stew level will be set to 2.
     * If the stew quantity is greater than 0, the stew level will be set to 1.
     * If the stew quantity is 0 or less, the stew level will be set to 0.
     */
    @Override
    public void markDirty()
    {
        super.markDirty();

        Level level = building.getColony().getWorld();
        BlockPos stewPos = stewpotLocation;

        if (level == null || level.isClientSide || stewPos == null) 
        {
            return;
        }
        
        int stewLevel = 0;

        if (stewQuantity > STEW_LEVEL_2)
        {
            stewLevel = 3;
        }
        else if (stewQuantity > STEW_LEVEL_1)
        {
            stewLevel = 2;
        }
        else if (stewQuantity > STEW_EMPTY)
        {
            stewLevel = 1;
        }

        setStewLevel(level, stewPos, stewLevel);
    }

    /**
     * Sets the level of a stewpot block at the given position in the given level.
     * If the block at the given position is not a StewpotBlock, this method does nothing.
     * 
     * @param level the level to modify.
     * @param pos the position of the block to modify.
     * @param newLevel the new level of the stewpot block.
     */
    @SuppressWarnings("null")
    public static void setStewLevel(
        final @Nonnull Level level,
        final @Nonnull BlockPos pos,
        final int newStewLevel)
    {
        BlockState state = level.getBlockState(pos);
        final int clamped = Mth.clamp(newStewLevel, 0, 3);

        // If target is empty, always end as a vanilla cauldron.
        if (clamped == 0)
        {
            if (!state.is(Blocks.CAULDRON))
            {
                // Only swap if currently our filled stewpot (or something else you choose to normalize).
                if (state.is(MCTradePostMod.STEWPOT_FILLED.get()) || state.getBlock() instanceof StewpotBlock)
                {
                    level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 2 | 16);
                }
            }
            return;
        }

        // clamped is 1..3 here.
        // Ensure we're using the filled stewpot block.
        if (state.is(Blocks.CAULDRON))
        {
            state = MCTradePostMod.STEWPOT_FILLED.get()
                .defaultBlockState()
                .setValue(StewpotBlock.LEVEL, clamped);

            level.setBlock(pos, state, 2 | 16);
            return;
        }

        // If it's not our filled stewpot, do nothing (avoid mutating other cauldron-like blocks).
        if (!state.is(MCTradePostMod.STEWPOT_FILLED.get()) && !(state.getBlock() instanceof StewpotBlock))
        {
            return;
        }

        // Update existing filled stewpot level.
        final BlockState updated = state.setValue(StewpotBlock.LEVEL, clamped);
        if (updated != state)
        {
            level.setBlock(pos, updated, 2 | 16);
        }
    }


    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        ingredientSet.clear();
        final ListTag ingredientTagList = compound.getList(TAG_INGREDIENTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < ingredientTagList.size(); i++)
        {
            final CompoundTag compoundNBT = ingredientTagList.getCompound(i);
            final CompoundTag ingredientItemTag = compoundNBT.getCompound(NbtTagConstants.STACK);

            if (ingredientItemTag.isEmpty())
            {
                continue;
            }

            int protectedQuantity = compoundNBT.getInt(TAG_PROTECTED_QUANTITY);
            ItemStorage tradeItem = new ItemStorage(ItemStack.parseOptional(NullnessBridge.assumeNonnull(provider), ingredientItemTag));
            tradeItem.setAmount(protectedQuantity);

            ingredientSet.add(tradeItem);
        }
        stewpotLocation = BlockPosUtil.readFromNBT(compound, TAG_STEWPOT_LOCATION);
        stewQuantity = compound.getFloat(TAG_STEW_QUANTITY);
        seasoningLevel = compound.getInt(TAG_SEASONING_LEVEL);
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
        @NotNull final ListTag ingredientTagList = new ListTag();
        for (ItemStorage ingredient : ingredientSet)
        {
            final CompoundTag compoundNBT = new CompoundTag();
            final Tag storedItem = ingredient.getItemStack().saveOptional(NullnessBridge.assumeNonnull(provider));
            compoundNBT.put(NbtTagConstants.STACK, NullnessBridge.assumeNonnull(storedItem));
            compoundNBT.putInt(TAG_PROTECTED_QUANTITY, ingredient.getAmount());
            ingredientTagList.add(compoundNBT);
        }
        BlockPosUtil.writeToNBT(compound, TAG_STEWPOT_LOCATION, stewpotLocation);
        compound.put(TAG_INGREDIENTS, ingredientTagList);
        compound.putFloat(TAG_STEW_QUANTITY, stewQuantity);
        compound.putInt(TAG_SEASONING_LEVEL, seasoningLevel);
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
        // Sort ingredients by display name (case-insensitive, locale-safe)
        final List<ItemStorage> sorted =
            ingredientSet.stream()
                .sorted(Comparator.comparing(
                    ingredient -> ingredient.getItemStack().getHoverName().getString(),
                    String.CASE_INSENSITIVE_ORDER
                ))
                .toList();

        buf.writeInt(sorted.size());
        for (ItemStorage ingredient : sorted)
        {
            Utils.serializeCodecMess(buf, ingredient.getItemStack());
            buf.writeInt(ingredient.getAmount());
        }
    }


    /**
     * Finds the closest kitchen to the given cauldron position.
     *
     * @param level the level containing the cauldron.
     * @param cauldronPos the position of the cauldron.
     * @return the kitchen building if found, null otherwise.
     */
    public static StewmelierIngredientModule kitchenFromCauldronPosition(@Nonnull Level level, final @Nonnull BlockPos cauldronPos)
    {
        IColony colony = IColonyManager.getInstance().getClosestColony(level, cauldronPos);
        if (colony == null) return null;

        for (IBuilding building : colony.getBuildingManager().getBuildings().values())
        {
            if (building instanceof BuildingKitchen)
            {
                StewmelierIngredientModule module = building.getModule(MCTPBuildingModules.STEWMELIER_INGREDIENTS);

                if (module != null && module.getStewpotLocation().equals(cauldronPos))
                {
                    return module;
                }
            }
        }

        return null;
    }
}
