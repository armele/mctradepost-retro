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
    private static final int METER_X = 115;
    private static final int METER_Y = 16;
    private static final int METER_WIDTH = 18;
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
        int x = leftPos + METER_X;
        int y = topPos + METER_Y;
        g.fill(x, y, x + METER_WIDTH, y + METER_HEIGHT, 0xFFC6C6C6);
        g.fill(x, y, x + METER_WIDTH, y + 2, 0xFF555555);
        g.fill(x, y, x + 2, y + METER_HEIGHT, 0xFF555555);
        g.fill(x, y + METER_HEIGHT - 2, x + METER_WIDTH, y + METER_HEIGHT, 0xFFFFFFFF);
        g.fill(x + METER_WIDTH - 2, y, x + METER_WIDTH, y + METER_HEIGHT, 0xFFFFFFFF);
        g.fill(x + 2, y + 2, x + METER_WIDTH - 2, y + METER_HEIGHT - 2, 0xFF555555);

        int fill = menu.capacity() <= 0 ? 0 : Mth.ceil((double) menu.gas() * METER_INNER_HEIGHT / menu.capacity());
        if (fill > 0)
        {
            int fillTop = y + 2 + METER_INNER_HEIGHT - fill;
            g.fill(x + 2, fillTop, x + METER_WIDTH - 2, y + METER_HEIGHT - 2, 0xFF397FC1);
            g.fill(x + 3, fillTop, x + 4, y + METER_HEIGHT - 2, 0xFF73B8EB);
        }
    }
}
