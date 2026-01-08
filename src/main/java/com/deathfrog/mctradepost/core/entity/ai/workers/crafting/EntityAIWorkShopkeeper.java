package com.deathfrog.mctradepost.core.entity.ai.workers.crafting;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.sounds.MCTPModSoundEvents;
import com.deathfrog.mctradepost.api.util.BuildingUtil;
import com.deathfrog.mctradepost.api.util.FrameLikeAccess;
import com.deathfrog.mctradepost.api.util.MCTPInventoryUtils;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.RequestUtil;
import com.deathfrog.mctradepost.api.util.SoundUtils;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.apiimp.initializer.MCTPInteractionInitializer;
import com.deathfrog.mctradepost.core.client.gui.modules.WindowEconModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingEconModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.ItemValueRegistry;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MarketplaceItemListModule;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.DisplayCase;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.DisplayCase.SaleState;
import com.deathfrog.mctradepost.core.colony.jobs.JobShopkeeper;
import com.deathfrog.mctradepost.item.CoinItem;
import com.deathfrog.mctradepost.item.SouvenirItem;
import com.google.common.collect.ImmutableList;
import com.ldtteam.blockui.mod.Log;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.entity.ai.statemachine.AIEventTarget;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIBlockingEventType;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.core.colony.buildings.modules.SettingsModule;
import com.minecolonies.core.colony.buildings.modules.settings.BoolSetting;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.colony.requestsystem.requests.StandardRequests.ItemStackRequest;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.mojang.logging.LogUtils;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.minecolonies.api.util.InventoryUtils;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.minecolonies.api.util.constant.Constants.*;
import com.minecolonies.api.crafting.ItemStorage;
import java.util.ArrayList;
import java.util.List;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_SHOPKEEPER;

/**
 * Handles the Shopkeeper AI. The shopkeeper works in the Marketplace.
 */
public class EntityAIWorkShopkeeper extends AbstractEntityAIInteract<JobShopkeeper, BuildingMarketplace>
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final String ITEM_SOLD = "items_sold";

    public enum ShopkeeperState implements IAIState
    {
        ANALYZE_INVENTORY, FILL_DISPLAYS, SELL_MERCHANDISE, MINT_COINS;

        @Override
        public boolean isOkayToEat()
        {
            return true;
        }
    }

    /**
     * Base xp gain for the shopkeeper.
     */
    private static final double BASE_XP_GAIN = 1;

    /**
     * The block pos to which the AI is going.
     */
    private BlockPos currentTarget;

    /**
     * Number of ticks that the AI should wait before deciding again
     */
    private static final int DECIDE_DELAY = 20;

    /**
     * Number of ticks that the AI should wait after completing a task
     */
    private static final int AFTER_TASK_DELAY = 3;

    // The base sell time for selling displayed items.
    // private static final int SELLTIME = MCTPConfig.baseSelltime.get();;

    /**
     * Id for the list of sellable items.
     */
    public static final String SELLABLE_LIST = "inventory";  // Note, the value here ends up getting used as the icon for the UI tab.

    @NonNls
    public static final String REQUESTS_TYPE_SELLABLE_UI = "com.deathfrog.mctradepost.gui.workerhuts.shopkeeper.sellables";

    public static final int MINTING_COOLDOWN = 20;
    protected int mintCooldownCounter = MINTING_COOLDOWN;

    protected BlockPos inventoryContainer = BlockPos.ZERO;

    /**
     * Worker status icon
     */
    private final static VisibleCitizenStatus SELLING =
        new VisibleCitizenStatus(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/icons/work/shopkeeper.png"),
            "com.mctradepost.gui.visiblestatus.shopkeeper");

    private final static VisibleCitizenStatus COMMUTING = new VisibleCitizenStatus(
        ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/icons/work/shopkeeper_commute.png"),
        "com.mctradepost.gui.visiblestatus.shopkeeper");

    /*
     * The number of coins to mint
     */
    private int coinsToMint = 0;

    /**
     * Constructor for the AI
     *
     * @param job the job to fulfill
     */
    @SuppressWarnings("unchecked")
    public EntityAIWorkShopkeeper(@NotNull final JobShopkeeper job)
    {
        super(job);
        super.registerTargets(new AIEventTarget<IAIState>(AIBlockingEventType.AI_BLOCKING, this::blockingLogic, TICKS_SECOND),
            new AITarget<IAIState>(IDLE, START_WORKING, 1),
            new AITarget<IAIState>(GET_MATERIALS, this::getMaterials, TICKS_SECOND),
            new AITarget<IAIState>(START_WORKING, this::decideWhatToDo, 10),
            new AITarget<IAIState>(DECIDE, this::decideWhatToDo, 10),
            new AITarget<IAIState>(ShopkeeperState.ANALYZE_INVENTORY, this::analyzeInventory, 10),
            new AITarget<IAIState>(ShopkeeperState.SELL_MERCHANDISE, this::sellFromDisplay, 5),
            new AITarget<IAIState>(ShopkeeperState.FILL_DISPLAYS, this::fillDisplays, 10),
            new AITarget<IAIState>(ShopkeeperState.MINT_COINS, this::mintCoins, 50));
        worker.setCanPickUpLoot(true);
    }

    /**
     * Handles the logic for the AI when in a blocking state. Iterates over display shelves and increments their tick count.
     *
     * @return the next IAIState after processing blocking logic
     */

    private IAIState blockingLogic()
    {
        for (BlockPos dispLocation : building.getDisplayShelves().keySet())
        {
            DisplayCase contents = building.getDisplayShelves().get(dispLocation);
            if (contents != null)
            {
                contents.setTickcount(contents.getTickcount() + 1);
            }
        }

        return null;
    }

    @Override
    public IAIState afterRequestPickUp() 
    {
        return AIWorkerState.DECIDE;
    }

    /**
     * Compute the value of the item stack for the marketplace.
     *
     * @param stack  the item stack to compute the value of.
     * @param amount the amount of the item in the stack.
     * @return the computed value of the item stack.
     */
    private int computeItemValue(ItemStack stack)
    {
        int value = ItemValueRegistry.getValue(stack);

        if (value == 0)
        {
            LOGGER.warn("Item {} has no value", stack.getItem().getDescriptionId());
        }

        return value;
    }

    /**
     * Returns a multiplier for the sales value based on the primary skill of the AI.
     * The multiplier is computed as 1 + (primary skill level / 50).
     * This means that for every 50 levels of the primary skill, the AI will increase the sales value by 1.
     * @return the computed multiplier.
     */
    private double skillMultiplier()
    {
        return 1 + (worker.getCitizenData().getCitizenSkillHandler().getLevel(getModuleForJob().getPrimarySkill()) / 50);
    }

    /**
     * Increase the sales value based on primary skills.
     */
    private void sellItem(@Nonnull ItemStack item)
    {
        final double skillMultiplier = skillMultiplier();

        Item underlyingItem = SouvenirItem.getOriginal(item);

        if (underlyingItem == null)
        {
            return;
        }

        ItemStack originalItem = new ItemStack(underlyingItem);

        StatsUtil.trackStatByName(building, ITEM_SOLD, originalItem.getHoverName(), 1);

        int sellvalue = SouvenirItem.getSouvenirValue(item);
        sellvalue = (int) Math.round(sellvalue * skillMultiplier);

        building.getModule(MCTPBuildingModules.ECON_MODULE).incrementBy(WindowEconModule.ITEM_SOLD, 1);

        // Cash generated
        building.getModule(MCTPBuildingModules.ECON_MODULE).incrementBy(WindowEconModule.CASH_GENERATED, sellvalue);
                       
        // Current balance
        building.getColony()
            .getStatisticsManager()
            .incrementBy(WindowEconModule.CURRENT_BALANCE, sellvalue, building.getColony().getDay());
    }

    /**
     * Method for the AI to try to get the materials needed for the task he's doing. Will request if there are no materials
     *
     * @return the new IAIState after doing this
     */
    private IAIState getMaterials()
    {
        if (!walkToBuilding())
        {
            setDelay(2);
            return getState();
        }

        final ImmutableList<ItemStorage> list =
            building.getModule(MarketplaceItemListModule.class, m -> m.getId().equals(SELLABLE_LIST)).getList();
        List<ItemStorage> sortedList = new ArrayList<>(list);

        // Optimize for the most valueable sellable items first.
        sortedList.sort((a, b) -> 
        {
            int valueA = computeItemValue(a.getItemStack());
            int valueB = computeItemValue(b.getItemStack());

            return Integer.compare(valueB, valueA); // descending order
        });

        if (sortedList.isEmpty())
        {
            int complaints = job.tickNoSaleItem();

            TraceUtils.dynamicTrace(TRACE_SHOPKEEPER, () -> LOGGER.info("Colony {} shopkeeper: Has no items configured for sale ({} complaints).", building.getColony().getID(), complaints));

            if (job.checkForSaleItemsInteraction())
            {
                worker.getCitizenData().triggerInteraction(new StandardInteraction(Component.translatable(MCTPInteractionInitializer.NO_SALE_ITEMS), ChatPriority.BLOCKING));
            }

            return DECIDE;
        }

        job.resetSaleItemCounter();

        // Move the item from the building to the worker.
        for (ItemStorage candidate : sortedList)
        {
            ItemStack targetStack = candidate.getItemStack();

            if (targetStack == null)
            {
                continue;
            }

            if (InventoryUtils.hasItemInProvider(building, stack -> stack != null && ItemStack.isSameItem(stack, targetStack)))
            {
                InventoryUtils.transferItemStackIntoNextFreeSlotFromProvider(building,
                    InventoryUtils.findFirstSlotInProviderNotEmptyWith(building,
                        stack -> stack != null && ItemStack.isSameItem(stack, targetStack)),
                    worker.getInventoryCitizen());

                // only transfer the highest value match
                break; 
            }
        }

        int slot = -1;

        // Identity the slot with the best candidate item in the workers inventory.
        for (ItemStorage candidate : sortedList)
        {
            ItemStack targetStack = candidate.getItemStack();

            if (targetStack == null)
            {
                continue;
            }

            slot = InventoryUtils.findFirstSlotInItemHandlerWith(worker.getInventoryCitizen(),
                stack -> stack != null && ItemStack.isSameItem(stack, targetStack));
            if (slot >= 0)
            {
                break;
            }
        }
        
        // Display that item in the worker's hand
        if (slot >= 0)
        {
            ItemStack stackInSlot = worker.getInventoryCitizen().getStackInSlot(slot);

            if (stackInSlot != null)
            {
                worker.setItemInHand(InteractionHand.MAIN_HAND, stackInSlot);
            }

            return START_WORKING;
        }

        // No item in the worker's inventory
        worker.setItemInHand(InteractionHand.MAIN_HAND, NullnessBridge.assumeNonnull(ItemStack.EMPTY));

        // If there are no open requests, ask for new sellable items to be delivered.
        if (!building.hasWorkerOpenRequests(worker.getCitizenData().getId()))
        {
            final ArrayList<ItemStack> itemList = new ArrayList<>();
            for (final ItemStorage item : sortedList)
            {
                final ItemStack itemStack = item.getItemStack();
                itemStack.setCount(itemStack.getMaxStackSize());
                itemList.add(itemStack);
            }
            
            if (!itemList.isEmpty())
            {
                worker.getCitizenData()
                    .createRequestAsync(new StackList(itemList,
                        BuildingMarketplace.REQUESTS_TYPE_SELLABLE,
                        Constants.STACKSIZE,
                        1,
                        building.getSetting(BuildingMarketplace.MIN).getValue()));
            }
        }

        setDelay(2);
        return START_WORKING;
    }

    /**
     * Wait for the AI to receive new requests from the building. If the AI needs an item, but there are no open requests, the AI will
     * transition to the GET_MATERIALS state to request items from the warehouse. If the AI does not need an item, the AI will
     * transition back to the DECIDE state.
     * 
     * @return The next AI state to transition to.
     */
    protected @NotNull IAIState waitForRequests() 
    {
        IAIState state = super.waitForRequests();

        if (state == NEEDS_ITEM)
        {
            return GET_MATERIALS;
        }

        return state;
    }

    /**
     * Method for the AI to decide what to do.
     *
     * @return the decision it made
     */
    private IAIState decideWhatToDo()
    {
        worker.getCitizenData().setVisibleStatus(COMMUTING);

        if (!walkToBuilding())
        {
            setDelay(2);
            return getState();
        }

        worker.getCitizenData().setVisibleStatus(VisibleCitizenStatus.WORKING);

        final BuildingMarketplace building = this.building;

        coinsToMint = coinsNeededInColony();

        if (coinsToMint > 0 && mintCooldownCounter-- <= 0)
        {
            mintCooldownCounter = MINTING_COOLDOWN;
            TraceUtils.dynamicTrace(TRACE_SHOPKEEPER, () -> LOGGER.info("Colony {} shopkeeper: Coins are needed: {}", building.getColony().getID(), coinsToMint));
            return ShopkeeperState.MINT_COINS;
        }

        if (BlockPos.ZERO.equals(inventoryContainer))
        {
            inventoryContainer = ((BuildingMarketplace) building).identifyInventoryPosition();

            if (BlockPos.ZERO.equals(inventoryContainer))
            {
                int complaints = job.tickNoInventoryManagement();

                TraceUtils.dynamicTrace(TRACE_SHOPKEEPER, () -> LOGGER.info("Colony {} shopkeeper: Has no inventory container ({} complaints).", building.getColony().getID(), complaints));

                if (job.checkForInventoryManagementInteraction())
                {
                    worker.getCitizenData().triggerInteraction(new StandardInteraction(Component.translatable(MCTPInteractionInitializer.NO_SHOP_INVENTORY), ChatPriority.BLOCKING));
                }
            }
        }

        
        if (!BlockPos.ZERO.equals(inventoryContainer) 
            && !MCTPInventoryUtils.isContainerEmpty(world, inventoryContainer)
            && worker.getRandom().nextInt(100) < 80)
        {
            job.resetInventoryManagementCounter();
            return ShopkeeperState.ANALYZE_INVENTORY;
        }

        building.markExpectedShelfPositionsAsShelfLocations();

        Map<BlockPos, DisplayCase> displayShelves = building.getDisplayShelves();
        TraceUtils.dynamicTrace(TRACE_SHOPKEEPER, () -> LOGGER.info("Colony {} shopkeeper: Deciding what to do. Display shelves: {}", building.getColony().getID(), displayShelves.size()));

        if (displayShelves.isEmpty())
        {
            LOGGER.warn("Shopkeeper: No display frames were found for the shopkeeper to use.");
            MessageUtils.format("entity.shopkeeper.noframes").sendTo(building.getColony()).forAllPlayers();
            setDelay(DECIDE_DELAY);
            return PAUSED;
        }

        IAIState nextState = null;

        if (building.hasWorkerOpenRequests(worker.getCitizenData().getId()))
        {
            // If we are waiting for materials, prioritize selling what's already placed.
            nextState = evaluteFramesForSelling();
            if (nextState != null)
            {
                return nextState;
            }
            nextState = evaluateFramesForFilling();
            if (nextState != null)
            {
                return nextState;
            }
        }
        else
        {
            // Otherwise, prioritize putting new items up for sale.
            nextState = evaluateFramesForFilling();
            if (nextState != null)
            {
                return nextState;
            }
            nextState = evaluteFramesForSelling();
            if (nextState != null)
            {
                return nextState;
            }
        }

        setDelay(DECIDE_DELAY);
        return DECIDE;
    }

    /**
     * Mint coins for the shopkeeper if the econ settings allow it.
     *
     * @return the next state to transition to.
     */
    protected IAIState mintCoins()
    {
        SettingsModule settings = building.getModule(MCTPBuildingModules.ECON_SETTINGS);
        Optional<BoolSetting> shouldMintCoins = settings.getOptionalSetting(BuildingMarketplace.AUTOMINT);

        BuildingMarketplace localBuilding = building;

        if (localBuilding == null)
        {
            return DECIDE;
        }

        if (coinsToMint > 0 && shouldMintCoins.isPresent() && shouldMintCoins.get().getValue())
        {
            ItemStack stack = localBuilding.mintCoins(null, coinsToMint);
            coinsToMint = 0;
            
            TraceUtils.dynamicTrace(TRACE_SHOPKEEPER,
                () -> LOGGER.info("Colony {} Shopkeeper: {} coins are needed and autominting is turned on. Mint result: {}", localBuilding.getColony().getID(), coinsToMint, stack));
            
            if (stack.isEmpty())
            {
                MessageUtils.format("mctradepost.marketplace.nsf").sendTo(localBuilding.getColony()).forAllPlayers();
                settings.with(BuildingMarketplace.AUTOMINT, new BoolSetting(false));
                return DECIDE;
            }

            StatsUtil.trackStat(localBuilding, WindowEconModule.COINS_MINTED, stack.getCount());

            if (!InventoryUtils.addItemStackToProvider(localBuilding, stack))
            {
                if (!InventoryUtils.addItemStackToProvider(worker, stack))
                {
                    BuildingEconModule econ = localBuilding.getModule(MCTPBuildingModules.ECON_MODULE);
                    econ.deposit(coinsToMint * MCTPConfig.tradeCoinValue.get());

                    stack.setCount(0);
                    MessageUtils.format("entity.shopkeeper.nospaceforcoins").sendTo(localBuilding.getColony()).forAllPlayers();

                    return DECIDE;
                }
            }
            
            BuildingUtil.bringThisToTheWarehouse(localBuilding, stack);
        }

        return DECIDE;
    }

    /**
     * Counts the number of coins needed to fulfill open requests in the colony.
     * 
     * @return the number of coins needed
     */
    protected int coinsNeededInColony()
    {
        int coinsNeeded = 0;

        CoinItem coinItem = MCTradePostMod.MCTP_COIN_ITEM.get();

        if (coinItem == null)
        {
            throw new IllegalStateException("TradePost coin item is null. This should never happen. Please report.");
        }

        for (IBuilding somebuilding : building.getColony().getBuildingManager().getBuildings().values())
        {
            ImmutableList<IRequest<?>> openRequests = RequestUtil.getOpenRequestsFromBuilding(somebuilding, false);

            // LOGGER.info("Building {} has open requests: {}", somebuilding.getBuildingDisplayName(), openRequests.size());

            for (IRequest<?> request : openRequests)
            {
                if (request instanceof ItemStackRequest itemRequest)
                {
                    Stack stack = itemRequest.getRequest();

                    if (stack != null && stack.getStack().is(coinItem))
                    {
                        coinsNeeded = coinsNeeded + stack.getStack().getCount();
                    }
                }
                else
                {
                    // LOGGER.info("Ignoring Request: {}", request);
                }
            }
        }

        int coinsOnHand = MCTPInventoryUtils.combinedInventoryCount(this.building, new ItemStorage(MCTradePostMod.MCTP_COIN_ITEM.get()));

        if (coinsNeeded > 0 && coinsOnHand >= coinsNeeded)
        {
            building.createPickupRequest(building.getPickUpPriority());
        }
        
        coinsNeeded = Math.max(coinsNeeded - coinsOnHand, 0);
        
        // Also override the sellables module method to keep in the building anything that is being converted to a souvenir.

        return coinsNeeded;
    }

    /**
     * Evaluates the item frames on display shelves to determine if any are empty and available to be filled. If an empty frame is
     * found, the AI state transitions to FILL_DISPLAYS for that frame's location. If no empty frames are found, the function returns
     * null.
     *
     * @return the next state to transition to (FILL_DISPLAYS) if an empty frame is found, or null if none are available.
     */
    private IAIState evaluateFramesForFilling()
    {

        if (building.getDisplayShelves().isEmpty())
        {
            Log.getLogger().error("Building {} has no display shelves in colony {}", building.getBuildingDisplayName(), building.getColony().getID());
            return null;
        }

        TraceUtils.dynamicTrace(TRACE_SHOPKEEPER, () -> LOGGER.info("Colony {}: Shopkeeper - evaluating {} frames for filling.", building.getColony().getID(), building.getDisplayShelves().size()));
        final Level world = building.getColony().getWorld();

        // First pass: Find any empty item frame (considered available to fill)
        for (final BlockPos displayLocation : building.getDisplayShelves().keySet())
        {
            if (displayLocation == null)
            {
                continue;
            }

            DisplayCase displayCase = building.getDisplayShelves().get(displayLocation);

            UUID frameId = displayCase.getFrameId();

            // ItemFrame frame = frameId == null ? null : (ItemFrame) ((ServerLevel) world).getEntity(frameId);
            FrameLikeAccess.FrameHandle handle = FrameLikeAccess.resolve(world, displayLocation, frameId);

            if (!handle.exists())
            {
                building.lostShelfAtDisplayPos(displayLocation);
                continue;
            }

            if (handle.getItem().isEmpty())
            {
                TraceUtils.dynamicTrace(TRACE_SHOPKEEPER, () -> LOGGER.info("Colony {}: Shopkeeper - found empty frame for filling at {}", building.getColony().getID(), displayLocation));

                this.currentTarget = displayLocation;
                setDelay(DECIDE_DELAY);
                worker.getCitizenData().setVisibleStatus(SELLING);
                return ShopkeeperState.FILL_DISPLAYS;
            }

        }

        return null;
    }

    /**
     * Evaluates the state of the item frames on the shelves, to see if any of them are full and ready to be sold. If one is found, it
     * transitions to the SELL_MERCHANDISE state for that display case. If none are found, it returns null, and the AI state machine
     * will transition to START_WORKING.
     * 
     * @return the next state to transition to, or null if none of the displays are ready to be sold.
     */
    private IAIState evaluteFramesForSelling()
    {

        if (building.getDisplayShelves().isEmpty())
        {
            Log.getLogger().error("Building {} has no display shelves in colony {}", building.getBuildingDisplayName(), building.getColony().getID());
            return null;
        }

        TraceUtils.dynamicTrace(TRACE_SHOPKEEPER, () -> LOGGER.info("Colony {}: Shopkeeper - evaluating {} frames for selling.", building.getColony().getID(), building.getDisplayShelves().size()));

        int framesChecked = 0;
        
        final Level world = building.getColony().getWorld();

        // Find any filled item frame (considered eligible to be sold)
        for (final BlockPos displayLocation : building.getDisplayShelves().keySet())
        {

            if (displayLocation == null)
            {
                continue;
            }

            DisplayCase displayCase = building.getDisplayShelves().get(displayLocation);

            UUID frameId = displayCase.getFrameId();

            // ItemFrame frame = frameId == null ? null : (ItemFrame) ((ServerLevel) world).getEntity(frameId);
            FrameLikeAccess.FrameHandle handle = FrameLikeAccess.resolve(world, displayLocation, frameId);

            if (!handle.exists())
            {
                building.lostShelfAtDisplayPos(displayLocation);
                continue;
            }

            if (!handle.getItem().isEmpty())
            {
                BlockPos pos = displayLocation.immutable();
                int ticks = displayCase.getTickcount();
                displayCase.setTickcount(ticks + 1);        // Purely informational now. May be used in a future iteration.

                if (displayCase.getSaleState() == SaleState.ORDER_PLACED)
                {
                    TraceUtils.dynamicTrace(TRACE_SHOPKEEPER, () -> LOGGER.info("Colony {} shopkeeper: We should sell from case at {}!", building.getColony().getID(), pos));

                    this.currentTarget = pos;
                    worker.getCitizenData().setVisibleStatus(SELLING);
                    return ShopkeeperState.SELL_MERCHANDISE;
                }
            }

            framesChecked++;
        }

        final int finalFramesChecked = framesChecked;
        TraceUtils.dynamicTrace(TRACE_SHOPKEEPER, () -> LOGGER.info("Colony {}: Shopkeeper - checked {} frames for selling and found none with offers.", building.getColony().getID(), finalFramesChecked));

        return null;
    }

    /**
     * Triggers a visual effect (enchant particles) and sound (cash register) at the given position. Used to simulate the AI selling an
     * item from a display stand.
     * 
     * @param pos the position of the effect
     */
    private void triggerEffect(BlockPos pos)
    {
        ServerLevel level = (ServerLevel) building.getColony().getWorld();

        // Additional particles and sound
        level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.POOF), pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 20, 0.5, 0.5, 0.5, 0.1);

        int chanceOfChaChing = (int) (MCTPConfig.registerSoundChance.get() * 100);
        SoundUtils.playSoundWithChance(level,
            null,
            pos,
            MCTPModSoundEvents.CASH_REGISTER,
            SoundSource.NEUTRAL,
            chanceOfChaChing,
            0.8f,
            1.0f);
    }

    /**
     * The AI will now remove the item from the display stand that he found full on his building and "sell" it, adding experience and
     * stats. Triggered from the ShopkeeperState.SELL_MERCHANDISE state.
     * 
     * @return the next IAIState after doing this
     */
    private IAIState sellFromDisplay()
    {
        BlockPos localCurrentTarget = currentTarget;

        if (localCurrentTarget == null)
        {
            return DECIDE;
        }

        DisplayCase displayCase = building.getDisplayShelves().get(localCurrentTarget);
        UUID frameId = displayCase.getFrameId();
        // ItemFrame frame = frameId == null ? null : (ItemFrame) ((ServerLevel) world).getEntity(frameId);
        FrameLikeAccess.FrameHandle handle = FrameLikeAccess.resolve(world, localCurrentTarget, frameId);

        if (!handle.exists())
        {
            building.lostShelfAtDisplayPos(localCurrentTarget);
            setDelay(AFTER_TASK_DELAY);
            return START_WORKING;
        }

        TraceUtils.dynamicTrace(TRACE_SHOPKEEPER, () -> LOGGER.info("Colony {} shopkeeper: Selling from display at {}.", building.getColony().getID(), localCurrentTarget));

        ItemStack item = handle.getItem();

        if (!item.isEmpty())
        {
            if (item.getItem() instanceof SouvenirItem)
            {
                TraceUtils.dynamicTrace(TRACE_SHOPKEEPER,
                    () -> LOGGER.info("Colony {} Shopkeeper: Selling item {} from display at {}", building.getColony().getID(), item, localCurrentTarget));

                // "Sell" the item — remove it from the frame
                handle.setItem(NullnessBridge.assumeNonnull(ItemStack.EMPTY)); 

                // Calculate the value of the item and credit it to the economic module and stats module.
                sellItem(item);
                triggerEffect(localCurrentTarget);

                // Add experience, stats, etc.
                worker.getCitizenExperienceHandler().addExperience(BASE_XP_GAIN);
                incrementActionsDoneAndDecSaturation();
            }
            else
            {
                // Remove the invalid item from the frame
                handle.setItem(NullnessBridge.assumeNonnull(ItemStack.EMPTY)); 

                // Attempt to insert into the worker's inventory
                ItemStack leftover = InventoryUtils.addItemStackToItemHandlerWithResult(worker.getInventoryCitizen(), item);

                // If inventory is full and there are leftovers, drop it at the worker's feet
                if (!leftover.isEmpty())
                {
                    InventoryUtils.spawnItemStack(world, worker.getX(), worker.getY(), worker.getZ(), leftover);
                }

                TraceUtils.dynamicTrace(TRACE_SHOPKEEPER,
                    () -> LOGGER.info("Colony {} Shopkeeper: removed unauthorized item {} from display at {}", building.getColony().getID(), item, currentTarget));
            }

            displayCase.setStack(NullnessBridge.assumeNonnull(ItemStack.EMPTY));    // Note that the display case is empty
            displayCase.setSaleState(SaleState.ORDER_FULFILLED);                    // Mark the order as fulfilled
            displayCase.setTickcount(-1);                                           // Reset the timer.
        }

        setDelay(AFTER_TASK_DELAY);
        return START_WORKING;
    }

    /**
     * The AI will now fill the display stand that was found empty
     *
     * @return the next IAIState after doing this
     */
    private IAIState fillDisplays()
    {
        // Acquire an item in hand if empty.
        if (worker.getItemInHand(InteractionHand.MAIN_HAND) == ItemStack.EMPTY)
        {
            final int slot = MCTPInventoryUtils.findRandomSlotInItemHandlerWith(worker.getInventoryCitizen(),
                stack -> building.getModule(MarketplaceItemListModule.class, m -> m.getId().equals(SELLABLE_LIST))
                    .isItemInList(new ItemStorage(stack)));

            if (slot >= 0)
            {
                ItemStack stackInSlot = worker.getInventoryCitizen().getStackInSlot(slot);
                if (!stackInSlot.isEmpty())
                {
                    worker.setItemInHand(InteractionHand.MAIN_HAND, stackInSlot);
                }
            }
            else
            {
                return GET_MATERIALS;
            }
        }

        BlockPos localCurrentTarget = currentTarget;
        if (localCurrentTarget == null)
        {
            return DECIDE;
        }

        if (!walkToWorkPos(localCurrentTarget))
        {
            setDelay(2);
            return getState();
        }

        DisplayCase displayCase = building.getDisplayShelves().get(localCurrentTarget);
        UUID frameId = displayCase.getFrameId();
        // ItemFrame frame = frameId == null ? null : (ItemFrame) ((ServerLevel) world).getEntity(frameId);
        FrameLikeAccess.FrameHandle handle = FrameLikeAccess.resolve(world, localCurrentTarget, frameId);

        if (!handle.exists())
        {
            building.lostShelfAtDisplayPos(localCurrentTarget);
            setDelay(AFTER_TASK_DELAY);
            return START_WORKING;
        }

        // Insert the item into the display, replacing what's there
        ItemStack heldItem = worker.getItemInHand(InteractionHand.MAIN_HAND);

        // Only fill if empty
        if (handle.getItem().isEmpty())
        {
            // Take the item from the inventory
            ItemStack placed = heldItem.split(1);

            if (!placed.isEmpty())
            {
                ItemStack souvenir;
                Item souvenirItem = MCTradePostMod.SOUVENIR.get();

                // If the shopkeeper has gotten their hands on unsold souvenirs somehow, don't re-souvenir them.
                if (souvenirItem != null && placed.is(souvenirItem))
                {
                    souvenir = placed;
                }
                else
                {
                    souvenir = SouvenirItem.createSouvenir(
                        NullnessBridge.assumeNonnull(placed.getItem()),
                        computeItemValue(placed)
                    );
                }

                final ItemStack souvenirFinal = souvenir.copy();

                TraceUtils.dynamicTrace(TRACE_SHOPKEEPER,
                    () -> LOGGER.info("Colony {} Shopkeeper: Placing souvenir {} in display at {}",
                        building.getColony().getID(),
                        SouvenirItem.toString(souvenirFinal),
                        currentTarget));

                handle.setItem(souvenir);   

                displayCase.setStack(souvenir);
                displayCase.setTickcount(0);
                displayCase.setSaleState(SaleState.FOR_SALE);

                worker.getCitizenExperienceHandler().addExperience(BASE_XP_GAIN);
                this.incrementActionsDoneAndDecSaturation();

                worker.setItemInHand(InteractionHand.MAIN_HAND, NullnessBridge.assumeNonnull(ItemStack.EMPTY));

                incrementActionsDone();
            }
        } 
        else
        {
            TraceUtils.dynamicTrace(TRACE_SHOPKEEPER,
                () -> LOGGER.info("Colony {} Shopkeeper: Display at {} is not empty while attempting to fill it. Contains {}", building.getColony().getID(), currentTarget, handle.getItem()));
        }

        setDelay(AFTER_TASK_DELAY);
        return START_WORKING;
    }

    /**
     * Analyzes the inventory management chest of the marketplace and adds all non-empty items to the list of sellable items.
     * If the inventory management chest is full, this method will attempt to move items from the chest into the building inventory.
     * If the building inventory is full, this method will drop the excess items into the world.
     * This method will return null if the inventory management chest is not found, or if there are no items to analyze.
     * Otherwise, this method will return the DECIDE state.
     * @return the next state to transition to, or null if there are no items to analyze.
     */
    protected IAIState analyzeInventory()
    {
        BlockPos localInvContainer = inventoryContainer;

        if (localInvContainer != null && !localInvContainer.equals(BlockPos.ZERO))
        {
            if (!walkToSafePos(localInvContainer))
            {
                return getState();
            }

            MarketplaceItemListModule itemModule = building.getModule(MarketplaceItemListModule.class);

            @SuppressWarnings("null")
            final IItemHandler inventoryBoxHandler = world.getCapability(NullnessBridge.assumeNonnull(Capabilities.ItemHandler.BLOCK), localInvContainer, null);

            if (inventoryBoxHandler != null)
            {
                for (int i = 0; i < inventoryBoxHandler.getSlots(); i++)
                {
                    ItemStack stackInSlot = inventoryBoxHandler.getStackInSlot(i);

                    if (!stackInSlot.isEmpty())
                    {
                        boolean addToSaleList = true;
                        int value = computeItemValue(stackInSlot);

                        // Attempt to move the stack out of the inventory management chest into the building inventory
                        TraceUtils.dynamicTrace(TRACE_SHOPKEEPER, () -> LOGGER.info("Colony {} Shopkeeper: Analyzed {} in inventory chest", building.getColony().getID(), stackInSlot));
                    
                        if (value == 0)
                        {
                            MessageUtils.format("Your shopkeeper determined %s has no value as a souvenir, and put it away.", stackInSlot.getDisplayName()).sendTo(building.getColony()).forAllPlayers();
                            addToSaleList = false;
                        }

                        if (addToSaleList)
                        {
                            // Add the item to the list of things the shopkeeper can sell
                            itemModule.addSellableItem(stackInSlot.getItem());
                        }

                        boolean canhold = InventoryUtils.transferItemStackIntoNextFreeSlotInProvider(inventoryBoxHandler, i, building);

                        // If there's no more room in the building inventory, drop the stack into the world.
                        if (!canhold)
                        {
                            ItemStack dropStack = stackInSlot.copy();
                            stackInSlot.setCount(0);
                            MCTPInventoryUtils.InsertOrDropByQuantity(building, new ItemStorage(dropStack.copy()), dropStack.getCount());
                        }
                    }
                }
            }
            else
            {
                LOGGER.warn("No inventory handling container found on the marketplace...");
            }
        }

        return DECIDE;
    }


    /**
     * Returns the number of times the AI should work before dumping the inventory.
     * For the shopkeeper AI, this is always 1, meaning that the AI will work once before dumping the inventory.
     */
    @Override
    protected int getActionsDoneUntilDumping()
    {
        return 1;
    }

    @Override
    public Class<BuildingMarketplace> getExpectedBuildingClass()
    {
        return BuildingMarketplace.class;
    }
}
