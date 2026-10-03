package com.deathfrog.mctradepost.core.client.model;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.entity.AirshipEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** Java representation of the Blockbench airship model. */
public class AirshipModel extends EntityModel<AirshipEntity>
{
    private final ModelPart root;

    public AirshipModel(ModelPart root)
    {
        this.root = root;
    }

    @SuppressWarnings({"null", "unused"})
    public static LayerDefinition createBodyLayer()
    {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(-0.3333F, 24.3333F, 3.1667F));

		PartDefinition seat = root.addOrReplaceChild("seat", CubeListBuilder.create(), PartPose.offset(-4.6667F, 6.6667F, -2.1667F));

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-10.5F, -1.0F, -11.5F, 13.0F, 2.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(4.3333F, -1.3333F, 4.3333F));

		PartDefinition cargo_area = root.addOrReplaceChild("cargo_area", CubeListBuilder.create(), PartPose.offset(0.3333F, -5.3333F, -2.1667F));

		PartDefinition left_wall = cargo_area.addOrReplaceChild("left_wall", CubeListBuilder.create().texOffs(20, 67).addBox(6.0F, -2.0F, -4.0F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(20, 74).addBox(6.0F, -2.0F, -1.0F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(70, 56).addBox(6.0F, -2.0F, 2.0F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(63, 56).addBox(6.0F, -2.0F, 5.0F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(50, 0).addBox(5.5F, -1.0F, -5.0F, 1.0F, 1.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(50, 0).addBox(5.5F, 1.0F, -5.0F, 1.0F, 1.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition right_wall = cargo_area.addOrReplaceChild("right_wall", CubeListBuilder.create().texOffs(24, 75).addBox(-7.0F, -2.0F, 2.0F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(28, 75).addBox(-7.0F, -2.0F, -1.0F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(32, 75).addBox(-7.0F, -2.0F, -4.0F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(36, 75).addBox(-7.0F, -2.0F, 5.0F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(50, 0).addBox(-6.5F, 1.0F, -5.0F, 1.0F, 1.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(50, 0).addBox(-6.5F, -1.0F, -5.0F, 1.0F, 1.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition back_wall = cargo_area.addOrReplaceChild("back_wall", CubeListBuilder.create().texOffs(36, 75).addBox(-5.0F, -2.0F, 6.5F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(36, 75).addBox(-2.0F, -2.0F, 6.5F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(36, 75).addBox(1.0F, -2.0F, 6.5F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(36, 75).addBox(4.0F, -2.0F, 6.5F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(51, 11).addBox(-5.5F, 1.0F, 6.0F, 11.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(51, 11).addBox(-5.5F, -1.0F, 6.0F, 11.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition front_wall = cargo_area.addOrReplaceChild("front_wall", CubeListBuilder.create().texOffs(36, 75).addBox(-5.0F, -2.8333F, -0.6667F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(36, 75).addBox(-2.0F, -2.8333F, -0.6667F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(36, 75).addBox(1.0F, -2.8333F, -0.6667F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(36, 75).addBox(4.0F, -2.8333F, -0.6667F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(51, 11).addBox(-5.5F, 0.1667F, -0.1667F, 11.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(51, 11).addBox(-5.5F, -1.8333F, -0.1667F, 11.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.8333F, -4.8333F));

		PartDefinition support = root.addOrReplaceChild("support", CubeListBuilder.create(), PartPose.offset(-5.8917F, -6.8333F, -6.9417F));

		PartDefinition front_right = support.addOrReplaceChild("front_right", CubeListBuilder.create().texOffs(120, 0).addBox(-0.975F, -1.5F, -1.025F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(124, 10).addBox(-0.525F, -2.5F, -0.475F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(124, 10).addBox(-6.525F, -17.5F, -0.475F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition front_right_rope_r1 = front_right.addOrReplaceChild("front_right_rope_r1", CubeListBuilder.create().texOffs(114, 0).addBox(-1.0F, -16.0F, -0.5F, 1.0F, 16.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.475F, -2.0F, 0.025F, 0.0F, 0.0F, -0.3927F));

		PartDefinition back_right = support.addOrReplaceChild("back_right", CubeListBuilder.create().texOffs(120, 0).addBox(-0.975F, -1.5F, -1.025F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(124, 10).addBox(-0.525F, -2.5F, -0.475F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(124, 10).addBox(-6.525F, -17.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 11.525F));

		PartDefinition back_right_rope_r1 = back_right.addOrReplaceChild("back_right_rope_r1", CubeListBuilder.create().texOffs(114, 0).addBox(-1.0F, -16.0F, -0.5F, 1.0F, 16.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.475F, -2.0F, 0.0F, 0.0F, 0.0F, -0.3927F));

		PartDefinition front_left = support.addOrReplaceChild("front_left", CubeListBuilder.create().texOffs(120, 0).addBox(11.425F, -1.5F, -1.025F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(124, 10).addBox(11.975F, -2.5F, -0.475F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(124, 10).addBox(17.975F, -17.5F, -0.475F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition front_left_rope_r1 = front_left.addOrReplaceChild("front_left_rope_r1", CubeListBuilder.create().texOffs(114, 0).addBox(0.0F, -16.0F, -0.5F, 1.0F, 16.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(11.975F, -2.0F, 0.025F, 0.0F, 0.0F, 0.3927F));

		PartDefinition back_left = support.addOrReplaceChild("back_left", CubeListBuilder.create().texOffs(120, 0).addBox(-1.025F, -1.5F, -1.025F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(124, 10).addBox(5.525F, -17.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(124, 10).addBox(-0.475F, -2.5F, -0.475F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(12.45F, 0.0F, 11.525F));

		PartDefinition back_left_rope_r1 = back_left.addOrReplaceChild("back_left_rope_r1", CubeListBuilder.create().texOffs(114, 0).addBox(0.0F, -16.0F, -0.5F, 1.0F, 16.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.475F, -2.0F, 0.0F, 0.0F, 0.0F, 0.3927F));

		PartDefinition balloon = root.addOrReplaceChild("balloon", CubeListBuilder.create().texOffs(16, 80).addBox(-12.0F, -8.0F, -14.1429F, 24.0F, 16.0F, 32.0F, new CubeDeformation(0.0F))
		.texOffs(12, 14).addBox(4.0F, 10.0F, 7.0F, 1.0F, 3.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(17, 20).addBox(-1.0F, 10.0F, 12.0F, 2.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 89).addBox(-3.0F, 8.0F, -5.0F, 6.0F, 5.0F, 18.0F, new CubeDeformation(0.0F))
		.texOffs(17, 19).addBox(-0.5F, 13.5F, 13.3571F, 1.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(12, 14).addBox(-5.0F, 10.0F, 7.0F, 1.0F, 3.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(25, 68).addBox(-3.0F, 11.0F, 17.0F, 6.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.3333F, -32.3333F, -3.0238F));

		PartDefinition left = balloon.addOrReplaceChild("left", CubeListBuilder.create().texOffs(40, 82).addBox(11.0F, -7.0F, -13.1429F, 2.0F, 14.0F, 30.0F, new CubeDeformation(0.0F))
		.texOffs(42, 84).addBox(12.0F, -6.0F, -12.1429F, 2.0F, 12.0F, 28.0F, new CubeDeformation(0.0F))
		.texOffs(44, 86).addBox(13.0F, -5.0F, -11.1429F, 2.0F, 10.0F, 26.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition right = balloon.addOrReplaceChild("right", CubeListBuilder.create().texOffs(42, 84).addBox(-14.0F, -6.0F, -12.1429F, 2.0F, 12.0F, 28.0F, new CubeDeformation(0.0F))
		.texOffs(40, 82).addBox(-13.0F, -7.0F, -13.1429F, 2.0F, 14.0F, 30.0F, new CubeDeformation(0.0F))
		.texOffs(44, 86).addBox(-15.0F, -5.0F, -11.1429F, 2.0F, 10.0F, 26.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition top = balloon.addOrReplaceChild("top", CubeListBuilder.create().texOffs(20, 82).addBox(-11.0F, -9.0F, -13.1429F, 22.0F, 2.0F, 30.0F, new CubeDeformation(0.0F))
		.texOffs(24, 84).addBox(-10.0F, -10.0F, -12.1429F, 20.0F, 2.0F, 28.0F, new CubeDeformation(0.0F))
		.texOffs(28, 86).addBox(-9.0F, -11.0F, -11.1429F, 18.0F, 2.0F, 26.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition bottom = balloon.addOrReplaceChild("bottom", CubeListBuilder.create().texOffs(28, 86).addBox(-9.0F, 9.0F, -11.1429F, 18.0F, 2.0F, 26.0F, new CubeDeformation(0.0F))
		.texOffs(24, 84).addBox(-10.0F, 8.0F, -12.1429F, 20.0F, 2.0F, 28.0F, new CubeDeformation(0.0F))
		.texOffs(20, 82).addBox(-11.0F, 7.0F, -13.1429F, 22.0F, 2.0F, 30.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition fore = balloon.addOrReplaceChild("fore", CubeListBuilder.create().texOffs(47, 111).addBox(-11.0F, -7.0F, 0.0F, 22.0F, 14.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(49, 111).addBox(-10.0F, -6.0F, -2.0F, 20.0F, 12.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(51, 111).addBox(-9.0F, -5.0F, -4.0F, 18.0F, 10.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(53, 111).addBox(-8.0F, -4.0F, -5.0F, 16.0F, 8.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(55, 111).addBox(-7.0F, -3.0F, -6.0F, 14.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -16.1429F));

		PartDefinition aft = balloon.addOrReplaceChild("aft", CubeListBuilder.create().texOffs(47, 111).addBox(-11.0F, -7.0F, 16.8571F, 22.0F, 14.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(50, 112).addBox(-10.0F, -6.0F, 18.8571F, 20.0F, 12.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(52, 112).addBox(-9.0F, -5.0F, 19.8571F, 18.0F, 10.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 128);
    }

    @Override
    public void setupAnim(@Nonnull AirshipEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
        float netHeadYaw, float headPitch)
    {
        // Animation is intentionally deferred. Named model parts retain stable pivots for that later work.
    }

    @Override
    public void renderToBuffer(@Nonnull PoseStack pose, @Nonnull VertexConsumer consumer, int light, int overlay, int color)
    {
        root.render(pose, consumer, light, overlay, color);
    }
}
