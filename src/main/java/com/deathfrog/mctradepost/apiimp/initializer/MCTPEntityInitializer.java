package com.deathfrog.mctradepost.apiimp.initializer;

import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.entity.mobs.EntityMercenary;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

import static com.minecolonies.api.util.constant.Constants.*;

@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class MCTPEntityInitializer
{
    public static EntityType<? extends PathfinderMob> PET_ENTITY = null;

    protected static float ENTITY_HEIGHT = 0.9f;
    protected static float ENTITY_WIDTH = 0.6f;

    public static void setupEntities(RegisterEvent event)
    {
        if (event.getRegistryKey().equals(Registries.ENTITY_TYPE))
        {
            final @Nullable Registry<EntityType<?>> registry = event.getRegistry(Registries.ENTITY_TYPE);

            PET_ENTITY = build(registry, "pet_entity",
              EntityType.Builder.of(EntityMercenary::new, MobCategory.CREATURE)
                .setTrackingRange(ENTITY_TRACKING_RANGE)
                .setUpdateInterval(ENTITY_UPDATE_FREQUENCY)
                .sized((float) ENTITY_WIDTH, (float) ENTITY_HEIGHT));
        }
    }

    private static <T extends Entity> EntityType<T> build(Registry<EntityType<?>> registry, final String key, final EntityType.Builder<T> builder)
    {
        EntityType<T> entity = builder.build(Constants.MOD_ID + ":" + key);
        Registry.register(registry, ResourceLocation.parse(Constants.MOD_ID + ":" + key), entity);
        return entity;
    }
}