package com.deathfrog.mctradepost.core.client.gui;

import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.core.inventory.TradeDockMenu;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.WaterRouteRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

/** Compact read-only list of a dock's persistent water linkages and break coordinates. */
public class TradeDockScreen extends AbstractContainerScreen<TradeDockMenu>
{
    @SuppressWarnings("null")
    private static @Nonnull final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/hopper.png");
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_TOP_HEIGHT = 18;
    private static final int PANEL_BOTTOM_HEIGHT = 7;
    private static final int PANEL_BOTTOM_TEXTURE_Y = 126;
    private static final int CONTENT_WIDTH = PANEL_WIDTH - 16;
    private static final int ROW_HEIGHT = 13;
    private int scroll;

    /**
     * Creates a read-only Trade Dock linkage screen.
     *
     * @param menu synchronized linkage menu
     * @param inventory viewing player's inventory
     * @param title screen title
     */
    public TradeDockScreen(TradeDockMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = Math.min(176, 42 + Math.max(1, operationalLinkCount()) * ROW_HEIGHT);
        inventoryLabelY = -1000;
    }

    /**
     * Draws an inventory-free panel assembled from the vanilla hopper container texture.
     *
     * @param graphics GUI drawing context
     * @param partialTick partial client tick
     * @param mouseX current mouse X position
     * @param mouseY current mouse Y position
     */
    @Override
    protected void renderBg(@Nonnull GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, PANEL_TOP_HEIGHT);
        for (int row = PANEL_TOP_HEIGHT; row < imageHeight - PANEL_BOTTOM_HEIGHT; row++)
        {
            graphics.blit(TEXTURE, leftPos, topPos + row, 0, PANEL_TOP_HEIGHT - 1, imageWidth, 1);
        }
        graphics.blit(TEXTURE, leftPos, topPos + imageHeight - PANEL_BOTTOM_HEIGHT,
            0, PANEL_BOTTOM_TEXTURE_Y, imageWidth, PANEL_BOTTOM_HEIGHT);
    }

    /**
     * Draws the linkage summary using vanilla container label colors.
     *
     * @param graphics GUI drawing context
     * @param mouseX current mouse X position
     * @param mouseY current mouse Y position
     */
    @SuppressWarnings("null")
    @Override
    protected void renderLabels(@Nonnull GuiGraphics graphics, int mouseX, int mouseY)
    {
        graphics.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), 8, 6, 0x404040, false);
        if (operationalLinkCount() == 0)
        {
            int y = 25;
            for (FormattedCharSequence line : font.split(
                Component.translatable("container.mctradepost.trade_dock.no_links"), CONTENT_WIDTH))
            {
                graphics.drawString(font, line, 8, y, 0x404040, false);
                y += font.lineHeight;
            }
            return;
        }

        int y = 22;
        int visible = Math.max(1, (imageHeight - 42) / ROW_HEIGHT);
        int operationalIndex = 0;
        int displayed = 0;

        for (TradeDockMenu.LinkView link : menu.links())
        {
            if (link.status() != WaterRouteRegistry.Status.OPERATIONAL)
            {
                continue;
            }
            if (operationalIndex++ < scroll)
            {
                continue;
            }
            if (displayed++ >= visible)
            {
                break;
            }

            if (y + ROW_HEIGHT > imageHeight)
            {
                break;
            }

            graphics.drawString(font,
                Component.translatable("container.mctradepost.trade_dock.link", link.destination().toShortString(), link.distance()),
                8,
                y,
                0x404040,
                false);
            y += ROW_HEIGHT;
        }
        int operationalLinks = operationalLinkCount();
        if (operationalLinks > visible)
        {
            Component range = Component.translatable("container.mctradepost.trade_dock.scroll",
                scroll + 1, Math.min(operationalLinks, scroll + visible), operationalLinks);
            graphics.drawString(font, range, imageWidth - 8 - font.width(range), 6, 0x606060, false);
        }
    }

    /**
     * Renders the Trade Dock panel without adding a second full-screen background pass.
     *
     * @param graphics GUI drawing context
     * @param mouseX current mouse X position
     * @param mouseY current mouse Y position
     * @param partialTick partial client tick
     */
    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * Scrolls the visible linkage range.
     *
     * @param mouseX current mouse X position
     * @param mouseY current mouse Y position
     * @param scrollX horizontal scroll amount
     * @param scrollY vertical scroll amount
     * @return {@code true} because the screen consumes scrolling
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        int visible = Math.max(1, (imageHeight - 42) / ROW_HEIGHT);
        scroll = Math.max(0, Math.min(Math.max(0, operationalLinkCount() - visible), scroll - (int) Math.signum(scrollY)));
        return true;
    }

    private int operationalLinkCount()
    {
        int count = 0;
        for (TradeDockMenu.LinkView link : menu.links())
        {
            if (link.status() == WaterRouteRegistry.Status.OPERATIONAL)
            {
                count++;
            }
        }
        return count;
    }
}
