package com.deathfrog.mctradepost.core.colony.buildings.modules;


    import java.util.Objects;
    import java.util.function.Predicate;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
    import com.minecolonies.api.crafting.ItemStorage;
    import net.minecraft.world.item.ItemStack;
    
    public class ExportData
    {
        private final StationData stationData;
        private final ItemStorage itemStorage;
        private final int cost;
        private int shipDistance = -1;
        private int trackDistance = -1;
        private int lastShipDay = -1;

        public ExportData(StationData stationData, ItemStorage itemStorage, int cost)
        {
            this.stationData = stationData;
            this.itemStorage = itemStorage;
            this.cost = cost;
            this.shipDistance = -1;
            this.trackDistance = -1;
            this.lastShipDay = -1;
        }

        public StationData getStationData() 
        { 
            return stationData; 
        }

        public int getCost() 
        { 
            return cost; 
        }

        public int getShipDistance() 
        { 
            return shipDistance; 
        }

        public void setShipDistance(int shipDistance) 
        { 
            this.shipDistance = shipDistance; 
        }

        public int getTrackDistance() 
        { 
            return trackDistance; 
        }

        public void setTrackDistance(int trackDistance) 
        { 
            this.trackDistance = trackDistance; 
        }

        public ItemStorage getItemStorage() 
        { 
            return itemStorage; 
        }

        public int getMaxStackSize() 
        {
            return itemStorage.getItemStack().getMaxStackSize();
        }

        public int getLastShipDay() 
        { 
            return lastShipDay; 
        }

        public void setLastShipDay(int lastShipDay) 
        { 
            this.lastShipDay = lastShipDay; 
        }

        /**
         * Predicate for the different usages to check if inventory contains a given item.
         *
         * @param cure the expected cure item.
         * @return the predicate for checking if the cure exists.
         */
        public static Predicate<ItemStack> hasExportItem(final ItemStorage exportItem) 
        {
            return stack -> isExportItem(stack, exportItem);
        }

        /**
         * Predicate for the different usages to check if inventory contains a cure.
         *
         * @param cure the expected cure item.
         * @return the predicate for checking if the cure exists.
         */
        public static Predicate<ItemStack> hasCoin() 
        {
            return stack -> isExportItem(stack, new ItemStorage(MCTradePostMod.MCTP_COIN_ITEM.get()));
        }

        /**
         * Check if the given item is a cure item.
         *
         * @param stack the input stack.
         * @param exportItem  the export item.
         * @return true if so.
         */
        public static boolean isExportItem(final ItemStack stack, final ItemStorage exportItem) 
        {
            return Objects.equals(new ItemStorage(stack), exportItem);
        }
    }