package com.deathfrog.mctradepost;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import com.deathfrog.mctradepost.network.ConfigurationPacket;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import net.neoforged.neoforge.server.ServerLifecycleHooks;


// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
@EventBusSubscriber(modid = MCTradePostMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class MCTPConfig
{
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // Server side Neoforge-managed configurations.
    public static final ConfigValue<Integer> tradeCoinValue;
    public static final ConfigValue<Integer> mintingLevel;

    static {
        BUILDER.push("marketplace");

        tradeCoinValue = BUILDER
            .comment("What is the value of a Trade Coin (‡)?")
            .define("tradeCoinValue", 500);

        mintingLevel = BUILDER
            .comment("At what building level can the Marketplace mint coins?")
            .define("mintingLevel", 2);

        BUILDER.pop();

        SPEC = BUILDER.build(); // Last

        // MCTradePostMod.LOGGER.info("Static initialization of MCTPConfig complete.");
    }


    /**
     * Sets up the configuration on the server side and sends configuration packets to all connected players.
     * This method ensures it's executed only on the server side by checking the LogicalSide.
     * If a valid server instance is found, it iterates over all connected players and sends them
     * the current configuration using the ConfigurationPacket. Logs the process for debugging purposes.
     * 
     * @param sourceEvent The event triggering the configuration setup.
     */
    private static void setupConfiguration(final ModConfigEvent sourceEvent)
    {
        // Ensure this runs only on the dedicated or integrated server
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ConfigurationPacket.sendPacketsToPlayer(player);
            }
            MCTradePostMod.LOGGER.info("Config sent to all connected players.");
        } else {
            MCTradePostMod.LOGGER.error("Could not get server to send config.");
        }
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

    /**
     * Deserialize a configuration setting from a string to its corresponding value in the config class.
     * @param configKey The key of the configuration setting to deserialize.
     * @param configValue The value of the configuration setting as a string.
     */
    public static void deserializeConfigurationSetting(String configKey, String configValue) {
        try {
            Field field = MCTPConfig.class.getDeclaredField(configKey);

            if (!ConfigValue.class.isAssignableFrom(field.getType())) {
                MCTradePostMod.LOGGER.warn("Field '{}' is not a ConfigValue", configKey);
                return;
            }

            field.setAccessible(true); // Allow access to private fields

            @SuppressWarnings("unchecked")
            ConfigValue<Object> configField = (ConfigValue<Object>) field.get(null); // Static field

            // Infer value type and parse from string
            Object parsedValue = parseValue(configField.get(), configValue);

            if (parsedValue != null) {
                configField.set(parsedValue);
                MCTradePostMod.LOGGER.info("Updated config '{}' to '{}'", configKey, configField.get());
            } else {
                MCTradePostMod.LOGGER.warn("Could not parse value '{}' for '{}'", configValue, configKey);
            }

        } catch (NoSuchFieldException | IllegalAccessException e) {
            MCTradePostMod.LOGGER.error("Error updating config '{}'", configKey, e);
        }
    }

    /**
     * Returns a map of configuration settings that can be serialized to a string.
     * This includes all public static fields of type ConfigValue.
     * The key in the map is the name of the field, and the value is the current value of that field as a string.
     * @return A map of configuration settings that can be serialized to a string.
     */
    public static Map<String, String> getSerializableConfigSettings() {
        Map<String, String> configMap = new HashMap<>();

        for (Field field : MCTPConfig.class.getDeclaredFields()) {
            if (ConfigValue.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);

                    @SuppressWarnings("unchecked")
                    ConfigValue<Object> configField = (ConfigValue<Object>) field.get(null); // static field

                    String key = field.getName();
                    String value = String.valueOf(configField.get());

                    configMap.put(key, value);
                } catch (IllegalAccessException e) {
                    MCTradePostMod.LOGGER.warn("Failed to read config field '{}'", field.getName(), e);
                }
            }
        }

        return configMap;
    }


    /**
     * Parses a configuration value from a string to its corresponding value in the config class.
     * Infer the value type from the type of the corresponding config field.
     * @param currentValue The current value of the configuration setting.
     * @param configValue The value of the configuration setting as a string.
     * @return The parsed value or null if the parsing failed.
     */
    private static Object parseValue(Object currentValue, String configValue) {
        try {
            if (currentValue instanceof Integer) {
                return Integer.parseInt(configValue);
            } else if (currentValue instanceof Boolean) {
                return Boolean.parseBoolean(configValue);
            } else if (currentValue instanceof Double) {
                return Double.parseDouble(configValue);
            } else if (currentValue instanceof Float) {
                return Float.parseFloat(configValue);
            } else if (currentValue instanceof Long) {
                return Long.parseLong(configValue);
            } else if (currentValue instanceof String) {
                return configValue;
            } else {
                // Add support for more types if needed
                MCTradePostMod.LOGGER.warn("Unsupported config value type: {}", currentValue.getClass().getSimpleName());
                return null;
            }
        } catch (Exception e) {
            MCTradePostMod.LOGGER.error("Error parsing config value '{}'", configValue, e);
            return null;
        }
    }

}
