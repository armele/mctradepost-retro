package com.deathfrog.mctradepost.core.client.gui;

import java.util.List;
import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.core.inventory.GasifierMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/** Client screen for Gasifier fuel, burn progress, and Lifting Gas storage. */
public class GasifierScreen extends AbstractContainerScreen<GasifierMenu>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/furnace.png");
    private static final ResourceLocation LIT_PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace("container/furnace/lit_progress");
    private static final ResourceLocation BURN_PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");
    private static final ResourceLocation GAS_TEXTURE = ResourceLocation.fromNamespaceAndPath("mctradepost", "textures/gui/gas_bubbles.png");
    private static final int GAS_TEXTURE_SIZE = 16;
    private static final int METER_X = 115;
    private static final int METER_Y = 16;
    private static final int METER_WIDTH = 20;
    private static final int METER_HEIGHT = 54;
    private static final int METER_INNER_HEIGHT = METER_HEIGHT - 4;

    public GasifierScreen(GasifierMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
    }

    @SuppressWarnings("null")
    @Override
    public void render(@Nonnull GuiGraphics g, int x, int y, float p)
    {
        super.render(g, x, y, p);
        renderTooltip(g, x, y);
        if (x >= leftPos + METER_X && x < leftPos + METER_X + METER_WIDTH &&
            y >= topPos + METER_Y && y < topPos + METER_Y + METER_HEIGHT)
            g.renderComponentTooltip(font, List.of(
                Component.translatable("fluid_type.mctradepost.lifting_gas"),
                Component.translatable("container.mctradepost.lifting_gas_meter_amount", menu.gas(), menu.capacity())
                    .withStyle(ChatFormatting.GRAY)), x, y);
    }

    @SuppressWarnings("null")
    @Override
    protected void renderBg(@Nonnull GuiGraphics g, float p, int x, int y)
    {
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // The Gasifier has only a fuel slot; erase the furnace's unused input and output slots.
        g.fill(leftPos + 55, topPos + 16, leftPos + 73, topPos + 34, 0xFFC6C6C6);
        g.fill(leftPos + 110, topPos + 29, leftPos + 142, topPos + 59, 0xFFC6C6C6);

        if (menu.isLit())
        {
            int height = Mth.ceil(menu.litProgress() * 13.0F) + 1;
            g.blitSprite(LIT_PROGRESS_SPRITE, 14, 14, 0, 14 - height,
                leftPos + 56, topPos + 50 - height, 14, height);
        }

        int width = Mth.ceil(menu.burnProgress() * 24.0F);
        g.blitSprite(BURN_PROGRESS_SPRITE, 24, 16, 0, 0, leftPos + 79, topPos + 34, width, 16);

        renderGasMeter(g);
    }

    private void renderGasMeter(GuiGraphics g)
    {
        // Convert the meter's GUI-relative horizontal offset into an absolute screen coordinate.
        int x = leftPos + METER_X;

        // Convert the meter's GUI-relative vertical offset into an absolute screen coordinate.
        int y = topPos + METER_Y;

        // Paint the complete meter rectangle light gray; later calls draw its frame and interior over it.
        g.fill(x, y, x + METER_WIDTH, y + METER_HEIGHT, 0xFFC6C6C6);

        // Draw the two-pixel dark top edge. GuiGraphics.fill uses exclusive right and bottom coordinates.
        g.fill(x, y, x + METER_WIDTH, y + 2, 0xFF555555);

        // Draw the two-pixel dark left edge for the recessed-frame effect.
        g.fill(x, y, x + 2, y + METER_HEIGHT, 0xFF555555);

        // Draw the two-pixel white bottom edge for the raised highlight side of the frame.
        g.fill(x, y + METER_HEIGHT - 1, x + METER_WIDTH, y + METER_HEIGHT, 0xFFFFFFFF);

        // Draw the two-pixel white right edge for the raised highlight side of the frame.
        g.fill(x + METER_WIDTH - 2, y, x + METER_WIDTH, y + METER_HEIGHT, 0xFFFFFFFF);

        // Paint the dark empty-meter cavity; adjust these four insets to hand-tune its bounds.
        g.fill(x + 2, y + 1, x + METER_WIDTH - 2, y + METER_HEIGHT - 1, 0xFF555555);

        // Convert gas/capacity into a pixel height, using zero when the capacity is invalid.
        int fill = menu.capacity() <= 0 ? 0 : Mth.ceil((double) menu.gas() * METER_INNER_HEIGHT / menu.capacity());

        // Skip all gas rendering when the calculated level occupies no pixels.
        if (fill > 0)
        {
            // Locate the fill's top edge; subtracting fill makes the gas rise upward from the bottom.
            int fillTop = y + 1 + METER_INNER_HEIGHT - fill;

            // Apply an optional extra horizontal inset to both the texture and its highlight strip.
            int fillInset = 0;

            // Set the texture's inclusive left edge; increasing this value narrows it from the left.
            int fillLeft = x + fillInset + 1;

            // Set the texture's exclusive right edge; increasing the trailing 2 narrows it from the right.
            int fillRight = x + METER_WIDTH - 2;

            // Set the texture's exclusive bottom edge; decreasing the trailing 1 raises that edge.
            int fillBottom = y + METER_HEIGHT - 1;

            // Tile and crop the gas-bubble image within the calculated fill rectangle.
            renderTiledGas(g, fillLeft, fillTop, fillRight, fillBottom);

            // Draw the lighter highlight over the texture; its first and third arguments set its X bounds.
            g.fill(x + fillInset + 2, fillTop, x + 4, y + METER_HEIGHT - 1, 0xFF8BAFF6);
        }
    }

    /** Draws unscaled gas tiles, cropping edge tiles and anchoring the pattern to the bottom. */
    @SuppressWarnings("null")
    private void renderTiledGas(GuiGraphics g, int left, int top, int right, int bottom)
    {
        for (int tileBottom = bottom; tileBottom > top; tileBottom -= GAS_TEXTURE_SIZE)
        {
            int tileHeight = Math.min(GAS_TEXTURE_SIZE, tileBottom - top);
            int tileY = tileBottom - tileHeight;
            int textureY = GAS_TEXTURE_SIZE - tileHeight;

            for (int tileX = left; tileX < right; tileX += GAS_TEXTURE_SIZE)
            {
                int tileWidth = Math.min(GAS_TEXTURE_SIZE, right - tileX);
                g.blit(GAS_TEXTURE, tileX, tileY, 0, textureY,
                    tileWidth, tileHeight, GAS_TEXTURE_SIZE, GAS_TEXTURE_SIZE);
            }
        }
    }
}
