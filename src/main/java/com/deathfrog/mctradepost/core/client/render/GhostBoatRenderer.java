package com.deathfrog.mctradepost.core.client.render;

import java.util.List;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.entity.GhostBoatEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/** Renders the shared trade vehicle controller with the vanilla oak boat entity model. */
public class GhostBoatRenderer extends EntityRenderer<GhostBoatEntity>
{
    @SuppressWarnings("null")
    private static final @Nonnull ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/boat/oak.png");
    private final ItemRenderer itemRenderer;
    private final List<ModelPart> boatParts;
    private final ModelPart leftPaddle;
    private final ModelPart rightPaddle;

    @SuppressWarnings("null")
    public GhostBoatRenderer(EntityRendererProvider.Context context)
    {
        super(context);
        itemRenderer = context.getItemRenderer();
        ModelPart root = context.bakeLayer(ModelLayers.createBoatModelName(Boat.Type.OAK));
        leftPaddle = root.getChild("left_paddle");
        rightPaddle = root.getChild("right_paddle");
        boatParts = List.of(root.getChild("bottom"), root.getChild("back"), root.getChild("front"),
            root.getChild("right"), root.getChild("left"), leftPaddle, rightPaddle);
        shadowRadius = 0.8F;
    }

    @SuppressWarnings("null")
    @Override
    public void render(@Nonnull GhostBoatEntity entity, float yaw, float partialTick, @Nonnull PoseStack pose,
        @Nonnull MultiBufferSource buffers, int light)
    {
        pose.pushPose();
        pose.translate(0.0F, 0.375F, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.mulPose(Axis.YP.rotationDegrees(90.0F));
        animatePaddles(entity.tickCount + partialTick);
        VertexConsumer boatBuffer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (ModelPart part : boatParts) part.render(pose, boatBuffer, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        if (!entity.getTradeItem().isEmpty())
        {
            pose.pushPose();
            pose.translate(0.0D, 0.55D, 0.0D);
            pose.scale(1.1F, 1.1F, 1.1F);
            pose.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * 4.0F));
            itemRenderer.renderStatic(entity.getTradeItem(), ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, entity.level(), entity.getId());
            pose.popPose();
        }
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    private void animatePaddles(float age)
    {
        animatePaddle(leftPaddle, age * 0.18F, false);
        animatePaddle(rightPaddle, age * 0.18F + (float) Math.PI, true);
    }

    private static void animatePaddle(ModelPart paddle, float phase, boolean right)
    {
        paddle.xRot = Mth.clampedLerp((float) (-Math.PI / 3), (float) (-Math.PI / 12), (Mth.sin(-phase) + 1.0F) / 2.0F);
        paddle.yRot = Mth.clampedLerp((float) (-Math.PI / 4), (float) (Math.PI / 4), (Mth.sin(-phase + 1.0F) + 1.0F) / 2.0F);
        if (right) paddle.yRot = (float) Math.PI - paddle.yRot;
    }

    @Override
    public @Nonnull ResourceLocation getTextureLocation(@Nonnull GhostBoatEntity entity)
    {
        return TEXTURE;
    }
}
