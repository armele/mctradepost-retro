package com.deathfrog.mctradepost.core.colony.buildings.modules;


import com.deathfrog.mctradepost.api.util.EconomicConstants;
import com.deathfrog.mctradepost.api.util.EconomicReductionReason;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.colony.managers.interfaces.IStatisticsManager;
import com.minecolonies.api.util.MathUtils;
import com.minecolonies.core.colony.managers.StatisticsManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Building Econ module.
 */
public class BuildingEconModule extends AbstractBuildingModule implements IPersistentModule
{
    /** Colony statistic containing the value of all successful treasury deductions. */
    public static final String TOTAL_EXPENSES = "economic_expenses";
    /** Prefix for statistics that divide treasury deductions by reason. */
    public static final String EXPENSE_REASON_PREFIX = TOTAL_EXPENSES + ".";
    /** Prefix for named statistics that divide a reason's deductions by detail. */
    public static final String EXPENSE_DETAIL_PREFIX = "economic_expense_details.";

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
     * @return true when the full positive amount was deposited
     */
    public boolean deposit(final int count)
    {
        if (count <= 0)
        {
            return false;
        }

        long totalValue = getTotalBalance();

        if (totalValue + count > Integer.MAX_VALUE)
        {
            long limitedDeposit = Integer.MAX_VALUE - totalValue;
            deposit((int)limitedDeposit);
            return false;
        }

        adjustBalance(count);
        
        return true;
    }

    /**
     * Attempts to withdraw a given value from the colony economy.
     *
     * @param amount the value to withdraw
     * @param reason stable reason for the reduction
     * @return true if the balance was sufficient and the withdrawal was applied
     */
    public boolean tryWithdraw(final int amount, final EconomicReductionReason reason)
    {
        return tryWithdraw(amount, reason, null);
    }

    /**
     * Attempts to withdraw value and records the exact amount by reason and optional detail.
     * No statistics are changed when the amount is invalid or the balance is insufficient.
     *
     * @param amount the positive value to withdraw
     * @param reason stable reason for the reduction
     * @param detail optional player-facing detail, such as an item or animal name
     * @return true if the balance was sufficient and the attributed withdrawal was applied
     */
    public boolean tryWithdraw(final int amount, final EconomicReductionReason reason, @Nullable final Component detail)
    {
        if (amount <= 0 || reason == null)
        {
            return false;
        }

        if (getTotalBalance() < amount)
        {
            return false;
        }

        final String reasonKey = EXPENSE_REASON_PREFIX + reason.statisticSuffix();
        adjustBalance(-amount);
        incrementExpense(statisticsManager, building.getColony().getDay(), reasonKey, amount, detail);
        incrementExpense(building.getColony().getStatisticsManager(), building.getColony().getDay(), reasonKey, amount, null);
        building.getColony().markDirty();
        return true;
    }

    /**
     * Attempts an attributed withdrawal directly from a colony treasury when no economic building module owns the operation.
     * No statistics are changed when the amount is invalid or the balance is insufficient.
     *
     * @param colony colony whose treasury funds the operation
     * @param amount positive value to withdraw
     * @param reason stable reason for the reduction
     * @return true if the balance was sufficient and the attributed withdrawal was applied
     */
    public static boolean tryWithdraw(final IColony colony, final int amount, final EconomicReductionReason reason)
    {
        if (colony == null || amount <= 0 || reason == null)
        {
            return false;
        }

        final IStatisticsManager manager = colony.getStatisticsManager();
        if (manager.getStatTotal(EconomicConstants.CURRENT_BALANCE) < amount)
        {
            return false;
        }

        final String reasonKey = EXPENSE_REASON_PREFIX + reason.statisticSuffix();
        manager.incrementBy(EconomicConstants.CURRENT_BALANCE, -amount, colony.getDay());
        incrementExpense(manager, colony.getDay(), reasonKey, amount, null);
        colony.markDirty();
        return true;
    }

    private void adjustBalance(final int amount)
    {
        statisticsManager.incrementBy(EconomicConstants.CURRENT_BALANCE, amount, building.getColony().getDay());
        building.getColony().getStatisticsManager().incrementBy(EconomicConstants.CURRENT_BALANCE, amount, building.getColony().getDay());
        markDirty();
    }

    private static void incrementExpense(final IStatisticsManager manager, final int day, final String reasonKey, final int amount,
        @Nullable final Component detail)
    {
        manager.incrementBy(TOTAL_EXPENSES, amount, day);
        manager.incrementBy(reasonKey, amount, day);
        if (detail != null)
        {
            final String detailName = detail.getString().replace(';', ',');
            if (!detailName.isBlank())
            {
                manager.incrementBy(EXPENSE_DETAIL_PREFIX + reasonKey.substring(EXPENSE_REASON_PREFIX.length()) + ";" + detailName, amount, day);
            }
        }
    }


    /**
     * Returns the total balance for the building.
     * @return the total balance.
     */
    public int getTotalBalance()
    {
        IStatisticsManager statsManager = building.getColony().getStatisticsManager();
        return statsManager.getStatTotal(EconomicConstants.CURRENT_BALANCE);
    }
}
