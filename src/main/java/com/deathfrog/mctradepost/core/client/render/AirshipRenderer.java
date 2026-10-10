package com.deathfrog.mctradepost.core.client.render;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.entity.AirshipEntity;
import com.deathfrog.mctradepost.core.client.model.AirshipModel;
import com.deathfrog.mctradepost.core.event.ModelRegistryHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/** Renders the visual vehicle used by air trade-route endpoint segments. */
public class AirshipRenderer extends EntityRenderer<AirshipEntity>
{
    @SuppressWarnings("null")
    private static final @Nonnull ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        MCTradePostMod.MODID, "textures/entity/airship.png");

    private final AirshipModel model;
    private final ItemRenderer itemRenderer;

    @SuppressWarnings("null")
    public AirshipRenderer(EntityRendererProvider.Context context)
    {
        super(context);
        model = new AirshipModel(context.bakeLayer(ModelRegistryHandler.AIRSHIP));
        itemRenderer = context.getItemRenderer();
        shadowRadius = 1.0F;
    }

    @SuppressWarnings("null")
    @Override
    public void render(@Nonnull AirshipEntity airship, float yaw, float partialTick, @Nonnull PoseStack pose,
        @Nonnull MultiBufferSource buffers, int light)
    {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(0.0F, -1.5F, 0.0F);
        model.setupAnim(airship, 0, 0, airship.tickCount + partialTick, 0, 0);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        model.renderToBuffer(pose, consumer, light, OverlayTexture.NO_OVERLAY, -1);
        pose.popPose();

        if (!airship.getTradeItem().isEmpty())
        {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
            pose.translate(0.0D, 0.3D, 0.125D);
            pose.scale(0.9F, 0.9F, 0.9F);
            pose.mulPose(Axis.YP.rotationDegrees((airship.tickCount + partialTick) * 4.0F));
            itemRenderer.renderStatic(airship.getTradeItem(), ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, airship.level(), airship.getId());
            pose.popPose();
        }

        super.render(airship, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public @Nonnull ResourceLocation getTextureLocation(@Nonnull AirshipEntity entity)
    {
        return TEXTURE;
    }
}
