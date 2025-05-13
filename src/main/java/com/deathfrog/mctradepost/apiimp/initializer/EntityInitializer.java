package com.deathfrog.mctradepost.apiimp.initializer;


import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.entity.ModEntities;
import com.deathfrog.mctradepost.core.entity.citizen.MCTPEntityCitizen;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = MCTradePostMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class EntityInitializer
{
    public final static DeferredRegister<EntityType<?>> ENTITY_TYPES =  DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, MCTradePostMod.MODID);

    public static void setupEntities(RegisterEvent event)
    {
        if (event.getRegistryKey().equals(Registries.ENTITY_TYPE))
        {    

            ModEntities.MCTP_CITIZEN = ENTITY_TYPES.register("mctp_citizen",
                    () -> EntityType.Builder.<MCTPEntityCitizen>of(MCTPEntityCitizen::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .build("mctp_citizen"));
        }
    }

    @SubscribeEvent
    public static void onRegisterEvent(RegisterEvent event) {
        setupEntities(event);
    }    
}
