package com.deathfrog.mctradepost.core.client.gui.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.DropDownList;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.managers.interfaces.IStatisticsManager;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.text.NumberFormat;
import java.util.*;

import static com.minecolonies.api.util.constant.WindowConstants.*;

/**
 * BOWindow for the Marketplace hut
 */
public class WindowEconModule extends AbstractModuleWindow
{
    private IStatisticsManager statsManager = null;
    /**
     * Drop down list for interval.
     */
    private DropDownList intervalDropdown;

    /**
     * Current selected interval.
     */
    public String selectedInterval = "com.mctradepost.coremod.gui.interval.yesterday";

    public static final String ITEM_SOLD = "item.sold";
    public static final String CASH_GENERATED = "cash.generated";
    public static final String CURRENT_BALANCE = "current.balance";
    public static final String PARTIAL_ECON_MODIFIER_NAME = "com.mctradepost.coremod.gui.econ.";

    /**
     * Util tags.
     */
    private static final String ECONWINDOW_RESOURCE_SUFFIX = ":gui/layouthuts/layouteconmodule.xml";

    public WindowEconModule(IBuildingView building, IStatisticsManager statsManager) {
        super(building, MCTradePostMod.MODID + ECONWINDOW_RESOURCE_SUFFIX);
        this.statsManager = statsManager;
    }

    /**
     * Map of intervals.
     */
    private static final LinkedHashMap<String, Integer> INTERVAL = new LinkedHashMap<>();

    static
    {
        INTERVAL.put("com.mctradepost.coremod.gui.interval.yesterday", 1);
        INTERVAL.put("com.mctradepost.coremod.gui.interval.lastweek", 7);
        INTERVAL.put("com.mctradepost.coremod.gui.interval.100days", 100);
        INTERVAL.put("com.mctradepost.coremod.gui.interval.alltime", -1);
    }



    @Override
    public void onOpened()
    {
        super.onOpened();
        updateStats();
    }

    private int getStatFor(String id, String intervalArg) {
        int stat = statsManager.getStatTotal(id);
        int interval = INTERVAL.get(intervalArg);
        if (interval > 0)
        {
            stat = statsManager.getStatsInPeriod(id, buildingView.getColony().getDay() - interval, buildingView.getColony().getDay());
        }

        return stat;     
    }

    /**
     * Update the display for the stats.
     */
    private void updateStats()
    {

        int currentBalance = getStatFor(CURRENT_BALANCE, "com.mctradepost.coremod.gui.interval.alltime");
        final Text balanceLabel = findPaneOfTypeByID("currentbalance", Text.class);
        NumberFormat formatter = NumberFormat.getIntegerInstance(); // or getCurrencyInstance() if using symbols
        String formattedSales = "‡" + formatter.format(currentBalance);        
        balanceLabel.setText(Component.literal(formattedSales));  

        int itemCount = getStatFor(ITEM_SOLD, selectedInterval);
        final Text countLabel = findPaneOfTypeByID("itemcount", Text.class);
        countLabel.setText(Component.literal(itemCount + ""));   

        int cashGenerated = getStatFor(CASH_GENERATED, selectedInterval);
        final Text cashLabel = findPaneOfTypeByID("totalcash", Text.class);
        formattedSales = "‡" + formatter.format(cashGenerated);        
        cashLabel.setText(Component.literal(formattedSales));  

        MCTradePostMod.LOGGER.info("Stats: {} items sold, {} cash generated (formatted as {})", itemCount, cashGenerated, formattedSales);

        intervalDropdown = findPaneOfTypeByID(DROPDOWN_INTERVAL_ID, DropDownList.class);
        intervalDropdown.setHandler(this::onDropDownListChanged);

        intervalDropdown.setDataProvider(new DropDownList.DataProvider()
        {
            @Override
            public int getElementCount()
            {
                return INTERVAL.size();
            }

            @Override
            public MutableComponent getLabel(final int index)
            {
                return Component.translatableEscape((String) INTERVAL.keySet().toArray()[index]);
            }
        });
        intervalDropdown.setSelectedIndex(new ArrayList<>(INTERVAL.keySet()).indexOf(selectedInterval));
    }

    private void onDropDownListChanged(final DropDownList dropDownList)
    {
        final String temp = (String) INTERVAL.keySet().toArray()[dropDownList.getSelectedIndex()];
        if (!temp.equals(selectedInterval))
        {
            selectedInterval = temp;
            updateStats();
        }
    }
}
