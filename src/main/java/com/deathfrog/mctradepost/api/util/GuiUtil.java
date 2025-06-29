package com.deathfrog.mctradepost.api.util;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Gradient;
import com.ldtteam.blockui.views.Box;

import net.minecraft.network.chat.Component;

public class GuiUtil
{
    public static final String PROGRESS_BAR = "progressBar";

    /**
     * Draws a progress bar in the given wrapper box using the given panes to define the size and position.
     * The progress bar is a gradient that transitions from red to green, with the position of the color
     * transition determined by the ratio of current to total. A tooltip is added with a string that displays
     * the progress as a fraction, percentage, and raw values.
     *
     * @param wrapper the box that will contain the progress bar
     * @param progressPane the pane that defines the size and position of the progress bar
     * @param current the current value of the progress
     * @param total the total value of the progress
     * @param boxLineWidth the line width of the containing box
     */
    public static void drawProgressBar(final Box wrapper, Pane progressPane, int current, int total, int boxLineWidth)
    {
        final Gradient nameGradient = new Gradient();
        final double progressRatio = (double) current / (double) total;

        // Clamp to [0, 1]
        final double clampedRatio = Math.max(0.0, Math.min(1.0, progressRatio));

        // Interpolate color
        final int red = (int) (255 * (1.0 - clampedRatio));
        final int green = (int) (255 * clampedRatio);
        final int blue = 0;

        nameGradient.setSize((int) (clampedRatio * progressPane.getWidth()) - (2 * boxLineWidth), progressPane.getHeight() - (2 * boxLineWidth));
        nameGradient.setPosition(progressPane.getX() + boxLineWidth, progressPane.getY() + boxLineWidth);
        wrapper.addChild(nameGradient);

        nameGradient.setGradientStart(red, green, blue, 255);
        nameGradient.setGradientEnd(red, green, blue, 255);

        String hoverText = String.format("%d of %d (%d%%)", current, total, (int)(progressRatio * 100));
        PaneBuilders.tooltipBuilder().hoverPane(progressPane).build().setText(Component.literal(hoverText));
    }
}
