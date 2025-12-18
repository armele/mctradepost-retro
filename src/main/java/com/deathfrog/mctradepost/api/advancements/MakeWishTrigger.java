package com.deathfrog.mctradepost.api.advancements;

import java.util.Optional;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger.SimpleInstance;
import net.minecraft.server.level.ServerPlayer;

public class MakeWishTrigger extends SimpleCriterionTrigger<MakeWishTrigger.MakeWishTriggerInstance>
{
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Triggers the listener checks if there are any listening in
     * 
     * @param player the player the check regards
     */
    public void trigger(final @Nonnull ServerPlayer player)
    {
        trigger(player, trigger -> true);
    }

    @Override
    public Codec<MakeWishTriggerInstance> codec()
    {
        return MakeWishTriggerInstance.CODEC;
    }

    public static record MakeWishTriggerInstance(Optional<ContextAwarePredicate> player) implements SimpleInstance
    {
        public static final Codec<MakeWishTriggerInstance> CODEC = RecordCodecBuilder.create(builder -> builder
            .group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(MakeWishTriggerInstance::player))
            .apply(builder, MakeWishTriggerInstance::new));



        public static Criterion<MakeWishTriggerInstance> MakeWish()
        {
            return MCTPAdvancementTriggers.MAKE_WISH.get().createCriterion(new MakeWishTriggerInstance(Optional.empty()));
        }
    }
}
