package com.deathfrog.mctradepost.core.client.render.souvenir;

import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BuiltInModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import javax.annotation.Nonnull;
import java.util.function.Function;

public class SouvenirGeometry implements IUnbakedGeometry<SouvenirGeometry>
{
    // The constructor may have any parameters you need, and store them in fields for further usage below.
    // If the constructor has parameters, the constructor call in MyGeometryLoader#read must match them.
    public SouvenirGeometry()
    {}

    // Method responsible for model baking, returning our dynamic model. Parameters in this method are:
    // - The geometry baking context. Contains many properties that we will pass into the model, e.g. light and ao values.
    // - The model baker. Can be used for baking sub-models.
    // - The sprite getter. Maps materials (= texture variables) to TextureAtlasSprites. Materials can be obtained from the context.
    // For example, to get a model's particle texture, call spriteGetter.apply(context.getMaterial("particle"));
    // - The model state. This holds the properties from the blockstate file, e.g. rotations and the uvlock boolean.
    // - The item overrides. This is the code representation of an "overrides" block in an item model.
    @SuppressWarnings("null")
    @Override
    public BakedModel bake(@Nonnull IGeometryBakingContext context,
        @Nonnull ModelBaker baker,
        @Nonnull Function<Material, TextureAtlasSprite> spriteGetter,
        @Nonnull ModelState modelState,
        @Nonnull ItemOverrides overrides)
    {
        return new BuiltInModel(context.getTransforms(),
            overrides,
            spriteGetter.apply(context.getMaterial("particle")),
            context.useBlockLight());
    }
}
