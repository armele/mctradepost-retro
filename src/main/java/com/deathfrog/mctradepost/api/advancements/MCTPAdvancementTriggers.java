package com.deathfrog.mctradepost.api.advancements;

import com.deathfrog.mctradepost.MCTradePostMod;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class MCTPAdvancementTriggers 
{
    @SuppressWarnings("null")
    public static final DeferredRegister<CriterionTrigger<?>> DEFERRED_REGISTER = DeferredRegister.create(Registries.TRIGGER_TYPE, MCTradePostMod.MODID);
    public static final String ADV_PET_TRAINED = "pet_trained";
    public static final String ADV_COLONY_CONNECTED = "colony_connected";
    public static final String ADV_RECYCLE_ITEM = "recycle_item";
    public static final String ADV_COMPLETE_VACATION = "complete_vacation";
    public static final String ADV_MAKE_WISH = "make_wish";

    public static final DeferredHolder<CriterionTrigger<?>, TrainPetTrigger>          PET_TRAINED           = DEFERRED_REGISTER.register(ADV_PET_TRAINED, TrainPetTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, ConnectColonyTrigger>     COLONY_CONNECTED      = DEFERRED_REGISTER.register(ADV_COLONY_CONNECTED, ConnectColonyTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, RecycleItemTrigger>       RECYCLE_ITEM          = DEFERRED_REGISTER.register(ADV_RECYCLE_ITEM, RecycleItemTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, CompleteVacationTrigger>  COMPLETE_VACATION     = DEFERRED_REGISTER.register(ADV_COMPLETE_VACATION, CompleteVacationTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, MakeWishTrigger>          MAKE_WISH             = DEFERRED_REGISTER.register(ADV_MAKE_WISH, MakeWishTrigger::new);
}