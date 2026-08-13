package com.deathfrog.mctradepost.core.client.model;

import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.api.entity.pets.PetDragon;
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
import net.minecraft.util.Mth;

/** Java model generated from pet_dragon.bbmodel, with procedural upright-dragon animation. */
public class PetDragonModel extends EntityModel<PetDragon>
{
    private final ModelPart root;
    @SuppressWarnings("unused")
    private final ModelPart dragon;
    private final ModelPart body;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart front_left_leg;
    private final ModelPart front_right_leg;
    private final ModelPart front_left_forearm;
    private final ModelPart front_right_forearm;
    private final ModelPart top_digits_left;
    private final ModelPart bottom_digit_left;
    private final ModelPart top_digits_right;
    private final ModelPart bottom_digit_right;
    private final ModelPart rear_left_leg;
    private final ModelPart rear_right_leg;
    private final ModelPart left_wing;
    private final ModelPart right_wing;
    private final ModelPart tail_1;
    private final ModelPart tail_2;
    private final ModelPart tail_3;
    private final ModelPart tail_tip;
    private final ModelPart spine;

    public PetDragonModel(ModelPart root)
    {
        this.root = root;
        this.dragon = root.getChild("dragon");
        this.body = root.getChild("dragon").getChild("body");
        this.neck = root.getChild("dragon").getChild("neck");
        this.head = root.getChild("dragon").getChild("head");
        this.jaw = root.getChild("dragon").getChild("head").getChild("jaw");
        this.front_left_leg = root.getChild("dragon").getChild("front_left_leg");
        this.front_right_leg = root.getChild("dragon").getChild("front_right_leg");
        this.front_left_forearm = root.getChild("dragon").getChild("front_left_leg").getChild("front_left_forearm");
        this.front_right_forearm = root.getChild("dragon").getChild("front_right_leg").getChild("front_right_forearm");
        this.top_digits_left = this.front_left_forearm.getChild("front_left_claw").getChild("top_digits_left");
        this.bottom_digit_left = this.front_left_forearm.getChild("front_left_claw").getChild("bottom_digit_left");
        this.top_digits_right = this.front_right_forearm.getChild("front_right_claw").getChild("top_digits_right");
        this.bottom_digit_right = this.front_right_forearm.getChild("front_right_claw").getChild("bottom_digit_right");
        this.rear_left_leg = root.getChild("dragon").getChild("rear_left_leg");
        this.rear_right_leg = root.getChild("dragon").getChild("rear_right_leg");
        this.left_wing = root.getChild("dragon").getChild("left_wing");
        this.right_wing = root.getChild("dragon").getChild("right_wing");
        this.tail_1 = root.getChild("dragon").getChild("tail_1");
        this.tail_2 = root.getChild("dragon").getChild("tail_1").getChild("tail_2");
        this.tail_3 = root.getChild("dragon").getChild("tail_1").getChild("tail_2").getChild("tail_3");
        this.tail_tip = root.getChild("dragon").getChild("tail_1").getChild("tail_2").getChild("tail_3").getChild("tail_tip");
        this.spine = root.getChild("dragon").getChild("spine");
    }

    @SuppressWarnings({"null", "unused"})
    public static LayerDefinition createBodyLayer()
    {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition dragon = partdefinition.addOrReplaceChild("dragon", CubeListBuilder.create(), PartPose.offset(0.0536F, 3.3793F, 0.0092F));

		PartDefinition body = dragon.addOrReplaceChild("body", CubeListBuilder.create().texOffs(82, 51).addBox(-6.0F, -6.8333F, -4.5F, 12.0F, 13.0F, 11.0F, new CubeDeformation(0.25F))
		.texOffs(82, 86).addBox(-7.0F, -8.8333F, -6.5F, 14.0F, 10.0F, 9.0F, new CubeDeformation(0.15F))
		.texOffs(36, 0).addBox(-5.0F, 1.1667F, -3.5F, 10.0F, 6.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.0536F, 4.454F, -0.5092F));

		PartDefinition neck = dragon.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 43).addBox(-3.5F, -5.768F, -4.3124F, 7.0F, 7.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.0536F, -4.6113F, -5.6969F));

		PartDefinition neck_lower_r1 = neck.addOrReplaceChild("neck_lower_r1", CubeListBuilder.create().texOffs(36, 16).addBox(-4.0F, -7.0F, -6.0F, 8.0F, 8.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 4.232F, 4.6876F, 0.3927F, 0.0F, 0.0F));

		PartDefinition head = dragon.addOrReplaceChild("head", CubeListBuilder.create().texOffs(36, 112).addBox(-5.0F, -3.1356F, -3.0459F, 10.0F, 8.0F, 8.0F, new CubeDeformation(0.15F))
		.texOffs(8, 115).addBox(-3.5F, 0.8644F, -9.0459F, 7.0F, 3.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.0536F, -12.2438F, -11.9633F));

		PartDefinition ear_right_r1 = head.addOrReplaceChild("ear_right_r1", CubeListBuilder.create().texOffs(114, 0).addBox(0.0F, -4.0F, -2.0F, 3.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.0F, 1.8644F, 1.9541F, 0.0F, 0.0F, 0.3927F));

		PartDefinition ear_left_r1 = head.addOrReplaceChild("ear_left_r1", CubeListBuilder.create().texOffs(114, 11).addBox(-3.0F, -4.0F, -2.0F, 3.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.0F, 1.8644F, 1.9541F, 0.0F, 0.0F, -0.3927F));

		PartDefinition horn_right_r1 = head.addOrReplaceChild("horn_right_r1", CubeListBuilder.create().texOffs(37, 0).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(37, 0).addBox(-7.0F, -6.0F, -1.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, -1.1356F, 2.9541F, -0.3927F, 0.0F, 0.0F));

		PartDefinition brow_right_r1 = head.addOrReplaceChild("brow_right_r1", CubeListBuilder.create().texOffs(72, 120).addBox(-1.0F, -3.0F, -4.0F, 4.0F, 3.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, -0.1356F, -1.0459F, 0.0F, 0.0F, -0.3927F));

		PartDefinition brow_left_r1 = head.addOrReplaceChild("brow_left_r1", CubeListBuilder.create().texOffs(72, 112).addBox(-3.0F, -3.0F, -4.0F, 4.0F, 3.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.0F, -0.1356F, -1.0459F, 0.0F, 0.0F, 0.3927F));

		PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(100, 105).addBox(-4.0F, -1.25F, -5.5F, 8.0F, 3.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(20, 111).addBox(1.5F, -1.75F, -7.0F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(20, 111).addBox(-2.5F, -1.75F, -7.0F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 4.6144F, -1.0459F));

		PartDefinition front_left_leg = dragon.addOrReplaceChild("front_left_leg", CubeListBuilder.create(), PartPose.offset(5.6964F, -1.3188F, -5.5307F));

		PartDefinition front_left_upper_r1 = front_left_leg.addOrReplaceChild("front_left_upper_r1", CubeListBuilder.create().texOffs(-2, 67).addBox(-1.5F, -1.0F, -3.5F, 3.0F, 2.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.25F, 1.0605F, -1.5215F, 0.3927F, 0.0F, 0.0F));

		PartDefinition front_left_forearm = front_left_leg.addOrReplaceChild("front_left_forearm", CubeListBuilder.create(), PartPose.offset(-0.15F, 2.6472F, -4.0999F));

		PartDefinition front_left_lower_r1 = front_left_forearm.addOrReplaceChild("front_left_lower_r1", CubeListBuilder.create().texOffs(30, 67).addBox(-1.0F, -0.3281F, -4.9838F, 2.0F, 2.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.1F, -1.1078F, -0.1786F, -0.3927F, 0.0F, 0.0F));

		PartDefinition front_left_claw = front_left_forearm.addOrReplaceChild("front_left_claw", CubeListBuilder.create().texOffs(0, 89).addBox(-1.2F, -1.7F, -1.6471F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.1F, -2.0078F, -4.7315F));

		PartDefinition top_digits_left = front_left_claw.addOrReplaceChild("top_digits_left", CubeListBuilder.create(), PartPose.offset(0.3F, -1.3F, -0.3971F));

		PartDefinition digit_three_r1 = top_digits_left.addOrReplaceChild("digit_three_r1", CubeListBuilder.create().texOffs(7, 91).addBox(0.6F, -0.2119F, -1.5F, 0.8F, 1.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(7, 91).addBox(-0.4F, -0.2119F, -1.5F, 0.8F, 1.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(7, 91).addBox(-1.4F, -0.2119F, -1.5F, 0.8F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.4F, -0.9381F, -1.9413F, -0.3927F, 0.0F, 0.0F));

		PartDefinition bottom_digit_left = front_left_claw.addOrReplaceChild("bottom_digit_left", CubeListBuilder.create(), PartPose.offset(-0.1F, -0.2381F, -1.9558F));

		PartDefinition digit_lower_r1 = bottom_digit_left.addOrReplaceChild("digit_lower_r1", CubeListBuilder.create().texOffs(8, 90).addBox(-0.4F, -0.8827F, -2.4239F, 0.8F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 1.0F, 0.3927F, 0.0F, 0.0F));

		PartDefinition front_right_leg = dragon.addOrReplaceChild("front_right_leg", CubeListBuilder.create(), PartPose.offset(-5.3036F, -1.3188F, -5.5307F));

		PartDefinition front_right_upper_r1 = front_right_leg.addOrReplaceChild("front_right_upper_r1", CubeListBuilder.create().texOffs(-2, 67).addBox(-1.5F, -1.0F, -3.5F, 3.0F, 2.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.25F, 1.0605F, -1.5215F, 0.3927F, 0.0F, 0.0F));

		PartDefinition front_right_forearm = front_right_leg.addOrReplaceChild("front_right_forearm", CubeListBuilder.create(), PartPose.offset(-0.15F, 1.6472F, -4.0999F));

		PartDefinition front_right_lower_r1 = front_right_forearm.addOrReplaceChild("front_right_lower_r1", CubeListBuilder.create().texOffs(30, 67).addBox(-1.0F, -0.3281F, -4.9838F, 2.0F, 2.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.1F, -0.1078F, -0.1786F, -0.3927F, 0.0F, 0.0F));

		PartDefinition front_right_claw = front_right_forearm.addOrReplaceChild("front_right_claw", CubeListBuilder.create().texOffs(0, 84).addBox(-1.2F, -1.7F, -1.6471F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.1F, -1.0078F, -4.7315F));

		PartDefinition top_digits_right = front_right_claw.addOrReplaceChild("top_digits_right", CubeListBuilder.create(), PartPose.offset(0.3F, -1.3F, -0.3971F));

		PartDefinition digit_three_r2 = top_digits_right.addOrReplaceChild("digit_three_r2", CubeListBuilder.create().texOffs(7, 91).addBox(0.6F, -0.2119F, -1.5F, 0.8F, 1.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(7, 91).addBox(-0.4F, -0.2119F, -1.5F, 0.8F, 1.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(7, 91).addBox(-1.4F, -0.2119F, -1.5F, 0.8F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.4F, -0.9381F, -1.9413F, -0.3927F, 0.0F, 0.0F));

		PartDefinition bottom_digit_right = front_right_claw.addOrReplaceChild("bottom_digit_right", CubeListBuilder.create(), PartPose.offset(-0.1F, -0.2381F, -1.9558F));

		PartDefinition digit_lower_r2 = bottom_digit_right.addOrReplaceChild("digit_lower_r2", CubeListBuilder.create().texOffs(8, 90).addBox(-0.4F, -0.8827F, -2.4239F, 0.8F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 1.0F, 0.3927F, 0.0F, 0.0F));

		PartDefinition rear_right_leg = dragon.addOrReplaceChild("rear_right_leg", CubeListBuilder.create().texOffs(44, 70).addBox(-1.5F, 2.0F, -0.5F, 3.0F, 8.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(18, 86).addBox(-2.0F, 9.5F, -2.5F, 4.0F, 2.5F, 6.5F, new CubeDeformation(0.0F)), PartPose.offset(-5.0536F, 8.6207F, 0.9908F));

		PartDefinition rear_right_upper_r1 = rear_right_leg.addOrReplaceChild("rear_right_upper_r1", CubeListBuilder.create().texOffs(112, 115).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 9.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -4.0F, 1.0F, -0.3927F, 0.0F, 0.0F));

		PartDefinition rear_right_claw = rear_right_leg.addOrReplaceChild("rear_right_claw", CubeListBuilder.create().texOffs(0, 124).addBox(-2.0F, -1.4F, -3.4413F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 124).addBox(-0.5F, -1.4F, -3.4413F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 124).addBox(1.0F, -1.4F, -3.4413F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 12.4F, -1.75F));

		PartDefinition rear_left_leg = dragon.addOrReplaceChild("rear_left_leg", CubeListBuilder.create().texOffs(44, 70).addBox(-1.5F, 2.0F, -0.5F, 3.0F, 8.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(18, 86).addBox(-2.0F, 9.5F, -2.5F, 4.0F, 2.5F, 6.5F, new CubeDeformation(0.0F)), PartPose.offset(4.9464F, 8.6207F, 0.9908F));

		PartDefinition rear_left_upper_r1 = rear_left_leg.addOrReplaceChild("rear_left_upper_r1", CubeListBuilder.create().texOffs(16, 70).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 9.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -4.0F, 1.0F, -0.3927F, 0.0F, 0.0F));

		PartDefinition rear_left_claw = rear_left_leg.addOrReplaceChild("rear_left_claw", CubeListBuilder.create().texOffs(0, 124).addBox(-2.0F, -1.4F, -3.4413F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 124).addBox(-0.5F, -1.4F, -3.4413F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 124).addBox(1.0F, -1.4F, -3.4413F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 12.4F, -1.75F));

		PartDefinition left_wing = dragon.addOrReplaceChild("left_wing", CubeListBuilder.create(), PartPose.offset(-7.0585F, -2.0054F, 3.7408F));

		PartDefinition left_wing_membrane_r1 = left_wing.addOrReplaceChild("left_wing_membrane_r1", CubeListBuilder.create().texOffs(48, 28).addBox(-0.3673F, -6.4239F, -3.5F, 1.5F, 11.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.8105F, -0.662F, 0.75F, 0.0F, 0.0F, -0.3927F));

		PartDefinition left_wing_arm_r1 = left_wing.addOrReplaceChild("left_wing_arm_r1", CubeListBuilder.create().texOffs(32, 86).addBox(-3.0F, -3.0F, -2.0F, 3.0F, 7.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.005F, 0.626F, -2.75F, 0.0F, 0.0F, -0.3927F));

		PartDefinition right_wing = dragon.addOrReplaceChild("right_wing", CubeListBuilder.create(), PartPose.offset(6.9514F, -2.5054F, 3.7408F));

		PartDefinition right_wing_membrane_r1 = right_wing.addOrReplaceChild("right_wing_membrane_r1", CubeListBuilder.create().texOffs(107, 31).addBox(-1.1327F, -6.4239F, -3.5F, 1.5F, 11.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.8105F, -0.162F, 0.75F, 0.0F, 0.0F, 0.3927F));

		PartDefinition right_wing_arm_r1 = right_wing.addOrReplaceChild("right_wing_arm_r1", CubeListBuilder.create().texOffs(32, 86).addBox(0.0F, -3.0F, -2.0F, 3.0F, 7.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.005F, 1.126F, -2.75F, 0.0F, 0.0F, 0.3927F));

		PartDefinition tail_1 = dragon.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(51, 85).addBox(-4.0F, -4.0F, -4.25F, 8.0F, 6.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.0536F, 6.6207F, 9.2408F));

		PartDefinition tail_2 = tail_1.addOrReplaceChild("tail_2", CubeListBuilder.create(), PartPose.offset(0.0F, -1.0F, 0.75F));

		PartDefinition tail_2_cube_r1 = tail_2.addOrReplaceChild("tail_2_cube_r1", CubeListBuilder.create().texOffs(32, 9).addBox(-3.0F, -2.0F, 0.0F, 6.0F, 5.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.3927F, 0.0F, 0.0F));

		PartDefinition tail_3 = tail_2.addOrReplaceChild("tail_3", CubeListBuilder.create(), PartPose.offset(0.0F, 3.0F, 6.0F));

		PartDefinition tail_3_cube_r1 = tail_3.addOrReplaceChild("tail_3_cube_r1", CubeListBuilder.create().texOffs(64, 30).addBox(-2.0F, -1.0F, 0.0F, 4.0F, 4.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.0F, 0.0F, -0.3927F, 0.0F, 0.0F));

		PartDefinition tail_tip = tail_3.addOrReplaceChild("tail_tip", CubeListBuilder.create(), PartPose.offset(0.0F, 3.0F, 6.0F));

		PartDefinition tail_tip_cube_r1 = tail_tip.addOrReplaceChild("tail_tip_cube_r1", CubeListBuilder.create().texOffs(64, 42).addBox(-1.0F, -0.8415F, -1.7696F, 2.0F, 3.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.0834F, 0.1215F, -0.3927F, 0.0F, 0.0F));

		PartDefinition spine = dragon.addOrReplaceChild("spine", CubeListBuilder.create(), PartPose.offset(-0.0536F, -2.645F, 4.4657F));

		PartDefinition back_spine_5_r1 = spine.addOrReplaceChild("back_spine_5_r1", CubeListBuilder.create().texOffs(114, 19).addBox(-1.0F, -5.5F, -1.5F, 2.0F, 7.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 8.0782F, 6.1877F, -0.3927F, 0.0F, 0.0F));

		PartDefinition back_spine_4_r1 = spine.addOrReplaceChild("back_spine_4_r1", CubeListBuilder.create().texOffs(114, 19).addBox(-1.0F, -5.5F, -1.5F, 2.0F, 7.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 5.0782F, 2.1877F, -0.3927F, 0.0F, 0.0F));

		PartDefinition back_spine_3_r1 = spine.addOrReplaceChild("back_spine_3_r1", CubeListBuilder.create().texOffs(114, 19).addBox(-1.0F, -4.5F, -1.5F, 2.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 1.0782F, -0.8123F, -0.3927F, 0.0F, 0.0F));

		PartDefinition back_spine_2_r1 = spine.addOrReplaceChild("back_spine_2_r1", CubeListBuilder.create().texOffs(114, 19).addBox(-1.0F, -4.5F, -1.5F, 2.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.9218F, -3.8123F, -0.3927F, 0.0F, 0.0F));

		PartDefinition back_spine_1_r1 = spine.addOrReplaceChild("back_spine_1_r1", CubeListBuilder.create().texOffs(114, 19).addBox(-1.0F, -3.5F, -1.5F, 2.0F, 7.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -4.9218F, -6.8123F, -0.3927F, 0.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 128);
    }

    @SuppressWarnings("null")
    @Override
    public void setupAnim(@Nonnull PetDragon entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch)
    {
        root.getAllParts().forEach(ModelPart::resetPose);
        float walk = Mth.clamp(limbSwingAmount, 0.0F, 1.0F);
        float stride = Mth.cos(limbSwing * 0.72F) * walk;
        float airborne = entity.onGround() ? 0.0F : 1.0F;
        float flapSpeed = airborne > 0.0F ? 1.05F : 0.34F;
        float flapSize = airborne > 0.0F ? 0.62F : 0.10F + walk * 0.12F;
        float flap = Mth.sin(ageInTicks * flapSpeed) * flapSize;

        head.yRot += Mth.clamp(netHeadYaw * Mth.DEG_TO_RAD, -0.70F, 0.70F);
        head.xRot += Mth.clamp(headPitch * Mth.DEG_TO_RAD, -0.45F, 0.50F);
        neck.yRot += head.yRot * 0.35F;
        neck.xRot += head.xRot * 0.20F;
        jaw.xRot += 0.04F + (Mth.sin(ageInTicks * 0.11F) + 1.0F) * 0.025F;

        // Upright gait: hind legs provide locomotion; small forelimbs counter-swing gently.
        rear_left_leg.xRot += stride * 0.72F - airborne * 0.22F;
        rear_right_leg.xRot -= stride * 0.72F + airborne * 0.22F;
        front_left_leg.xRot -= stride * 0.16F;
        front_right_leg.xRot += stride * 0.16F;
        front_left_forearm.xRot += 0.10F + walk * 0.08F;
        front_right_forearm.xRot += 0.10F + walk * 0.08F;

        animateGrab(top_digits_left, bottom_digit_left, grabAmount(ageInTicks));
        animateGrab(top_digits_right, bottom_digit_right, grabAmount(ageInTicks + 63.0F));

        left_wing.zRot += flap + airborne * 0.22F;
        right_wing.zRot -= flap + airborne * 0.22F;
        float bodyOffsetY = Mth.abs(Mth.sin(limbSwing * 0.72F)) * walk * 0.35F - airborne * 0.45F;
        float bodyPitch = walk * 0.04F - airborne * 0.10F;
        body.y += bodyOffsetY;
        body.xRot += bodyPitch;
        // Spine is a sibling of body, so share only its animation deltas. Assigning
        // body.y directly would destroy the distinct base offsets exported by Blockbench.
        spine.y += bodyOffsetY;
        spine.xRot += bodyPitch;

        float tailWave = Mth.sin(ageInTicks * 0.09F) * 0.08F + stride * 0.10F;
        tail_1.yRot += tailWave;
        tail_2.yRot += Mth.sin(ageInTicks * 0.09F - 0.45F) * 0.11F + stride * 0.13F;
        tail_3.yRot += Mth.sin(ageInTicks * 0.09F - 0.90F) * 0.14F + stride * 0.16F;
        tail_tip.yRot += Mth.sin(ageInTicks * 0.09F - 1.35F) * 0.18F + stride * 0.20F;
    }

    /** Returns a smooth close-and-release pulse lasting 24 ticks in each 120-tick cycle. */
    private static float grabAmount(float animationTime)
    {
        float cycleTime = animationTime % 120.0F;
        if (cycleTime < 0.0F)
        {
            cycleTime += 120.0F;
        }
        if (cycleTime >= 24.0F)
        {
            return 0.0F;
        }

        float pulse = Mth.sin(Mth.PI * cycleTime / 24.0F);
        return pulse * pulse;
    }

    private static void animateGrab(ModelPart topDigits, ModelPart bottomDigit, float amount)
    {
        topDigits.xRot += amount * 0.65F;
        bottomDigit.xRot -= amount * 0.55F;
    }

    @Override
    public void renderToBuffer(@Nonnull PoseStack pose, @Nonnull VertexConsumer consumer, int light, int overlay, int color)
    {
        root.render(pose, consumer, light, overlay, color);
    }
}
