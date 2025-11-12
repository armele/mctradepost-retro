package com.deathfrog.mctradepost.core.entity.ai.workers.crafting;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.sounds.MCTPModSoundEvents;
import com.deathfrog.mctradepost.api.util.BuildingUtil;
import com.deathfrog.mctradepost.api.util.MCTPInventoryUtils;
import com.deathfrog.mctradepost.api.util.RequestUtil;
import com.deathfrog.mctradepost.api.util.SoundUtils;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.client.gui.modules.WindowEconModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingEconModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.ItemValueRegistry;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MarketplaceItemListModule;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.DisplayCase;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.DisplayCase.SaleState;
import com.deathfrog.mctradepost.core.colony.jobs.JobShopkeeper;
import com.deathfrog.mctradepost.item.SouvenirItem;
import com.google.common.collect.ImmutableList;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.entity.ai.statemachine.AIEventTarget;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIBlockingEventType;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.core.colony.buildings.modules.SettingsModule;
import com.minecolonies.core.colony.buildings.modules.settings.BoolSetting;
import com.minecolonies.core.colony.requestsystem.requests.StandardRequests.ItemStackRequest;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.mojang.logging.LogUtils;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import java.util.Map;
import java.util.Optional;
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
    public static final String ENTITY_SHOPKEEPER_NO_SALEABLE_ITEMS = "entity.shopkeeper.noitems";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final String ITEM_SOLD = "items_sold";

    public enum ShopkeeperState implements IAIState
    {
        FILL_DISPLAYS, SELL_MERCHANDISE, MINT_COINS;

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
     * The number of times the AI will check if the player has set any items on the list until messaging him
     */
    private static final int TICKS_UNTIL_COMPLAIN = 12000;

    /**
     * The ticks elapsed since the last complain
     */
    private int ticksToComplain = 0;

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

    private double skillMultiplier()
    {
        return 1 + (worker.getCitizenData().getCitizenSkillHandler().getLevel(getModuleForJob().getPrimarySkill()) / 50);
    }

    /**
     * Increase the sales value based on primary skills.
     */
    private void sellItem(ItemStack item)
    {
        final double skillMultiplier = skillMultiplier();
        ItemStack originalItem = new ItemStack(SouvenirItem.getOriginal(item));

        StatsUtil.trackStatByName(building, ITEM_SOLD, originalItem.getHoverName(), 1);

        int sellvalue = SouvenirItem.getSouvenirValue(item);
        sellvalue = (int) Math.round(sellvalue * skillMultiplier);

        building.getModule(MCTPBuildingModules.ECON_MODULE).incrementBy(WindowEconModule.ITEM_SOLD, 1);
        building.getModule(MCTPBuildingModules.ECON_MODULE).incrementBy(WindowEconModule.CASH_GENERATED, sellvalue); // Cash generated
                                                                                                                     // over all time.
        building.getColony()
            .getStatisticsManager()
            .incrementBy(WindowEconModule.CURRENT_BALANCE, sellvalue, building.getColony().getDay());                // Current
                                                                                                                     // balance.
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
            building.getModuleMatching(MarketplaceItemListModule.class, m -> m.getId().equals(SELLABLE_LIST)).getList();
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
            complain();
            return getState();
        }

        for (ItemStorage candidate : sortedList)
        {
            ItemStack targetStack = candidate.getItemStack();

            if (InventoryUtils.hasItemInProvider(building, stack -> ItemStack.isSameItem(stack, targetStack)))
            {
                InventoryUtils.transferItemStackIntoNextFreeSlotFromProvider(building,
                    InventoryUtils.findFirstSlotInProviderNotEmptyWith(building,
                        stack -> ItemStack.isSameItem(stack, targetStack)),
                    worker.getInventoryCitizen());

                // only transfer the highest value match
                break; 
            }
        }

        int slot = -1;
        for (ItemStorage candidate : sortedList)
        {
            ItemStack targetStack = candidate.getItemStack();
            slot = InventoryUtils.findFirstSlotInItemHandlerWith(worker.getInventoryCitizen(),
                stack -> ItemStack.isSameItem(stack, targetStack));
            if (slot >= 0)
            {
                break;
            }
        }
            
        if (slot >= 0)
        {
            worker.setItemInHand(InteractionHand.MAIN_HAND, worker.getInventoryCitizen().getStackInSlot(slot));
            return START_WORKING;
        }

        worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

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
            TraceUtils.dynamicTrace(TRACE_SHOPKEEPER, () -> LOGGER.info("Shopkeeper: Coins are needed: {}", coinsToMint));
            return ShopkeeperState.MINT_COINS;
        }

        building.markExpectedShelfPositionsAsShelfLocations();

        Map<BlockPos, DisplayCase> displayShelves = building.getDisplayShelves();
        TraceUtils.dynamicTrace(TRACE_SHOPKEEPER,
            () -> LOGGER.info("Shopkeeper: Deciding what to do. Display shelves: {}", displayShelves.size()));

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
        return START_WORKING;
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

        if (coinsToMint > 0 && shouldMintCoins.isPresent() && shouldMintCoins.get().getValue())
        {
            ItemStack stack = building.mintCoins(null, coinsToMint);
            coinsToMint = 0;
            
            TraceUtils.dynamicTrace(TRACE_SHOPKEEPER,
                () -> LOGGER.info("Shopkeeper: {} coins are needed and autominting is turned on. Mint result: {}", coinsToMint, stack));
            
            if (stack.isEmpty())
            {
                MessageUtils.format("mctradepost.marketplace.nsf").sendTo(building.getColony()).forAllPlayers();
                settings.with(BuildingMarketplace.AUTOMINT, new BoolSetting(false));
                return DECIDE;
            }

            StatsUtil.trackStat(building, WindowEconModule.COINS_MINTED, stack.getCount());

            if (!InventoryUtils.addItemStackToProvider(building, stack))
            {
                if (!InventoryUtils.addItemStackToProvider(worker, stack))
                {
                    BuildingEconModule econ = building.getModule(MCTPBuildingModules.ECON_MODULE);
                    econ.deposit(coinsToMint * MCTPConfig.tradeCoinValue.get());

                    stack.setCount(0);
                    MessageUtils.format("entity.shopkeeper.nospaceforcoins").sendTo(building.getColony()).forAllPlayers();

                    return DECIDE;
                }
            }
            
            BuildingUtil.bringThisToTheWarehouse(building, stack);
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

        for (IBuilding somebuilding : building.getColony().getBuildingManager().getBuildings().values())
        {
            ImmutableList<IRequest<?>> openRequests = RequestUtil.getOpenRequestsFromBuilding(somebuilding, false);

            // LOGGER.info("Building {} has open requests: {}", somebuilding.getBuildingDisplayName(), openRequests.size());

            for (IRequest<?> request : openRequests)
            {
                if (request instanceof ItemStackRequest itemRequest)
                {
                    Stack stack = itemRequest.getRequest();

                    // LOGGER.info("Stack Request: {}", stack.getStack());

                    if (stack != null && stack.getStack().is(MCTradePostMod.MCTP_COIN_ITEM.get()))
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
        // First pass: Find any empty item frame (considered available to fill)
        for (final BlockPos displayLocation : building.getDisplayShelves().keySet())
        {
            DisplayCase displayCase = building.getDisplayShelves().get(displayLocation);
            final Level world = building.getColony().getWorld();
            ItemFrame frame = (ItemFrame) ((ServerLevel) world).getEntity(displayCase.getFrameId());

            if (frame == null)
            {
                building.lostShelfAtDisplayPos(displayLocation);
            }
            else
            {
                if (frame.getItem().isEmpty())
                {
                    this.currentTarget = displayLocation;
                    setDelay(DECIDE_DELAY);
                    worker.getCitizenData().setVisibleStatus(SELLING);
                    return ShopkeeperState.FILL_DISPLAYS;
                }
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
        // Find any filled item frame (considered eligible to be sold)
        for (final BlockPos displayLocation : building.getDisplayShelves().keySet())
        {
            DisplayCase displayCase = building.getDisplayShelves().get(displayLocation);

            final Level world = building.getColony().getWorld();
            ItemFrame frame = (ItemFrame) ((ServerLevel) world).getEntity(displayCase.getFrameId());

            if (frame == null)
            {
                building.lostShelfAtDisplayPos(displayLocation);
            }
            else
            {
                if (!frame.getItem().isEmpty())
                {
                    BlockPos pos = displayLocation.immutable();
                    int ticks = displayCase.getTickcount();
                    displayCase.setTickcount(ticks + 1);        // Purely informational now. May be used in a future iteration.

                    if (displayCase.getSaleState() == SaleState.ORDER_PLACED)
                    {
                        TraceUtils.dynamicTrace(TRACE_SHOPKEEPER, () -> LOGGER.info("Shopkeeper: We should sell this!"));

                        this.currentTarget = pos;
                        worker.getCitizenData().setVisibleStatus(SELLING);
                        return ShopkeeperState.SELL_MERCHANDISE;
                    }
                }
            }
        }

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
        level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 20, 0.5, 0.5, 0.5, 0.1);

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
        DisplayCase displayCase = building.getDisplayShelves().get(currentTarget);
        ItemFrame frame = (ItemFrame) ((ServerLevel) world).getEntity(displayCase.getFrameId());

        TraceUtils.dynamicTrace(TRACE_SHOPKEEPER, () -> LOGGER.info("Shopkeeper: Selling from display."));

        if (frame == null)
        {
            building.lostShelfAtDisplayPos(currentTarget);
        }
        else
        {
            ItemStack item = frame.getItem();
            if (!item.isEmpty())
            {
                if (item.getItem() instanceof SouvenirItem)
                {
                    TraceUtils.dynamicTrace(TRACE_SHOPKEEPER,
                        () -> LOGGER.info("Shopkeeper: Selling item {} from display at {}", item, currentTarget));

                    // "Sell" the item — remove it from the frame
                    frame.setItem(ItemStack.EMPTY);         // Empty the visual frame
                    sellItem(item);                         // Calculate the value of the item and credit it to the economic module and
                                                            // stats module.
                    triggerEffect(currentTarget);
                    // Add experience, stats, etc.
                    worker.getCitizenExperienceHandler().addExperience(BASE_XP_GAIN);
                    incrementActionsDoneAndDecSaturation();
                }
                else
                {
                    // Remove the invalid item from the frame
                    frame.setItem(ItemStack.EMPTY);

                    // Attempt to insert into the worker's inventory
                    ItemStack leftover = InventoryUtils.addItemStackToItemHandlerWithResult(worker.getInventoryCitizen(), item);

                    // If inventory is full and there are leftovers, drop it at the worker's feet
                    if (!leftover.isEmpty())
                    {
                        InventoryUtils.spawnItemStack(world, worker.getX(), worker.getY(), worker.getZ(), leftover);
                    }

                    TraceUtils.dynamicTrace(TRACE_SHOPKEEPER,
                        () -> LOGGER.info("Shopkeeper: removed unauthorized item {} from display at {}", item, currentTarget));
                }

                TraceUtils.dynamicTrace(TRACE_SHOPKEEPER,
                    () -> LOGGER.info("Shopkeeper: sold item {} from display at {}", item, currentTarget));

                displayCase.setStack(ItemStack.EMPTY);                  // Note that the display case is empty
                displayCase.setSaleState(SaleState.ORDER_FULFILLED);    // Mark the order as fulfilled
                displayCase.setTickcount(-1);                           // Reset the timer.
            }
        }

        setDelay(AFTER_TASK_DELAY);
        return START_WORKING;
    }

    /**
     * The AI will now fill the display stand that was found empty
     *
     * @return the nex IAIState after doing this
     */
    private IAIState fillDisplays()
    {
        if (worker.getItemInHand(InteractionHand.MAIN_HAND) == ItemStack.EMPTY)
        {
            final int slot = MCTPInventoryUtils.findRandomSlotInItemHandlerWith(worker.getInventoryCitizen(),
                stack -> building.getModuleMatching(MarketplaceItemListModule.class, m -> m.getId().equals(SELLABLE_LIST))
                    .isItemInList(new ItemStorage(stack)));



            if (slot >= 0)
            {
                worker.setItemInHand(InteractionHand.MAIN_HAND, worker.getInventoryCitizen().getStackInSlot(slot));
            }
            else
            {
                return GET_MATERIALS;
            }
        }
        if (!walkToWorkPos(currentTarget))
        {
            setDelay(2);
            return getState();
        }

        DisplayCase displayCase = building.getDisplayShelves().get(currentTarget);
        ItemFrame frame = (ItemFrame) ((ServerLevel) world).getEntity(displayCase.getFrameId());

        if (frame == null)
        {
            building.lostShelfAtDisplayPos(currentTarget);
        }
        else
        {
            // Insert the item into the frame, replacing what's there
            ItemStack heldItem = worker.getItemInHand(InteractionHand.MAIN_HAND);
            if (frame.getItem().isEmpty())
            {
                ItemStack placed = heldItem.split(1);           // Take the item from the inventory();
                ItemStack souvenir = null;

                // If the shopkeeper has gotten their hands on unsold souvenirs somehow, don't re-souvenir them.
                if (placed.is(MCTradePostMod.SOUVENIR.get()))
                {
                    souvenir = placed;
                }
                else
                {
                    souvenir = SouvenirItem.createSouvenir(placed.getItem(), computeItemValue(placed));
                }

                final ItemStack souvenirFinal = souvenir.copy();

                TraceUtils.dynamicTrace(TRACE_SHOPKEEPER,
                    () -> LOGGER.info("Shopkeeper: Placing souvenir {} in display at {}",
                        SouvenirItem.toString(souvenirFinal),
                        currentTarget));

                frame.setItem(souvenir);                        // Show the item
                displayCase.setStack(souvenir);                 // Record what the case is holding (for persistance)
                displayCase.setTickcount(0);          // Start counter for this display shelf
                displayCase.setSaleState(SaleState.FOR_SALE);   // Mark the display as for sale

                worker.getCitizenExperienceHandler().addExperience(BASE_XP_GAIN);
                this.incrementActionsDoneAndDecSaturation();

                worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

                incrementActionsDone();
            }
        }

        setDelay(AFTER_TASK_DELAY);
        return START_WORKING;
    }

    @Override
    protected int getActionsDoneUntilDumping()
    {
        return 1;
    }

    /**
     * If the list of allowed items is empty, the AI will message all the officers of the colony asking for them to set the list.
     * Happens more or less once a day if the list is not filled
     */
    private void complain()
    {
        if (ticksToComplain <= 0)
        {
            ticksToComplain = TICKS_UNTIL_COMPLAIN;
            MessageUtils.format(ENTITY_SHOPKEEPER_NO_SALEABLE_ITEMS, building.getColony().getName()).sendTo(building.getColony()).forAllPlayers();
        }
        else
        {
            ticksToComplain--;
        }
    }

    @Override
    public Class<BuildingMarketplace> getExpectedBuildingClass()
    {
        return BuildingMarketplace.class;
    }
}
