package com.deathfrog.mctradepost.core.client.gui;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.core.inventory.MooringBayMenu;
import com.deathfrog.mctradepost.core.blocks.blockentity.LiftingGasStorage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Client screen for installing Route Surveys and viewing Mooring Bay gas storage. */
public class MooringBayScreen extends AbstractContainerScreen<MooringBayMenu>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/hopper.png");
    private static final int EXTRA_LINE_HEIGHT = 18;
    private static final int ORIGINAL_IMAGE_HEIGHT = 133;

    public MooringBayScreen(MooringBayMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        imageHeight = ORIGINAL_IMAGE_HEIGHT + EXTRA_LINE_HEIGHT;
        inventoryLabelY = 39 + EXTRA_LINE_HEIGHT;
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int x, int y, float p)
    {
        super.render(g, x, y, p);
        renderTooltip(g, x, y);
    }

    @SuppressWarnings("null")
    @Override
    protected void renderBg(@Nonnull GuiGraphics g, float p, int x, int y)
    {
        // Insert a text-height strip before the hopper slots while retaining the vanilla texture's borders.
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, 18);
        for (int row = 0; row < EXTRA_LINE_HEIGHT; row++)
            g.blit(TEXTURE, leftPos, topPos + 18 + row, 0, 17, imageWidth, 1);
        g.blit(TEXTURE, leftPos, topPos + 18 + EXTRA_LINE_HEIGHT, 0, 18, imageWidth, ORIGINAL_IMAGE_HEIGHT - 18);
    }

    @SuppressWarnings("null")
    @Override
    protected void renderLabels(@Nonnull GuiGraphics g, int x, int y)
    {
        super.renderLabels(g, x, y);
        Component gas = Component.translatable(LiftingGasStorage.LIFTING_GAS_DISPLAY_KEY, menu.gas(), menu.capacity());
        g.drawString(font, gas, 8, 20, 4210752, false);
    }
}
