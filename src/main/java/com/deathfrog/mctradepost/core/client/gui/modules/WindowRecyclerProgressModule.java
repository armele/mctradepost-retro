package com.deathfrog.mctradepost.core.client.gui.modules;

import java.util.List;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.RecyclerProgressView;
import com.deathfrog.mctradepost.api.util.GuiUtil;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingRecycling.RecyclingProcessor;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.ldtteam.blockui.views.Box;
import com.minecolonies.api.util.constant.WindowConstants;
import com.minecolonies.core.client.gui.AbstractModuleWindow;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class WindowRecyclerProgressModule extends AbstractModuleWindow<RecyclerProgressView>
{
    private static final String RECYCPROGRESSWINDOW_RESOURCE_SUFFIX = "gui/layouthuts/layoutrecyclerprogress.xml";

    private static final int MAX_OUTPUT_SHOWN = 10;     // How many stacks of output items will we display?

    /**
     * Scrollinglist of the resources.
     */
    protected ScrollingList processorDisplayList;

    protected Text capacityPane;
    protected int maxProcessors;

    public WindowRecyclerProgressModule(RecyclerProgressView moduleView, int maxProcessors)
    {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, RECYCPROGRESSWINDOW_RESOURCE_SUFFIX));

        processorDisplayList = findPaneOfTypeByID(WindowConstants.WINDOW_ID_LIST_REQUESTS, ScrollingList.class);
        capacityPane = findPaneOfTypeByID("capacity", Text.class);
        this.maxProcessors = maxProcessors;
    }

    @Override
    public void onOpened()
    {
        super.onOpened();
        updateProcessors();
    }

    /**
     * Updates the display for the recycling processors in the gui.
     * @see {@link ScrollingList.DataProvider}
     */
    public void updateProcessors() {
        if (processorDisplayList == null)
        {
            MCTradePostMod.LOGGER.warn("ProcessorDisplayList is null.");
            return;
        }
        processorDisplayList.setDataProvider(new ScrollingList.DataProvider()
        {
            List<RecyclingProcessor> recyclingProcessors = moduleView.getRecyclingProcessors().stream().toList();

            @Override
            public int getElementCount()
            {
                capacityPane.setText(Component.literal(recyclingProcessors.size() + " of " + maxProcessors + " in use."));
                return recyclingProcessors.size();
            }

            @Override
            public void updateElement(final int index, final Pane rowPane)
            {

                recyclingProcessors = moduleView.getRecyclingProcessors().stream().toList();

                if (index < 0 || index >= recyclingProcessors.size())
                {
                    return;
                }

                final Box wrapperBox = rowPane.findPaneOfTypeByID(WindowConstants.WINDOW_ID_REQUEST_BOX, Box.class);
                wrapperBox.setPosition(wrapperBox.getX(), wrapperBox.getY());
                wrapperBox.setSize(wrapperBox.getParent().getWidth(), wrapperBox.getHeight());

                final Box progressBar = rowPane.findPaneOfTypeByID(GuiUtil.PROGRESS_BAR, Box.class);

                final ItemIcon inputStackDisplay = rowPane.findPaneOfTypeByID("inputStack", ItemIcon.class);
                final RecyclingProcessor processor = recyclingProcessors.get(index);
                final ItemStack inputStack = processor.processingItem;

                drawProgressBar(wrapperBox, progressBar, processor);

                if (!inputStack.isEmpty())
                {
                    inputStackDisplay.setVisible(true);
                    inputStackDisplay.setItem(inputStack);
                }

                int outputIndex = 0;
                for (final ItemStack outputStack : processor.output) {
                    if (outputIndex >= MAX_OUTPUT_SHOWN) {
                        break;
                    }
                    final ItemIcon outputStackDisplay = rowPane.findPaneOfTypeByID("outputStack" + outputIndex, ItemIcon.class);
                    outputStackDisplay.setVisible(true);
                    outputStackDisplay.setItem(outputStack);
                    outputIndex++;        
                }

            }
        });
    }


    /**
     * Draws the progress bar for a recycling processor in the GUI based on its processing timer.
     *
     * @param wrapper      the container Box for the progress bar.
     * @param progressPane the Pane representing the visual progress bar.
     * @param processor    the RecyclingProcessor whose progress is being represented.
     */
    private void drawProgressBar(final Box wrapper, Pane progressPane, RecyclingProcessor processor)
    {
        GuiUtil.drawProgressBar(wrapper, progressPane, processor.processingTimer, processor.processingTimerComplete, 0);
    }
}
