package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.minecolonies.api.colony.buildings.modules.IAltersRequiredItems;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.colony.buildings.modules.ItemListModule;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import java.util.function.Predicate;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.NotNull;
import com.google.common.collect.UnmodifiableIterator;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.Utils;

public class MarketplaceItemListModule extends ItemListModule implements IAltersRequiredItems
{
   public MarketplaceItemListModule(String id)
   {
      super(id);
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

         // ItemStack souvenirVersion = SouvenirItem.createSouvenir(item.getItem(), ItemValueRegistry.getValue(item.getItemStack()));
         // MCTradePostMod.LOGGER.info("Serializing souvenir: {} with souvenir value: {}", souvenirVersion,
         // SouvenirItem.getSouvenirValue(souvenirVersion));

         Utils.serializeCodecMess(buf, item.getItemStack());
      }
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
}
