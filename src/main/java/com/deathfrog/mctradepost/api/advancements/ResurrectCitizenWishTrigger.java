package com.deathfrog.mctradepost.api.advancements;

import java.util.Optional;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/** Triggered only when a Wish You Were Here ritual successfully resurrects a citizen. */
public class ResurrectCitizenWishTrigger extends SimpleCriterionTrigger<ResurrectCitizenWishTrigger.Instance>
{
    public void trigger(@Nonnull ServerPlayer player)
    {
        trigger(player, instance -> true);
    }

    @Override
    public Codec<Instance> codec()
    {
        return Instance.CODEC;
    }

    public record Instance(Optional<ContextAwarePredicate> player) implements SimpleInstance
    {
        @SuppressWarnings("null")
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(builder -> builder
            .group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player))
            .apply(builder, Instance::new));
    }
}
