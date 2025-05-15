package com.deathfrog.mctradepost.core.entity.ai.workers.crafting;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.jobs.JobShopkeeper;
import com.deathfrog.mctradepost.core.colony.jobs.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.jobs.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.colony.jobs.buildings.workerbuildings.DisplayCase;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.entity.ai.statemachine.AIEventTarget;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIBlockingEventType;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.constant.translation.RequestSystemTranslationConstants;
import com.minecolonies.core.colony.buildings.modules.ItemListModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingComposter;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import com.minecolonies.api.util.InventoryUtils;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.minecolonies.api.util.constant.Constants.*;
import com.minecolonies.api.crafting.ItemStorage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.minecolonies.api.util.constant.StatisticsConstants.ITEM_USED;
import static com.minecolonies.core.colony.buildings.modules.BuildingModules.STATS_MODULE;


/**
 * Handles the Marketplace.
 */
public class EntityAIWorkShopkeeper extends AbstractEntityAIInteract<JobShopkeeper, BuildingMarketplace>
{
    /**
     * Base xp gain for the composter.
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

    private static final int SELLTIME = 60;

    /**
     * Id in compostable map for list.
     */
    public static final String SELLABLE_LIST = "sellables";  // Was "COMPOSTABLE_LIST"

        @NonNls
    public static final String REQUESTS_TYPE_SELLABLE_UI = "com.deathfrog.mctradepost.gui.workerhuts.shopkeeper.sellables";

    /**
     * Composting icon
     */
    private final static VisibleCitizenStatus SELLING =
      new VisibleCitizenStatus(ResourceLocation.parse(MCTradePostMod.MODID + ":textures/icons/work/shopkeeper.png"), "com.mctradepost.gui.visiblestatus.shopkeeper");

    /**
     * Constructor for the AI
     *
     * @param job the job to fulfill
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public EntityAIWorkShopkeeper(@NotNull final JobShopkeeper job)
    {
        super(job);
        super.registerTargets(
          new AIEventTarget(AIBlockingEventType.AI_BLOCKING, this::blockingLogic, TICKS_SECOND),
          new AITarget(IDLE, START_WORKING, 1),
          new AITarget(GET_MATERIALS, this::getMaterials, TICKS_SECOND),
          new AITarget(START_WORKING, this::decideWhatToDo, 1),
          new AITarget(CRAFT, this::sellFromDisplay, 5),
          new AITarget(COMPOSTER_FILL, this::fillDisplays, 10)
        );
        worker.setCanPickUpLoot(true);
    }

    /**
     * Handles the logic for the AI when in a blocking state.
     * Iterates over display shelves and increments their tick count.
     *
     * @return the next IAIState after processing blocking logic
     */

    private IAIState blockingLogic()
    {

        for (BlockPos dispLocation : building.getDisplayShelves().keySet()) {
            DisplayCase contents = building.getDisplayShelves().get(dispLocation);
            if (contents != null) {
                contents.setTickcount(contents.getTickcount() + 1);
            }
        }
        
        return null;
    }   

    /**
     * Compute the value of the item stack for the marketplace.
     *
     * @param stack the item stack to compute the value of.
     * @param amount the amount of the item in the stack.
     * @return the computed value of the item stack.
     */
    private long computeItemValue(ItemStack stack, int amount)
    {
        // TODO: Item Exchange Rate
        int value = 1;

        return value * amount;
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
        // final Level world = building.getColony().getWorld();

        building.getModule(STATS_MODULE).incrementBy(ITEM_USED + ";" + item.getDescriptionId(), 1);

        // TODO: Compute sales values and record them somewhere.
        long sellvalue = computeItemValue(item, 1);
        sellvalue = Math.round(sellvalue * skillMultiplier);

        // TODO: Build GUI for ECON module in which Increment global variable for value generated.
        building.getModule(MCTPBuildingModules.ECON_MODULE).incrementBy(MCTPBuildingModules.ECON_MODULE + ";" + item.getDescriptionId(), 1);

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

        final List<ItemStorage> list = building.getModuleMatching(ItemListModule.class, m -> m.getId().equals(SELLABLE_LIST)).getList();
        if (list.isEmpty())
        {
            complain();
            return getState();
        }

        if (InventoryUtils.hasItemInProvider(building, stack -> list.contains(new ItemStorage(stack))))
        {
            InventoryUtils.transferItemStackIntoNextFreeSlotFromProvider(
              building,
              InventoryUtils.findFirstSlotInProviderNotEmptyWith(building, stack -> list.contains(new ItemStorage(stack))),
              worker.getInventoryCitizen());
        }

        final int slot = InventoryUtils.findFirstSlotInItemHandlerWith(
          worker.getInventoryCitizen(),
          stack -> list.contains(new ItemStorage(stack))
        );
        if (slot >= 0)
        {
            worker.setItemInHand(InteractionHand.MAIN_HAND, worker.getInventoryCitizen().getStackInSlot(slot));
            return START_WORKING;
        }

        worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        if (!building.hasWorkerOpenRequests(worker.getCitizenData().getId()))
        {
            final ArrayList<ItemStack> itemList = new ArrayList<>();
            for (final ItemStorage item : list)
            {
                final ItemStack itemStack = item.getItemStack();
                itemStack.setCount(itemStack.getMaxStackSize());
                itemList.add(itemStack);
            }
            if (!itemList.isEmpty())
            {
                worker.getCitizenData()
                  .createRequestAsync(new StackList(itemList,
                    RequestSystemTranslationConstants.REQUESTS_TYPE_COMPOSTABLE,
                    Constants.STACKSIZE,
                    1,
                    building.getSetting(BuildingComposter.MIN).getValue()));
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
        worker.getCitizenData().setVisibleStatus(VisibleCitizenStatus.WORKING);

        if (!walkToBuilding())
        {
            setDelay(2);
            return getState();
        }

        final BuildingMarketplace building = this.building;
        Map<BlockPos, DisplayCase> displayShelves = building.getDisplayShelves();

        MCTradePostMod.LOGGER.info("Deciding what to do. Display shelves: {}", displayShelves.size());

        // First pass: Find any empty item frame (considered available to fill)
        for (final BlockPos displayLocation : building.getDisplayShelves().keySet())
        {
            DisplayCase displayCase = building.getDisplayShelves().get(displayLocation);
            final Level world = building.getColony().getWorld();
            ItemFrame frame = (ItemFrame) ((ServerLevel) world).getEntity(displayCase.getFrameId());

            if (frame == null) {
                building.lostShelfAtDisplayPos(displayLocation);
            } else {
                if (frame.getItem().isEmpty()) {
                    this.currentTarget = displayLocation;
                    setDelay(DECIDE_DELAY);
                    worker.getCitizenData().setVisibleStatus(SELLING);
                    return COMPOSTER_FILL;
                }
            }
        }

        // Second pass: Find any filled item frame (considered ready to "craft" / process)
        for (final BlockPos displayLocation : building.getDisplayShelves().keySet())
        {
            DisplayCase displayCase = building.getDisplayShelves().get(displayLocation);

            final Level world = building.getColony().getWorld();
            ItemFrame frame = (ItemFrame) ((ServerLevel) world).getEntity(displayCase.getFrameId());

            if (frame == null) {
                building.lostShelfAtDisplayPos(displayLocation);
            } else {

                if (!frame.getItem().isEmpty())
                {
                    BlockPos pos = displayLocation.immutable();
                    int ticks = displayCase.getTickcount();

                    if (ticks >= SELLTIME) {
                        this.currentTarget = pos;
                        setDelay(DECIDE_DELAY);
                        worker.getCitizenData().setVisibleStatus(SELLING);
                        return CRAFT;
                    } else if (ticks >= 0) {
                        displayCase.setTickcount(ticks + 1);
                    } else { // Something got put in the display frame but not by the builder.  Remove it!
                        ItemStack frameItem = frame.getItem();
                        if (!frameItem.isEmpty())
                        {
                            // Remove the item from the frame
                            frame.setItem(ItemStack.EMPTY);

                            // Attempt to insert into the worker's inventory
                            ItemStack leftover = InventoryUtils.addItemStackToItemHandlerWithResult(worker.getInventoryCitizen(), frameItem);

                            // If inventory is full and there are leftovers, drop it at the worker's feet
                            if (!leftover.isEmpty()) {
                                InventoryUtils.spawnItemStack(world, worker.getX(), worker.getY(), worker.getZ(), leftover);
                            }

                            MCTradePostMod.LOGGER.info("Shopkeeper removed unauthorized item {} from display at {}", frameItem, pos);

                        }

                    }

                }
            }
        }

        setDelay(DECIDE_DELAY);
        return START_WORKING;
    }

    /**
     * The AI will now remove the item from the display stand that he found full on his building and "sell" it, adding experience and stats.
     * @return the next IAIState after doing this
     */
    private IAIState sellFromDisplay()
    {
        DisplayCase displayCase = building.getDisplayShelves().get(currentTarget);
        ItemFrame frame = (ItemFrame) ((ServerLevel) world).getEntity(displayCase.getFrameId());

        if (frame == null) {
            building.lostShelfAtDisplayPos(currentTarget);
        } else {
            ItemStack item = frame.getItem();
            if (!item.isEmpty())
            {
                // "Sell" the item — remove it from the frame
                frame.setItem(ItemStack.EMPTY);

                // Add experience, stats, etc.
                worker.getCitizenExperienceHandler().addExperience(BASE_XP_GAIN);
                incrementActionsDoneAndDecSaturation();

                sellItem(item);
                displayCase.setStack(ItemStack.EMPTY);
                displayCase.setTickcount(-1);
            }
        }

        setDelay(AFTER_TASK_DELAY);
        return START_WORKING;
    }

    /**
     * The AI will now fill the display stand that he found empty on his building
     *
     * @return the nex IAIState after doing this
     */
    private IAIState fillDisplays()
    {

        if (worker.getItemInHand(InteractionHand.MAIN_HAND) == ItemStack.EMPTY)
        {
            final int slot = InventoryUtils.findFirstSlotInItemHandlerWith(
              worker.getInventoryCitizen(),
              stack -> building.getModuleMatching(ItemListModule.class, m -> m.getId().equals(SELLABLE_LIST)).isItemInList(new ItemStorage(stack)));

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

        if (frame == null) {
            building.lostShelfAtDisplayPos(currentTarget);
        } else {
            // "Insert" the item into the frame, replacing what's there
            ItemStack heldItem = worker.getItemInHand(InteractionHand.MAIN_HAND);
            if (frame.getItem().isEmpty())
            {
                frame.setItem(heldItem.copy());           // Show the item
                displayCase.setStack(heldItem.copy());    // Record what the case is holding (for persistance)
                displayCase.setTickcount(0);    // Start countdown for this display shelf

                worker.getCitizenExperienceHandler().addExperience(BASE_XP_GAIN);
                this.incrementActionsDoneAndDecSaturation();

                // Remove from inventory (simulate placing it in the world)
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
     * If the list of allowed items is empty, the AI will message all the officers of the colony asking for them to set the list. Happens more or less once a day if the list is not
     * filled
     */
    private void complain()
    {
        if (ticksToComplain <= 0)
        {
            ticksToComplain = TICKS_UNTIL_COMPLAIN;
            MessageUtils.format("entity.shopkeeper.noitems")
              .sendTo(building.getColony()).forAllPlayers();
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
