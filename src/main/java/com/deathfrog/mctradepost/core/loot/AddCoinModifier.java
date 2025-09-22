package com.deathfrog.mctradepost.core.loot;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

public class AddCoinModifier extends LootModifier
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final MapCodec<AddCoinModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
        codecStart(instance)
            .and(com.mojang.serialization.Codec.FLOAT.fieldOf("chance").forGetter(m -> m.baseChance))
            .apply(instance, AddCoinModifier::new)
    );

    private final float baseChance;

    public AddCoinModifier(LootItemCondition[] conditions, float chance) 
    {
        super(conditions);
        this.baseChance = chance;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(@Nonnull ObjectArrayList<ItemStack> generatedLoot, @Nonnull LootContext ctx)
    {
        // The entity whose loot is being generated
        // Entity e = ctx.getParamOrNull(LootContextParams.THIS_ENTITY);
        
        // LOGGER.info("Checking coin loot on {}", e);

        float luck = ctx.getLuck();
        float chance = Math.min(1f, Math.max(0f, baseChance + (luck * 0.01f)));
        final RandomSource rng = ctx.getRandom();

        if (rng.nextFloat() < chance)
        {
            generatedLoot.add(new ItemStack(MCTradePostMod.MCTP_COIN_ITEM.get(), 1));
        }

        chance = chance / 8;
        if (rng.nextFloat() < chance)
        {
            generatedLoot.add(new ItemStack(MCTradePostMod.MCTP_COIN_GOLD.get(), 1));
        }

        chance = chance / 8;
        if (rng.nextFloat() < chance)
        {
            generatedLoot.add(new ItemStack(MCTradePostMod.MCTP_COIN_DIAMOND.get(), 1));
        }
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
