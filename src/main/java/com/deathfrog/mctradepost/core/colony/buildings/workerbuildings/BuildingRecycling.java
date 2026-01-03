package com.deathfrog.mctradepost.core.colony.buildings.workerbuildings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.advancements.MCTPAdvancementTriggers;
import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.api.colony.buildings.modules.RecyclingItemListModule;
import com.deathfrog.mctradepost.api.items.MCTPModDataComponents;
import com.deathfrog.mctradepost.api.items.datacomponent.RecyclableRecord;
import com.deathfrog.mctradepost.api.research.MCTPResearchConstants;
import com.deathfrog.mctradepost.api.util.DomumOrnamentumHelper;
import com.deathfrog.mctradepost.api.util.MCTPInventoryUtils;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.SoundUtils;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.modules.ItemValueRegistry;
import com.deathfrog.mctradepost.core.colony.buildings.modules.settings.SortSetting;
import com.deathfrog.mctradepost.core.entity.ai.workers.crafting.EntityAIWorkRecyclingEngineer;
import com.deathfrog.mctradepost.recipe.DeconstructionRecipe;
import com.deathfrog.mctradepost.recipe.DeconstructionRecipe.Output;
import com.ldtteam.domumornamentum.recipe.ModRecipeTypes;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.managers.interfaces.IRegisteredStructureManager;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.IItemHandlerCapProvider;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.settings.BoolSetting;
import com.minecolonies.core.colony.buildings.modules.settings.SettingKey;
import com.minecolonies.core.util.AdvancementUtils;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_RECYCLING;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_RECYCLING_RECIPE;

public class BuildingRecycling extends AbstractBuilding
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final String ITEMS_RECOVERED = "items_recovered";

    // If true, any output with a crafting recipe will be resubmitted for further recycling.
    public static final ISettingKey<BoolSetting> ITERATIVE_PROCESSING =
        new SettingKey<>(BoolSetting.class, ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "iterative_processing"));

    public static final ISettingKey<SortSetting> ALLOW_SORT =
        new SettingKey<SortSetting>(SortSetting.class, ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "allow_sort"));

    public static final String REQUESTS_TYPE_RECYCLABLE = "com.mctradepost.coremod.request.recyclable";
    public static final String RECYCLER_NO_INPUT_BOX = "com.mctradepost.recycler.no_input_box";
    public static final String RECYCLER_NO_OUTPUT_BOX = "com.mctradepost.recycler.no_output_box";
    public static final String RECYCLER_NO_RECYCLABLES_SET = "com.mctradepost.recycler.no_list_configured";
    public static final String NO_RECYCLE_TAG = "NoRecycle";

    public static final int PENALTY_RECYCLING_STAT_LEVEL = 10;
    public static final int FLAWLESS_RECYCLING_STAT_LEVEL = MCTPConfig.flawlessRecycling.get();

    /**
     * Structurize tag identifying where the input and output containers are.
     */
    private static final String STRUCT_TAG_OUTPUT_CONTAINER = "output_container";
    private static final String STRUCT_TAG_INPUT_CONTAINER = "input_container";
    private static final String STRUCT_TAG_GRINDER = "grinder";

    public static final String SERIALIZE_RECYCLINGPROCESSORS_TAG = "RecyclingProcessors";

    protected final Object2IntOpenHashMap<ItemStorage> allItems = new Object2IntOpenHashMap<>();

    private Set<RecyclingProcessor> recyclingProcessors = ConcurrentHashMap.newKeySet();

    private static final int WAREHOUSE_INVENTORY_COOLDOWN = MCTPConfig.warehouseInventoryCooldown.get();
    private int wareHouseCooldownCounter = 0;

    public BuildingRecycling(@NotNull IColony colony, BlockPos pos)
    {
        super(colony, pos);
    }

    /**
     * Returns the unique identifier for the recycling building's schematic.
     *
     * @return the schematic name for the recycling building
     */
    @Override
    public String getSchematicName()
    {
        return ModBuildings.RECYCLING_ID;
    }


    /**
     * Deserializes the NBT data for the recycling building, restoring its state from the
     * provided CompoundTag.
     *
     * @param provider The holder lookup provider for item and block references.
     * @param compound The CompoundTag containing the serialized state of the
     *                 recycling building.
     */
    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag compound)
    {
        if (provider == null || compound == null) return;

        super.deserializeNBT(provider, compound);
        deserializeRecyclingProcessors(provider, compound);
        deserializeAllowableItems(provider, compound);
    }

    /**
     * Deserializes the current state of the recycling processors from the given NBT tag. The tag is expected to contain a list of
     * CompoundTags, each representing a recycling processor's state. The list is expected to be stored under the key
     * SERIALIZE_RECYCLINGPROCESSORS_TAG in the given tag. Each CompoundTag in the list is expected to contain the deserialized state
     * of a recycling processor. The state of the recycling processors is stored in the field #recyclingProcessors.
     * 
     * @param tag The CompoundTag containing the serialized state of the recycling processors.
     */
    public void deserializeRecyclingProcessors(HolderLookup.Provider provider, CompoundTag tag)
    {
        recyclingProcessors.clear();

        if (tag.contains(SERIALIZE_RECYCLINGPROCESSORS_TAG, Tag.TAG_LIST))
        {
            ListTag processorListTag = tag.getList(SERIALIZE_RECYCLINGPROCESSORS_TAG, Tag.TAG_COMPOUND);
            for (Tag element : processorListTag)
            {
                CompoundTag processorTag = (CompoundTag) element;
                RecyclingProcessor processor = new RecyclingProcessor();
                processor.deserialize(provider, processorTag);
                recyclingProcessors.add(processor);
            }
        }
    }

    /**
     * Serializes the current state of the building, including the state of the allowable items list, into a RegistryFriendlyByteBuf.
     * The state of the allowable items list is stored under the key EntityAIWorkRecyclingEngineer.RECYCLING_LIST in the serialized
     * CompoundTag.
     * 
     * @param buf      The RegistryFriendlyByteBuf to serialize the state of the building into.
     * @param fullSync Whether or not to serialize the full state of the building, or just the delta.
     */
    @Override
    public void serializeToView(final RegistryFriendlyByteBuf buf, final boolean fullSync)
    {
        super.serializeToView(buf, fullSync);

        // Serialize allowable items
        CompoundTag allowableItemsTag = new CompoundTag();
        RegistryAccess regAccess = buf.registryAccess();

        if (regAccess != null)
        {
            serializeAllowableItems(regAccess, allowableItemsTag);
        }

        buf.writeNbt(allowableItemsTag);
    }

    /**
     * Serializes the current state of the building, including the state of the recycling processors, into an NBT tag. The state of the
     * recycling processors is stored under the key SERIALIZE_RECYCLINGPROCESSORS_TAG in the returned tag.
     *
     * @return the serialized NBT tag.
     */
    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider)
    {
        if (provider == null) return null;

        CompoundTag tag = super.serializeNBT(provider);
        Tag recyclingProcessorsTag = serializeRecyclingProcessors(provider).get(SERIALIZE_RECYCLINGPROCESSORS_TAG);
        if (recyclingProcessorsTag != null)
        {
            tag.put(SERIALIZE_RECYCLINGPROCESSORS_TAG, recyclingProcessorsTag);
        }

        // Serialize allowable items
        serializeAllowableItems(NullnessBridge.assumeNonnull(provider), tag);

        return tag;
    }

    /**
     * Serializes the current state of the recycling processors into an NBT tag. The tag contains a list of tags, each of which
     * represents the state of a single recycling processor. The list is stored under the key SERIALIZE_RECYCLINGPROCESSORS_TAG.
     *
     * @return the serialized NBT tag.
     */
    public @Nonnull CompoundTag serializeRecyclingProcessors(@Nonnull HolderLookup.Provider provider)
    {
        CompoundTag tag = new CompoundTag();
        ListTag processorListTag = new ListTag();

        for (RecyclingProcessor processor : recyclingProcessors)
        {
            processorListTag.add(processor.serialize(provider));
        }

        tag.put(SERIALIZE_RECYCLINGPROCESSORS_TAG, processorListTag);

        return tag;
    }

    /**
     * Serializes the list of allowable items into the given CompoundTag. The list is stored under the key
     * EntityAIWorkRecyclingEngineer#RECYCLING_LIST. Each item is represented as a CompoundTag with keys "stack" and "count", where
     * "stack" is the serialized form of the item stack and "count" is the number of items allowed.
     *
     * @param provider The holder lookup provider for item and block references.
     * @param tag      The CompoundTag to which the list of allowable items should be serialized.
     */
    public void serializeAllowableItems(@Nonnull HolderLookup.Provider provider, CompoundTag tag)
    {
        if (allItems != null && !allItems.isEmpty())
        {
            ListTag itemsListTag = new ListTag();
            for (ItemStorage storage : allItems.keySet())
            {
                CompoundTag itemTag = new CompoundTag();
                ItemStack stack = storage.getItemStack();
                Tag stackTag = stack.save(provider);

                if (stackTag == null) continue;

                itemTag.put("stack", stackTag);
                itemTag.putInt("count", allItems.getInt(storage));
                itemsListTag.add(itemTag);
            }
            tag.put(EntityAIWorkRecyclingEngineer.RECYCLING_LIST, itemsListTag);
        }
    }

    /**
     * Deserializes the allowable items from the given NBT compound tag and updates the building's state. This method clears the
     * current list of allowable items and repopulates it by reading from the compound tag specified. The allowable items are stored
     * under the key specified by EntityAIWorkRecyclingEngineer#RECYCLING_LIST, and each item is represented as a CompoundTag within a
     * ListTag. The items are added to the allItems map with a default value of 0.
     *
     * @param provider The holder lookup provider for item and block references.
     * @param compound The CompoundTag containing the serialized state of allowable items.
     */
    public void deserializeAllowableItems(@Nonnull HolderLookup.Provider provider, CompoundTag tag)
    {
        this.allItems.clear();

        if (tag != null && tag.contains(EntityAIWorkRecyclingEngineer.RECYCLING_LIST))
        {
            ListTag outputTag = tag.getList(EntityAIWorkRecyclingEngineer.RECYCLING_LIST, Tag.TAG_COMPOUND);

            for (int i = 0; i < outputTag.size(); i++)
            {
                CompoundTag itemTag = outputTag.getCompound(i);
                CompoundTag stackTag = itemTag.getCompound("stack");

                if (stackTag == null) continue;

                ItemStack stack = ItemStack.parseOptional(provider, stackTag);
                int count = itemTag.getInt("count");

                if (!stack.isEmpty())
                {
                    allItems.put(new ItemStorage(stack), count);
                }
            }
        }
    }

    /**
     * Initialize the output positions based on what is tagged in the structure. This makes the building look for the correct number of
     * output positions even if some are missing. That way a "repair" action will fix the problem.
     * 
     * @return a list of block positions that are tagged as output containers
     */
    public List<BlockPos> identifyOutputPositions()
    {
        final List<BlockPos> outputContainers = getLocationsFromTag(STRUCT_TAG_OUTPUT_CONTAINER);
        return outputContainers;
    }

    /**
     * Initialize the input positions based on what is tagged in the structure. This makes the building look for the correct number of
     * input positions even if some are missing. That way a "repair" action will fix the problem.
     * 
     * @return a list of block positions that are tagged as input containers
     */
    public List<BlockPos> identifyInputPositions()
    {
        final List<BlockPos> inputContainers = getLocationsFromTag(STRUCT_TAG_INPUT_CONTAINER);
        return inputContainers;
    }

    /**
     * Initialize the equipment positions based on what is tagged in the structure. This makes the building look for the correct number
     * of equipment positions even if some are missing. That way a "repair" action will fix the problem.
     * 
     * @return a list of block positions that are tagged as equipment positions
     */
    public List<BlockPos> identifyEquipmentPositions()
    {
        final List<BlockPos> equipmentSpots = getLocationsFromTag(STRUCT_TAG_GRINDER);
        return equipmentSpots;
    }

    public static class RecyclingProcessor
    {
        public ItemStack processingItem = null;
        public int processingTimer = -1;
        public int processingTimerComplete = -1;
        public List<ItemStack> output = null;

        /**
         * Serializes the state of the recycling processor into an NBT tag. The tag contains the following elements:
         * <ul>
         * <li>"ProcessingItem": the item currently being processed, stored as a CompoundTag representing the item stack.</li>
         * <li>"OutputItems": the items produced by the processor, stored as a ListTag of CompoundTags representing the item
         * stacks.</li>
         * <li>"ProcessingTimer": the number of ticks remaining in the processing timer, stored as an int.</li>
         * </ul>
         * If any of these values are missing, the default values are set: ItemStack.EMPTY for the processing item, an empty list for
         * the output items, and -1 for the processing timer.
         * 
         * @param provider The holder lookup provider for item and block references.
         * @return the serialized NBT tag.
         */
        public CompoundTag serialize(@Nonnull HolderLookup.Provider provider)
        {
            CompoundTag tag = new CompoundTag();

            if (!processingItem.isEmpty())
            {
                Tag itemTag = processingItem.save(provider);

                if (itemTag != null)
                {
                    tag.put("ProcessingItem", itemTag);
                }
            }

            if (output != null && !output.isEmpty())
            {
                ListTag outputTag = new ListTag();
                for (ItemStack stack : output)
                {
                    outputTag.add(stack.save(provider));
                }
                tag.put("OutputItems", outputTag);
            }
            tag.putInt("ProcessingTimer", processingTimer);
            tag.putInt("ProcessingTimerComplete", processingTimerComplete);

            return tag;
        }

        /**
         * Deserializes the given CompoundTag into the processing item, output items, and processing timer. The processing item is
         * stored under the key "ProcessingItem" and is expected to be a CompoundTag representing the item stack. The output items are
         * stored under the key "OutputItems" and are expected to be a ListTag of CompoundTags representing the item stacks. The
         * processing timer is stored under the key "ProcessingTimer" and is expected to be an int. If any of these values are missing,
         * the default values are set: ItemStack.EMPTY for the processing item, an empty list for the output items, and -1 for the
         * processing timer.
         * 
         * @param provider The holder lookup provider for item and block references.
         * @param tag      The CompoundTag containing the serialized state of the processor.
         */
        public void deserialize(@NotNull final HolderLookup.Provider provider, @NotNull final CompoundTag tag)
        {
            if (tag.contains("ProcessingItem"))
            {
                CompoundTag itemTag = tag.getCompound("ProcessingItem");
                this.processingItem = ItemStack.parseOptional(NullnessBridge.assumeNonnull(provider), NullnessBridge.assumeNonnull(itemTag));
            }
            else
            {
                this.processingItem = ItemStack.EMPTY;
            }

            if (tag.contains("OutputItems"))
            {
                ListTag outputTag = tag.getList("OutputItems", Tag.TAG_COMPOUND);
                this.output = new ArrayList<ItemStack>(outputTag.size());

                for (int i = 0; i < outputTag.size(); i++)
                {
                    CompoundTag itemTag = outputTag.getCompound(i);

                    if (itemTag == null) continue;

                    ItemStack stack = ItemStack.parseOptional(NullnessBridge.assumeNonnull(provider), itemTag);
                    if (!stack.isEmpty())
                    {
                        this.output.add(stack);
                    }
                }
            }

            this.processingTimer = tag.getInt("ProcessingTimer");
            this.processingTimerComplete = tag.getInt("ProcessingTimerComplete");
        }

        /**
         * Returns true if the processing timer has exceeded the processing timer complete value, indicating that the recycling
         * processor has finished its current task.
         * 
         * @return true if the recycling processor has finished its current task, false otherwise.
         */
        public boolean isFinished()
        {
            return processingTimer > processingTimerComplete;
        }

        /**
         * Returns a string representation of the recycling processor, including the item to be recycled, the number of ticks remaining
         * in the processing timer, and the items produced by the processor. The string is in the format
         * "RecyclingProcessor{processingItem=ItemStack, processingTimer=int, output=List<ItemStack>}".
         * 
         * @return the string representation of the recycling processor.
         */
        @Override
        public String toString()
        {
            return "RecyclingProcessor {" + "processingItem=" +
                processingItem +
                ", processingTimer=" +
                processingTimer +
                ", processingTimerComplete=" +
                processingTimerComplete +
                ", output=" +
                output +
                '}';
        }
    }

    /**
     * Adds a recycling processor to the list of processors associated with this building.
     * 
     * @param itemToRecycle the item to be recycled.
     * @param worker        the citizen data associated with the worker who is recycling the item. or null if this is intended to
     *                      simulate flawless recycling.
     * @return true if the processor was added, false if the item cannot be recycled.
     */
    public boolean addRecyclingProcess(@Nonnull ItemStack itemToRecycle, int workerSkill)
    {
        List<ItemStack> recyclingOutput = outputList(itemToRecycle, workerSkill);

        if (recyclingOutput != null && !recyclingOutput.isEmpty())
        {
            RecyclingProcessor processor = new RecyclingProcessor();
            processor.processingItem = itemToRecycle;
            processor.processingTimer = 0;
            processor.processingTimerComplete = MCTPConfig.baseRecyclerTime.get();
            processor.output = recyclingOutput;
            recyclingProcessors.add(processor);

            markDirty();

            return true;
        }
        else
        {
            TraceUtils.dynamicTrace(TRACE_RECYCLING,
                () -> LOGGER.info("No recycling processor added for for item {}.", itemToRecycle));
        }

        return false;
    }

    /**
     * Removes the given recycling processor from the list of processors associated with this building and marks the building as dirty.
     * This is intended to be used when the processor has finished its task and should be removed from the list of in-progress
     * recycling operations.
     * 
     * @param processor the recycling processor to remove.
     */
    public void removeRecyclingProcess(RecyclingProcessor processor)
    {
        recyclingProcessors.remove(processor);
        markDirty();
    }

    /**
     * Checks if the given item can be recycled by this building. This is done by checking if the output list for the given item is not
     * empty. If the output list is not empty, then the item can be recycled.
     * 
     * @param itemToRecycle the item to check for recyclability
     * @return true if a full stack of this item can be recycled, false otherwise
     */
    public boolean isRecyclable(ItemStack itemToRecycle)
    {
        ItemStack hypotheticalStack = itemToRecycle.copy();
        hypotheticalStack.setCount(hypotheticalStack.getMaxStackSize());

        List<ItemStack> recyclingOutput = outputList(itemToRecycle, -1);
        return recyclingOutput != null && !recyclingOutput.isEmpty();
    }

    /**
     * Retrieves the set of recycling processors associated with this building. Each recycling processor represents an individual
     * recycling operation currently in progress within the building.
     *
     * @return a set containing the recycling processors.
     */

    public Set<RecyclingProcessor> getRecyclingProcessors()
    {
        return recyclingProcessors;
    }

    /**
     * Calculates the machine capacity of the recycling building. The capacity is determined by the building's level and is twice the
     * building's level.
     *
     * @return the machine capacity based on the building's level.
     */

    public int getMachineCapacity()
    {
        return getBuildingLevel() * 2;
    }

    /**
     * Checks if the building has processing capacity available to start a new recycling process. This is determined by comparing the
     * number of recycling processors currently in use to the machine capacity of the building.
     *
     * @return true if the building has processing capacity available, false otherwise.
     */
    public boolean hasProcessingCapacity()
    {
        return getRecyclingProcessors().size() < getMachineCapacity();
    }

    /**
     * Triggers a visual effect (enchant particles) and sound (cash register) at the given position. Used to simulate the AI selling an
     * item from a display stand.
     * 
     * @param pos the position of the effect
     */
    public void triggerEffect(BlockPos pos, SoundEvent sound, SimpleParticleType particleType, int chanceOfSound)
    {
        if (pos == null)
        {
            return;
        }
        ServerLevel level = (ServerLevel) getColony().getWorld();

        if (level == null)
        {
            return;
        }

        if (particleType != null)
        {
            level.sendParticles(particleType, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 20, 0.5, 0.5, 0.5, 0.1);
        }

        if (sound != null)
        {
            SoundUtils.playSoundWithChance(level, null, pos, sound, SoundSource.NEUTRAL, chanceOfSound, 0.8f, 1.0f);
        }
    }

    /**
     * Called every tick that the colony updates. This method is responsible for processing each recycling processor, checking if the
     * processor has finished its task, and if so, generating the output for that processor and removing it from the list of
     * in-progress recycling operations. Additionally, this method refreshes the list of items stored in all warehouses within the
     * colony.
     * 
     * @param colony the colony that this building is a part of
     */
    @Override
    public void onColonyTick(IColony colony)
    {
        super.onColonyTick(colony);

        for (RecyclingProcessor processor : recyclingProcessors)
        {
            TraceUtils.dynamicTrace(TRACE_RECYCLING, () -> LOGGER.info("Processor working: {}.", processor));

            processor.processingTimer++;

            if (processor.isFinished())
            {
                generateOutput(processor.output);
                removeRecyclingProcess(processor);

                AdvancementUtils.TriggerAdvancementPlayersForColony(this.getColony(),
                    player -> {
                        if (player != null) 
                        {
                            MCTPAdvancementTriggers.RECYCLE_ITEM.get().trigger(player);
                        }
                    });
            }
        }

        // Refresh the list of items stored in all warehouses within the colony.
        if (wareHouseCooldownCounter <= 0)
        {
            refreshItemList();
            wareHouseCooldownCounter = WAREHOUSE_INVENTORY_COOLDOWN;
        }
        else
        {
            wareHouseCooldownCounter--;
        }
    }

    /**
     * Generates the output for a given input item stack. This method takes the input stack and converts it into a list of output
     * stacks using the associated recipe. Each output stack is then inserted into an output chest using the tryInsertIntoOutputChest
     * method.
     *
     * @param stack the input item stack to process
     */
    public void generateOutput(List<ItemStack> output)
    {
        if (output == null || output.isEmpty())
        {
            return;
        }

        for (ItemStack itemStack : output)
        {
            if (!itemStack.isEmpty())
            {
                ItemStack outputCopy = itemStack.copy();

                StatsUtil.trackStatByName(this, ITEMS_RECOVERED, outputCopy.getHoverName(), outputCopy.getCount());

                tryInsertIntoOutputChest(outputCopy);
            }
        }

        markDirty();
    }

    /**
     * Prioritizes a list of recipes by selecting the first non-Architect's cutter recipe. If all recipes are of type Architect's
     * cutter, the first one is selected.
     *
     * @param recipes the list of recipes to prioritize
     * @return the selected recipe, or null if the list is empty or null
     */
    protected Recipe<?> prioritizeRecipeList(List<RecipeHolder<?>> recipes)
    {
        Recipe<?> selectedRecipe = null;

        if (recipes == null || recipes.isEmpty())
        {
            return null;
        }

        // Take non-Architect's cutter recipes first.
        for (RecipeHolder<?> recipe : recipes)
        {
            if (recipe.value().getType() == ModRecipeTypes.ARCHITECTS_CUTTER.get() && selectedRecipe == null)
            {
                selectedRecipe = recipe.value();
            }
            else
            {
                selectedRecipe = recipe.value();
                return selectedRecipe;
            }
        }

        return selectedRecipe;
    }

    /**
     * Modifies the list of ingredients for a given recipe to accommodate for the Architect's Cutter recipe type. If the recipe is of
     * type Architect's Cutter, it will return a list of ingredients that includes all the blocks in the tag associated with the
     * recipe. If the recipe is not of type Architect's Cutter, it will simply return the list of ingredients from the recipe.
     *
     * @param recipe the recipe to modify the ingredients for
     * @return the modified list of ingredients
     */
    protected List<Ingredient> determineIngredients(Recipe<?> recipe, ItemStack inputStack)
    {
        if (recipe.getType() == ModRecipeTypes.ARCHITECTS_CUTTER.get())
        {
            List<ItemStack> textures =
                DomumOrnamentumHelper.getDomumOrnamentumTextureComponents(inputStack, getColony().getWorld().registryAccess());

            // Let's turn this into a list of ingredients
            final List<Ingredient> ingredientlist = new ArrayList<>();
            for (final ItemStack ingredientStack : textures)
            {
                ingredientlist.add(Ingredient.of(ingredientStack));
            }

            return ingredientlist;
        }
        else
        {
            return recipe.getIngredients();
        }
    }

    /**
     * Attempts to find a DeconstructionRecipe for the given ItemStack in the given Level.
     * 
     * @param stack the ItemStack to search for a DeconstructionRecipe
     * @param level the Level to search for a DeconstructionRecipe
     * @return an Optional containing the DeconstructionRecipe if found, or an empty Optional if no matching recipe was found
     */
    private Optional<RecipeHolder<DeconstructionRecipe>> findDeconstructionRecipe(@Nonnull ItemStack stack, Level level) 
    {
        RecipeManager manager = level.getRecipeManager();
        SingleRecipeInput input   = new SingleRecipeInput(stack);

        // Let RecipeManager find the first matching recipe of your custom type
        RecipeType<DeconstructionRecipe> decon = MCTradePostMod.DECON_RECIPE_TYPE.get();

        if (decon == null) 
        {
            return Optional.empty();
        }

        return manager.getRecipeFor(decon, input, level);
    }


    /**
     * Returns a list of items that can be deconstructed from the given inputStack, taking into account the worker's skill level and the
     * item's damage value. This method will attempt to find a recipe which matches the input item, and if found, will calculate the
     * output items based on the recipe's ingredients and the input stack's count. The output items will be scaled down based on the
     * recycling efficiency and the item's damage value.
     * 
     * @param inputStack the item stack to deconstruct
     * @param workerSkill the skill level of the worker (or -1 for flawless recycling)
     * @return a list of items that can be deconstructed from the given inputStack, or null if no matching recipe is found.
     */
    protected @Nullable Tuple<Object2IntOpenHashMap<ItemStorage>, ItemStack> outputsFromReverseEngineeredRecipe(ItemStack inputStack)
    {
        Recipe<?> recipe = null;

        Level level = this.getColony().getWorld();

        if (level == null || level.getRecipeManager() == null)
        {
            return null;
        }
        
        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<?>> recipes =
            ItemValueRegistry.getRecipeListForItem(recipeManager, inputStack.getItem(), level);

        if (recipes == null || recipes.isEmpty())
        {
            TraceUtils.dynamicTrace(TRACE_RECYCLING_RECIPE, () -> LOGGER.info("No recipes found for item {}.", inputStack));
            return null;
        }

        TraceUtils.dynamicTrace(TRACE_RECYCLING_RECIPE,
            () -> LOGGER.info("Found {} recipes for item {}.", recipes.size(), inputStack));

        recipe = prioritizeRecipeList(recipes);

        Object2IntOpenHashMap<ItemStorage> outputItems = new Object2IntOpenHashMap<>();

        ItemStack resultStack = recipe.getResultItem(NullnessBridge.assumeNonnull(level.registryAccess()));

        if (resultStack.isEmpty())
        {
            return null;
        }

        List<ItemStack> remainingItems = MCTPInventoryUtils.calculateSecondaryOutputs(recipe, level);
        List<Ingredient> ingredients = determineIngredients(recipe, inputStack);

        final Recipe<?> selectedRecipeForLogging = recipe;
        TraceUtils.dynamicTrace(TRACE_RECYCLING_RECIPE,
            () -> LOGGER.info("Recipe for {} has {} ingredients and {} remaining items.",
                inputStack,
                selectedRecipeForLogging.getIngredients().size(),
                remainingItems.size()));

        for (Ingredient ingredient : ingredients)
        {
            if (ingredient.isEmpty() || ingredient.getItems().length == 0)
            {
                continue;
            }
            else
            {
                ItemStack[] ingredientItems = ingredient.getItems();
                boolean exclude = false;

                ItemStack itemStack = ingredientItems[0]; // This will return all possible variations for a given slot. We want only
                                                          // the first.

                // MCTradePostMod.LOGGER.info("Checking ingredient {} for extraction.", itemStack);

                for (ItemStack remainingItem : remainingItems)
                {
                    if (remainingItem.isEmpty() || itemStack.isEmpty()) continue;

                    if (ItemStack.isSameItem(itemStack, remainingItem))
                    {
                        // Skip ingredients which are also part of the output (not consumed by the recipe).
                        exclude = true;
                    }
                }

                if (!itemStack.isEmpty() && itemStack.getCount() > 0 && !exclude)
                {
                    // MCTradePostMod.LOGGER.info("Including {} in the output.", itemStack);
                    ItemStack outputCopy = itemStack.copy();
                    outputItems.addTo(new ItemStorage(outputCopy), outputCopy.getCount());
                }
                else
                {
                    TraceUtils.dynamicTrace(TRACE_RECYCLING_RECIPE, () -> LOGGER.info("Excluding {} from the output.", itemStack));
                }
            }
        }

        return new Tuple<Object2IntOpenHashMap<ItemStorage>, ItemStack>(outputItems, resultStack);
    }

    /**
     * Given a deconstruction recipe and a level, returns a map of items to their respective counts that would be output by the recipe.
     * This method takes into account the chance of each output item being dropped.
     *
     * @param level the level to use when generating random numbers
     * @param recipe the deconstruction recipe to process
     * @return a map of items to their respective counts that would be output by the recipe
     */
    protected Object2IntOpenHashMap<ItemStorage> outputsFromDeconstructionRecipe(Level level, DeconstructionRecipe recipe)
    {
        Object2IntOpenHashMap<ItemStorage> outputItems = new Object2IntOpenHashMap<>();

        // Note that worker skill results are later applied ON TOP of chance results in the recipe
        for (Output e : recipe.getOutputs()) 
        {
            if (level.random.nextFloat() <= e.chance()) 
            {
                ItemStack copy = e.stack().copy();
                
                if (!copy.isEmpty()) 
                {
                    outputItems.addTo(new ItemStorage(copy), copy.getCount());
                }
            }
        }

        return outputItems;
    }

    /**
     * Calculates the recycling efficiency based on the worker's skill level.
     * This method calculates a value between 0.5 and 1.0, where 0.5 is the baseline efficiency
     * and 1.0 is the maximum efficiency.
     * 
     * If the worker's skill level is less than 0, this method will return 1.0.
     * If the worker's skill level is less than or equal to PENALTY_RECYCLING_STAT_LEVEL, this method will
     * return the baseline efficiency minus a value proportional to the difference between the
     * worker's skill level and PENALTY_RECYCLING_STAT_LEVEL.
     * If the worker's skill level is greater than PENALTY_RECYCLING_STAT_LEVEL, this method will
     * return the baseline efficiency plus a value proportional to the difference between the
     * worker's skill level and FLAWLESS_RECYCLING_STAT_LEVEL, capped at 1.0.
     * 
     * @param workerSkill the skill level of the worker
     * @return the recycling efficiency based on the worker's skill level
     */
    protected double calculateRecyclingEfficiency(int workerSkill)
    {
        double baseline = 0.5;

        if (workerSkill < 0)
        {
            return 1.0;
        }
        else
        {
            if (workerSkill <= PENALTY_RECYCLING_STAT_LEVEL)
            {
                return baseline - (0.05 * (PENALTY_RECYCLING_STAT_LEVEL - workerSkill));
            }
            else
            {
                return Math.min(1.0, baseline + (.5 * ((double) workerSkill / (double) FLAWLESS_RECYCLING_STAT_LEVEL)));
            }
        }
    }

    /**
     * Given an input item stack, return a list of output stacks using the associated deconstruction recipe. This method takes the
     * input stack and converts it into a list of output stacks using the associated recipe. Each output stack is then inserted into an
     * output chest using the tryInsertIntoOutputChest method.
     *
     * @param stack       the input item stack to process
     * @param workerSkill the skill level of the worker (or -1 for flawless recycling)
     * @return a list of output item stacks
     */
    public List<ItemStack> outputList(@Nonnull ItemStack inputStack, int workerSkill)
    {
        Object2IntOpenHashMap<ItemStorage> candidateMaterialsOutput = null;
        Tuple<Object2IntOpenHashMap<ItemStorage>, ItemStack> outputResult = null;
        List<ItemStack> actualOutput = new ArrayList<ItemStack>();

        // How many of a given item would normally be created by the selected recipe?
        ItemStack referenceResultStack = null;

        if (getColony() == null || getColony().getWorld() == null || getColony().getWorld().getRecipeManager() == null)
        {
            return null;
        }

        Level level = getColony().getWorld();

        if (level == null || level.getRecipeManager() == null)
        {
            return null;
        }

        Optional<RecipeHolder<DeconstructionRecipe>> deconRecipe = findDeconstructionRecipe(inputStack, level);

        // For anything specified by a deconstruction recipe, use that without searching further.
        if (deconRecipe.isPresent())
        {
            final Recipe<?> selectedRecipeForLogging = deconRecipe.get().value();
            TraceUtils.dynamicTrace(TRACE_RECYCLING_RECIPE,
                () -> LOGGER.info("Found deconstruction recipe {} for item {} (max damage {}).", selectedRecipeForLogging, inputStack, inputStack.getMaxDamage()));
            candidateMaterialsOutput = outputsFromDeconstructionRecipe(level, deconRecipe.get().value());

            // Deconstruction recipes by definition apply to only a single item being deconstructed, so a 1:1 reference result is correct.
            referenceResultStack = inputStack.copy();
        }
        else
        {
            outputResult = outputsFromReverseEngineeredRecipe(inputStack);

            if (outputResult == null)
            {
                return null;
            }

            candidateMaterialsOutput = outputResult.getA();
            referenceResultStack = outputResult.getB();
        }

        if (candidateMaterialsOutput == null)
        {
            TraceUtils.dynamicTrace(TRACE_RECYCLING_RECIPE, () -> LOGGER.info("No candidate outpout materials found for item {}.", inputStack));
            return null;
        }

        for (ItemStorage item : candidateMaterialsOutput.keySet())
        {
            int total = candidateMaterialsOutput.getInt(item);
            ItemStack baseStack = item.getItemStack().copy();

            // Scale the output count based on the number of items in the input stack
            int recipeProductCount = referenceResultStack.getCount() > 0 ? referenceResultStack.getCount() : 1;
            double recyclingEfficiency = calculateRecyclingEfficiency(workerSkill);
            double damageFactor =
                inputStack.getMaxDamage() > 0 ? inputStack.getDamageValue() / (double) inputStack.getMaxDamage() : 1.0;
            int recyclingOutputCount = (int) ((double) total * ((double) inputStack.getCount() / (double) recipeProductCount));

            // For items that don't take damage, scale down the output count only by the recyclingEfficiency.
            if (inputStack.getMaxDamage() <= 0)
            {
                recyclingOutputCount *= recyclingEfficiency;
            }

            int maxStackSize = baseStack.getMaxStackSize();

            /*
            final double recyclingOutputEfficiencyForLogging = recyclingEfficiency;
            TraceUtils.dynamicTrace(TRACE_RECYCLING_RECIPE, () -> LOGGER.info("Analyzing candidate output materials {} found for item {}. Damage factor: {}, recycling efficiency: {}, recycling output count: {}, max stack size: {}."
                , baseStack, inputStack, damageFactor, recyclingEfficiency, recyclingOutputEfficiencyForLogging, maxStackSize));
            */
            
            while (recyclingOutputCount > 0)
            {
                int thisStackCount = Math.min(maxStackSize, recyclingOutputCount);
                ItemStack stackPart = baseStack.copy();

                /**
                 * For items that can be damaged, and are damaged, give a random chance based on level of damage and recycling
                 * efficiency of being able to deconstruct each ingredient. Note this may result in an item being reported as
                 * non-recyclable on one attempt, but recyclable on a later attempt.
                 */
                if (inputStack.getMaxDamage() > 0 && damageFactor < 1.0)
                {
                    int successes = 0;
                    for (int i = 0; i < thisStackCount; i++)
                    {
                        if (level.random.nextDouble() < ((1 - damageFactor) * recyclingEfficiency))
                        {
                            successes++;
                        }
                    }
                    stackPart.setCount(successes);
                }
                else
                {
                    stackPart.setCount(thisStackCount);
                }

                if (!stackPart.isEmpty())
                {
                    actualOutput.add(stackPart);
                }

                recyclingOutputCount -= thisStackCount;
            }
        }

        if (inputStack.isEnchanted())
        {
            double disenchantmentStrength = this.getColony()
                .getResearchManager()
                .getResearchEffects()
                .getEffectStrength(MCTPResearchConstants.RESEARCH_DISENCHANTING);
            double roll = level.random.nextDouble();
            if (disenchantmentStrength > 0.0 && roll < disenchantmentStrength)
            {
                TraceUtils.dynamicTrace(TRACE_RECYCLING,
                    () -> LOGGER.info("Stripping enchantments from {}, with a chance of {} and a roll of {}.",
                        inputStack,
                        disenchantmentStrength,
                        roll));

                // ItemStack enchantments = MCTPInventoryUtils.extractEnchantmentsToBook(inputStack.copy());
                List<ItemStack> enchantments = MCTPInventoryUtils.extractEnchantmentsToBooks(inputStack.copy());

                if (!enchantments.isEmpty())
                {
                    actualOutput.addAll(enchantments);
                }
            }
            else
            {
                TraceUtils.dynamicTrace(TRACE_RECYCLING,
                    () -> LOGGER.info("The {} cannot be disenchanted, with a chance of {} and a roll of {}.",
                        inputStack,
                        disenchantmentStrength,
                        roll));
            }
        }
        return actualOutput;
    }

    /**
     * Attempts to insert the given item stack into the first available output chest found in the output positions. If the item stack
     * is successfully inserted, this method returns true. If the item stack cannot be inserted and there is no output chest, a message
     * is sent to all players in the colony indicating that there is no output chest. If the item stack cannot be inserted and there is
     * an output chest, the remaining item stack is dropped in the world at the position of the last output chest that was found.
     * 
     * @param stack the item stack to be inserted into the output chest.
     * @return true if the item stack was successfully inserted, false otherwise.
     */
    private boolean tryInsertIntoOutputChest(@Nonnull ItemStack stack)
    {
        IItemHandler handler = null;
        BlockPos lastPos = null;
        ItemStack remaining = stack;

        for (BlockPos pos : identifyOutputPositions())
        {
            if (pos == null || pos.equals(BlockPos.ZERO)) continue;

            IItemHandlerCapProvider itemHandlerOpt = IItemHandlerCapProvider.wrap(this.getColony().getWorld().getBlockEntity(pos));

            if (itemHandlerOpt != null)
            {
                handler = itemHandlerOpt.getItemHandlerCap();
            }

            if (handler == null) continue;

            for (int slot = 0; slot < handler.getSlots(); slot++)
            {
                remaining = handler.insertItem(slot, remaining, false);
                triggerEffect(pos, SoundEvents.AMETHYST_BLOCK_CHIME, ParticleTypes.POOF, slot);
                if (remaining.isEmpty()) return true;
            }

            lastPos = pos;
        }

        if (lastPos == null)
        {
            MessageUtils.format(RECYCLER_NO_OUTPUT_BOX).sendTo(this.getColony()).forAllPlayers();
            return false;
        }

        // Drop whatever is remaining in the world.
        InventoryUtils.spawnItemStack(this.getColony().getWorld(), lastPos.getX(), lastPos.getY(), lastPos.getZ(), remaining);

        return false;
    }

    /**
     * Retrieves the list of items that are pending for recycling in the building.
     * 
     * @return a list of ItemStorage objects representing the items awaiting processing.
     */
    public List<ItemStorage> getPendingRecyclingQueue()
    {
        List<ItemStorage> pendingRecyclingQueue =
            getModule(RecyclingItemListModule.class, m -> m.getId().equals(EntityAIWorkRecyclingEngineer.RECYCLING_LIST))
                .getPendingRecyclingQueue();
        return pendingRecyclingQueue;
    }

    /**
     * Refreshes the list of items stored in all warehouses within the colony. This method retrieves the registered structure manager
     * for the colony and obtains a list of all warehouse locations. For each warehouse, it calculates the item storage contents and
     * aggregates them into a map, which is then used to update the function that provides the set of all items stored in the
     * building's warehouse.
     */
    public void refreshItemList()
    {
        allItems.clear();

        if (getColony() == null || getColony().getBuildingManager() == null)
        {
            LOGGER.warn("Recycling Center: Colony or Building Manager is null while attempting to refresh the recyclable list.");
            return;
        }

        IRegisteredStructureManager buildingManager = getColony().getBuildingManager();
        List<IWareHouse> warehouses = buildingManager.getWareHouses();
        final List<ItemStorage> pendingRecyclingQueue = getPendingRecyclingQueue();

        for (IWareHouse wh : warehouses)
        {
            Object2IntMap<ItemStorage> whItems = null;

            BlockPos whPos = wh.getPosition();

            if (whPos != null)
            {
                TraceUtils.dynamicTrace(TRACE_RECYCLING_RECIPE, () -> LOGGER.info("Analyzing inventory of warehouse at: {}.", whPos));

                IBuilding warehouse = IColonyManager.getInstance().getBuilding(getColony().getWorld(), whPos);
                whItems = MCTPInventoryUtils.contentsForBuilding(warehouse);
                for (final Entry<ItemStorage> entry : whItems.object2IntEntrySet())
                {
                    if (!pendingRecyclingQueue.contains(entry.getKey()))
                    {
                        allItems.addTo(entry.getKey(), entry.getIntValue());
                    }
                }
            }
        }

        markDirty();
    }

    /**
     * Records whether the given item is recyclable or not, and returns the modified item stack. If the stack is not recyclable, the
     * item stack is not modified. If the stack is recyclable, a RecyclableRecord component is added to the stack with isRecyclable set
     * to true.
     * 
     * @param stackToCheck the item stack to check and modify.
     * @return the modified item stack.
     */
    public ItemStack recordRecyclability(ItemStack stackToCheck)
    {
        RecyclableRecord recyclableRecord = RecyclableRecord.fromStack(stackToCheck);

        if (recyclableRecord == null)
        {
            if (isRecyclable(stackToCheck))
            {
                recyclableRecord = new RecyclableRecord(true);
            }
            else
            {
                recyclableRecord = new RecyclableRecord(false);
            }

            stackToCheck.set(NullnessBridge.assumeNonnull(MCTPModDataComponents.RECYCLABLE_COMPONENT), recyclableRecord);
        }

        return stackToCheck;
    }
    
    /**
     * Returns true if this building can sort items in its inventory.
     * 
     * @return true if the building can sort items, false otherwise.
     */
    @Override 
    public boolean canSort()
    {
        return true;
    }
}
