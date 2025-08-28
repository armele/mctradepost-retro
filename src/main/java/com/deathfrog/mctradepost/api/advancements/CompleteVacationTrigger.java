package com.deathfrog.mctradepost.api.advancements;

import java.util.Optional;

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

public class CompleteVacationTrigger extends SimpleCriterionTrigger<CompleteVacationTrigger.CompleteVacationTriggerInstance>
{
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Triggers the listener checks if there are any listening in
     * 
     * @param player the player the check regards
     */
    public void trigger(final ServerPlayer player)
    {
        trigger(player, trigger -> true);
    }

    @Override
    public Codec<CompleteVacationTriggerInstance> codec()
    {
        return CompleteVacationTriggerInstance.CODEC;
    }

    public static record CompleteVacationTriggerInstance(Optional<ContextAwarePredicate> player) implements SimpleInstance
    {
        public static final Codec<CompleteVacationTriggerInstance> CODEC = RecordCodecBuilder.create(builder -> builder
            .group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(CompleteVacationTriggerInstance::player))
            .apply(builder, CompleteVacationTriggerInstance::new));



        public static Criterion<CompleteVacationTriggerInstance> CompleteVacation()
        {
            return MCTPAdvancementTriggers.COMPLETE_VACATION.get().createCriterion(new CompleteVacationTriggerInstance(Optional.empty()));
        }
    }
}
