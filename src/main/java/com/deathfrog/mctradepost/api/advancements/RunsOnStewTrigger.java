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

public class RunsOnStewTrigger extends SimpleCriterionTrigger<RunsOnStewTrigger.RunsOnStewTriggerInstance>
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
    public Codec<RunsOnStewTriggerInstance> codec()
    {
        return RunsOnStewTriggerInstance.CODEC;
    }

    public static record RunsOnStewTriggerInstance(Optional<ContextAwarePredicate> player) implements SimpleInstance
    {
        public static final Codec<RunsOnStewTriggerInstance> CODEC = RecordCodecBuilder.create(builder -> builder
            .group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(RunsOnStewTriggerInstance::player))
            .apply(builder, RunsOnStewTriggerInstance::new));



        public static Criterion<RunsOnStewTriggerInstance> RunsOnStew()
        {
            return MCTPAdvancementTriggers.RUNS_ON_STEW.get().createCriterion(new RunsOnStewTriggerInstance(Optional.empty()));
        }
    }
}
