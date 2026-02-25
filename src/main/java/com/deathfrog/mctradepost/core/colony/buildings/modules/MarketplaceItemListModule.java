package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.minecolonies.api.colony.buildings.modules.IAltersRequiredItems;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.colony.buildings.modules.ItemListModule;
import com.mojang.logging.LogUtils;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.ItemValueManager;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.google.common.collect.UnmodifiableIterator;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.Utils;

public class MarketplaceItemListModule extends ItemListModule implements IAltersRequiredItems
{
   public static final Logger LOGGER = LogUtils.getLogger();
   public static final String OPTION_SET = "optionSet";

   protected Set<ItemStorage> itemOptionSet = new HashSet<>();


   public MarketplaceItemListModule(String id)
   {
      super(id);
   }

   /**
    * Deserializes the NBT data for the module.
    *
    * Clears the current itemOptionSet and repopulates it using the data from the NBT.
    *
    * @param provider The holder lookup provider for item and block references.
    * @param compound The CompoundTag containing the serialized state of the
    *                 trade list.
    */
   @Override
   public void deserializeNBT(@NotNull HolderLookup.@NotNull Provider provider, @NotNull CompoundTag compound)
   {
      super.deserializeNBT(provider, compound);

      itemOptionSet.clear();

      final ListTag filterableList = compound.getList(OPTION_SET, Tag.TAG_COMPOUND);

      for (int i = 0; i < filterableList.size(); i++)
      {
         final CompoundTag itemTag = filterableList.getCompound(i);
         if (itemTag.isEmpty())
         {
               continue;
         }

         itemOptionSet.add(new ItemStorage(ItemStack.parseOptional(
               NullnessBridge.assumeNonnull(provider), itemTag
         )));
      }

     // LOGGER.info("Loading OPTION_SET {} entries", itemOptionSet.size());
   }

   /**
    * Serializes the current state of this module to the given compound tag.
    * The state is represented as a list of item stacks, where each item stack is serialized using the {@link ItemStack#saveOptional(HolderLookup.Provider)} method.
    * The list size is written as an integer, followed by the serialized item stacks.
    * The serialized list is stored in the compound tag under the key {@link #OPTION_SET}.
    * @param provider The holder lookup provider for item and block references.
    * @param compound The compound tag containing the serialized state of the
    *                 trade list.
    */
   @Override
   public void serializeNBT(@NotNull HolderLookup.Provider provider, @NotNull CompoundTag compound)
   {
      super.serializeNBT(provider, compound);

      // LOGGER.info("serializeNBT: getList.size={} itemOptionSet.size={}", this.getList().size(), itemOptionSet.size());     

      Iterator<ItemStorage> optionsIterator = itemOptionSet.iterator();
      ListTag filteredItems = new ListTag();

      while (optionsIterator.hasNext())
      {
         ItemStorage item = (ItemStorage) optionsIterator.next();

         filteredItems.add(item.getItemStack().saveOptional(NullnessBridge.assumeNonnull(provider)));
      }
   
      // LOGGER.info("Saving OPTION_SET {} entries", filteredItems.size());

      compound.put(OPTION_SET, filteredItems);
   }

   /**
    * Serializes the current state of this module to the given buffer.
    * The state is represented as a list of item stacks, where each item stack is serialized using the {@link Utils#serializeCodecMess(RegistryFriendlyByteBuf, ItemStack)} method.
    * The list size is written as an integer, followed by the serialized item stacks.
    */
   @Override
   public void serializeToView(@NotNull RegistryFriendlyByteBuf buf)
   {
      buf.writeInt(this.getList().size());
      UnmodifiableIterator<ItemStorage> var2 = this.getList().iterator();

      while (var2.hasNext())
      {
         ItemStorage item = (ItemStorage) var2.next();

         Utils.serializeCodecMess(buf, item.getItemStack());
      }

      // LOGGER.info("Serializing {} entries to view.", itemOptionSet.size());

      buf.writeInt(itemOptionSet.size());
      Iterator<ItemStorage> optionsIterator = itemOptionSet.iterator();

      while (optionsIterator.hasNext())
      {
         final ItemStorage item = optionsIterator.next();
         final ItemStack stack = item.getItemStack();

         Utils.serializeCodecMess(buf, stack);
         buf.writeInt(marketplaceValue(stack)); // server-authoritative value
      }
   }

   @Override
   public void addItem(ItemStorage item)
   {
      // LOGGER.info("Adding item {}", item);

      super.addItem(item);
   }

   @Override
   public void removeItem(ItemStorage item)
   {
      // LOGGER.info("Removing item {}", item);

      super.removeItem(item);
   }

   /**
    * Resets this module to its default state (no items).
    */
   @Override
   public void resetToDefaults()
   {
      clearItems();
      itemOptionSet.clear();
      markDirty();
   }

   /**
    * Adds an item to the list of items that are kept in the inventory when it is cleared.
    * This is used to specify items that are expected to be exported from the marketplace.
    * @param item The item to add to the list of items to be kept.
    */
   public void addSellableItem(Item item)
   {
      // LOGGER.info("Adding sellable item {}", item);

      ItemStorage itemStorage = new ItemStorage(item);
      addItem(itemStorage);
      itemOptionSet.add(itemStorage);

      markDirty();
   }

   /**
    * Modifies the items to be kept in the inventory. This method is called when the inventory is about to be cleared. Any items
    * expected to be exported are added to the list of items to be kept.
    * 
    * @param consumer The consumer to call for each item in the inventory.
    */
   @Override
   public void alterItemsToBeKept(final TriConsumer<Predicate<ItemStack>, Integer, Boolean> consumer)
   {
      // If we've marked these items as sellable, exclude a stack's worth from building pickup.
      if (!this.getList().isEmpty())
      {
         for (ItemStorage data : getList())
         {
            int quantity = data.getItemStack().getMaxStackSize();
            consumer.accept(stack -> ItemStackUtils.compareItemStacksIgnoreStackSize(stack, data.getItemStack(), false, true),
               quantity,
               false);
         }
      }
   }


   /**
    * Calculates the value of a market item based on its type.
    * Returns 0 if the item stack is null or empty.
    * Otherwise, returns the value of the item type as specified by the ItemValueManager.
    * @param marketItem The item stack to calculate the value of.
    * @return The value of the item stack.
    */
   public static int marketplaceValue(ItemStack marketItem)
   {
      if (marketItem == null || marketItem.isEmpty()) return 0;

      return ItemValueManager.get(marketItem.getItem());
   }
}
