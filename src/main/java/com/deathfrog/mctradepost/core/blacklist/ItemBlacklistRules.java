package com.deathfrog.mctradepost.core.blacklist;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Shared parser and evaluator for datapack-defined item blacklist rules. */
public final class ItemBlacklistRules
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private final String description;
    private final @Nonnull List<CompiledRule> denyRules = new ArrayList<>();
    private final @Nonnull List<CompiledRule> allowRules = new ArrayList<>();

    /**
     * Creates an independent blacklist rule set.
     *
     * @param description human-readable subsystem name used in reload diagnostics
     */
    public ItemBlacklistRules(@Nonnull final String description)
    {
        this.description = description;
    }

    /**
     * Replaces this rule set with the supplied datapack resources.
     *
     * @param jsonMap JSON resources discovered beneath the owning reload listener's folder
     */
    public void load(@Nonnull final Map<ResourceLocation, JsonElement> jsonMap)
    {
        denyRules.clear();
        allowRules.clear();
        for (final ResourceLocation key : jsonMap.keySet().stream().sorted().toList())
        {
            if (key == null) continue;
            
            try
            {
                final RuleFile file = MCTradePostMod.GSON.fromJson(jsonMap.get(key), new TypeToken<RuleFile>() { }.getType());
                if (file == null) continue;
                if (file.replace())
                {
                    denyRules.clear();
                    allowRules.clear();
                }
                compile(file.rules(), denyRules, key);
                compile(file.deny(), denyRules, key);
                compile(file.allow(), allowRules, key);
            }
            catch (final Exception ex)
            {
                LOGGER.error("Failed to load {} blacklist file {}.", description, key, ex);
            }
        }
        LOGGER.info("Loaded {} {} blacklist deny rules and {} allow rules.",
            denyRules.size(), description, allowRules.size());
    }

    /**
     * Determines whether any deny rule matches without a matching allow rule.
     *
     * @param stack item stack to evaluate
     * @param level active level, when a matcher requires world context
     * @return whether the stack is denied
     */
    public boolean isBlacklisted(@Nonnull final ItemStack stack, @Nullable final Level level)
    {
        return findMatch(stack, level) != null;
    }

    /**
     * Finds the effective deny rule after applying allow-rule precedence.
     *
     * @param stack item stack to evaluate
     * @param level active level, when a matcher requires world context
     * @return matching deny rule, or {@code null} when the stack is allowed
     */
    @Nullable
    public CompiledRule findMatch(@Nonnull final ItemStack stack, @Nullable final Level level)
    {
        if (stack.isEmpty()) return null;
        for (final CompiledRule rule : allowRules)
        {
            if (rule.matches(stack, level)) return null;
        }
        for (final CompiledRule rule : denyRules)
        {
            if (rule.matches(stack, level)) return rule;
        }
        return null;
    }

    /**
     * Compiles raw definitions and appends all valid rules to an output list.
     *
     * @param definitions raw rule definitions, or {@code null}
     * @param output compiled-rule destination
     * @param source source resource used in validation messages
     */
    private void compile(@Nullable final List<RuleDefinition> definitions,
        @Nonnull final List<CompiledRule> output, @Nonnull final ResourceLocation source)
    {
        if (definitions == null) return;
        for (final RuleDefinition definition : definitions)
        {
            final CompiledRule rule = CompiledRule.compile(definition);
            if (rule != null) output.add(rule);
            else LOGGER.warn("Ignoring invalid {} blacklist rule {} in {}.", description, definition, source);
        }
    }

    /**
     * Datapack representation of one blacklist file.
     *
     * @param replace whether rules accumulated from earlier files are cleared
     * @param rules convenience alias for additional deny rules
     * @param deny rules that blacklist matching stacks
     * @param allow rules that override matches from {@code rules} and {@code deny}
     */
    public record RuleFile(boolean replace, List<RuleDefinition> rules, List<RuleDefinition> deny, List<RuleDefinition> allow) { }

    /**
     * Raw blacklist rule.
     *
     * @param type matcher type: item, tag, namespace/mod/modid, or predicate
     * @param id matcher-specific identifier
     */
    public record RuleDefinition(String type, String id) { }

    /**
     * Validated executable blacklist rule.
     *
     * @param type normalized matcher type
     * @param id normalized matcher identifier
     * @param matcher executable stack matcher
     */
    public record CompiledRule(String type, String id, RuleMatcher matcher)
    {
        /**
         * Validates and compiles a raw rule.
         *
         * @param definition raw rule definition
         * @return compiled rule, or {@code null} when invalid or unsupported
         */
        @SuppressWarnings("null")
        @Nullable
        public static CompiledRule compile(@Nullable final RuleDefinition definition)
        {
            if (definition == null || definition.type() == null || definition.id() == null) return null;
            final String type = definition.type().trim().toLowerCase();
            final String id = definition.id().trim();
            if (id.isEmpty()) return null;
            return switch (type)
            {
                case "item" -> compileItem(id);
                case "tag" -> compileTag(id);
                case "namespace", "mod", "modid" -> new CompiledRule("namespace", id,
                    (stack, level) -> "*".equals(id)
                        || id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace()));
                case "predicate" -> compilePredicate(id);
                default -> null;
            };
        }

        /**
         * Tests this rule against an item stack.
         *
         * @param stack item stack to evaluate
         * @param level active level, when required by the matcher
         * @return whether this rule matches
         */
        public boolean matches(@Nonnull final ItemStack stack, @Nullable final Level level)
        {
            return matcher.matches(stack, level);
        }

        /**
         * Compiles an exact registry item matcher.
         *
         * @param id item registry identifier
         * @return compiled rule, or {@code null} for an invalid identifier
         */
        @Nullable
        private static CompiledRule compileItem(final @Nonnull String id)
        {
            final ResourceLocation location = ResourceLocation.tryParse(id);
            if (location == null) return null;
            final Item item = BuiltInRegistries.ITEM.get(location);
            return new CompiledRule("item", id, (stack, level) -> stack.is(item));
        }

        /**
         * Compiles an item-tag matcher.
         *
         * @param id item-tag identifier
         * @return compiled rule, or {@code null} for an invalid identifier
         */
        @SuppressWarnings("null")
        @Nullable
        private static CompiledRule compileTag(final @Nonnull String id)
        {
            final ResourceLocation location = ResourceLocation.tryParse(id);
            if (location == null) return null;
            final TagKey<Item> tag = TagKey.create(NullnessBridge.assumeNonnull(Registries.ITEM), location);
            return new CompiledRule("tag", id, (stack, level) -> stack.is(tag));
        }

        /**
         * Compiles a named built-in stack predicate.
         *
         * @param id predicate name
         * @return compiled rule, or {@code null} when the predicate is unknown
         */
        @SuppressWarnings("null")
        @Nullable
        private static CompiledRule compilePredicate(final String id)
        {
            return switch (id.toLowerCase())
            {
                case "is_food" -> new CompiledRule("predicate", id,
                    (stack, level) -> stack.getFoodProperties(null) != null);
                default -> null;
            };
        }
    }

    /** Executable matcher used by a compiled blacklist rule. */
    @FunctionalInterface
    public interface RuleMatcher
    {
        /**
         * Tests whether a stack satisfies this matcher.
         *
         * @param stack item stack to evaluate
         * @param level active level, when required by the matcher
         * @return whether the stack matches
         */
        boolean matches(ItemStack stack, @Nullable Level level);
    }
}
