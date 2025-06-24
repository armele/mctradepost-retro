package com.deathfrog.mctradepost.core.colony.buildings.modules;


import com.deathfrog.mctradepost.core.client.gui.modules.WindowEconModule;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.colony.managers.interfaces.IStatisticsManager;
import com.minecolonies.api.util.MathUtils;
import com.minecolonies.core.colony.managers.StatisticsManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

/**
 * Building Econ module.
 */
public class BuildingEconModule extends AbstractBuildingModule implements IPersistentModule
{

    private IStatisticsManager statisticsManager = new StatisticsManager();

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        statisticsManager.readFromNBT(compound);
    }

    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, CompoundTag compound)
    {
        statisticsManager.writeToNBT(compound);
    }

    @Override
    public void serializeToView(final RegistryFriendlyByteBuf buf, final boolean fullSync)
    {
        statisticsManager.serialize(buf, fullSync);
    }

    /**
     * Get the statistic manager of the building.
     * @return the manager.
     */
    public IStatisticsManager getBuildingStatisticsManager()
    {
        return statisticsManager;
    }

    /**
     * Helper method for incrementation of the stats.
     * @param s the stat id to increment.
     */
    public void increment(final String s)
    {
       statisticsManager.increment(s, building.getColony().getDay());
       if (MathUtils.RANDOM.nextInt(10) == 0)
       {
           markDirty();
       }
    }

    /**
     * Helper method for incrementation of the stats by a count.
     * @param s the stat id to increment.
     * @param count the count to increment it by.
     */
    public void incrementBy(final String s, final int count)
    {
        statisticsManager.incrementBy(s, count, building.getColony().getDay());
        if (MathUtils.RANDOM.nextInt(10) <= count)
        {
            markDirty();
        }
    }

    /**
     * Deposits a given count of coins into the building's economy, adding the corresponding amount of value to the economy.
     * @param count the count to deposit
     */
    public void deposit(final int count)
    {
        statisticsManager.incrementBy(WindowEconModule.CURRENT_BALANCE, count, building.getColony().getDay());  // Building stats
        building.getColony().getStatisticsManager().incrementBy(WindowEconModule.CURRENT_BALANCE, count, building.getColony().getDay());         // Colony stats (the official current balance)
        markDirty();
    }


    /**
     * Returns the total balance for the building.
     * @return the total balance.
     */
    public int getTotalBalance()
    {
        IStatisticsManager statsManager = building.getColony().getStatisticsManager();
        return statsManager.getStatTotal(WindowEconModule.CURRENT_BALANCE);
    }
}
