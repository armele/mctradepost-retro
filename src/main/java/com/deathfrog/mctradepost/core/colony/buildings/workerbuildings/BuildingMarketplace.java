package com.deathfrog.mctradepost.core.colony.buildings.workerbuildings;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IVisitorData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.ITickRateStateMachine;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.TavernBuildingModule;
import com.minecolonies.core.colony.buildings.modules.settings.IntSetting;
import com.minecolonies.core.colony.buildings.modules.settings.SettingKey;
import com.minecolonies.core.entity.ai.visitor.EntityAIVisitor.VisitorState;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.minecolonies.core.entity.visitor.VisitorCitizen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.core.client.gui.modules.WindowEconModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingEconModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.event.wishingwell.WellLocations;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nonnull;

import static com.minecolonies.api.util.constant.BuildingConstants.CONST_DEFAULT_MAX_BUILDING_LEVEL;

/**
 * Class of the marketplace building.
 */
public class BuildingMarketplace extends AbstractBuilding
{
    public enum ShoppingState implements IState
    {
        GOING_SHOPPING,
        IS_SHOPPING,
        DONE_SHOPPING
    }

    protected WellLocations ritualData = new WellLocations();

    protected final static int ADVERTISING_COOLDOWN_MAX = 3; // In colony ticks (500 regular ticks)
    protected int advertisingCooldown = ADVERTISING_COOLDOWN_MAX;

    // Keep a list of who the resort has "advertized" to (who has had the EntityAIShoppingTask added to their AI)
    // This is deliberately global across all marketplaces.
    protected static List<IVisitorData> advertisingList = new ArrayList<>();

    public BuildingMarketplace(@NotNull IColony colony, BlockPos pos) {
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
    public static final ISettingKey<IntSetting> MIN = new SettingKey<>(IntSetting.class, ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "warehousemin"));

    /**
     * Return a list of display shelves assigned to this hut.
     *
     * @return copy of the list
     */
    public List<BlockPos> getDisplayShelfPositions()
    {
        return ImmutableList.copyOf(displayShelfContents.keySet());
    }

    /**
     * Returns the map of display shelf positions to their contents.
     * The positions are the BlockPos of the block that the item frame is attached to.
     * The contents are the ItemStack that is currently in the item frame.
     * @return a copy of the map.
     */
    public Map<BlockPos, DisplayCase> getDisplayShelves()
    {
        return displayShelfContents;
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
     * @return the ritual data for this marketplace.
     */
    public WellLocations getRitualData() {
        return ritualData;
    }

    @Override
    public void registerBlockPosition(@NotNull final BlockState block, @NotNull final BlockPos pos, @NotNull final Level world)
    {
        // MCTradePostMod.LOGGER.info("Registering Block Position: " + block + " at " + pos);
        super.registerBlockPosition(block, pos, world);

        List<ItemFrame> frames = world.getEntitiesOfClass(ItemFrame.class, new AABB(pos));

        if (!frames.isEmpty() && !displayShelfContents.containsKey(pos)) {
            MCTradePostMod.LOGGER.info("Adding display position: {}", pos);
            displayShelfContents.put(pos, new DisplayCase(pos, frames.get(0).getUUID()));
        }
    }

    /**
     * Called when the colony's world is loaded and the display frame that was at the given position is not found.
     * If the display frame exists in the world, it will be updated to reflect the current items in the display shelf contents.
     * 
     * @param pos The position of the display shelf.
     */
    public void lostShelfAtDisplayPos(BlockPos pos) {
        if (displayShelfContents.containsKey(pos)) {
            List<ItemFrame> frames = colony.getWorld().getEntitiesOfClass(ItemFrame.class, new AABB(pos));

            // If a new frame can be found at the expected position, use it.
            if (!frames.isEmpty()) {
                displayShelfContents.put(pos, new DisplayCase(pos, frames.get(0).getUUID(), displayShelfContents.get(pos).getStack(), 0));
                // Otherwise, issue a message about the missing frame.
            } else {
                List<BlockPos> expectedPositions = getLocationsFromTag(STRUCT_TAG_DISPLAY_SHELF);

                // If this is no longer an expected shelf position (building has been relocated, for example), remove it.
                if (!expectedPositions.contains(pos)) {
                    displayShelfContents.remove(pos);
                } else {
                    displayShelfContents.put(pos, new DisplayCase(pos, null));
                    if (this.isBuilt()) {  // Only send this if the building is built, otherwise it will be ignored.
                        MCTradePostMod.LOGGER.warn("Missing a display frame at {}", pos);                        
                    }

                }
            }       
        } else {
            MCTradePostMod.LOGGER.warn("Looking for a display shelf at {}", pos);
        }
    }
    
    /**
     * Synchronizes the items in the display frames with the saved items in the building's display shelf contents.
     * Iterates over each display shelf position and sets the corresponding item in the item frame at that position.
     * Ensures that the item frames in the world reflect the current state of the building's item storage.
     */
    private void syncDisplayFramesWithSavedItems()
    {
        Level level = colony.getWorld();
        if (level == null) return;

        for (Map.Entry<BlockPos, DisplayCase> entry : displayShelfContents.entrySet())
        {
            ItemStack stack = entry.getValue().getStack();

            ItemFrame frame = (ItemFrame) ((ServerLevel) level).getEntity(entry.getValue().getFrameId());

            if (frame == null) {
                MCTradePostMod.LOGGER.warn("Missing a display frame at {}", entry.getKey());
            } else {
                frame.setItem(stack);
            }
        }
    }

    /**
     * Initialize the display shelf locations based on what is tagged in the structure.
     * This makes the building look for the correct number of display shelves even if some are missing.
     * That way a "repair" action will fix the problem.
     * 
     * @return a list of block positions that are tagged as display shelves
     */
    public List<BlockPos> identifyExpectedShelfPositions() {
        // We want any tagged display locations nto be added into the displayShelfContents if not already there.
        final List<BlockPos> shelfLocations = getLocationsFromTag(STRUCT_TAG_DISPLAY_SHELF);
        return shelfLocations;
    }

    /**
     * This method is used to initialize the displayShelfContents map with all tagged display locations in the structure.
     * It is called when the building is initialized, and ensures that any missing display shelves are marked as such.
     * This is important because it allows the building to correctly track the display shelves and their contents, even if some are missing.
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
     * Deserializes the NBT data for the building, restoring its state from the provided CompoundTag.
     * Clears the current display shelf contents and repopulates it using the data from the NBT.
     * Synchronizes the display frames in the world with the deserialized shelf contents.
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

        ritualData.deserializeNBT(provider, compound);

        syncDisplayFramesWithSavedItems();
    }

    /**
     * Mints a given number of trade coins, removing the corresponding amount of value from the building's economy.
     * @param player the player using the minting function (not used, but required for later potential functionality)
     * @param coinsToMint the number of coins to mint
     * @return a stack of the minted coins
     */
    public ItemStack mintCoins(Player player, int coinsToMint) {
        int coinValue = MCTPConfig.tradeCoinValue.get();
        ItemStack coinStack = ItemStack.EMPTY;

        if (coinsToMint > 0) {
            int valueToRemove = coinsToMint * coinValue;

            BuildingEconModule econ = this.getModule(MCTPBuildingModules.ECON_MODULE);
            if (valueToRemove < econ.getTotalBalance()) {
                coinStack = new ItemStack(MCTradePostMod.MCTP_COIN_ITEM.get(), coinsToMint);
                econ.incrementBy(WindowEconModule.COINS_MINTED, coinsToMint);
                econ.incrementBy(WindowEconModule.CURRENT_BALANCE, -valueToRemove);
                econ.markDirty();

                this.getColony().getStatisticsManager().incrementBy(WindowEconModule.CURRENT_BALANCE, -valueToRemove, this.getColony().getDay());
            } else {
                MessageUtils.format("mctradepost.marketplace.nsf").sendTo(player);
            }
        }

        return coinStack;
    }      


    /**
     * Deposits a given stack of trade coins into the building's economy, adding the corresponding amount of value to the economy.
     * @param player the player using the depositing function (not used, but required for later potential functionality)
     * @param coinsToDeposit the stack of coins to deposit
     */
    public void depositCoins(Player player, ItemStack coinsToDeposit) {
        int coinValue = MCTPConfig.tradeCoinValue.get();

        if (coinsToDeposit.getCount() > 0) {
            int valueToAdd = coinsToDeposit.getCount() * coinValue;

            BuildingEconModule econ = this.getModule(MCTPBuildingModules.ECON_MODULE);

            econ.incrementBy(WindowEconModule.CURRENT_BALANCE, valueToAdd);
            econ.markDirty();

            // Now remove the coins from the player's inventory
            coinsToDeposit.setCount(0);
            player.getInventory().setChanged();
        }
    }     

    /**
     * Finds the nearest BuildingMarketplace to the given BlockPos in the given Level,
     * assuming BlockPos is located inside a colony.
     * 
     * @param level the level to search in
     * @param pos the BlockPos to search near
     * @return the nearest BuildingMarketplace, or null if none is found
     */
    public static BuildingMarketplace getMarketplaceFromPos(Level level, BlockPos pos) {
        // Check if inside a MineColonies colony
        BuildingMarketplace closestMarketplace = null;
        IColonyManager colonyManager = IColonyManager.getInstance();
        IColony colony = colonyManager.getColonyByPosFromWorld(level, pos);
        
        if (colony != null) {
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
     * Serializes the NBT data for the building, including the display shelf contents.
     * The display shelf contents are stored in a list of CompoundTags, where each CompoundTag
     * contains the BlockPos of the display shelf and the ItemStack of the item in that shelf.
     * The CompoundTag is stored in the parent CompoundTag under the key TAG_DISPLAYSHELVES.
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
            shelfTagList.add(entry.getValue().toNBT(provider));
        }

        compound.put(TAG_DISPLAYSHELVES, shelfTagList);

        ritualData.serializeNBT(provider);

        return compound;
    }

    /**
     * Called every tick that the colony updates.
     * 
     * @param colony the colony that this building is a part of
     */
    @Override
    public void onColonyTick(IColony colony) {
        super.onColonyTick(colony);
        advertisingCooldown--;

        if (advertisingCooldown > 0)
            return;

        List<Integer> visitorIDs = new ArrayList<Integer>();

        // MCTradePostMod.LOGGER.info("Looking for a tavern to advertise at.");

        // Once the marketplace is built, visitors start thinking about shopping...
        // Note: approach below for finding tavern mimics EventHandler.onEntityConverted 
        final BlockPos tavernPos = colony.getBuildingManager().getRandomBuilding(b -> !b.getModulesByType(TavernBuildingModule.class).isEmpty());
        if (tavernPos != null) {
            // MCTradePostMod.LOGGER.info("Tavern module found - collecting visitor IDs.");
            final IBuilding tavern = colony.getBuildingManager().getBuilding(tavernPos);
            TavernBuildingModule tavernModule = tavern.getFirstModuleOccurance(TavernBuildingModule.class);
            visitorIDs.addAll(tavernModule.getExternalCitizens());
        }

        for (Integer visitorID : visitorIDs) {
            IVisitorData visitor = (IVisitorData) colony.getVisitorManager().getVisitor(visitorID);

            if (visitor != null && !advertisingList.contains(visitor)) {
                MCTradePostMod.LOGGER.info("Adding visitor to advertising list: {}", visitor.getEntity().get().getName());
                
                VisitorCitizen vitizen  = (VisitorCitizen) visitor.getEntity().get();

                ITickRateStateMachine<IState> stateMachine = vitizen.getEntityStateController();
                EntityAIShoppingTask task = new EntityAIShoppingTask(visitor, this);
                stateMachine.addTransition(new TickingTransition<>(VisitorState.IDLE, task::wantsToShop, task::goingShopping, 150));
                stateMachine.addTransition(new TickingTransition<>(VisitorState.WANDERING, task::wantsToShop, task::goingShopping, 150));
                stateMachine.addTransition(new TickingTransition<>(ShoppingState.GOING_SHOPPING, () -> true, task::goingShopping, 150));
                stateMachine.addTransition(new TickingTransition<>(ShoppingState.IS_SHOPPING, () -> true, task::isShopping, 150));
                stateMachine.addTransition(new TickingTransition<>(ShoppingState.DONE_SHOPPING, () -> true, task::doneShopping, 150));

                advertisingList.add(visitor);
            }
        }

        advertisingCooldown = ADVERTISING_COOLDOWN_MAX;
    }

    /**
     * Calculate the chance of a visitor shopping at this marketplace based on its building level and the global config setting MCTPConfig.shoppingChance.
     * 
     * @return the chance of a visitor shopping at this marketplace
     */
    public int shoppingChance() {
        int shoppingChance = MCTPConfig.shoppingChance.get();
        return this.getBuildingLevel() * shoppingChance;
    }

    /* EntityAIShoppingTask inserted into the Visitor AI by the marketplace. */
    public class EntityAIShoppingTask {
        IVisitorData visitor = null;
        BuildingMarketplace marketplace = null;
        private static final int ONE_HUNDRED = 100;

        public EntityAIShoppingTask(IVisitorData visitor, @Nonnull BuildingMarketplace marketplace) {
            this.visitor = visitor;
            this.marketplace = marketplace;
        }


        /**
         * Checks if a visitor wants to go shopping at the marketplace.
         * 
         * This method is called by the AI when it wants to decide what to do.
         * It will return true if the visitor should go shopping, false otherwise.
         * The decision is based on the time of day (no shopping at night) and
         * a random chance based on the marketplace's shopping chance.
         * 
         * @return true if the visitor should go shopping, false otherwise
         */
        private boolean wantsToShop()
        {
            // No shopping at night.
            if (!WorldUtil.isDayTime(marketplace.getColony().getWorld())) {
                return false;
            }

            if (marketplace.shoppingChance() > ThreadLocalRandom.current().nextDouble() * ONE_HUNDRED) {
                // MCTradePostMod.LOGGER.info("Visitor {} is taking a shopping trip!", visitor.getEntity().get().getName());
                return true;
            } else {
                // MCTradePostMod.LOGGER.info("Visitor {} does not need to shop.", visitor.getEntity().get().getName());
                return false;
            }

        }
        
        /**
         * Attempts to navigate the visitor to the marketplace location.
         *
         * @return the next shopping state, IS_SHOPPING if the visitor has reached 
         *         the marketplace, otherwise GOING_SHOPPING if still en route.
         */
        public IState goingShopping() {
            if (EntityNavigationUtils.walkToPos(visitor.getEntity().get(), marketplace.getLocation().getInDimensionLocation(), 3, true)) {
                return ShoppingState.IS_SHOPPING;
            } else {
                return ShoppingState.GOING_SHOPPING;
            }
        }

        /**
         * Attempts to navigate the visitor to the marketplace location.
         *
         * @return the next shopping state, IS_SHOPPING if the visitor has reached 
         *         the marketplace, otherwise GOING_SHOPPING if still en route.
         */
        public IState isShopping() {
            BlockPos pos = null;
            
            if (wantsToShop()) {
                final List<BlockPos> displayPositions = marketplace.getDisplayShelfPositions();
                pos = displayPositions.get(ThreadLocalRandom.current().nextInt(displayPositions.size()));
            } else {
                return ShoppingState.DONE_SHOPPING;
            }

            EntityNavigationUtils.walkToPos(visitor.getEntity().get(), pos, 1, true);
            return ShoppingState.IS_SHOPPING;
        }

        /**
         * Called when the visitor is done shopping.  This method is called by the AI's state machine
         * when it has finished shopping and is ready to go back to IDLE.
         * 
         * @return the next state to go to, which is always VisitorState.IDLE
         */
        public IState doneShopping() {
            return VisitorState.IDLE;
        }
    }

}

