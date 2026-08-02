package com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.api.research.MCTPResearchConstants;
import com.deathfrog.mctradepost.api.util.BuildingUtil;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingEconModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MarketplaceItemListModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop.MarketDailyRoller.MarketOffer;
import com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop.MarketDailyRoller.MarketTier;
import com.deathfrog.mctradepost.item.CoinItem;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.StatsUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

/**
 * Stores and applies the Marketplace's retained searches and recurring Rare Finds subscriptions.
 */
public class MarketplaceSourcingModule extends AbstractBuildingModule implements IPersistentModule
{
    public static final String SUBSCRIPTIONS_FILLED = "subscriptions_filled";
    public static final String SUBSCRIPTIONS_CANCELLED_NSF = "subscriptions_cancelled_nsf";
    public static final String SUBSCRIPTIONS_MISSED_INVENTORY = "subscriptions_missed_inventory";

    private static final String TAG_SEARCHES = "retainedSearches";
    private static final String TAG_SUBSCRIPTIONS = "subscriptions";
    private static final String TAG_STACK = "stack";
    private static final String TAG_TIER = "tier";
    private static final String TAG_INVESTMENT_LEVEL = "investmentLevel";
    private static final String TAG_INVESTMENT_UNTIL = "investmentUntil";
    private static final String TAG_LAST_PROCESSED = "lastProcessed";

    private final List<RetainedSearch> searches = new ArrayList<>();
    private final List<Subscription> subscriptions = new ArrayList<>();

    /** Describes one retained item and its current prepaid investment. */
    public record RetainedSearch(@Nonnull ItemStack stack, MarketTier tier, int investmentLevel, long investmentUntil) {}

    /** Describes one recurring item purchase and the last day on which it was attempted. */
    public record Subscription(@Nonnull ItemStack stack, MarketTier tier, long lastProcessedDay) {}

    /**
     * Returns the feature capacity supplied by the current building level and research.
     *
     * @param research research effect which gates the requested feature
     * @return zero before level three or research, otherwise one to three slots
     */
    public int capacity(net.minecraft.resources.ResourceLocation research)
    {
        boolean unlocked = building.getColony().getResearchManager().getResearchEffects().getEffectStrength(research) > 0;
        return unlocked ? Math.max(0, building.getBuildingLevel() - 2) : 0;
    }

    /** @return an immutable snapshot of retained searches. */
    public List<RetainedSearch> getSearches()
    {
        return List.copyOf(searches);
    }

    /** @return an immutable snapshot of active subscriptions. */
    public List<Subscription> getSubscriptions()
    {
        return List.copyOf(subscriptions);
    }

    /**
     * Adds an explicitly tier-tagged item to the retained-search list.
     *
     * @param stack selected item
     * @return whether the selection was accepted
     */
    @SuppressWarnings("null")
    public boolean addSearch(ItemStack stack)
    {
        MarketTier tier = MarketTierSources.taggedTier(stack);
        int unlockedTier = (int) building.getColony().getResearchManager().getResearchEffects()
            .getEffectStrength(MCTPResearchConstants.THRIFTSHOP_TIER);
        if (tier == null || tier.ordinal() + 1 > unlockedTier
            || searches.size() >= capacity(MCTPResearchConstants.RETAINED_SEARCH) || containsSearch(stack)) return false;
        searches.add(new RetainedSearch(stack.copyWithCount(1), tier, 0, 0L));
        markDirty();
        return true;
    }

    /**
     * Removes a retained search and forfeits any remaining prepaid investment.
     *
     * @param stack item whose search should be removed
     */
    public void removeSearch(@Nonnull ItemStack stack)
    {
        searches.removeIf(search -> ItemStack.isSameItemSameComponents(search.stack(), stack));
        markDirty();
    }

    /**
     * Prepays or extends an investment. Extensions must use the active investment level.
     *
     * @param stack retained item
     * @param level requested investment level, from one through three
     * @param player paying player
     * @param currentDay current MineColonies day
     * @return whether XP was charged and the investment was updated
     */
    public boolean invest(@Nonnull ItemStack stack, int level, ServerPlayer player, long currentDay)
    {
        if (level < 1 || level > 3) return false;
        for (int i = 0; i < searches.size(); i++)
        {
            RetainedSearch search = searches.get(i);
            if (!ItemStack.isSameItemSameComponents(search.stack(), stack)) continue;
            boolean active = search.investmentUntil() > currentDay;
            if (active && search.investmentLevel() != level) return false;
            int cost = MCTPConfig.retainedSearchInvestmentBaseXp.get() * level;
            if (player.totalExperience < cost) return false;
            player.giveExperiencePoints(-cost);
            player.playNotifySound(NullnessBridge.assumeNonnull(SoundEvents.ENCHANTMENT_TABLE_USE), SoundSource.PLAYERS, 0.8F, 1.0F);
            long baseDay = active ? search.investmentUntil() : currentDay;
            searches.set(i, new RetainedSearch(search.stack(), search.tier(), level,
                baseDay + MCTPConfig.retainedSearchInvestmentDays.get()));
            markDirty();
            return true;
        }
        return false;
    }

    /**
     * Cancels the investment for an item without removing its retained search.
     *
     * @param stack retained item
     */
    public void cancelInvestment(@Nonnull ItemStack stack)
    {
        for (int i = 0; i < searches.size(); i++)
        {
            RetainedSearch search = searches.get(i);
            if (ItemStack.isSameItemSameComponents(search.stack(), stack))
            {
                searches.set(i, new RetainedSearch(search.stack(), search.tier(), 0, 0L));
                markDirty();
                return;
            }
        }
    }

    /**
     * Creates a subscription after its first purchase has been completed by the Rare Finds module.
     *
     * @param offer subscribed offer
     * @param currentDay activation day
     * @return whether a subscription slot was available
     */
    @SuppressWarnings("null")
    public boolean activateSubscription(@Nonnull MarketOffer offer, long currentDay)
    {
        if (subscriptions.size() >= capacity(MCTPResearchConstants.MARKETPLACE_SUBSCRIPTIONS) || containsSubscription(offer.stack())) return false;
        subscriptions.add(new Subscription(offer.stack().copy(), offer.tier(), currentDay));
        markDirty();
        return true;
    }

    /**
     * Counts active subscriptions in a tier, limited by the current researched building capacity.
     *
     * @param tier tier to count
     * @return number of reserved slots
     */
    public int subscriptionCount(MarketTier tier)
    {
        int limit = capacity(MCTPResearchConstants.MARKETPLACE_SUBSCRIPTIONS);
        int count = 0;
        for (int i = 0; i < Math.min(limit, subscriptions.size()); i++)
        {
            if (subscriptions.get(i).tier() == tier) count++;
        }
        return count;
    }

    /**
     * Returns whether the item has an active, capacity-backed subscription.
     *
     * @param stack item to inspect
     * @return whether the item is currently subscribed
     */
    public boolean isSubscribed(@Nonnull ItemStack stack)
    {
        int limit = Math.min(capacity(MCTPResearchConstants.MARKETPLACE_SUBSCRIPTIONS), subscriptions.size());
        return subscriptions.subList(0, limit).stream()
            .anyMatch(subscription -> ItemStack.isSameItemSameComponents(subscription.stack(), stack));
    }

    /**
     * Builds informational Rare Finds rows for active subscriptions.
     *
     * @return subscription offers priced at their current recurring cost
     */
    public List<MarketOffer> subscriptionOffers()
    {
        int limit = Math.min(capacity(MCTPResearchConstants.MARKETPLACE_SUBSCRIPTIONS), subscriptions.size());
        List<MarketOffer> result = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++)
        {
            Subscription subscription = subscriptions.get(i);
            result.add(new MarketOffer(subscription.stack().copy(), subscription.tier(),
                subscriptionPrice(subscription.stack(), subscription.tier())));
        }
        return result;
    }

    /**
     * Cancels the subscription for an item.
     *
     * @param stack subscribed item
     */
    public void cancelSubscription(@Nonnull ItemStack stack)
    {
        subscriptions.removeIf(subscription -> ItemStack.isSameItemSameComponents(subscription.stack(), stack));
        markDirty();
    }

    /**
     * Processes at most one renewal for each due subscription on a natural game day.
     * Unstaffed Marketplaces leave subscriptions untouched and do not create backlog.
     *
     * @param currentDay current MineColonies day
     */
    @SuppressWarnings("null")
    public void processSubscriptions(long currentDay)
    {
        if (building.getBuildingLevel() < 3 || capacity(MCTPResearchConstants.MARKETPLACE_SUBSCRIPTIONS) == 0
            || ((com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace) building).shopkeeper() == null) return;

        BuildingEconModule econ = building.getModule(MCTPBuildingModules.ECON_MODULE);
        int index = 0;
        int capacity = capacity(MCTPResearchConstants.MARKETPLACE_SUBSCRIPTIONS);
        while (index < subscriptions.size() && index < capacity)
        {
            Subscription subscription = subscriptions.get(index);
            if (subscription.lastProcessedDay() >= currentDay)
            {
                index++;
                continue;
            }
            int price = subscriptionPrice(subscription.stack(), subscription.tier());
            if (econ.getTotalBalance() < price)
            {
                StatsUtil.trackStatByName(building, SUBSCRIPTIONS_CANCELLED_NSF, subscription.stack().getHoverName(), 1);
                subscriptions.remove(index);
                MessageUtils.format("mctradepost.subscription.cancelled.nsf", subscription.stack().getHoverName()).sendTo(building.getColony()).forAllPlayers();
                continue;
            }

            ItemStack purchase = subscription.stack().copy();
            ItemStack simulated = ItemHandlerHelper.insertItemStacked(building.getItemHandlerCap(), purchase.copy(), true);
            if (!simulated.isEmpty())
            {
                StatsUtil.trackStatByName(building, SUBSCRIPTIONS_MISSED_INVENTORY, purchase.getHoverName(), 1);
                subscriptions.set(index, new Subscription(subscription.stack(), subscription.tier(), currentDay));
                index++;
                continue;
            }

            econ.deposit(-price);
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(building.getItemHandlerCap(), purchase.copy(), false);
            if (!remainder.isEmpty())
            {
                econ.deposit(price);
                subscriptions.set(index, new Subscription(subscription.stack(), subscription.tier(), currentDay));
                index++;
                continue;
            }
            BuildingUtil.bringThisToTheWarehouse(building, purchase.copy());
            StatsUtil.trackStatByName(building, SUBSCRIPTIONS_FILLED, purchase.getHoverName(), 1);
            subscriptions.set(index, new Subscription(subscription.stack(), subscription.tier(), currentDay));
            index++;
        }
        markDirty();
    }

    /**
     * Replaces eligible ordinary offers with successful retained searches without changing tier slot counts.
     *
     * @param offers rolled offers
     * @param day current MineColonies day
     * @param random deterministic roll source
     */
    public void promoteRetainedSearches(List<MarketOffer> offers, long day, RandomSource random)
    {
        if (capacity(MCTPResearchConstants.RETAINED_SEARCH) == 0) return;
        List<RetainedSearch> candidates = new ArrayList<>(searches.subList(0, Math.min(searches.size(), capacity(MCTPResearchConstants.RETAINED_SEARCH))));
        java.util.Collections.shuffle(candidates, new java.util.Random(random.nextLong()));
        java.util.EnumSet<MarketTier> promotedTiers = java.util.EnumSet.noneOf(MarketTier.class);
        for (RetainedSearch search : candidates)
        {
            if (search.investmentLevel() == 0 || search.investmentUntil() <= day) continue;
            if (promotedTiers.contains(search.tier())) continue;
            if (containsSubscription(search.stack()) || offers.stream().anyMatch(o -> ItemStack.isSameItemSameComponents(o.stack(), search.stack()))) continue;
            double chance = MCTPConfig.retainedSearchBaseChance.get() + investmentBonus(search, day);
            if (random.nextDouble() >= Math.min(1.0D, chance)) continue;
            for (int i = 0; i < offers.size(); i++)
            {
                MarketOffer replaced = offers.get(i);
                if (replaced.tier() == search.tier())
                {
                    ItemStack promoted = search.stack().copyWithCount(replaced.stack().getCount());

                    if (promoted == null) continue;

                    int promotedPrice = (int) Math.min(Integer.MAX_VALUE, (long) replaced.price() * 2L);
                    offers.set(i, new MarketOffer(promoted, search.tier(), promotedPrice));
                    promotedTiers.add(search.tier());
                    break;
                }
            }
        }
    }

    /**
     * Computes the recurring subscription price from item value, stack size, tier floor, and configured premium.
     *
     * @param stack delivered stack
     * @param tier original offer tier
     * @return recurring economic cost
     */
    public static int subscriptionPrice(ItemStack stack, MarketTier tier)
    {
        int itemValue = MarketplaceItemListModule.marketplaceValue(stack) * stack.getCount();
        int coin = MCTPConfig.tradeCoinValue.get();
        int floor = switch (tier)
        {
            case TIER1_COMMON -> (int) Math.ceil(coin * 0.8D);
            case TIER2_UNCOMMON -> (int) Math.ceil(coin * 1.2D);
            case TIER3_RARE -> coin * CoinItem.GOLD_MULTIPLIER;
            case TIER4_EPIC -> coin * CoinItem.DIAMOND_MULTIPLIER;
        };
        return Math.max(1, (int) Math.ceil(Math.max(itemValue, floor) * MCTPConfig.subscriptionPriceMultiplier.get()));
    }

    /** Returns the configured bonus for an investment that is active on the supplied day. */
    private double investmentBonus(RetainedSearch search, long day)
    {
        if (search.investmentUntil() <= day) return 0.0D;
        return switch (search.investmentLevel())
        {
            case 1 -> MCTPConfig.retainedSearchInvestmentLevelOneBonus.get();
            case 2 -> MCTPConfig.retainedSearchInvestmentLevelTwoBonus.get();
            case 3 -> MCTPConfig.retainedSearchInvestmentLevelThreeBonus.get();
            default -> 0.0D;
        };
    }

    /** Returns whether an equivalent retained-search item is already stored. */
    private boolean containsSearch(@Nonnull ItemStack stack)
    {
        return searches.stream().anyMatch(search -> ItemStack.isSameItemSameComponents(search.stack(), stack));
    }

    /** Returns whether an equivalent item already has a subscription. */
    private boolean containsSubscription(@Nonnull ItemStack stack)
    {
        return subscriptions.stream().anyMatch(subscription -> ItemStack.isSameItemSameComponents(subscription.stack(), stack));
    }

    /** {@inheritDoc} */
    @Override
    public void serializeNBT(@NotNull HolderLookup.Provider provider, CompoundTag compound)
    {
        ListTag searchTags = new ListTag();
        for (RetainedSearch search : searches)
        {
            CompoundTag tag = stackTierTag(provider, search.stack(), search.tier());
            tag.putInt(TAG_INVESTMENT_LEVEL, search.investmentLevel());
            tag.putLong(TAG_INVESTMENT_UNTIL, search.investmentUntil());
            searchTags.add(tag);
        }
        compound.put(TAG_SEARCHES, searchTags);
        ListTag subscriptionTags = new ListTag();
        for (Subscription subscription : subscriptions)
        {
            CompoundTag tag = stackTierTag(provider, subscription.stack(), subscription.tier());
            tag.putLong(TAG_LAST_PROCESSED, subscription.lastProcessedDay());
            subscriptionTags.add(tag);
        }
        compound.put(TAG_SUBSCRIPTIONS, subscriptionTags);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("null")
    @Override
    public void deserializeNBT(@NotNull HolderLookup.Provider provider, CompoundTag compound)
    {
        searches.clear();
        for (Tag raw : compound.getList(TAG_SEARCHES, Tag.TAG_COMPOUND))
        {
            CompoundTag tag = (CompoundTag) raw;
            ItemStack stack = ItemStack.parseOptional(provider, tag.getCompound(TAG_STACK));
            MarketTier tier = readTier(tag);
            if (!stack.isEmpty()) searches.add(new RetainedSearch(stack, tier, tag.getInt(TAG_INVESTMENT_LEVEL), tag.getLong(TAG_INVESTMENT_UNTIL)));
        }
        subscriptions.clear();
        for (Tag raw : compound.getList(TAG_SUBSCRIPTIONS, Tag.TAG_COMPOUND))
        {
            CompoundTag tag = (CompoundTag) raw;
            ItemStack stack = ItemStack.parseOptional(provider, tag.getCompound(TAG_STACK));
            MarketTier tier = readTier(tag);
            if (!stack.isEmpty()) subscriptions.add(new Subscription(stack, tier, tag.getLong(TAG_LAST_PROCESSED)));
        }
    }

    /** {@inheritDoc} */
    @Override
    public void serializeToView(@NotNull RegistryFriendlyByteBuf buf)
    {
        buf.writeVarInt(capacity(MCTPResearchConstants.RETAINED_SEARCH));
        buf.writeVarInt(capacity(MCTPResearchConstants.MARKETPLACE_SUBSCRIPTIONS));
        buf.writeLong(building.getColony().getWorld().getDayTime() / MarketDailyRoller.TICKS_PER_DAY);
        buf.writeVarInt(searches.size());
        for (RetainedSearch search : searches)
        {
            com.minecolonies.api.util.Utils.serializeCodecMess(buf, search.stack());
            buf.writeVarInt(search.tier().ordinal());
            buf.writeVarInt(search.investmentLevel());
            buf.writeLong(search.investmentUntil());
        }
        buf.writeVarInt(subscriptions.size());
        for (Subscription subscription : subscriptions)
        {
            com.minecolonies.api.util.Utils.serializeCodecMess(buf, subscription.stack());
            buf.writeVarInt(subscription.tier().ordinal());
            buf.writeLong(subscription.lastProcessedDay());
        }
    }

    /** Serializes the fields shared by retained searches and subscriptions. */
    @SuppressWarnings("null")
    private CompoundTag stackTierTag(HolderLookup.Provider provider, ItemStack stack, MarketTier tier)
    {
        CompoundTag tag = new CompoundTag();
        tag.put(TAG_STACK, NullnessBridge.assumeNonnull(stack.save(provider)));
        tag.putString(TAG_TIER, tier.name());
        return tag;
    }

    /** Reads a persisted tier defensively, defaulting to the common tier for legacy data. */
    private MarketTier readTier(CompoundTag tag)
    {
        try { return MarketTier.valueOf(tag.getString(TAG_TIER)); }
        catch (IllegalArgumentException ignored) { return MarketTier.TIER1_COMMON; }
    }
}
