package com.deathfrog.mctradepost.recipe;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

public class DeconstructionRecipe implements Recipe<SingleRecipeInput>
{
    public static final String DECON_RECIPE_KEY = "deconstruction";

    public record Output(ItemStack stack, float chance)
    {
        public static final MapCodec<Output> CODEC =
            RecordCodecBuilder.mapCodec(i -> i
                .group(ItemStack.CODEC.fieldOf("item").forGetter(Output::stack),
                    com.mojang.serialization.Codec.FLOAT.optionalFieldOf("chance", 1.0f).forGetter(Output::chance))
                .apply(i, Output::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Output> STREAM_CODEC =
            StreamCodec.composite(
                ItemStack.STREAM_CODEC, Output::stack,
                ByteBufCodecs.FLOAT, Output::chance,
                Output::new
            );
    }

    private final Ingredient input;
    private final ObjectArrayList<Output> outputs;

    public DeconstructionRecipe(Ingredient input, ObjectArrayList<Output> outputs)
    {
        this.input = input;
        this.outputs = outputs;
    }

    @Override
    public boolean matches(@Nonnull SingleRecipeInput in, @Nonnull Level level)
    {
        // SingleRecipeInput is a record SingleRecipeInput(ItemStack item)
        return !in.item().isEmpty() && input.test(in.item());
    }

    @Override
    public ItemStack assemble(@Nonnull SingleRecipeInput in, @Nonnull HolderLookup.Provider regs)
    {
        return ItemStack.EMPTY; // your machine reads outputs directly
    }

    @Override
    public boolean canCraftInDimensions(int w, int h)
    {
        return true;
    }

    @Override
    public ItemStack getResultItem(@Nonnull HolderLookup.Provider regs)
    {
        return ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<?> getSerializer()
    {
        return MCTradePostMod.DECON_RECIPE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType()
    {
        return MCTradePostMod.DECON_RECIPE_TYPE.get();
    }

    public Ingredient getInput()
    {
        return input;
    }

    public ObjectArrayList<Output> getOutputs()
    {
        return outputs;
    }

    /** Serializer for 1.21.x (MapCodec + StreamCodec) */
    public static class Serializer implements RecipeSerializer<DeconstructionRecipe>
    {
        public static final MapCodec<DeconstructionRecipe> CODEC = RecordCodecBuilder.mapCodec(
            i -> i
                .group(Ingredient.CODEC_NONEMPTY.fieldOf("input").forGetter(DeconstructionRecipe::getInput),
                    Output.CODEC.codec()
                        .listOf()
                        .fieldOf("results")
                        .xmap(list -> new ObjectArrayList<>(list), list -> list)
                        .forGetter(DeconstructionRecipe::getOutputs))
                .apply(i, DeconstructionRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, DeconstructionRecipe> STREAM_CODEC =
            StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, DeconstructionRecipe::getInput,
                ByteBufCodecs.collection(ObjectArrayList::new, Output.STREAM_CODEC), DeconstructionRecipe::getOutputs,
                DeconstructionRecipe::new
            );

        @Override
        public MapCodec<DeconstructionRecipe> codec()
        {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, DeconstructionRecipe> streamCodec()
        {
            return STREAM_CODEC;
        }
    }
}
