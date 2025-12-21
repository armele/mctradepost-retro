package com.deathfrog.mctradepost.core.loot;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.sounds.MCTPModSoundEvents;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.item.CoinItem;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
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

    @SuppressWarnings("null")
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
        Entity e = ctx.getParamOrNull(NullnessBridge.assumeNonnull(LootContextParams.THIS_ENTITY));
        
        // LOGGER.info("Checking coin loot on {}", e);

        float luck = ctx.getLuck();
        float chance = Math.min(1f, Math.max(0f, baseChance + (luck * 0.01f)));
        final RandomSource rng = ctx.getRandom();

        CoinItem coinItem = MCTradePostMod.MCTP_COIN_ITEM.get();
        CoinItem goldCoin = MCTradePostMod.MCTP_COIN_GOLD.get();
        CoinItem diamondCoin = MCTradePostMod.MCTP_COIN_DIAMOND.get();

        if (coinItem == null || goldCoin == null || diamondCoin == null) 
        {
            throw new IllegalStateException("Trade Post Coin items not initialized. This should never happen. Please report.");
        }

        if (rng.nextFloat() < chance)
        {
            generatedLoot.add(new ItemStack(coinItem, 1));
        }

        chance = chance / 8;
        if (rng.nextFloat() < chance)
        {
            generatedLoot.add(new ItemStack(goldCoin, 1));
        }

        chance = chance / 8;
        if (rng.nextFloat() < chance)
        {
            generatedLoot.add(new ItemStack(diamondCoin, 1));
        }

        if (!generatedLoot.isEmpty() && e != null)
        {
            BlockPos pos = e.getOnPos();

            if (pos != null && pos.equals(BlockPos.ZERO))
            {
                ctx.getLevel().playSound(null,
                    pos,
                    MCTPModSoundEvents.CASH_REGISTER,
                    SoundSource.NEUTRAL,
                    (float) .8,
                    (float) 1.0f);
            }
        }

        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
