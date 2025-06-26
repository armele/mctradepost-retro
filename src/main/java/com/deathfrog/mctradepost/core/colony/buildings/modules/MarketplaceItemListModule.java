package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.colony.buildings.modules.ItemListModule;

import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

import com.google.common.collect.UnmodifiableIterator;
import com.minecolonies.api.util.Utils;

public class MarketplaceItemListModule extends ItemListModule {

    public MarketplaceItemListModule(String id)
    {
        super(id);
    }
    
    @Override
   public void serializeToView(@NotNull RegistryFriendlyByteBuf buf) {
      buf.writeInt(this.getList().size());
      UnmodifiableIterator<ItemStorage> var2 = this.getList().iterator();

      while(var2.hasNext()) {
         ItemStorage item = (ItemStorage)var2.next();

         // ItemStack souvenirVersion = SouvenirItem.createSouvenir(item.getItem(), ItemValueRegistry.getValue(item.getItemStack()));
         // MCTradePostMod.LOGGER.info("Serializing souvenir: {} with souvenir value: {}", souvenirVersion, SouvenirItem.getSouvenirValue(souvenirVersion));

         Utils.serializeCodecMess(buf, item.getItemStack());
      }

   }
}
