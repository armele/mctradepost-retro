package com.deathfrog.mctradepost.core.entity.ai.workers.crafting;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.StatsUtil;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingRecycling;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingRecycling.RecyclingProcessor;
import com.deathfrog.mctradepost.core.colony.jobs.JobRecyclingEngineer;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.IItemHandlerCapProvider;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.api.util.constant.StatisticsConstants;
import com.minecolonies.core.colony.buildings.modules.ItemListModule;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class EntityAIWorkRecyclingEngineer extends AbstractEntityAIBasic<JobRecyclingEngineer, BuildingRecycling> {
    public static final Logger LOGGER = LogUtils.getLogger();

    public enum RecyclingStates implements IAIState {
        UNLOAD_OUTPUT,
        LOAD_BUILDING,
        LOAD_TO_INPUT,
        START_MACHINE,
        MAINTAIN_EQUIPMENT
        ;

        @Override
        public boolean isOkayToEat()
        {
            return false;
        }
    }

    /**
     * Id in recyclables for list.
     */
    public static final String RECYCLING_LIST = "recyclables";

    public static final int BASE_XP_GAIN = 1;
    public static final String REQUESTS_TYPE_RECYCLABLE_UI = "com.deathfrog.mctradepost.gui.workerhuts.recyclingengineer.recyclables";

    public static final String RECYCLING_STAT = "recycling_stat";

    /**
     * Crafting icon
     */
    private final static VisibleCitizenStatus RECYCLING =
      new VisibleCitizenStatus(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/icons/work/recyclingengineer.png"), "com.mctradepost.gui.visiblestatus.recyclingengineer");

    protected final static int COMPLAINT_COOLDOWN_MAX = 100;
    protected int complaintCooldown = 0;

    protected BlockPos currentOutputChest = null;
    protected BlockPos currentInputChest = null;
    protected BlockPos maintenanceLocation = null;

    /**
     * Initialize the recycling engineer and add all his tasks.
     *
     * @param recycling engineer logic for the job he has.
     */
    public EntityAIWorkRecyclingEngineer(@NotNull final JobRecyclingEngineer recyclingengineer)
    {
        super(recyclingengineer);
        super.registerTargets(
          new AITarget<IAIState>(IDLE, START_WORKING, 10),
          new AITarget<IAIState>(START_WORKING, DECIDE, 10),
          new AITarget<IAIState>(DECIDE, this::decideWhatToDo, 10),
          new AITarget<IAIState>(RecyclingStates.UNLOAD_OUTPUT, this::unloadOutput, 20),
          new AITarget<IAIState>(RecyclingStates.LOAD_BUILDING, this::loadToBuilding, 20),
          new AITarget<IAIState>(RecyclingStates.LOAD_TO_INPUT, this::loadToInput, 20),
          new AITarget<IAIState>(RecyclingStates.START_MACHINE, this::startMachine, 20),
          new AITarget<IAIState>(RecyclingStates.MAINTAIN_EQUIPMENT, this::maintainEquipment, 20),
          new AITarget<IAIState>(GET_MATERIALS, this::getMaterials, 20)
        );
        worker.setCanPickUpLoot(true);
    }

    /* RECYCLING - Sort output chest. (SORT_OUTPUT IState)
    // - Materials with no crafting recipe are put away in the building inventory.
    // - Materials with further crafting recipies are put back into the input chest if the building setting for iterative processing is true.
    // 
    // RECYCLING - Move input chest items in process. (LOAD_RECYCLER IState)
    // - Number of simultaneous decompositions varies by building level.
    // - Items found in the input chest which can be decomposed are put "in process"
    // - Items found in the input chest which cannot be decomposed are put back into the building inventory.
    // - Output goes into the output chest.  Excess goes into the building inventory or is dumped into the world.
    // - Items considered "in process" are serialized so we don't lose them on a restart.
    // - When items are in process, blocks marked with a "work_area" tag get some graphic effects.
    // 
    // RECYCLING - Request items from the warehouse.
    // - When the input chest is empty, examine the recycling module list of recyclables.
    // - Request a stack of whatever has the greatest number in the warehouse.
    // - When the order is fulfilled, move the items to the input chest.
    */

    /**
     * Decide what to do next.  If there's a non-empty output chest, sort its contents.
     * Otherwise, load the recycling module.
     * @return the next AI state to transition to.
     */
    public IAIState decideWhatToDo() {
        BuildingRecycling recycling = (BuildingRecycling) building;

        LOGGER.trace("Recycling Engineer: Deciding what to do.");
        if (recycling.getRecyclingProcessors().size() > 0) {
            checkMachine(1, maintenanceLocation);
        }

        // Check if any output boxes need to be unoaded.
        for (BlockPos pos : recycling.identifyOutputPositions()) {
            // Identify a chest at position pos.
            // Try to get an item handler from a tile entity at this position
            IItemHandlerCapProvider itemHandlerOpt = IItemHandlerCapProvider.wrap(world.getBlockEntity(pos));

            if (itemHandlerOpt != null) {
                IItemHandler handler = itemHandlerOpt.getItemHandlerCap();

                // Check if there's at least one non-empty slot
                for (int i = 0; i < handler.getSlots(); i++) {
                    if (!handler.getStackInSlot(i).isEmpty()) {
                        this.currentOutputChest = pos;
                        return RecyclingStates.UNLOAD_OUTPUT;
                    }
                }
            }
        }

        boolean hasInput = hasInput();
        if (recycling.hasProcessingCapacity()) {
            if (hasInput) return RecyclingStates.START_MACHINE; 
        } else {
            if (hasInput) return RecyclingStates.MAINTAIN_EQUIPMENT;
        }

        // Look in inventory for recyclables
        return GET_MATERIALS;
    }


    /**
     * Check if we have input waiting to be started (items exist in input boxes).
     * If items are found, return the START_MACHINE state.
     * @return the next AI state to transition to.
     */
    protected boolean hasInput() {
        BuildingRecycling recycling = (BuildingRecycling) building;

        // Check if the machine needs to be started (items exist in input boxes and we have processing capacity).
        for (BlockPos pos : recycling.identifyInputPositions()) {
            // Identify a chest at position pos.
            // Try to get an item handler from a tile entity at this position
            IItemHandlerCapProvider itemHandlerOpt = IItemHandlerCapProvider.wrap(world.getBlockEntity(pos));

            if (itemHandlerOpt != null) {
                IItemHandler handler = itemHandlerOpt.getItemHandlerCap();

                // Check if there's at least one non-empty slot
                for (int i = 0; i < handler.getSlots(); i++) {
                    if (!handler.getStackInSlot(i).isEmpty()) {
                        this.currentInputChest = pos;
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Walk to the current output chest and unload it into the worker's inventory.
     * If the worker's inventory is full, do not unload the chest.
     * If the recycling module is set to iterative processing, after unloading the chest, return to the LOAD_RECYCLER state.
     * Otherwise, return to the DUMPING state.
     * @return the next AI state to transition to.
     */
    public IAIState unloadOutput() {
        BuildingRecycling recycling = (BuildingRecycling) building;
        boolean cancarry = true;

        LOGGER.trace("Recycling Engineer: Unloading output.");

        worker.getCitizenData().setVisibleStatus(RECYCLING);

        if (currentOutputChest != null) {
            if (!walkToSafePos(currentOutputChest)) {
                return getState();
            }

            IItemHandlerCapProvider itemHandlerOpt = IItemHandlerCapProvider.wrap(world.getBlockEntity(currentOutputChest));

            if (itemHandlerOpt != null) {
                IItemHandler chestHandler = itemHandlerOpt.getItemHandlerCap();

                for (int i = 0; i < chestHandler.getSlots(); i++) {
                    ItemStack stackInChest = chestHandler.getStackInSlot(i);

                    if (!stackInChest.isEmpty() && cancarry) {
                        // Attempt to insert the stack into worker's inventory
                        LOGGER.trace("Trying to insert {} into worker's inventory", stackInChest);
                        cancarry = InventoryUtils.transferItemStackIntoNextFreeSlotInProvider(chestHandler, i, worker);
                    }
                }
            }
            worker.setItemInHand(InteractionHand.MAIN_HAND, worker.getInventoryCitizen().getStackInSlot(0));
            
            // If the worker could unload everything in the chest, set currentOutputChest to null
            if (cancarry) {
                currentOutputChest = null;
            }

            if (building.getSetting(BuildingRecycling.ITERATIVE_PROCESSING).getValue()) {
                LOGGER.info("Load recycler next.");
                return RecyclingStates.LOAD_TO_INPUT;
            } else {
                LOGGER.info("Dump stuff into the building inventory next.");
                return RecyclingStates.LOAD_BUILDING;
            }
        }

        return DECIDE;
    }

    /**
     * Walk to the recycling center and load items from the worker's inventory into the recycling center's inventory.
     * If the worker's inventory is full, do not unload the chest.
     * If the recycling module is set to iterative processing, after loading the chest, return to the LOAD_RECYCLER state.
     * Otherwise, return to the DUMPING state.
     * @return the next AI state to transition to.
     */
    public IAIState loadToBuilding() {
        BuildingRecycling recycling = (BuildingRecycling) building;
        boolean canhold = true;

        LOGGER.info("Recycling Engineer: Loading to building.");

        if (!walkToSafePos(recycling.getPosition())) {
            return getState();
        }

        IItemHandler itemHandler = recycling.getItemHandlerCap();
        if (itemHandler != null) {
            IItemHandler workerInventory = getInventory(); // assumes this method exists for worker inventory

            for (int i = 0; i < workerInventory.getSlots(); i++) {
                ItemStack stackInChest = workerInventory.getStackInSlot(i);

                if (!stackInChest.isEmpty() && canhold) {
                    // Attempt to insert the stack into building inventory
                    LOGGER.info("Trying to insert {} into building inventory", stackInChest);
                    canhold = InventoryUtils.transferItemStackIntoNextFreeSlotInProvider(workerInventory, i, recycling);
                }
            }

            worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        } else {
            LOGGER.warn("No inventory handling found on the recycling center...");
        }

        if (canhold) {
            return DECIDE;
        } else {
            return INVENTORY_FULL;
        }
    }

    /**
     * Walk to the recycling center and load items from the worker's inventory into the recycling center's inventory.
     * If the worker's inventory is full, do not unload the chest.
     * If the recycling module is set to iterative processing, after loading the chest, return to the LOAD_RECYCLER state.
     * Otherwise, return to the DUMPING state.
     * @return the next AI state to transition to.
     */
    public IAIState loadToInput() {
        BuildingRecycling recycling = (BuildingRecycling) building;
        boolean canhold = true;

        LOGGER.info("Recycling Engineer: Loading to recycler.");

        if (currentInputChest == null) {
            currentInputChest = recycling.identifyInputPositions().getFirst();
        }

        if (currentInputChest == null) {
            LOGGER.error("No input chest found on the recycling center...");
            complain(BuildingRecycling.RECYCLER_NO_INPUT_BOX);
            return DECIDE;
        }

        if (!walkToSafePos(currentInputChest)) {
            return getState();
        }

        IItemHandlerCapProvider itemHandlerOpt = IItemHandlerCapProvider.wrap(world.getBlockEntity(currentInputChest));

        if (itemHandlerOpt.getItemHandlerCap() != null) {
            IItemHandler workerInventory = getInventory(); // assumes this method exists for worker inventory

            for (int i = 0; i < workerInventory.getSlots(); i++) {
                ItemStack stackInSlot = workerInventory.getStackInSlot(i);

                if (!stackInSlot.isEmpty() && canhold) {
                    // Attempt to insert the stack into the input chest inventory
                    LOGGER.info("Trying to insert {} into input chest", stackInSlot);
                    canhold = InventoryUtils.transferItemStackIntoNextFreeSlotInProvider(workerInventory, i, itemHandlerOpt);
                }
            }

            worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        } else {
            LOGGER.warn("No inventory handling found on the recycling center...");
            complain(BuildingRecycling.RECYCLER_NO_INPUT_BOX);
            return DECIDE;
        }

        if (canhold) {
            return DECIDE;
        } else {
            return RecyclingStates.START_MACHINE;
        }
   
    }
    

    /**
     * Initiates the recycling machine process by checking if there is space available
     * for new recycling processors. If the input chest has items and the machine
     * capacity is not exceeded, it will start processing the items.
     * 
     * @return the next AI state, either DECIDE or START_MACHINE, based on the status
     *         of the input chest and the machine's capacity.
     */
    public IAIState startMachine() {
        LOGGER.info("Starting recycling machine.");

        worker.getCitizenData().setVisibleStatus(RECYCLING);

        BuildingRecycling recycling = (BuildingRecycling) building;
        BlockPos pos = recycling.identifyInputPositions().getFirst();
        IItemHandlerCapProvider chestHandlerOpt = IItemHandlerCapProvider.wrap(world.getBlockEntity(pos));

        if (chestHandlerOpt.getItemHandlerCap() != null) {
            if (recycling.hasProcessingCapacity()) {
                LOGGER.info("Room for more - load it up!");

                for (int i = 0; i < chestHandlerOpt.getItemHandlerCap().getSlots(); i++) {
                    ItemStack stackToRecycle = chestHandlerOpt.getItemHandlerCap().getStackInSlot(i).copy();

                    if (stackToRecycle == null || stackToRecycle.isEmpty()) {
                        continue;
                    }

                    if (stackToRecycle.getCount() > 0) {
                        LOGGER.info("Starting recycling process for {}", stackToRecycle.getDescriptionId());
                        final ItemStack removedStack = chestHandlerOpt.getItemHandlerCap().extractItem(i, Integer.MAX_VALUE, false);

                        if (recycling.addRecyclingProcess(removedStack.copy())) {
                            LOGGER.warn("Recording recycling stats for: {}, count {}", stackToRecycle.getDescriptionId(), stackToRecycle.getCount());
                            worker.getCitizenExperienceHandler().addExperience(BASE_XP_GAIN);
                            StatsUtil.trackStat(recycling, StatisticsConstants.ITEM_USED, stackToRecycle, stackToRecycle.getCount());
                            worker.getCitizenColonyHandler().getColonyOrRegister().getStatisticsManager().increment(RECYCLING_STAT, worker.getCitizenColonyHandler().getColonyOrRegister().getDay());
                        } else {
                            LOGGER.info("This item cannot be recycled {}", removedStack.getDescriptionId());
                            if (!InventoryUtils.addItemStackToItemHandler(recycling.getItemHandlerCap(), stackToRecycle)) {
                                InventoryUtils.spawnItemStack(recycling.getColony().getWorld(), pos.getX(), pos.getY(), pos.getZ(), removedStack);
                                return INVENTORY_FULL;
                            }
                        }
                    }
                }
                
                return DECIDE;

            } else {
                LOGGER.info("Machine full.");

                return DECIDE;
            }
        } else {
            LOGGER.warn("No input location found on the recycling center...");
            complain(BuildingRecycling.RECYCLER_NO_INPUT_BOX);
        }

        return DECIDE;
    }

    /**
     * Checks the status of all recycling processors and updates their processing timers.
     * If a processor has completed its recycling task, it is removed from the active processors list.
     * 
     * @return true if at least one recycling job has finished processing, false otherwise.
     */
    public boolean checkMachine(int speedBoost, BlockPos pos) {
        BuildingRecycling recycling = (BuildingRecycling) building;
        boolean jobfinished = false;

        for (RecyclingProcessor processor : recycling.getRecyclingProcessors()) {
            processor.processingTimer += speedBoost;
            if (processor.isFinished()) {
                recycling.generateOutput(processor.processingItem);
                recycling.getRecyclingProcessors().remove(processor);
                jobfinished = true;
            }
            building.triggerEffect(pos, SoundEvents.GRAVEL_STEP, ParticleTypes.ELECTRIC_SPARK, 80);
        }

        return jobfinished;
    }

    /**
     * Complain to the player if there is a problem with the recycling center, unless
     * we have recently complained.  If we have, just decrement the cooldown.
     * @param message the message to send.
     */
    private void complain(String message)
    {
        if (complaintCooldown <= 0)
        {
            complaintCooldown = COMPLAINT_COOLDOWN_MAX;
            MessageUtils.format(message)
              .sendTo(building.getColony()).forAllPlayers();
        }
        else
        {
            complaintCooldown--;
        }
    }

    /**
     * Method for the AI to try to get the materials needed for the task they're doing. Will request if there are no materials
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

        final List<ItemStorage> list = building.getModuleMatching(ItemListModule.class, m -> m.getId().equals(RECYCLING_LIST)).getList();
        if (list.isEmpty())
        {
            complain(BuildingRecycling.RECYCLER_NO_RECYCLABLES_SET);
            return DECIDE;
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
            return RecyclingStates.LOAD_TO_INPUT;
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
                    BuildingRecycling.REQUESTS_TYPE_RECYCLABLE,
                    Constants.STACKSIZE,
                    1));
            }
        }

        setDelay(2);
        return START_WORKING;
    }

    /**
     * Maintains the equipment of the recycling building. This AIState is responsible for ensuring that the recycling processors are in working order.
     * The AI will walk to a random equipment position and check if the processor at that position is in working order.
     * If not, the AI will fix the processor by consuming a random item from the recycling list.
     * The AI will then return to the DECIDE state.
     * @return the next state to go to.
     */
    public IAIState maintainEquipment() {
        BuildingRecycling recycling = (BuildingRecycling) building;
        int boost = 2 + (worker.getCitizenData().getCitizenSkillHandler().getLevel(getModuleForJob().getPrimarySkill()) / 20);
        List<BlockPos> equipmentSpots = recycling.identifyEquipmentPositions();

        if (maintenanceLocation == null) {
            maintenanceLocation = equipmentSpots.get(ThreadLocalRandom.current().nextInt(equipmentSpots.size()));
        }

        if (!walkToSafePos(maintenanceLocation)) {
            return RecyclingStates.MAINTAIN_EQUIPMENT;
        } else {
            if (recycling.getRecyclingProcessors().size() > 0) {
                checkMachine(boost, maintenanceLocation);
            }
            maintenanceLocation = null;
        }

        return DECIDE;
    }

    @Override
    public Class<BuildingRecycling> getExpectedBuildingClass()
    {
        return BuildingRecycling.class;
    }
}