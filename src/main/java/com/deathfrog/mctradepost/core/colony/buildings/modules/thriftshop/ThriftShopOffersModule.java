package com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_RAREFINDS;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.api.advancements.MCTPAdvancementTriggers;
import com.deathfrog.mctradepost.api.research.MCTPResearchConstants;
import com.deathfrog.mctradepost.api.sounds.MCTPModSoundEvents;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingEconModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop.MarketDailyRoller.MarketOffer;
import com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop.MarketDailyRoller.MarketTier;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.item.CoinItem;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.api.util.Utils;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ThriftShopOffersModule extends AbstractBuildingModule implements IPersistentModule 
{
    public static final Logger LOGGER = LogUtils.getLogger();

    final static String RARE_FIND_PURCHASE = "rarefind";

    // ---------------- NBT keys ----------------
    private static final String TAG_OFFERS = "offers";
    private static final String TAG_LAST_ROLL = "lastRoll";
    private static final String TAG_REROLL_COST = "reRollCost";
    private static final String TAG_REROLL_INDEX = "reRollIndex";
    private static final String TAG_REROLL_COOLDOWN = "reRollCooldown";

    private static final String TAG_OFFER_STACK = "stack";
    private static final String TAG_OFFER_PRICE = "price";
    private static final String TAG_OFFER_TIER = "tier"; // store enum name or ordinal

    List<MarketOffer> offers = new ArrayList<>();
    long lastRollDay = 0L;
    int rerollCost = -1;
    int rerollIndex = 0;
    
    
    final static int REROLL_COOLDOWN_MAX = 16 * 16 * 16;
    final static int REROLL_COOLDOWN_MIN = 16;
    int rerollCooldown = REROLL_COOLDOWN_MIN;

    public ThriftShopOffersModule() 
    {
        // Constructor implementation
    }


    /**
     * Deserializes the NBT data for the module, restoring its state from the provided CompoundTag.
     * Clears the current display shelf contents and repopulates it using the data from the NBT.
     * Synchronizes the display frames in the world with the deserialized shelf contents.
     *
     * @param provider The holder lookup provider for item and block references.
     * @param compound The CompoundTag containing the serialized state of the building.
     */
    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        // If no tag exists, keep current state (MineColonies pattern)
        if (compound == null || provider == null)
        {
            return;
        }

        lastRollDay = compound.getLong(TAG_LAST_ROLL);
        rerollCost = compound.getInt(TAG_REROLL_COST);
        rerollCooldown = compound.getInt(TAG_REROLL_COOLDOWN);
        rerollIndex = compound.getInt(TAG_REROLL_INDEX);
        
        // offers
        if (!compound.contains(TAG_OFFERS, Tag.TAG_LIST))
        {
            // Nothing persisted
            offers.clear();
            // LOGGER.info("No offers persisted for Thrift Shop");
            return;
        }

        final ListTag list = compound.getList(TAG_OFFERS, Tag.TAG_COMPOUND);

        final List<MarketOffer> loaded = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++)
        {
            final CompoundTag offerTag = list.getCompound(i);

            // tier
            MarketTier tier = MarketTier.TIER1_COMMON;
            if (offerTag.contains(TAG_OFFER_TIER, Tag.TAG_STRING))
            {
                String name = offerTag.getString(TAG_OFFER_TIER);
                try
                {
                    tier = MarketTier.valueOf(name);
                }
                catch (IllegalArgumentException ex)
                {
                    // Unknown tier string from older version; keep default
                }
            }
            else if (offerTag.contains(TAG_OFFER_TIER, Tag.TAG_INT))
            {
                int ord = offerTag.getInt(TAG_OFFER_TIER);
                MarketTier[] values = MarketTier.values();
                if (ord >= 0 && ord < values.length)
                {
                    tier = values[ord];
                }
            }

            // stack
            ItemStack stack = ItemStack.EMPTY;
            if (offerTag.contains(TAG_OFFER_STACK, Tag.TAG_COMPOUND))
            {
                // 1.21+ safe read using provider
                stack = ItemStack.parseOptional(provider, NullnessBridge.assumeNonnull(offerTag.getCompound(TAG_OFFER_STACK)));
            }

            // price
            int price = offerTag.getInt(TAG_OFFER_PRICE);

            // LOGGER.info("Colony {} - Offer read: {} at {}", building.getColony().getID(), stack.getHoverName(), price);

            if (!stack.isEmpty() && price > 0)
            {
                loaded.add(new MarketOffer(stack, tier, price));
            }
        }

        // Replace state
        offers.clear();
        offers.addAll(loaded);

        // LOGGER.info("Offers read: {}", offers.size());

        markDirty();
    }

    /**
     * Serializes the NBT data for the trade list, storing its state in the
     * provided CompoundTag.
     *
     * @param provider The holder lookup provider for item and block references.
     * @param compound The CompoundTag containing the serialized state of the
     *                 trade list.
     */
    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, CompoundTag compound)
    {
        if (compound == null || provider == null)
        {
            return;
        }

        compound.putLong(TAG_LAST_ROLL, lastRollDay);
        compound.putInt(TAG_REROLL_COST, rerollCost);
        compound.putInt(TAG_REROLL_COOLDOWN, rerollCooldown);
        compound.putInt(TAG_REROLL_INDEX, rerollIndex);

        final ListTag list = new ListTag();

        for (final MarketOffer offer : offers)
        {
            if (offer == null || offer.stack() == null || offer.price() <= 0)
            {
                continue;
            }

            final ItemStack stack = offer.stack();
            final int price = offer.price();

            if (stack.isEmpty())
            {
                continue;
            }

            final CompoundTag offerTag = new CompoundTag();

            // Store tier as enum name (stable across reorderings)
            offerTag.putString(TAG_OFFER_TIER, NullnessBridge.assumeNonnull(offer.tier().name()));

            // Store item stack using provider-aware save (1.21 safe)
            offerTag.put(TAG_OFFER_STACK, NullnessBridge.assumeNonnull(stack.save(provider)));

            offerTag.putInt(TAG_OFFER_PRICE, price);

            // LOGGER.info("Colony {} - Offer saved: {} at {}", building.getColony().getID(), stack.getHoverName(), price);

            list.add(offerTag);
        }
        
        // LOGGER.info("Colony {}: Offers saved {}", building.getColony().getID(), list.size());

        compound.put(TAG_OFFERS, list);
    }

    /**
     * Serializes the trade list to the given RegistryFriendlyByteBuf for
     * transmission to the client. 
     *
     * @param buf the buffer to serialize the trade list to.
     */
    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf)
    {
        // Always write lastRoll first so client can show “last refreshed”
        buf.writeLong(lastRollDay);
        buf.writeInt(rerollCost);

        // Write offers count + each offer
        buf.writeVarInt(offers.size());

        for (final MarketOffer offer : offers)
        {
            // Defensive defaults (client code must mirror this)
            final ItemStack stack = (offer != null && offer.stack() != null) ? offer.stack() : ItemStack.EMPTY;
            final int price = (offer != null && offer.price() > 0) ? offer.price() : -1;
            final MarketTier tier = (offer != null && offer.tier() != null) ? offer.tier() : MarketTier.TIER1_COMMON;

            // ItemStack: MineColonies commonly uses a helper (Utils.serializeCodecMess / deserializeCodecMess).
            Utils.serializeCodecMess(buf, stack);
            buf.writeVarInt(price);

            // Tier: write enum ordinal (smaller), or name (more stable).
            // For view packets, ordinal is fine as long as you control both ends.
            buf.writeVarInt(tier.ordinal());
        }
    }

    /**
     * Calculates the reroll cost for the thrift shop based on the reroll research effect strength.
     * If the reroll research effect strength is less than 1, the reroll cost is set to -1.
     * Otherwise, the reroll cost is decremented by 16 if it is greater than the base value,
     * and set to the base value if it is less than the base value.
     * The base value is the trade coin value multiplied by the GOLD_MULTIPLIER.
     */
    protected void determineRerollCost()
    {
        int oldRerollCost = rerollCost;
        int baseValue = MCTPConfig.tradeCoinValue.get() * CoinItem.GOLD_MULTIPLIER;

        final int rerollResearch = (int) building.getColony().getResearchManager().getResearchEffects().getEffectStrength(MCTPResearchConstants.THRIFTSHOP_REROLL);  

        if (rerollResearch < 1)
        {
            rerollCost = -1;
        }
        else 
        {
            if (rerollCost > baseValue)
            {
                // Decrement reroll cost (they become cheaper over time if selections are not rerolled)
                rerollCost -= rerollCooldown;
                rerollCooldown = Math.min(rerollCooldown + REROLL_COOLDOWN_MIN, REROLL_COOLDOWN_MAX);
            }

            if (rerollCost < baseValue)
            {
                rerollCost = baseValue;
            }
        }

        if (oldRerollCost != rerollCost)
        {
            markDirty();
        }
    }

    /**
     * Determines the number of offers to roll for each tier of the thrift shop, based on the current research tier.
     * The number of offers is determined by the research tier and the "Thrift Shop More" research effect strength.
     * @param thriftShopResearchTier the current research tier
     * @return an array of integers, where the first element is the number of common offers, the second element is the number of uncommon offers, the third element is the number of rare offers, and the fourth element is the number of epic offers
     */
    protected int[] determineOffers(final int thriftShopResearchTier)
    {
        int commonOffers = 0;
        int uncommonOffers = 0;
        int rareOffers = 0;
        int epicOffers = 0;

        double thriftShopMore = building.getColony().getResearchManager().getResearchEffects().getEffectStrength(MCTPResearchConstants.THRIFTSHOP_MORE);

        switch (thriftShopResearchTier)
        {
            case 0:
            default:
                commonOffers = 0;
                uncommonOffers = 0;
                rareOffers = 0;
                epicOffers = 0;
                break;

            case 1:
                commonOffers = 2;
                uncommonOffers = 1;
                rareOffers = 0;
                epicOffers = 0;
                break;

            case 2:
                commonOffers = 3;
                uncommonOffers = 2;
                rareOffers = 0;
                epicOffers = 0;
                break;

            case 3:
                commonOffers = 4;
                uncommonOffers = 2;
                rareOffers = 1;
                epicOffers = 0;
                break;

            case 4:
                commonOffers = 4;
                uncommonOffers = 3;
                rareOffers = 2;
                epicOffers = 1;
                break;
        }

        if (thriftShopMore > 0)
        {
            commonOffers *= (int) thriftShopMore;
            uncommonOffers *= (int) thriftShopMore;
            rareOffers *= (int) thriftShopMore;
            epicOffers *= (int) thriftShopMore;
        }

        return new int[]{commonOffers, uncommonOffers, rareOffers, epicOffers};        
    }

    /**
     * Rolls the daily offers for the thrift shop. This is called automatically by the building module on server side.
     * The rolled offers are stored in the offers field of this class.
     */
    public void rollDailyOffers(boolean reroll)
    {
        Level level = building.getColony().getWorld();

        if (level == null || level.isClientSide)
        {
            TraceUtils.dynamicTrace(TRACE_RAREFINDS, () -> LOGGER.info("Colony {}: Rare Finds - Skipping rolls for client side: {}", building.getColony().getID()));
            return;
        }

        determineRerollCost();

        final int thriftShopResearchTier = (int) building.getColony().getResearchManager().getResearchEffects().getEffectStrength(MCTPResearchConstants.THRIFTSHOP_TIER);  

        ICitizenData shopkeeper = ((BuildingMarketplace) building).shopkeeper();

        if (thriftShopResearchTier < 1 || shopkeeper == null)
        {
            TraceUtils.dynamicTrace(TRACE_RAREFINDS, () -> LOGGER.info("Colony {}: Rare Finds - Skipping rolls and wiping selections for unresearched or unstaffed thrift shop: {}", building.getColony().getID(), thriftShopResearchTier));
            offers.clear();
            return; 
        }

        // Current Minecraft "day number"
        final long currentDay = level.getDayTime() / MarketDailyRoller.TICKS_PER_DAY;

        // lastRoll stores the day number
        if (lastRollDay == currentDay && !reroll)
        {
            TraceUtils.dynamicTrace(TRACE_RAREFINDS, () -> LOGGER.info("Colony {}: Rare Finds - Skipping rolls for already-rolled day: {}", building.getColony().getID(), lastRollDay));
            return; 
        }

        if (reroll)
        {
            this.rerollIndex++;
        }
        else
        {
            this.rerollIndex = 0;
        }

        final int[] offerAmounts = determineOffers(thriftShopResearchTier);

        TraceUtils.dynamicTrace(TRACE_RAREFINDS, () -> LOGGER.info("Colony {}: Rare Finds - rolling daily offers for day: {} at research tier {} with offers {} common, {} uncommon, {} rare, {} epic", 
            building.getColony().getID(), currentDay, thriftShopResearchTier, offerAmounts[0], offerAmounts[1], offerAmounts[2], offerAmounts[3]));

        lastRollDay = currentDay;
        offers = MarketDailyRoller.rollDailyOffers((ServerLevel) level, (BuildingMarketplace) building, rerollIndex, offerAmounts[0], offerAmounts[1], offerAmounts[2], offerAmounts[3]);


        MessageUtils.format("mctradepost.thriftshop.reroll.success").sendTo(building.getColony()).forAllPlayers();

        markDirty();
    }

    /**
     * Rerolls the daily offers for the thrift shop, given the player who triggered it.
     * The cost of rerolling is stored in rerollCost and is taken from the colony's economy.
     * If the colony does not have enough money to reroll, a message is sent to the player.
     * The cost of rerolling is increased by a factor of CoinItem.GOLD_MULTIPLIER each time it is called.
     * @param player the player who triggered the reroll
     */
    public void rerollSelections(Player player) 
    {
        TraceUtils.dynamicTrace(TRACE_RAREFINDS, () -> LOGGER.info("Colony {}: Processing reroll request.", building.getColony().getID()));

        final int rerollResearch = (int) building.getColony().getResearchManager().getResearchEffects().getEffectStrength(MCTPResearchConstants.THRIFTSHOP_REROLL);  
        final BuildingEconModule econ = building.getModule(MCTPBuildingModules.ECON_MODULE);

        if (rerollCost > econ.getTotalBalance() || rerollResearch < 1)
        {
            MessageUtils.format("mctradepost.thriftshop.reroll.nsf").sendTo(player);
            return;
        }

        econ.deposit(-rerollCost);

        rerollCooldown = REROLL_COOLDOWN_MIN;
        rerollCost = rerollCost * CoinItem.GOLD_MULTIPLIER;

        withdrawEffects(building);

        TraceUtils.dynamicTrace(TRACE_RAREFINDS, () -> LOGGER.info("Colony {}: Clearing existing offers.", building.getColony().getID()));
        offers.clear();
        rollDailyOffers(true);

        building.markDirty();
        markDirty();
    }


    /**
     * Purchases an item from the daily offers of the thrift shop building.
     * If the item is found in the daily offers, it is removed from the list and the player's hotbar is updated.
     * The price of the item is removed from the building's economy.
     * 
     * @param stack the item to purchase
     * @param price the price of the item
     * @param player the player making the purchase
     */
    public void purchaseItem(@Nonnull ItemStack stack, int price, Player player)
    {
        TraceUtils.dynamicTrace(TRACE_RAREFINDS, () -> LOGGER.info("Colony {}: Processing purchase of {} at cost {}", building.getColony().getID(), stack.getHoverName(), price));

        boolean found = false;

        for (int i = 0; i < offers.size(); i++)
        {
            MarketOffer offer = offers.get(i);
            ItemStack listItem = offer.stack();
            if (offer == null || listItem == null || offer.price() <= 0)
            {
                continue;
            }

            if (!ItemStack.isSameItem(stack, listItem) || offer.price() != price)
            {
                continue;
            }

            found = true;

            processPurchase(offer, player);

            break;
        }
            
        if (!found)
        {
            TraceUtils.dynamicTrace(TRACE_RAREFINDS, () -> LOGGER.info("Colony {}: Could not find matching offer: {}", building.getColony().getID(), stack.getHoverName()));
        }

        building.markDirty();
        return;
    }

    /**
     * Processes a purchase made by a player from the thrift shop.
     * 
     * First, it checks if the building has enough money to purchase the item.
     * If not, it sends a message to the player and returns.
     * 
     * If the item is an epic item, it triggers the rare find advancement.
     * It then tracks the rare find purchase stat for the building.
     * It puts the item in the player's hotbar or drops it if their inventory is full.
     * It then removes the money from the building's economy.
     * If the bottomless research has not been done, or the item tier is too high for it to apply,
     * it removes the offer from the list of offers and marks the building as dirty.
     * Finally, it withdraws the effects from the building and adds one experience to the shopkeeper.
     * @param offer the market offer to purchase
     * @param player the player making the purchase
     */    
    protected void processPurchase(MarketOffer offer, Player player)
    {
        final int bottomlessOffers = (int) building.getColony().getResearchManager().getResearchEffects().getEffectStrength(MCTPResearchConstants.THRIFTSHOP_BOTTOMLESS);  

        BuildingEconModule econ = building.getModule(MCTPBuildingModules.ECON_MODULE);

        if (offer.price() > econ.getTotalBalance())
        {
            MessageUtils.format("mctradepost.thriftshop.nsf").sendTo(player);
            return;
        }

        if (offer.tier() == MarketTier.TIER4_EPIC)
        {
            if (player instanceof ServerPlayer serverPlayer)
            {
                MCTPAdvancementTriggers.RARE_FIND.get().trigger(serverPlayer);
            }
        }

        StatsUtil.trackStat(building, RARE_FIND_PURCHASE, 1);

        InventoryUtils.putItemToHotbarAndSelectOrDrop(offer.stack().copy(), player);

        econ.deposit(-offer.price());

        // Remove the offer from the list if the bottomless research has not been done, or the tier is too high for it to apply.
        if (bottomlessOffers < 1 || offer.tier() == MarketTier.TIER3_RARE || offer.tier() == MarketTier.TIER4_EPIC)
        {
            offers.remove(offer);
            markDirty();
        }

        withdrawEffects(building);

        ICitizenData shopkeeper = ((BuildingMarketplace) building).shopkeeper();
        if (shopkeeper != null)
        {
            Optional<AbstractEntityCitizen> shopkeeperEntity = shopkeeper.getEntity();

            if (shopkeeperEntity.isPresent())
            {
                AbstractEntityCitizen shopkeeperCitizen = shopkeeperEntity.get();
                shopkeeperCitizen.getCitizenExperienceHandler().addExperience(1);
            }
        } 
    }

    /**
     * Plays a sound and displays particles at the marketplace's location when a player withdraws coins.
     * The sound is a cash register sound and the particles are the happy villager particles.
     * @param marketplace the marketplace building
     */
    protected void withdrawEffects(IBuilding marketplace)
    {
        BlockPos pos = marketplace.getPosition();
        marketplace.getColony().getWorld().playSound(
                null,                         // null = all players tracking this entity
                pos.getX(), pos.getY(), pos.getZ(),
                MCTPModSoundEvents.CASH_REGISTER,
                net.minecraft.sounds.SoundSource.NEUTRAL,
                0.3F,                         // volume
                1.0F);                        // pitch

        ((ServerLevel)marketplace.getColony().getWorld()).sendParticles(
                NullnessBridge.assumeNonnull(ParticleTypes.HAPPY_VILLAGER),
                pos.getX(), pos.getY(), pos.getZ(),
                4,                           // count
                0.3, 0.3, 0.3,               // x,y,z scatter
                0.0);                        // speed
    }

}
