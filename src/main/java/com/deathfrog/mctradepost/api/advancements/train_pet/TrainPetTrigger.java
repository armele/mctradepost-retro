package com.deathfrog.mctradepost.api.advancements.train_pet;

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

public class TrainPetTrigger extends SimpleCriterionTrigger<TrainPetTrigger.TrainPetTriggerInstance>
{
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Triggers the listener checks if there are any listening in
     * 
     * @param player the player the check regards
     */
    public void trigger(final ServerPlayer player)
    {
        LOGGER.info("Triggering TrainPetTrigger");
        trigger(player, trigger -> true);
    }

    @Override
    public Codec<TrainPetTriggerInstance> codec()
    {
        return TrainPetTriggerInstance.CODEC;
    }

    public static record TrainPetTriggerInstance(Optional<ContextAwarePredicate> player) implements SimpleInstance
    {
        public static final Codec<TrainPetTriggerInstance> CODEC = RecordCodecBuilder.create(builder -> builder
            .group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TrainPetTriggerInstance::player))
            .apply(builder, TrainPetTriggerInstance::new));



        public static Criterion<TrainPetTriggerInstance> trainPet()
        {
            return MCTPAdvancementTriggers.PET_TRAINED.get().createCriterion(new TrainPetTriggerInstance(Optional.empty()));
        }
    }
}
