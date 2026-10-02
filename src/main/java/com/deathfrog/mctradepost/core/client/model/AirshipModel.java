package com.deathfrog.mctradepost.core.client.model;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.entity.AirshipEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
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

    @SuppressWarnings("null")
    public static LayerDefinition createBodyLayer()
    {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeListBuilder body = CubeListBuilder.create();

        // Gondola and cargo railings.
        cube(body, 0, 0, -6.5F, -2, -4, 13, 2, 12);
        cube(body, 20, 67, -7, -7, -3, 1, 6, 1);
        cube(body, 20, 74, -7, -7, 0, 1, 6, 1);
        cube(body, 70, 56, -7, -7, 3, 1, 6, 1);
        cube(body, 63, 56, -7, -7, 6, 1, 6, 1);
        cube(body, 24, 75, 6, -7, 3, 1, 6, 1);
        cube(body, 28, 75, 6, -7, 0, 1, 6, 1);
        cube(body, 32, 75, 6, -7, -3, 1, 6, 1);
        cube(body, 36, 75, 6, -7, 6, 1, 6, 1);
        cube(body, 50, 0, 5.5F, -4, -4, 1, 1, 12);
        cube(body, 50, 0, 5.5F, -6, -4, 1, 1, 12);
        cube(body, 50, 0, -6.5F, -6, -4, 1, 1, 12);
        cube(body, 50, 0, -6.5F, -4, -4, 1, 1, 12);
        cube(body, 36, 75, 4, -7, 7.5F, 1, 6, 1);
        cube(body, 36, 75, 1, -7, 7.5F, 1, 6, 1);
        cube(body, 36, 75, -2, -7, 7.5F, 1, 6, 1);
        cube(body, 36, 75, -5, -7, 7.5F, 1, 6, 1);
        cube(body, 51, 11, -5.5F, -4, 7, 11, 1, 1);
        cube(body, 51, 11, -5.5F, -6, 7, 11, 1, 1);
        cube(body, 36, 75, 4, -7, -4.5F, 1, 6, 1);
        cube(body, 36, 75, 1, -7, -4.5F, 1, 6, 1);
        cube(body, 36, 75, -2, -7, -4.5F, 1, 6, 1);
        cube(body, 36, 75, -5, -7, -4.5F, 1, 6, 1);
        cube(body, 51, 11, -5.5F, -4, -4, 11, 1, 1);
        cube(body, 51, 11, -5.5F, -6, -4, 11, 1, 1);

        // Balloon supports and hooks.
        cube(body, 120, 0, 5.2F, -8, -4.8F, 2, 7, 2);
        cube(body, 124, 10, 5.75F, -9, -4.25F, 1, 1, 1);
        cube(body, 120, 0, -7.2F, -8, -4.8F, 2, 7, 2);
        cube(body, 124, 10, -6.75F, -9, -4.25F, 1, 1, 1);
        cube(body, 120, 0, -7.2F, -8, 6.725F, 2, 7, 2);
        cube(body, 124, 10, -6.75F, -9, 7.275F, 1, 1, 1);
        cube(body, 120, 0, 5.2F, -8, 6.725F, 2, 7, 2);
        cube(body, 124, 10, 5.75F, -9, 7.275F, 1, 1, 1);
        cube(body, 124, 10, 11.75F, -24, -4.25F, 1, 1, 1);
        cube(body, 124, 10, 11.75F, -24, 7.25F, 1, 1, 1);
        cube(body, 124, 10, -12.75F, -24, 7.25F, 1, 1, 1);
        cube(body, 124, 10, -12.75F, -24, -4.25F, 1, 1, 1);

        // Balloon shell.
        cube(body, 16, 80, -12, -40, -14, 24, 16, 32);
        cube(body, 48, 112, -11, -39, -15, 22, 14, 2);
        cube(body, 50, 112, -10, -38, -16, 20, 12, 2);
        cube(body, 52, 112, -9, -37, -17, 18, 10, 2);
        cube(body, 54, 112, -8, -36, -18, 16, 8, 2);
        cube(body, 56, 112, -7, -35, -19, 14, 6, 2);
        cube(body, 52, 112, -9, -37, 19, 18, 10, 2);
        cube(body, 50, 112, -10, -38, 18, 20, 12, 2);
        cube(body, 48, 112, -11, -39, 17, 22, 14, 2);
        cube(body, 20, 82, -11, -41, -13, 22, 2, 30);
        cube(body, 24, 84, -10, -42, -12, 20, 2, 28);
        cube(body, 28, 86, -9, -43, -11, 18, 2, 26);
        cube(body, 28, 86, -9, -23, -11, 18, 2, 26);
        cube(body, 24, 84, -10, -24, -12, 20, 2, 28);
        cube(body, 20, 82, -11, -25, -13, 22, 2, 30);
        cube(body, 40, 82, -13, -39, -13, 2, 14, 30);
        cube(body, 42, 84, -14, -38, -12, 2, 12, 28);
        cube(body, 44, 86, -15, -37, -11, 2, 10, 26);
        cube(body, 44, 86, 13, -37, -11, 2, 10, 26);
        cube(body, 42, 84, 12, -38, -12, 2, 12, 28);
        cube(body, 40, 82, 11, -39, -13, 2, 14, 30);

        // Tail and machinery. The propeller remains its own named part for future animation.
        cube(body, 12, 14, -5, -22, 7.14286F, 1, 3, 10);
        cube(body, 12, 14, 4, -22, 7.14286F, 1, 3, 10);
        cube(body, 17, 20, -1, -22, 12.14286F, 2, 4, 4);
        cube(body, 17, 19, -0.5F, -18.5F, 13.5F, 1, 1, 5);
        cube(body, 0, 89, -3, -24, -4.85714F, 6, 5, 18);

        root.addOrReplaceChild("body", body, PartPose.offset(0, 24, 0));
        root.addOrReplaceChild("propeller",
            CubeListBuilder.create().texOffs(25, 68).addBox(-3, -3, -0.5F, 6, 6, 1),
            PartPose.offset(0, 6, 17.64286F));

        addRope(root, "front_right_rope", 5.75F, 8.5F, -3.75F, -22.5F, 0, -16, -0.5F);
        addRope(root, "back_right_rope", 5.75F, 8.5F, 7.75F, -22.5F, 0, -16, -0.5F);
        addRope(root, "back_left_rope", -5.75F, 8.5F, 7.75F, 22.5F, -1, -16, -0.5F);
        addRope(root, "front_left_rope", -5.75F, 8.5F, -3.75F, 22.5F, -1, -16, -0.5F);

        return LayerDefinition.create(mesh, 128, 128);
    }

    private static void cube(CubeListBuilder builder, int u, int v, float x, float y, float z, float dx, float dy, float dz)
    {
        builder.texOffs(u, v).addBox(x, y, z, dx, dy, dz);
    }

    private static void addRope(PartDefinition root, String name, float originX, float originY, float originZ,
        float zRotationDegrees, float x, float y, float z)
    {
        root.addOrReplaceChild(name,
            CubeListBuilder.create().texOffs(114, 0).addBox(x, y, z, 1, 16, 1),
            PartPose.offsetAndRotation(originX, 24 - originY, originZ, 0, 0, (float) Math.toRadians(zRotationDegrees)));
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
