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

public class RecycleItemTrigger extends SimpleCriterionTrigger<RecycleItemTrigger.RecycleItemTriggerInstance>
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
    public Codec<RecycleItemTriggerInstance> codec()
    {
        return RecycleItemTriggerInstance.CODEC;
    }

    public static record RecycleItemTriggerInstance(Optional<ContextAwarePredicate> player) implements SimpleInstance
    {
        public static final Codec<RecycleItemTriggerInstance> CODEC = RecordCodecBuilder.create(builder -> builder
            .group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(RecycleItemTriggerInstance::player))
            .apply(builder, RecycleItemTriggerInstance::new));



        public static Criterion<RecycleItemTriggerInstance> RecycleItem()
        {
            return MCTPAdvancementTriggers.RECYCLE_ITEM.get().createCriterion(new RecycleItemTriggerInstance(Optional.empty()));
        }
    }
}
