package com.deathfrog.mctradepost.recipe;

import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/** Optional datapack override for a Gasifier fuel. Furnace fuels remain the fallback. */
public record GasifierRecipe(Ingredient input, int burnTime, int gasYield) implements Recipe<SingleRecipeInput>
{
    public static final String ID = "gasifying";

    @Override
    public boolean matches(@Nonnull SingleRecipeInput input, @Nonnull Level level)
    {
        return this.input.test(input.item());
    }

    @Override
    public ItemStack assemble(@Nonnull SingleRecipeInput input, @Nonnull HolderLookup.Provider registries)
    {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height)
    {
        return true;
    }

    @Override
    public ItemStack getResultItem(@Nonnull HolderLookup.Provider registries)
    {
        return ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<?> getSerializer()
    {
        return MCTradePostMod.GASIFIER_RECIPE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType()
    {
        return MCTradePostMod.GASIFIER_RECIPE_TYPE.get();
    }

    /** Serializes datapack and network forms of Gasifier recipes. */
    public static final class Serializer implements RecipeSerializer<GasifierRecipe>
    {
        @SuppressWarnings("null")
        private static final MapCodec<GasifierRecipe> CODEC = RecordCodecBuilder.mapCodec(
            i -> i
                .group(Ingredient.CODEC_NONEMPTY.fieldOf("input").forGetter(GasifierRecipe::input),
                    Codec.INT.fieldOf("burn_time").forGetter(GasifierRecipe::burnTime),
                    Codec.INT.fieldOf("gas").forGetter(GasifierRecipe::gasYield))
                .apply(i, GasifierRecipe::new));

        @SuppressWarnings("null")
        private static final StreamCodec<RegistryFriendlyByteBuf, GasifierRecipe> STREAM_CODEC =
            StreamCodec.composite(Ingredient.CONTENTS_STREAM_CODEC,
                GasifierRecipe::input,
                ByteBufCodecs.VAR_INT,
                GasifierRecipe::burnTime,
                ByteBufCodecs.VAR_INT,
                GasifierRecipe::gasYield,
                GasifierRecipe::new);

        @Override
        public MapCodec<GasifierRecipe> codec()
        {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, GasifierRecipe> streamCodec()
        {
            return STREAM_CODEC;
        }
    }
}
