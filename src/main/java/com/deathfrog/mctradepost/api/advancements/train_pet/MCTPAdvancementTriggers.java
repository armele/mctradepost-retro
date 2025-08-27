package com.deathfrog.mctradepost.api.advancements.train_pet;

import com.deathfrog.mctradepost.MCTradePostMod;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class MCTPAdvancementTriggers {
    public static final DeferredRegister<CriterionTrigger<?>> DEFERRED_REGISTER = DeferredRegister.create(Registries.TRIGGER_TYPE, MCTradePostMod.MODID);
    public static final String ADV_PET_TRAINED = "pet_trained";

    public static final DeferredHolder<CriterionTrigger<?>, TrainPetTrigger>          PET_TRAINED           = DEFERRED_REGISTER.register(ADV_PET_TRAINED, TrainPetTrigger::new);
}