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

public class RareFindTrigger extends SimpleCriterionTrigger<RareFindTrigger.RareFindTriggerInstance>
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
    public Codec<RareFindTriggerInstance> codec()
    {
        return RareFindTriggerInstance.CODEC;
    }

    public static record RareFindTriggerInstance(Optional<ContextAwarePredicate> player) implements SimpleInstance
    {
        public static final Codec<RareFindTriggerInstance> CODEC = RecordCodecBuilder.create(builder -> builder
            .group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(RareFindTriggerInstance::player))
            .apply(builder, RareFindTriggerInstance::new));



        public static Criterion<RareFindTriggerInstance> RareFind()
        {
            return MCTPAdvancementTriggers.RARE_FIND.get().createCriterion(new RareFindTriggerInstance(Optional.empty()));
        }
    }
}
