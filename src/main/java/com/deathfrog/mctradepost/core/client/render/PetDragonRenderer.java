package com.deathfrog.mctradepost.core.client.render;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.entity.pets.PetDragon;
import com.deathfrog.mctradepost.core.client.model.PetDragonModel;
import com.deathfrog.mctradepost.core.event.ModelRegistryHandler;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class PetDragonRenderer extends MobRenderer<PetDragon, PetDragonModel>
{
    @SuppressWarnings("null")
    private static final @Nonnull ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        MCTradePostMod.MODID, "textures/entity/pet_dragon.png");

    @SuppressWarnings("null")
    public PetDragonRenderer(EntityRendererProvider.Context context)
    {
        super(context, new PetDragonModel(context.bakeLayer(ModelRegistryHandler.PET_DRAGON)), 0.5F);
    }

    @Override
    public @Nonnull ResourceLocation getTextureLocation(@Nonnull PetDragon entity)
    {
        return TEXTURE;
    }

    @Override
    protected void scale(@Nonnull PetDragon entity, @Nonnull PoseStack pose, float partialTick)
    {
        pose.scale(0.72F, 0.72F, 0.72F);
    }
}
