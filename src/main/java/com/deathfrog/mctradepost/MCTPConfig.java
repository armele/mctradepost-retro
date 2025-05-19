package com.deathfrog.mctradepost;

import java.io.ObjectInputFilter.Config;
import java.lang.reflect.Field;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;


// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
@EventBusSubscriber(modid = MCTradePostMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class MCTPConfig
{
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ConfigValue<Integer> marketplaceLevel3;

    static {
        BUILDER.push("marketplace");

        marketplaceLevel3 = BUILDER
            .comment("How much does unlocking the level 3 marketplace cost?")
            .define("marketplaceLevel3", 500);

        BUILDER.pop();

        SPEC = BUILDER.build(); // Last

        // TODO: Eliminate once testing is complete.
        MCTradePostMod.LOGGER.info("Static initialization of MCTPConfig complete.");
    }


    private static void setupConfiguration(final ModConfigEvent sourceEvent)
    {
        // No-op
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent.Loading event)
    {
        MCTradePostMod.LOGGER.info("Loading config");
        setupConfiguration(event);
    }

    @SubscribeEvent
    static void onReload(final ModConfigEvent.Reloading event)
    {
        MCTradePostMod.LOGGER.info("Reloading config");
        setupConfiguration(event);
    }

    /**
     * Register the config with the given mod container.
     * @param modContainer The mod container to register the config with.
     */
    public static void register(ModContainer modContainer) {
        MCTradePostMod.LOGGER.info("Registering MCTPConfig to handle configurations.");
        modContainer.registerConfig(ModConfig.Type.COMMON, SPEC, "mctradepost-common.toml");
    }
}
