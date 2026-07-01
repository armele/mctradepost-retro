package com.deathfrog.mctradepost.recipe;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PotionShapelessRecipe implements CraftingRecipe
{
    public static final String POTION_SHAPELESS_RECIPE_KEY = "potion_shapeless";

    private final List<PotionInput> inputs;
    private final ItemStack result;

    public PotionShapelessRecipe(final List<PotionInput> inputs, final ItemStack result)
    {
        this.inputs = List.copyOf(inputs);
        this.result = result;
    }

    public List<PotionInput> getPotionInputs()
    {
        return inputs;
    }

    public ItemStack getResultStack()
    {
        return result;
    }

    @SuppressWarnings("null")
    public int getInputCount()
    {
        return inputs.stream().mapToInt(PotionInput::count).sum();
    }

    @Override
    public boolean matches(@Nonnull final CraftingInput input, @Nonnull final Level level)
    {
        @SuppressWarnings("null")
        final int[] remaining = inputs.stream().mapToInt(PotionInput::count).toArray();
        int found = 0;

        for (int slot = 0; slot < input.size(); slot++)
        {
            final ItemStack stack = input.getItem(slot);
            if (stack.isEmpty())
            {
                continue;
            }

            boolean matched = false;
            for (int i = 0; i < inputs.size(); i++)
            {
                if (remaining[i] > 0 && inputs.get(i).matches(stack))
                {
                    remaining[i]--;
                    found++;
                    matched = true;
                    break;
                }
            }

            if (!matched)
            {
                return false;
            }
        }

        return found == getInputCount();
    }

    @Override
    public ItemStack assemble(@Nonnull final CraftingInput input, @Nonnull final HolderLookup.Provider registries)
    {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(final int width, final int height)
    {
        return width * height >= getInputCount();
    }

    @Override
    public ItemStack getResultItem(@Nonnull final HolderLookup.Provider registries)
    {
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer()
    {
        return MCTradePostMod.POTION_SHAPELESS_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType()
    {
        return RecipeType.CRAFTING;
    }

    @Override
    public NonNullList<Ingredient> getIngredients()
    {
        final NonNullList<Ingredient> ingredients = NonNullList.create();

        for (final PotionInput input : inputs)
        {
            final Ingredient ingredient = Ingredient.of(input.displayStack());
            for (int i = 0; i < input.count(); i++)
            {
                ingredients.add(ingredient);
            }
        }

        return ingredients;
    }

    public List<List<ItemStack>> getDisplayInputs()
    {
        final List<List<ItemStack>> displayInputs = new ArrayList<>(getInputCount());

        for (final PotionInput input : inputs)
        {
            final ItemStack stack = input.displayStack();
            for (int i = 0; i < input.count(); i++)
            {
                displayInputs.add(List.of(stack.copy()));
            }
        }

        return displayInputs;
    }

    @Override
    public boolean isSpecial()
    {
        return false;
    }

    @Override
    public CraftingBookCategory category()
    {
        return CraftingBookCategory.MISC;
    }

    public record PotionInput(ResourceLocation item, ResourceLocation potion, int count)
    {
        @SuppressWarnings("null")
        public static final MapCodec<PotionInput> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("item", BuiltInRegistries.ITEM.getKey(Items.POTION))
                        .forGetter(PotionInput::item),
                    ResourceLocation.CODEC.fieldOf("potion")
                        .forGetter(PotionInput::potion),
                    Codec.intRange(1, 9).optionalFieldOf("count", 1)
                        .forGetter(PotionInput::count)
                ).apply(instance, PotionInput::new)
            );

        @SuppressWarnings("null")
        public static final StreamCodec<RegistryFriendlyByteBuf, PotionInput> STREAM_CODEC =
            StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, PotionInput::item,
                ResourceLocation.STREAM_CODEC, PotionInput::potion,
                ByteBufCodecs.VAR_INT, PotionInput::count,
                PotionInput::new
            );

        @SuppressWarnings("null")
        public boolean matches(final ItemStack stack)
        {
            final Optional<Holder.Reference<Potion>> potionHolder = BuiltInRegistries.POTION.getHolder(potion);

            return stack.is(resolveItem())
                && potionHolder.isPresent()
                && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(potionHolder.get());
        }

        @SuppressWarnings("null")
        public ItemStack displayStack()
        {
            final Optional<Holder.Reference<Potion>> potionHolder = BuiltInRegistries.POTION.getHolder(potion);
            if (potionHolder.isEmpty())
            {
                return new ItemStack(resolveItem());
            }

            return PotionContents.createItemStack(resolveItem(), potionHolder.get());
        }

        @SuppressWarnings("null")
        private @Nonnull Item resolveItem()
        {
            return BuiltInRegistries.ITEM.getOptional(item).orElse(Items.POTION);
        }
    }

    public static class Serializer implements RecipeSerializer<PotionShapelessRecipe>
    {
        @SuppressWarnings("null")
        public static final MapCodec<PotionShapelessRecipe> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    PotionInput.CODEC.codec().listOf().fieldOf("inputs").forGetter(PotionShapelessRecipe::getPotionInputs),
                    ItemStack.CODEC.fieldOf("result").forGetter(PotionShapelessRecipe::getResultStack)
                ).apply(instance, PotionShapelessRecipe::new)
            );

        @SuppressWarnings("null")
        public static final StreamCodec<RegistryFriendlyByteBuf, PotionShapelessRecipe> STREAM_CODEC =
            StreamCodec.composite(
                ByteBufCodecs.collection(ArrayList::new, PotionInput.STREAM_CODEC), PotionShapelessRecipe::getPotionInputs,
                ItemStack.STREAM_CODEC, PotionShapelessRecipe::getResultStack,
                PotionShapelessRecipe::new
            );

        @Override
        public MapCodec<PotionShapelessRecipe> codec()
        {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, PotionShapelessRecipe> streamCodec()
        {
            return STREAM_CODEC;
        }
    }
}
