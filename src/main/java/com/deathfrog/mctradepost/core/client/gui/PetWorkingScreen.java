package com.deathfrog.mctradepost.core.client.gui;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.inventory.PetWorkingMenu;
import com.deathfrog.mctradepost.core.client.render.ForageAreaOverlay;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

/**
 * Client screen for a pet working inventory with a side-mounted focus slot.
 */
public class PetWorkingScreen extends AbstractContainerScreen<PetWorkingMenu>
{
    private static final int TOOLTIP_MAX_WIDTH = 220;
    private static final ResourceLocation TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final ResourceLocation FOCUS_SLOT_TEXTURE =
        ResourceLocation.fromNamespaceAndPath("mctradepost", "textures/gui/sniff.png");
    private static final ResourceLocation HIGHLIGHT_OFF_TEXTURE =
        ResourceLocation.fromNamespaceAndPath("mctradepost", "textures/gui/highlight_off.png");
    private static final ResourceLocation HIGHLIGHT_ON_TEXTURE =
        ResourceLocation.fromNamespaceAndPath("mctradepost", "textures/gui/highlight_on.png");
    private ForageAreaCheckbox forageAreaCheckbox;

    /**
     * Creates a working-block screen.
     *
     * @param menu synchronized working-block menu
     * @param inventory viewing player's inventory
     * @param title working-block display title
     */
    public PetWorkingScreen(PetWorkingMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        imageWidth = 212;
        imageHeight = 168;
        inventoryLabelY = 74;
    }

    @Override
    protected void init()
    {
        super.init();
        final BlockPos pos = menu.workingPosition();

        Minecraft localMc = minecraft;
        
        if (pos != null && localMc != null)
        {
            forageAreaCheckbox = addRenderableWidget(new ForageAreaCheckbox(leftPos + 184, topPos + 43,
                ForageAreaOverlay.isEnabled(localMc.level, pos)));
        }
    }

    @Override
    protected void containerTick()
    {
        super.containerTick();

        Minecraft localMinecraft = minecraft;

        if (localMinecraft == null) return;

        if (forageAreaCheckbox != null && forageAreaCheckbox.selected
            && !ForageAreaOverlay.validateFocus(localMinecraft.level, menu.workingPosition(), menu.focusStack()))
        {
            forageAreaCheckbox.selected = false;
        }
    }

    /**
     * Draws the three-row inventory background and the research-sensitive focus
     * slot extension.
     *
     * @param graphics GUI drawing context
     * @param partialTick partial client tick
     * @param mouseX current mouse X position
     * @param mouseY current mouse Y position
     */
    @Override
    protected void renderBg(@Nonnull GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        graphics.blit(NullnessBridge.assumeNonnull(TEXTURE), leftPos, topPos, 0, 0, 176, 71);
        graphics.blit(NullnessBridge.assumeNonnull(TEXTURE), leftPos, topPos + 71, 0, 126, 176, 96);
        graphics.fill(leftPos + 176, topPos, leftPos + imageWidth, topPos + 71, 0xffc6c6c6);

        // Match the recessed bevel used by vanilla inventory slots: shadow on
        // the top/left, highlight on the bottom/right, and a gray well.
        graphics.fill(leftPos + 183, topPos + 17, leftPos + 202, topPos + 18, 0xff373737);
        graphics.fill(leftPos + 183, topPos + 17, leftPos + 184, topPos + 36, 0xff373737);
        graphics.fill(leftPos + 183, topPos + 35, leftPos + 202, topPos + 36, 0xffffffff);
        graphics.fill(leftPos + 201, topPos + 17, leftPos + 202, topPos + 36, 0xffffffff);
        graphics.fill(leftPos + 184, topPos + 18, leftPos + 201, topPos + 35,
            menu.isFocusEnabled() ? 0xff8b8b8b : 0xff6b6b6b);
        RenderSystem.enableBlend();
        graphics.setColor(1.0F, 1.0F, 1.0F, 0.75F);
        graphics.blit(
            NullnessBridge.assumeNonnull(FOCUS_SLOT_TEXTURE),
            leftPos + 184,
            topPos + 18,
            0,
            0,
            16,
            16,
            16,
            16);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();

        if (!menu.isFocusEnabled())
        {
            graphics.fill(leftPos + 184, topPos + 18, leftPos + 200, topPos + 34, 0x55000000);
        }
    }

    /**
     * Draws the standard container labels and a centered heading for the
     * dedicated focused-foraging slot.
     *
     * @param graphics GUI drawing context
     * @param mouseX current mouse X position
     * @param mouseY current mouse Y position
     */
    @SuppressWarnings("null")
    @Override
    protected void renderLabels(@Nonnull GuiGraphics graphics, int mouseX, int mouseY)
    {
        super.renderLabels(graphics, mouseX, mouseY);

        final Component focusTitle = Component.translatable("gui.mctradepost.focused_foraging.title");
        final int sidePanelStart = 176;
        final int sidePanelWidth = imageWidth - sidePanelStart;
        final int titleX = sidePanelStart + (sidePanelWidth - font.width(focusTitle)) / 2;
        graphics.drawString(font, focusTitle, titleX, 6, 0x404040, false);
    }

    /**
     * Renders the screen and the contextual focus-slot tooltip.
     *
     * @param graphics GUI drawing context
     * @param mouseX current mouse X position
     * @param mouseY current mouse Y position
     * @param partialTick partial client tick
     */
    @SuppressWarnings("null")
    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        super.render(graphics, mouseX, mouseY, partialTick);

        final boolean focusSlotHovered = hoveredSlot == menu.slots.get(PetWorkingMenu.FOCUS_SLOT);
        if (!focusSlotHovered)
        {
            renderTooltip(graphics, mouseX, mouseY);
        }

        if (focusSlotHovered)
        {
            final List<Component> tooltip;
            if (menu.focusStack().isEmpty())
            {
                tooltip = menu.isFocusEnabled()
                    ? List.of(
                        Component.translatable("gui.mctradepost.focused_foraging.insert.1"),
                        Component.translatable("gui.mctradepost.focused_foraging.insert.2"))
                    : List.of(
                        Component.translatable("gui.mctradepost.focused_foraging.insert.1"),
                        Component.translatable("gui.mctradepost.focused_foraging.insert.2"),
                        Component.translatable("gui.mctradepost.focused_foraging.research_required"));
            }
            else if (menu.isFocusEnabled())
            {
                tooltip = List.of(Component.translatable(
                    "gui.mctradepost.focused_foraging.prefer", menu.focusStack().getHoverName()));
            }
            else
            {
                tooltip = List.of(Component.translatable("gui.mctradepost.focused_foraging.research_required"));
            }
            graphics.renderTooltip(font, wrapTooltip(tooltip), mouseX, mouseY);
        }
    }

    /** Wraps tooltip components without discarding formatting or explicit line breaks. */
    @SuppressWarnings("null")
    private List<FormattedCharSequence> wrapTooltip(final List<Component> tooltip)
    {
        final List<FormattedCharSequence> wrapped = new ArrayList<>();
        tooltip.forEach(line -> wrapped.addAll(font.split(line, TOOLTIP_MAX_WIDTH)));
        return wrapped;
    }

    /** Compact image toggle sized for the working block's narrow focus side panel. */
    private final class ForageAreaCheckbox extends AbstractButton
    {
        private boolean selected;

        private ForageAreaCheckbox(int x, int y, boolean selected)
        {
            super(x, y, 18, 18, Component.translatable("gui.mctradepost.forage_area"));
            this.selected = selected;
        }

        @Override
        public void onPress()
        {
            Minecraft localMc = minecraft;
            
            if (localMc != null)
            {
                selected = !selected;
                ForageAreaOverlay.setEnabled(localMc.level, menu.workingPosition(), menu.focusStack(), selected);
            }
        }

        @SuppressWarnings("null")
        @Override
        protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
        {
            graphics.blit(selected ? HIGHLIGHT_ON_TEXTURE : HIGHLIGHT_OFF_TEXTURE,
                getX(), getY(), width, height, 0.0F, 0.0F, 18, 18, 18, 18);
            if (isHovered())
            {
                graphics.renderTooltip(font,
                    wrapTooltip(List.of(Component.translatable("gui.mctradepost.forage_area.tooltip"))),
                    mouseX, mouseY);
            }
        }

        @SuppressWarnings("null")
        @Override
        protected void updateWidgetNarration(@Nonnull NarrationElementOutput output)
        {
            output.add(NarratedElementType.TITLE, createNarrationMessage());
            output.add(NarratedElementType.USAGE,
                Component.translatable("gui.mctradepost.forage_area.tooltip"));
        }
    }
}
