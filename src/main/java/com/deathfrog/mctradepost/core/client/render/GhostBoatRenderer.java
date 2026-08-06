package com.deathfrog.mctradepost.core.client.render;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.entity.GhostBoatEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Temporary boat renderer using the vanilla oak-boat item model. */
public class GhostBoatRenderer extends EntityRenderer<GhostBoatEntity>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/boat/oak.png");
    private final ItemRenderer itemRenderer;

    public GhostBoatRenderer(EntityRendererProvider.Context context)
    {
        super(context);
        itemRenderer = context.getItemRenderer();
        shadowRadius = 0.8F;
    }

    @Override
    public void render(GhostBoatEntity entity, float yaw, float partialTick, @Nonnull PoseStack pose,
        @Nonnull MultiBufferSource buffers, int light)
    {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        pose.scale(1.8F, 1.8F, 1.8F);
        itemRenderer.renderStatic(new ItemStack(Items.OAK_BOAT), ItemDisplayContext.GROUND, light, 0, pose, buffers, entity.level(), entity.getId());
        pose.popPose();
        if (!entity.getTradeItem().isEmpty())
        {
            pose.pushPose();
            pose.translate(0.0D, 0.55D, 0.0D);
            pose.scale(1.1F, 1.1F, 1.1F);
            itemRenderer.renderStatic(entity.getTradeItem(), ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, entity.level(), entity.getId());
            pose.popPose();
        }
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public @Nonnull ResourceLocation getTextureLocation(@Nonnull GhostBoatEntity entity)
    {
        return TEXTURE;
    }
}
