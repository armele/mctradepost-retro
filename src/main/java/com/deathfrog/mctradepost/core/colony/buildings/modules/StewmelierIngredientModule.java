package com.deathfrog.mctradepost.core.colony.buildings.modules;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.Utils;
import com.minecolonies.api.util.constant.NbtTagConstants;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public class StewmelierIngredientModule extends AbstractBuildingModule implements IPersistentModule 
{
    /**
     * NBT tags for the ingredient list.
     */
    private static final String TAG_INGREDIENTS = "ingredients";
    private static final String TAG_PROTECTED_QUANTITY = "protectedQuantity";


    protected Set<ItemStorage> ingredientSet = new HashSet<>();

    public StewmelierIngredientModule() 
    {
        // Constructor implementation
    }

    public Set<ItemStorage> getIngredients() 
    {
        return ingredientSet;
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
        for (ItemStorage ingredient : ingredientSet)
        {
            final CompoundTag compoundNBT = new CompoundTag();
            final Tag storedItem = ingredient.getItemStack().saveOptional(NullnessBridge.assumeNonnull(provider));
            compoundNBT.put(NbtTagConstants.STACK, NullnessBridge.assumeNonnull(storedItem));
            compoundNBT.putInt(TAG_PROTECTED_QUANTITY, ingredient.getAmount());
            importTagList.add(compoundNBT);
        }
        compound.put(TAG_INGREDIENTS, importTagList);
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
}
