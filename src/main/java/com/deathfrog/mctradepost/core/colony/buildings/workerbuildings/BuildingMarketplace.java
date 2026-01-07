package com.deathfrog.mctradepost.core.colony.buildings.workerbuildings;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IVisitorData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.ITickRateStateMachine;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.TavernBuildingModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.modules.settings.BoolSetting;
import com.minecolonies.core.colony.buildings.modules.settings.IntSetting;
import com.minecolonies.core.colony.buildings.modules.settings.SettingKey;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.visitor.VisitorCitizen;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.api.colony.buildings.jobs.MCTPModJobs;
import com.deathfrog.mctradepost.api.research.MCTPResearchConstants;
import com.deathfrog.mctradepost.api.util.FrameLikeAccess;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.client.gui.modules.WindowEconModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingEconModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop.ThriftShopOffersModule;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.DisplayCase.SaleState;
import com.deathfrog.mctradepost.core.entity.ai.workers.minimal.EntityAIShoppingTask;
import com.deathfrog.mctradepost.core.event.wishingwell.WellLocations;
import com.deathfrog.mctradepost.item.CoinItem;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_SHOPKEEPER;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_SHOPPER;
import static com.minecolonies.api.util.constant.BuildingConstants.CONST_DEFAULT_MAX_BUILDING_LEVEL;

/**
 * Class of the marketplace building.
 */
public class BuildingMarketplace extends AbstractBuilding
{
    public static final String STRUCT_TAG_INVENTORY_CONTAINER = "inventory";
    public static final Logger LOGGER = LogUtils.getLogger();

    protected WellLocations ritualData = new WellLocations();

    protected final static int ADVERTISING_COOLDOWN_MAX = 3; // In colony ticks (500 regular ticks)
    protected int advertisingCooldown = ADVERTISING_COOLDOWN_MAX;

    // Keep a list of who the resort has "advertised" to (who has had the EntityAIShoppingTask added to their AI)
    // This is deliberately global across all marketplaces.
    protected static List<IVisitorData> advertisingList = new ArrayList<>();

    public BuildingMarketplace(@NotNull IColony colony, BlockPos pos)
    {
        super(colony, pos);
    }

    public static final String REQUESTS_TYPE_SELLABLE = "com.mctradepost.coremod.request.sellable";

    /**
     * Tag to store the display shelf list.
     */
    private static final String TAG_DISPLAYSHELVES = "displayshelves";

    /**
     * Structurize tag identifying where display shelves are expected to be.
     */
    private static final String STRUCT_TAG_DISPLAY_SHELF = "display_shelf";

    /**
     * Map of shelf locations and contents.
     */
    private final Map<BlockPos, DisplayCase> displayShelfContents = new ConcurrentHashMap<>();

    /**
     * Key for min remainder at warehouse.
     */
    public static final ISettingKey<IntSetting> MIN =
        new SettingKey<>(IntSetting.class, ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "warehousemin"));

    /**
     * Key for autominting setting.
     */
    public static final ISettingKey<BoolSetting> AUTOMINT =
        new SettingKey<>(BoolSetting.class, ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "automint"));

    /**
     * Return a list of display shelves assigned to this hut.
     *
     * @return copy of the list
     */
    public List<BlockPos> getDisplayShelfPositions()
    {
        Set<BlockPos> result = displayShelfContents.keySet();

        if (result != null)
        {
            return ImmutableList.copyOf(result);
        }

        return ImmutableList.of();
    }

    /**
     * Returns a list of display shelves that have items for sale.
     * 
     * @return a list of display shelves with items for sale
     */
    public List<DisplayCase> getDisplayShelvesWithItemsForSale()
    {
        List<DisplayCase> result = new ArrayList<>();
        for (DisplayCase display : displayShelfContents.values())
        {
            if (display.getSaleState() == SaleState.FOR_SALE)
            {
                result.add(display);
            }
        }

        return result;
    }

    /**
     * Returns the map of display shelf positions to their contents. The positions are the BlockPos of the block that the item frame is
     * attached to. The contents are the ItemStack that is currently in the item frame.
     * 
     * @return a copy of the map.
     */
    public Map<BlockPos, DisplayCase> getDisplayShelves()
    {
        return displayShelfContents;
    }

    /**
     * Returns the BlockPos of the inventory container in the marketplace building. If there are more than one inventory container, it
     * will log a warning and use the first one.
     * 
     * @return the BlockPos of the inventory container
     */
    public BlockPos identifyInventoryPosition()
    {
        final List<BlockPos> inventoryContainers = getLocationsFromTag(STRUCT_TAG_INVENTORY_CONTAINER);

        if (inventoryContainers.isEmpty())
        {
            return BlockPos.ZERO;
        }

        if (inventoryContainers.size() > 1)
        {
            LOGGER.warn("Marketplace for {} at {} has more than one inventory container. Using the first one.", this.getColony().getID(), this.getPosition());
        }

        return inventoryContainers.get(0);
    }

    @NotNull
    @Override
    public String getSchematicName()
    {
        return ModBuildings.MARKETPLACE_ID;
    }

    @Override
    public int getMaxBuildingLevel()
    {
        return CONST_DEFAULT_MAX_BUILDING_LEVEL;
    }

    /**
     * Gets the ritual data for this marketplace, which tracks the state of wishing wells within the colony.
     * 
     * @return the ritual data for this marketplace.
     */
    public WellLocations getRitualData()
    {
        return ritualData;
    }

    /**
     * Gets the shopkeeper of the marketplace, if any. Returns null if no shopkeeper is assigned.
     * 
     * @return the shopkeeper of the marketplace, or null if none is assigned.
     */
    public ICitizenData shopkeeper()
    {
        WorkerBuildingModule module = this.getModule(WorkerBuildingModule.class, m -> m.getJobEntry() == MCTPModJobs.shopkeeper.get());

        List<ICitizenData> employees = module.getAssignedCitizen();

        if (employees.isEmpty())
        {
            return null;
        }

        ICitizenData shopkeeper = employees.get(0);

        return shopkeeper;
    }

    /**
     * Returns true if the marketplace is open for business, i.e. if it has a shopkeeper assigned and the shopkeeper is working.
     * 
     * @return true if the marketplace is open for business, false otherwise.
     */
    public boolean isOpenForBusiness()
    {
        ICitizenData shopkeeper = shopkeeper();

        if (shopkeeper == null)
        {
            return false;
        }

        final Optional<AbstractEntityCitizen> optionalEntityCitizen = shopkeeper.getEntity();

        if (optionalEntityCitizen == null || !optionalEntityCitizen.isPresent())
        {
            return false;
        }

        AbstractEntityCitizen shopkeeperEntity = optionalEntityCitizen.get();
        IState workState = ((EntityCitizen) shopkeeperEntity).getCitizenAI().getState();

        return CitizenAIState.WORKING.equals(workState);
    }

    /**
     * Called when the building is loaded from world and the display shelf at the given position is no longer present.
     * This can happen if the building is moved, or if the display shelf is destroyed.
     * <p>
     * This function will attempt to resolve the display shelf by looking for a vanilla ItemFrame or a FastItemFrame block at the given position.
     * If a frame-like block is found, it will be updated to reflect the current items in the display shelf contents.
     * If no frame-like block is found, but the position is still an expected shelf position, the display shelf will be marked as missing.
     * <p>
     * If the display shelf is marked as missing and the building is built, a warning will be logged to indicate that a display shelf is missing.
     * <p>
     * This function is idempotent and can be called multiple times on the same building without causing harm.
     * <p>
     * The function takes a BlockPos argument, which is the position of the display shelf to be resolved.
     */
    public void lostShelfAtDisplayPos(@Nonnull BlockPos pos)
    {
        if (!displayShelfContents.containsKey(pos))
        {
            MCTradePostMod.LOGGER.warn("Looking for a display shelf at {}", pos);
            return;
        }

        TraceUtils.dynamicTrace(TRACE_SHOPKEEPER, () -> LOGGER.info("Colony {}: Shopkeeper - evaluating lost shelf at {}", getColony().getID(), pos));

        final Level level = colony.getWorld();
        final DisplayCase existing = displayShelfContents.get(pos);

        // 1) If this is no longer an expected shelf position, remove it (building moved, etc.)
        final List<BlockPos> expectedPositions = getLocationsFromTag(STRUCT_TAG_DISPLAY_SHELF);
        if (!expectedPositions.contains(pos))
        {
            displayShelfContents.remove(pos);
            return;
        }

        // 2) Try to resolve frame-like (vanilla UUID/entity OR FastItemFrames block)
        final FrameLikeAccess.FrameHandle handle =
            FrameLikeAccess.resolve(level, pos, existing.getFrameId());

        if (!handle.exists())
        {
            // Expected shelf pos, but neither entity nor frame-like block exists.
            // Keep shelf entry but mark missing.
            displayShelfContents.put(pos, new DisplayCase(pos, null));
            if (this.isBuilt())
            {
                MCTradePostMod.LOGGER.warn("Missing a display frame at {}", pos);
            }
            return;
        }

        // 3) It exists. If it's a vanilla ItemFrame and we can discover its UUID, refresh it.
        // (FrameLikeAccess might have resolved via tag/block; we still want the UUID if a real entity exists.)
        AABB box = new AABB(pos).inflate(0.25);

        final List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, NullnessBridge.assumeNonnull(box));
        if (!frames.isEmpty())
        {
            final ItemFrame frame = frames.get(0);

            // Preserve whatever item we were tracking
            displayShelfContents.put(pos, new DisplayCase(pos, frame.getUUID(), existing.getStack(), 0));
            return;
        }

        // 4) Frame-like exists but no entity (Fast Item Frames case): keep the shelf, clear UUID
        displayShelfContents.put(pos, new DisplayCase(pos, null, existing.getStack(), 0));

        // update to reflect current items in the display shelf contents
        ItemStack stored = existing.getStack();
        if (stored != null && !stored.isEmpty() && handle.getItem().isEmpty())
        {
            handle.setItem(stored);
        }
    }

    /**
     * Synchronizes the items in the display frames with the saved items in the building's display shelf contents. Iterates over each
     * display shelf position and sets the corresponding item in the item frame at that position. Ensures that the item frames in the
     * world reflect the current state of the building's item storage.
     */
    private void syncDisplayFramesWithSavedItems()
    {
        final Level level = colony.getWorld();
        if (level == null) return;

        for (Map.Entry<BlockPos, DisplayCase> entry : displayShelfContents.entrySet())
        {
            final BlockPos pos = entry.getKey();
            final DisplayCase dc = entry.getValue();
            if (dc == null) continue;

            final ItemStack saved = dc.getStack() == null ? ItemStack.EMPTY : dc.getStack();
            final UUID frameId = dc.getFrameId(); // may be null with Fast Item Frames

            final FrameLikeAccess.FrameHandle handle = FrameLikeAccess.resolve(level, pos, frameId);

            if (!handle.exists())
            {
                MCTradePostMod.LOGGER.warn("Missing a display frame/frame-like at {}", pos);
                continue;
            }

            // Optional: avoid redundant writes
            final ItemStack current = handle.getItem();
            @SuppressWarnings("null")
            final boolean alreadyMatches =
                ItemStack.isSameItemSameComponents(current, saved) && current.getCount() == saved.getCount();

            if (!alreadyMatches)
            {
                final boolean ok = handle.setItem(saved);
                if (!ok)
                {
                    MCTradePostMod.LOGGER.warn("Could not sync display frame/frame-like at {}", pos);
                }
            }
        }
    }

    /**
     * Initialize the display shelf locations based on what is tagged in the structure. This makes the building look for the correct
     * number of display shelves even if some are missing. That way a "repair" action will fix the problem.
     * 
     * @return a list of block positions that are tagged as display shelves
     */
    public List<BlockPos> identifyExpectedShelfPositions()
    {
        // We want any tagged display locations nto be added into the displayShelfContents if not already there.
        final List<BlockPos> shelfLocations = getLocationsFromTag(STRUCT_TAG_DISPLAY_SHELF);
        return shelfLocations;
    }

    /**
     * This method is used to initialize the displayShelfContents map with all tagged display locations in the structure. It is called
     * when the building is initialized, and ensures that any missing display shelves are marked as such. This is important because it
     * allows the building to correctly track the display shelves and their contents, even if some are missing.
     */
    public void markExpectedShelfPositionsAsShelfLocations()
    {
        List<BlockPos> shelfLocations = identifyExpectedShelfPositions();
        for (BlockPos pos : shelfLocations)
        {
            displayShelfContents.putIfAbsent(pos, new DisplayCase(pos, null));
        }
    }

    /**
     * Deserializes the NBT data for the building, restoring its state from the provided CompoundTag. Clears the current display shelf
     * contents and repopulates it using the data from the NBT. Synchronizes the display frames in the world with the deserialized
     * shelf contents.
     *
     * @param provider The holder lookup provider for item and block references.
     * @param compound The CompoundTag containing the serialized state of the building.
     */
    // @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        super.deserializeNBT(provider, compound);

        // Fill in display shelf locations with the saved items.
        ListTag shelfTagList = compound.getList(TAG_DISPLAYSHELVES, Tag.TAG_COMPOUND);
        for (int i = 0; i < shelfTagList.size(); ++i)
        {
            CompoundTag shelfTag = shelfTagList.getCompound(i);
            DisplayCase contents = DisplayCase.fromNBT(shelfTag, provider);
            displayShelfContents.put(contents.getPos(), contents);
        }

        ritualData.deserializeNBT(NullnessBridge.assumeNonnull(provider), compound);

        syncDisplayFramesWithSavedItems();
    }

    /**
     * Retrieves the level of the primary skill of the shopkeeper assigned to this building.
     * If there is no shopkeeper, or the shopkeeper is not assigned to a module, or the module does not have a primary skill, this method returns 0.
     * 
     * @return the level of the primary skill of the shopkeeper, or 0 if no suitable worker is found.
     */
    public int shopkeeperPrimarySkill()
    {
        int skill = 0;
        
        WorkerBuildingModule module = this.getModule(WorkerBuildingModule.class, m -> m.getJobEntry() == MCTPModJobs.shopkeeper.get());

        ICitizenData shopkeeper = shopkeeper();

        if (shopkeeper != null)
        {
            skill = shopkeeper.getCitizenSkillHandler().getLevel(module.getPrimarySkill());
        }

        return skill;
    }

    /**
     * Mints a given number of trade coins, removing the corresponding amount of value from the building's economy.
     * 
     * @param player      the player using the minting function (not used, but required for later potential functionality)
     * @param coinsToMint the number of coins to mint
     * @return a stack of the minted coins
     */
    public ItemStack mintCoins(Player player, int coinsToMint)
    {
        int coinValue = MCTPConfig.tradeCoinValue.get();
        ItemStack coinStack = ItemStack.EMPTY;

        if (coinsToMint > 0)
        {
            int valueToRemove = coinsToMint * coinValue;

            BuildingEconModule econ = this.getModule(MCTPBuildingModules.ECON_MODULE);
            if (valueToRemove < econ.getTotalBalance())
            {
                CoinItem coinItem = MCTradePostMod.MCTP_COIN_ITEM.get();

                if (coinItem == null)
                {
                    MCTradePostMod.LOGGER.error("TradePost coin item missing during minting. This should not happen! Please report.");
                    return ItemStack.EMPTY;
                }

                coinStack = new ItemStack(coinItem, coinsToMint);
                CoinItem.setMintColony(coinStack, colony.getName());
                econ.incrementBy(WindowEconModule.COINS_MINTED, coinsToMint);
                econ.deposit(-valueToRemove);
            }
            else
            {
                if (player != null)
                {
                    MessageUtils.format("mctradepost.marketplace.nsf").sendTo(player);
                }
            }
        }

        return coinStack;
    }

    /**
     * Deposits a given stack of trade coins into the building's economy, adding the corresponding amount of value to the economy.
     * 
     * @param player         the player using the depositing function (not used, but required for later potential functionality)
     * @param coinsToDeposit the stack of coins to deposit
     */
    public void depositCoins(Player player, ItemStack coinsToDeposit)
    {
        int coinValue = MCTPConfig.tradeCoinValue.get();

        CoinItem goldCoin = MCTradePostMod.MCTP_COIN_GOLD.get();
        CoinItem diamondCoin = MCTradePostMod.MCTP_COIN_DIAMOND.get();

        if (goldCoin == null || diamondCoin == null)
        {
            MCTradePostMod.LOGGER.error("TradePost coin items missing during depositing. This should not happen! Please report.");
            return;
        }

        if (coinsToDeposit.is(goldCoin))
        {
            coinValue = coinValue * CoinItem.GOLD_MULTIPLIER;
        }
        else if (coinsToDeposit.is(diamondCoin))
        {
            coinValue = coinValue * CoinItem.DIAMOND_MULTIPLIER;
        }

        if (coinsToDeposit.getCount() > 0)
        {
            int valueToAdd = coinsToDeposit.getCount() * coinValue;

            BuildingEconModule econ = this.getModule(MCTPBuildingModules.ECON_MODULE);

            econ.deposit(valueToAdd);

            // Now remove the coins from the player's inventory
            coinsToDeposit.setCount(0);
            player.getInventory().setChanged();
        }
    }

    /**
     * Finds the nearest BuildingMarketplace to the given BlockPos in the given Level, assuming BlockPos is located inside a colony.
     * 
     * @param level the level to search in
     * @param pos   the BlockPos to search near
     * @return the nearest BuildingMarketplace, or null if none is found
     */
    public static BuildingMarketplace getMarketplaceFromPos(@Nonnull Level level, @Nonnull BlockPos pos)
    {
        // Check if inside a MineColonies colony
        BuildingMarketplace closestMarketplace = null;
        IColonyManager colonyManager = IColonyManager.getInstance();
        IColony colony = colonyManager.getColonyByPosFromWorld(level, pos);

        if (colony != null)
        {
            // Find the nearest BuildingMarketplace
            closestMarketplace = colony.getBuildingManager()
                .getBuildings()
                .values()
                .stream()
                .filter(b -> b instanceof BuildingMarketplace)
                .map(b -> (BuildingMarketplace) b)
                .min(Comparator.comparingDouble(market -> market.getPosition().distSqr(pos)))
                .orElse(null);
        }
        return closestMarketplace;
    }

    /**
     * Serializes the NBT data for the building, including the display shelf contents. The display shelf contents are stored in a list
     * of CompoundTags, where each CompoundTag contains the BlockPos of the display shelf and the ItemStack of the item in that shelf.
     * The CompoundTag is stored in the parent CompoundTag under the key TAG_DISPLAYSHELVES.
     * 
     * @param provider The holder lookup provider for item and block references.
     * @return The serialized CompoundTag containing the state of the building.
     */
    // @Override
    public CompoundTag serializeNBT(@NotNull final HolderLookup.Provider provider)
    {
        final CompoundTag compound = super.serializeNBT(provider);
        final ListTag shelfTagList = new ListTag();

        for (Map.Entry<BlockPos, DisplayCase> entry : displayShelfContents.entrySet())
        {
            shelfTagList.add(entry.getValue().toNBT(NullnessBridge.assumeNonnull(provider)));
        }

        compound.put(TAG_DISPLAYSHELVES, shelfTagList);

        ritualData.serializeNBT(NullnessBridge.assumeNonnull(provider));

        return compound;
    }

    /**
     * Called every tick that the colony updates.
     * 
     * @param colony the colony that this building is a part of
     */
    @Override
    public void onColonyTick(IColony colony)
    {
        super.onColonyTick(colony);
        advertisingCooldown--;

        ThriftShopOffersModule thriftModule = this.getModule(MCTPBuildingModules.THRIFTSHOP);

        if (thriftModule != null)
        {
            thriftModule.rollDailyOffers(false);
        }

        if (advertisingCooldown > 0) return;

        List<Integer> visitorIDs = new ArrayList<Integer>();

        // MCTradePostMod.LOGGER.info("Looking for a tavern to advertise at.");

        // Once the marketplace is built, visitors start thinking about shopping...
        // Note: approach below for finding tavern mimics EventHandler.onEntityConverted
        final BlockPos tavernPos =
            colony.getBuildingManager().getRandomBuilding(b -> !b.getModules(TavernBuildingModule.class).isEmpty());
        if (tavernPos != null)
        {
            // MCTradePostMod.LOGGER.info("Tavern module found - collecting visitor IDs.");
            final IBuilding tavern = colony.getBuildingManager().getBuilding(tavernPos);
            TavernBuildingModule tavernModule = tavern.getModule(TavernBuildingModule.class);
            visitorIDs.addAll(tavernModule.getExternalCitizens());
        }

        for (Integer visitorID : visitorIDs)
        {
            IVisitorData visitor = (IVisitorData) colony.getVisitorManager().getVisitor(visitorID);

            if (visitor != null && !visitor.getEntity().isEmpty() && !advertisingList.contains(visitor))
            {
                TraceUtils.dynamicTrace(TRACE_SHOPPER,
                    () -> LOGGER.info("Adding visitor to advertising list: {}", visitor.getEntity().get().getName()));

                VisitorCitizen vitizen = (VisitorCitizen) visitor.getEntity().get();

                ITickRateStateMachine<IState> stateMachine = vitizen.getEntityStateController();
                EntityAIShoppingTask shoppingTask = new EntityAIShoppingTask(visitor, this);
                shoppingTask.init(stateMachine);

                advertisingList.add(visitor);
            }
        }

        advertisingCooldown = ADVERTISING_COOLDOWN_MAX;
    }

    /**
     * Calculate the chance of a visitor shopping at this marketplace based on its building level and the global config setting
     * MCTPConfig.shoppingChance.
     * 
     * @return the chance of a visitor shopping at this marketplace
     */
    public double shoppingChance()
    {
        double shoppingChance = MCTPConfig.shoppingChance.get() / 100.0;
        double researchModifier =
            getColony().getResearchManager().getResearchEffects().getEffectStrength(MCTPResearchConstants.ADVERTISING);
        shoppingChance = (shoppingChance * 1 + researchModifier);
        return this.getBuildingLevel() * shoppingChance;
    }
}
