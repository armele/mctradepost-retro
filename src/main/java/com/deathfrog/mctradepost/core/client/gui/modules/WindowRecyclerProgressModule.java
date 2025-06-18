package com.deathfrog.mctradepost.core.client.gui.modules;

import java.util.List;
import java.util.Set;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingRecycling.RecyclingProcessor;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Gradient;
import com.ldtteam.blockui.controls.Image;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.ldtteam.blockui.views.Box;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.constant.WindowConstants;
import com.minecolonies.core.client.gui.AbstractModuleWindow;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class WindowRecyclerProgressModule extends AbstractModuleWindow
{
    private static final String RECYCPROGRESSWINDOW_RESOURCE_SUFFIX = ":gui/layouthuts/layoutrecyclerprogress.xml";
    public static final String PROGRESS_BAR = "progressBar";

    private static final int MAX_OUTPUT_SHOWN = 10;     // How many stacks of output items will we display?

    private static final int BAR_X_OFFSET = 1;
    private static final int BAR_Y_OFFSET = 5;
    private static final int BAR_HEIGHT = 8;
    private static final int BAR_WIDTH = 53;

    private List<RecyclingProcessor> recyclingProcessors = null;
    
    /**
     * Scrollinglist of the resources.
     */
    protected ScrollingList processorDisplayList;

    protected Text capacityPane;
    protected int maxProcessors;

    public WindowRecyclerProgressModule(IBuildingView buildingView, Set<RecyclingProcessor> recyclingProcessors, int maxProcessors)
    {
        super(buildingView, MCTradePostMod.MODID + RECYCPROGRESSWINDOW_RESOURCE_SUFFIX);
        this.recyclingProcessors = recyclingProcessors.stream().toList();
        processorDisplayList = findPaneOfTypeByID(WindowConstants.WINDOW_ID_LIST_REQUESTS, ScrollingList.class);
        capacityPane = findPaneOfTypeByID("capacity", Text.class);
        this.maxProcessors = maxProcessors;
    }

    @Override
    public void onOpened()
    {
        super.onOpened();

        if (processorDisplayList != null && recyclingProcessors != null)
        {
            capacityPane.setText(Component.literal(recyclingProcessors.size() + " of " + maxProcessors + " in use."));
            updateProcessors();
        } else {
            MCTradePostMod.LOGGER.warn("ProcessorDisplayList or recyclingProcessors is null.");
        }
    }

    /**
     * Updates the display for the recycling processors in the gui.
     * @see {@link ScrollingList.DataProvider}
     */
    public void updateProcessors() {

        processorDisplayList.setDataProvider(new ScrollingList.DataProvider()
        {

            @Override
            public int getElementCount()
            {
                return recyclingProcessors.size();
            }

            @Override
            public void updateElement(final int index, final Pane rowPane)
            {
                if (index < 0 || index >= recyclingProcessors.size())
                {
                    return;
                }


                final Box wrapperBox = rowPane.findPaneOfTypeByID(WindowConstants.WINDOW_ID_REQUEST_BOX, Box.class);
                wrapperBox.setPosition(wrapperBox.getX(), wrapperBox.getY());
                wrapperBox.setSize(wrapperBox.getParent().getWidth(), wrapperBox.getHeight());

                final Image progressImage = rowPane.findPaneOfTypeByID(PROGRESS_BAR, Image.class);

                final ItemIcon inputStackDisplay = rowPane.findPaneOfTypeByID("inputStack", ItemIcon.class);
                final RecyclingProcessor processor = recyclingProcessors.get(index);
                final ItemStack inputStack = processor.processingItem;

                drawProgressBar(wrapperBox, progressImage, processor);

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
     * Draws the progress bar for an in-progress recycling operation.
     * @param view          the view to assign the progressbar onto.
     * @param offsetX       the horizontal offset for the progress bar
     * @param offsetY       the vertical offset for the progress bar
     * @param processor     the processor whose progress is being drawn.
     * @param subBar        the bar to overlay the gradient over.
     */
    private void drawProgressBar(final Box wrapper, Image progressImage, RecyclingProcessor processor)
    {
        final Gradient nameGradient = new Gradient();
        final double progressRatio = (double) processor.processingTimer / (double) processor.processingTimerComplete;

        // Clamp to [0, 1]
        final double clampedRatio = Math.max(0.0, Math.min(1.0, progressRatio));

        // Interpolate color
        final int red = (int) (255 * (1.0 - clampedRatio));
        final int green = (int) (255 * clampedRatio);
        final int blue = 0;

        nameGradient.setSize((int) (clampedRatio * BAR_WIDTH), BAR_HEIGHT);
        nameGradient.setPosition(progressImage.getX() + BAR_X_OFFSET, progressImage.getY() + BAR_Y_OFFSET);
        wrapper.addChild(nameGradient);

        nameGradient.setGradientStart(red, green, blue, 255);
        nameGradient.setGradientEnd(red, green, blue, 255);

        /*
        String percentString = String.format("%.0f%%", clampedRatio * 100);
        nameGradient.setText(Component.literal(percentString));
        nameGradient.setTextAlignment(Alignment.MIDDLE);
        */
    }
}
