package com.deathfrog.mctradepost.core.client.render.souvenir;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.item.SouvenirItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;




public class SouvenirISTER extends BlockEntityWithoutLevelRenderer {

    public SouvenirISTER() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        MCTradePostMod.LOGGER.info("Cosntructing SouvenirISTER.");        
    }

    /**
     * Renders an ItemStack as a souvenir in a glass block "snow globe".
     * If the item is a block, it is centered and scaled up to fill the globe.
     * If the item is not a block, it is scaled down and centered to appear inside the globe.
     * The original item or block is rendered inside the glass using the appropriate transform context.
     * @param stack The ItemStack to render as a souvenir.
     * @param context The context in which the item is being rendered.
     * @param pose The current transformation matrix.
     * @param buffers The render target.
     * @param light The light level.
     * @param overlay The overlay to render.
     */
    @Override
    public void renderByItem(
        @Nonnull final ItemStack stack, 
        @Nonnull final ItemDisplayContext context, 
        @Nonnull final PoseStack pose, 
        @Nonnull final MultiBufferSource buffers, 
        final int light, 
        final int overlay) 
    {

        Item original = SouvenirItem.getOriginal(stack);
        if (original == null) 
        {
            return;
        }

        ItemStack originalStack = new ItemStack(original);
        boolean isBlock = original instanceof BlockItem;

        Minecraft mc = Minecraft.getInstance();

        // First, render a glass block as base "snow globe"
        ItemStack glass = new ItemStack(Blocks.GLASS);
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.75);
        pose.scale(1.2f, 1.2f, 1.2f);
        mc.getItemRenderer().renderStatic(glass, ItemDisplayContext.FIXED, light, overlay, pose, buffers, mc.level, 0);
        pose.popPose();

        // Then, render the original item or block inside the glass
        ItemDisplayContext appliedTransform = ItemDisplayContext.GROUND; // isBlock ? ItemDisplayContext.FIXED : ItemDisplayContext.GROUND;

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.7);
        pose.scale(0.7f, 0.7f, 0.7f);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        float rotation = (mc.level.getGameTime() % 360) * 1.0f;
        pose.mulPose(Axis.YP.rotationDegrees(rotation)); 

        mc.getItemRenderer().renderStatic(originalStack, appliedTransform, light, overlay, pose, buffers, mc.level, 0);
        
        pose.popPose();
    }
}
