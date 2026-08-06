package com.deathfrog.mctradepost.core.client.render;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.entity.WagonEntity;
import com.deathfrog.mctradepost.core.client.model.TradeCartModel;
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

public class WagonRenderer extends EntityRenderer<WagonEntity>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/entity/tradecart.png");
    private final TradeCartModel model;
    private final ItemRenderer itemRenderer;

    public WagonRenderer(EntityRendererProvider.Context context)
    {
        super(context);
        model = new TradeCartModel(context.bakeLayer(ModelRegistryHandler.TRADE_CART));
        itemRenderer = context.getItemRenderer();
        shadowRadius = 0.75F;
    }

    @Override
    public void render(@Nonnull WagonEntity wagon, float yaw, float partialTick, @Nonnull PoseStack pose,
        @Nonnull MultiBufferSource buffers, int light)
    {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(0.0F, -1.5F, 0.0F);
        model.setupAnim(wagon, 0, 0, wagon.tickCount + partialTick, 0, 0);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        model.renderToBuffer(pose, consumer, light, OverlayTexture.NO_OVERLAY, -1);
        pose.popPose();

        if (!wagon.getTradeItem().isEmpty())
        {
            pose.pushPose();
            pose.translate(0.0D, 0.9D, 0.0D);
            pose.scale(1.1F, 1.1F, 1.1F);
            itemRenderer.renderStatic(wagon.getTradeItem(), ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, wagon.level(), wagon.getId());
            pose.popPose();
        }
        super.render(wagon, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public @Nonnull ResourceLocation getTextureLocation(@Nonnull WagonEntity entity)
    {
        return TEXTURE;
    }
}
