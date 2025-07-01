package com.deathfrog.mctradepost.core.client.render;

import javax.annotation.Nonnull;

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
public class GhostCartRenderer extends MinecartRenderer<GhostCartEntity> {

    private final ItemRenderer itemRenderer;

    public GhostCartRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, ModelLayers.MINECART);
        this.itemRenderer = ctx.getItemRenderer();      // reuse vanilla renderer
    }

    @Override
    public void render(@Nonnull GhostCartEntity cart, float yaw, float partialTicks,
                       @Nonnull PoseStack pose, @Nonnull MultiBufferSource buf, int light) {

        super.render(cart, yaw, partialTicks, pose, buf, light); // cart model

        ItemStack stack = cart.getTradeItem();
        if (stack.isEmpty()) return;

        pose.pushPose();
        pose.scale(1.20F, 1.20F, 1.20F);  // a bit larger than default block
        pose.translate(0.0, 0.1, 0.0);   // Sitting low in the cart

        pose.mulPose(Axis.YP.rotationDegrees((cart.tickCount + partialTicks) * 4F)); // slow spin (optional)


        /* --- render item as a flat (GROUND) transform --- */
        itemRenderer.renderStatic(stack,
                                  ItemDisplayContext.GROUND,
                                  light,
                                  OverlayTexture.NO_OVERLAY,
                                  pose, buf, null, cart.getId());

        pose.popPose();
    }
}
