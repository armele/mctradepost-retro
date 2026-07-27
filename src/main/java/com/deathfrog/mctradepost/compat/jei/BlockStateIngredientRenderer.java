package com.deathfrog.mctradepost.compat.jei;

import java.util.List;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.entity.pets.goals.scavenge.ScavengeBlockStateHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Renders a block state in a JEI item ingredient slot while leaving the
 * underlying item ingredient available to JEI's lookup and focus systems.
 */
public final class BlockStateIngredientRenderer implements IIngredientRenderer<ItemStack>
{
    private static final int SLOT_SIZE = 16;
    private static final int FULL_BRIGHT = 0x00F000F0;

    private final Block block;
    private final BlockState displayState;
    private final ResourceLocation blockId;

    /**
     * Creates a renderer for a source block.
     *
     * @param block source block represented by the JEI ingredient
     * @param blockId registry id shown in the source tooltip
     */
    public BlockStateIngredientRenderer(final Block block, final ResourceLocation blockId)
    {
        this.block = block;
        this.displayState = ScavengeBlockStateHelper.representativeState(block);
        this.blockId = blockId;
    }

    /**
     * Returns the width occupied by the renderer.
     *
     * @return standard JEI slot content width
     */
    @Override
    public int getWidth()
    {
        return SLOT_SIZE;
    }

    /**
     * Returns the height occupied by the renderer.
     *
     * @return standard JEI slot content height
     */
    @Override
    public int getHeight()
    {
        return SLOT_SIZE;
    }

    /**
     * Draws the representative block state centered inside the ingredient slot.
     *
     * @param guiGraphics active GUI graphics context
     * @param ingredient underlying searchable JEI item ingredient
     */
    @SuppressWarnings("null")
    @Override
    public void render(@Nonnull final GuiGraphics guiGraphics, @Nonnull final ItemStack ingredient)
    {
        final PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(SLOT_SIZE / 2.0F, SLOT_SIZE - 4.0F, 100.0F);
        pose.scale(-10.0F, -10.0F, -10.0F);
        pose.translate(-0.5F, -0.5F, 0.0F);
        pose.mulPose(Axis.XP.rotationDegrees(30.0F));

        pose.translate(0.5F, 0.0F, -0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(225.0F));
        pose.translate(-0.5F, 0.0F, 0.5F);

        pose.pushPose();
        pose.translate(0.0F, 0.0F, -1.0F);

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
            displayState,
            pose,
            guiGraphics.bufferSource(),
            FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY,
            ModelData.EMPTY,
            null);
        guiGraphics.flush();
        pose.popPose();
        pose.popPose();
    }

    /**
     * Provides source-block information instead of the underlying item tooltip.
     *
     * @param ingredient underlying searchable JEI item ingredient
     * @param tooltipFlag active tooltip detail flag
     * @return localized block name and registry id
     */
    @Override
    public List<Component> getTooltip(@Nonnull final ItemStack ingredient, @Nonnull final TooltipFlag tooltipFlag)
    {
        return List.of(
            Component.translatable("jei.mctradepost.pet_foraging.tooltip.source", block.getName()),
            Component.literal(blockId.toString()+"").withStyle(ChatFormatting.DARK_GRAY));
    }

}
