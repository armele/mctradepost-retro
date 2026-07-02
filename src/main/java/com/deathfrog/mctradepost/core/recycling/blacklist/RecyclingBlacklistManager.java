package com.deathfrog.mctradepost.core.recycling.blacklist;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Reloadable manager for recycler blacklist rules. Rules are loaded from
 * datapack JSON files and evaluated in a consistent order so that blacklist
 * checks can be shared by recycler UI filtering and runtime recycling logic.
 */
public final class RecyclingBlacklistManager extends SimpleJsonResourceReloadListener
{
    /**
     * Root resource folder searched for recycling blacklist definitions.
     */
    public static final String RECYCLING_BLACKLIST_FOLDER = "recycling_blacklist";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<CompiledRule> DENY_RULES = new ArrayList<>();
    private static final List<CompiledRule> ALLOW_RULES = new ArrayList<>();

    /**
     * Creates the reload listener used to load recycling blacklist files from
     * datapacks.
     */
    public RecyclingBlacklistManager()
    {
        super(MCTradePostMod.GSON, RECYCLING_BLACKLIST_FOLDER);
    }

    /**
     * Reloads blacklist rule files from datapacks and rebuilds the in-memory
     * allow and deny rule lists.
     *
     * @param jsonMap all JSON elements found under the blacklist resource folder
     * @param resourceManager the active resource manager performing the reload
     * @param profiler the profiler used for reload instrumentation
     */
    @Override
    protected void apply(@Nonnull final Map<ResourceLocation, com.google.gson.JsonElement> jsonMap,
        @Nonnull final ResourceManager resourceManager,
        @Nonnull final ProfilerFiller profiler)
    {
        DENY_RULES.clear();
        ALLOW_RULES.clear();

        final List<ResourceLocation> orderedKeys = jsonMap.keySet().stream().sorted().toList();
        for (final ResourceLocation key : orderedKeys)
        {
            if (key == null) continue;

            try
            {
                final RuleFile ruleFile = MCTradePostMod.GSON.fromJson(jsonMap.get(key), new TypeToken<RuleFile>()
                {
                }.getType());

                if (ruleFile == null)
                {
                    continue;
                }

                if (ruleFile.replace())
                {
                    DENY_RULES.clear();
                    ALLOW_RULES.clear();
                }

                compileRules(ruleFile.rules(), NullnessBridge.assumeNonnull(DENY_RULES), key);
                compileRules(ruleFile.deny(), NullnessBridge.assumeNonnull(DENY_RULES), key);
                compileRules(ruleFile.allow(), NullnessBridge.assumeNonnull(ALLOW_RULES), key);
            }
            catch (final Exception ex)
            {
                LOGGER.error("Failed to load recycling blacklist file {}.", key, ex);
            }
        }

        LOGGER.info("Loaded {} recycling blacklist deny rules and {} allow rules.", DENY_RULES.size(), ALLOW_RULES.size());
    }

    /**
     * Registers the blacklist manager as a server resource reload listener so it
     * updates whenever datapacks are reloaded.
     *
     * @param event the listener registration event
     */
    @SubscribeEvent
    public static void listenForRecyclingBlacklistRecords(@Nonnull final AddReloadListenerEvent event)
    {
        event.addListener(new RecyclingBlacklistManager());
    }

    /**
     * Checks whether the supplied stack is blacklisted from recycler use.
     * Allow-rules override deny-rules when both match.
     *
     * @param stack the item stack to test
     * @param level the active level used for tag and registry lookups
     * @return {@code true} if the stack is blacklisted, otherwise {@code false}
     */
    public static boolean isBlacklisted(@Nonnull final ItemStack stack, @Nullable final Level level)
    {
        return findMatch(stack, level) != null;
    }

    /**
     * Finds the deny-rule that currently blacklists the supplied item stack. If
     * the stack matches both deny and allow rules, allow-rules win and
     * {@code null} is returned.
     *
     * @param stack the item stack to test
     * @param level the active level used for tag and registry lookups
     * @return the matching deny-rule, or {@code null} if the item is not blacklisted
     */
    @Nullable
    public static CompiledRule findMatch(@Nonnull final ItemStack stack, @Nullable final Level level)
    {
        if (stack.isEmpty())
        {
            return null;
        }

        for (final CompiledRule allowRule : ALLOW_RULES)
        {
            if (allowRule.matches(stack, level))
            {
                return null;
            }
        }

        for (final CompiledRule denyRule : DENY_RULES)
        {
            if (denyRule.matches(stack, level))
            {
                return denyRule;
            }
        }

        return null;
    }

    /**
     * Compiles raw JSON rule definitions into executable in-memory rules.
     *
     * @param rules the raw rules to compile
     * @param output the destination list for compiled rules
     * @param sourceId the resource file currently being compiled, used for logging
     */
    private static void compileRules(@Nullable final List<RuleDefinition> rules,
        @Nonnull final List<CompiledRule> output,
        @Nonnull final ResourceLocation sourceId)
    {
        if (rules == null)
        {
            return;
        }

        for (final RuleDefinition rule : rules)
        {
            final CompiledRule compiled = CompiledRule.compile(rule);
            if (compiled != null)
            {
                output.add(compiled);
            }
            else
            {
                LOGGER.warn("Ignoring invalid recycling blacklist rule {} in {}.", rule, sourceId);
            }
        }
    }

    /**
     * Raw datapack file format for recycling blacklist definitions.
     *
     * @param replace whether previously accumulated rules should be discarded before applying this file
     * @param rules additional deny-rules, provided as a convenience alias for simple files
     * @param deny deny-rules that blacklist matching items
     * @param allow allow-rules that override matching deny-rules
     */
    public record RuleFile(boolean replace, List<RuleDefinition> rules, List<RuleDefinition> deny, List<RuleDefinition> allow)
    {
    }

    /**
     * Raw rule definition loaded from JSON.
     *
     * @param type the rule type, such as {@code item}, {@code tag}, {@code namespace}, or {@code predicate}
     * @param id the identifier associated with the rule type
     */
    public record RuleDefinition(String type, String id)
    {
    }

    /**
     * Executable blacklist rule derived from a datapack definition.
     *
     * @param type the rule type
     * @param id the normalized identifier string for logging and diagnostics
     * @param matcher the predicate used to test a stack against the rule
     */
    public record CompiledRule(String type, String id, RuleMatcher matcher)
    {
        /**
         * Compiles a raw rule definition into an executable rule.
         *
         * @param rule the rule definition to compile
         * @return the compiled rule, or {@code null} if the definition is invalid
         */
        @Nullable
        public static CompiledRule compile(@Nullable final RuleDefinition rule)
        {
            if (rule == null || rule.type() == null || rule.id() == null)
            {
                return null;
            }

            final String normalizedType = rule.type().trim().toLowerCase();
            final String normalizedId = rule.id().trim();
            if (normalizedId.isEmpty())
            {
                return null;
            }

            return switch (normalizedType)
            {
                case "item" -> compileItemRule(normalizedId);
                case "tag" -> compileTagRule(normalizedId);
                case "namespace", "mod", "modid" -> new CompiledRule("namespace", normalizedId,
                    (stack, level) -> {
                        if ("*".equals(normalizedId)) return true;
                        Item item = stack.getItem();
                        if (item == null) return false;
                        final ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
                        return itemId != null && normalizedId.equals(itemId.getNamespace());
                    });
                case "predicate" -> compilePredicateRule(normalizedId);
                default -> null;
            };
        }

        /**
         * Tests whether this rule matches the supplied item stack.
         *
         * @param stack the item stack to test
         * @param level the active level used for tag-sensitive predicates
         * @return {@code true} if the rule matches the stack
         */
        public boolean matches(@Nonnull final ItemStack stack, @Nullable final Level level)
        {
            return matcher.matches(stack, level);
        }

        /**
         * Compiles a concrete item-id rule.
         *
         * @param id the item id to match
         * @return the compiled rule, or {@code null} if the id is invalid
         */
        @Nullable
        private static CompiledRule compileItemRule(@Nonnull final String id)
        {
            final ResourceLocation itemId = ResourceLocation.tryParse(id);
            if (itemId == null)
            {
                return null;
            }

            final Item item = BuiltInRegistries.ITEM.get(itemId);

            return new CompiledRule("item", id, (stack, level) -> stack.is(item));
        }

        /**
         * Compiles an item-tag rule.
         *
         * @param id the tag id to match
         * @return the compiled rule, or {@code null} if the id is invalid
         */
        @Nullable
        private static CompiledRule compileTagRule(@Nonnull final String id)
        {
            final ResourceLocation tagId = ResourceLocation.tryParse(id);
            if (tagId == null)
            {
                return null;
            }

            final TagKey<Item> tagKey = TagKey.create(NullnessBridge.assumeNonnull(Registries.ITEM), tagId);

            if (tagKey == null)
            {
                return null;
            }

            return new CompiledRule("tag", id, (stack, level) -> stack.is(tagKey));
        }

        /**
         * Compiles a built-in predicate rule.
         *
         * @param id the predicate identifier
         * @return the compiled rule, or {@code null} if the predicate is unknown
         */
        @SuppressWarnings("null")
        @Nullable
        private static CompiledRule compilePredicateRule(@Nonnull final String id)
        {
            return switch (id.toLowerCase())
            {
                case "is_food" -> new CompiledRule("predicate", id,
                    (stack, level) -> stack.getFoodProperties(null) != null);
                default -> null;
            };
        }
    }

    /**
     * Functional matcher used by compiled blacklist rules.
     */
    @FunctionalInterface
    public interface RuleMatcher
    {
        /**
         * Tests whether a blacklist rule matches the supplied stack.
         *
         * @param stack the item stack to test
         * @param level the active level used for tag-sensitive lookups
         * @return {@code true} if the rule matches the stack
         */
        boolean matches(ItemStack stack, @Nullable Level level);
    }
}
