package com.deathfrog.mctradepost.core.client.render;

import javax.annotation.Nonnull;

import org.joml.Quaternionf;

import com.deathfrog.mctradepost.api.entity.GhostCartEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.MinecartRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

@OnlyIn(Dist.CLIENT)
public class GhostCartRenderer extends MinecartRenderer<GhostCartEntity>
{
    private final ItemRenderer itemRenderer;

    public GhostCartRenderer(EntityRendererProvider.Context ctx)
    {
        super(ctx, ModelLayers.MINECART);
        this.itemRenderer = ctx.getItemRenderer();      // reuse vanilla renderer
    }

    /**
     * Renders the given GhostCartEntity, including its trade item if it has one.
     * The trade item is rendered as if it were on the ground, and is scaled up slightly to be more visible.
     * The item is also rotated to face the direction of the cart's movement.
     *
     * @param cart the GhostCartEntity to render
     * @param yaw the yaw of the cart
     * @param partialTicks the partial ticks of the cart's movement
     * @param pose the PoseStack to use for rendering
     * @param buf the MultiBufferSource to render into
     * @param light the light level to render with
     */
    @Override
    public void render(@Nonnull GhostCartEntity cart,
        float yaw,
        float partialTicks,
        @Nonnull PoseStack pose,
        @Nonnull MultiBufferSource buf,
        int light)
    {
        super.render(cart, yaw, partialTicks, pose, buf, light); // cart model

        ItemStack stack = cart.getTradeItem();
        if (stack.isEmpty()) return;

        pose.pushPose();
        pose.scale(1.20F, 1.20F, 1.20F);  // a bit larger than default block
        pose.translate(0.0, 0.1, 0.0);   // Sitting low in the cart

        Quaternionf spin = Axis.YP.rotationDegrees((cart.tickCount + partialTicks) * 4F);

        if (spin != null) 
        {
            pose.mulPose(spin);
        }

        /* --- render item as a flat (GROUND) transform --- */
        itemRenderer.renderStatic(stack, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, pose, buf, null, cart.getId());

        pose.popPose();
    }
}
