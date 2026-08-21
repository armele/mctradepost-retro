package com.deathfrog.mctradepost.core.colony.buildings.modules;

import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.blocks.StewpotBlock;
import com.deathfrog.mctradepost.core.ModTags;
import com.ldtteam.structurize.api.BlockPosUtil;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.Utils;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.api.util.constant.NbtTagConstants;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingKitchen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

public class StewmelierIngredientModule extends AbstractBuildingModule implements IPersistentModule, ITickingModule
{
    /**
     * NBT tags for the ingredient list.
     */
    private static final String TAG_INGREDIENTS = "ingredients";
    private static final String TAG_PROTECTED_QUANTITY = "protectedQuantity";
    private static final String TAG_STEWPOT_LOCATION = "stewpotLocation";
    private static final String TAG_STEW_QUANTITY = "stewQuantity";
    private static final String TAG_SEASONING_LEVEL = "seasoningLevel";
    private static final String TAG_DESIRED_STEW_TIER = "desiredStewTier";
    private static final String TAG_ACTUAL_STEW_TIER = "actualStewTier";
    private static final String TAG_CREDITED_INGREDIENTS = "creditedIngredients";

    // Number of bowls a single seasoning item supports.
    private static final float STEW_SEASONING_LEVEL = 4.0f;

    public static final int STEW_EMPTY = 0;
    public static final int STEW_LEVEL_1 = 25;
    public static final int STEW_LEVEL_2 = 50;

    protected BlockPos stewpotLocation = BlockPos.ZERO;
    protected float unseasonedQuantity = 0.0f;
    protected float stewQuantity = 0.0f;
    protected int seasoningLevel = 0;
    protected StewTier desiredStewTier = StewTier.BASIC;
    protected StewTier actualStewTier = StewTier.BASIC;
    protected final Set<ResourceLocation> creditedIngredients = new HashSet<>();
    
    protected Set<ItemStorage> ingredientSet = new HashSet<>();

    public StewmelierIngredientModule() 
    {
        // Constructor implementation
    }

    public Set<ItemStorage> getIngredients() 
    {
        return ingredientSet;
    }

    public int ingredientCount() 
    {
        return ingredientSet.size();
    }

    public BlockPos getStewpotLocation() 
    {
        return stewpotLocation;
    }

    public void setStewpotLocation(BlockPos location) 
    {
        stewpotLocation = location;
        markDirty();
    }

    /**
     * Periodically invalidates stale stewpot claims independently of worker AI activity.
     * Unloaded positions are ignored to avoid loading chunks solely for validation.
     *
     * @param colony colony invoking the slow building-module tick
     */
    @Override
    public void onColonyTick(@NotNull final IColony colony)
    {
        if (stewpotLocation == null || BlockPos.ZERO.equals(stewpotLocation)) return;
        final Level level = colony.getWorld();
        if (level == null || !WorldUtil.isBlockLoaded(level, stewpotLocation)) return;

        if (!isValidStewpot(level, stewpotLocation) || !isPreferredOwner(building, stewpotLocation))
        {
            setStewpotLocation(BlockPos.ZERO);
        }
    }

    /**
     * Checks the complete physical stewpot structure used by the Stewmelier.
     *
     * @param level world containing the pot
     * @param pos claimed cauldron position
     * @return true when the position contains an accepted cauldron above a lit campfire
     */
    @SuppressWarnings("null")
    public static boolean isValidStewpot(final Level level, final BlockPos pos)
    {
        if (level == null || pos == null) return false;
        final Block block = level.getBlockState(pos).getBlock();
        final boolean validCauldron = block == Blocks.CAULDRON
            || block == MCTradePostMod.STEWPOT_FILLED.get()
            || block instanceof CauldronBlock;
        if (!validCauldron) return false;

        final BlockState fireState = level.getBlockState(pos.below());
        return fireState.is(Blocks.CAMPFIRE) && fireState.getValue(CampfireBlock.LIT);
    }

    public float getStewQuantityFractional() 
    {
        return stewQuantity;
    }

    public int getStewQuantityBowlsWorth() 
    {
        return (int) stewQuantity;
    }

    public void setStewQuantity(float quantity) 
    {
        float oldQuantity = stewQuantity;
        stewQuantity = quantity;

        useSeasoning(stewQuantity - oldQuantity);
        resetPotCreditsIfDrained(oldQuantity);

        markDirty();
    }

    public void addStew(float adjustBy) 
    {
        final float oldQuantity = stewQuantity;
        useSeasoning(adjustBy);
        stewQuantity += adjustBy;
        resetPotCreditsIfDrained(oldQuantity);
        markDirty();
    }

    /**
     * Clears qualification credits when a usable pot is drained below one bowl.
     *
     * @param oldQuantity quantity before the latest adjustment
     */
    private void resetPotCreditsIfDrained(final float oldQuantity)
    {
        if (oldQuantity >= 1.0f && stewQuantity < 1.0f)
        {
            creditedIngredients.clear();
            actualStewTier = StewTier.BASIC;
        }
    }

    /** @return the player-selected production tier. */
    public StewTier getDesiredStewTier() { return desiredStewTier; }

    /** @return the tier currently represented by the pot contents. */
    public StewTier getActualStewTier() { return actualStewTier; }

    /** @return the number of distinct ingredients credited to the current pot. */
    public int getCreditedIngredientCount() { return creditedIngredients.size(); }

    /**
     * Counts distinct credited ingredients that currently belong to the protein tag.
     *
     * @return number of distinct credited protein ingredients
     */
    public int getCreditedProteinIngredientCount()
    {
        int proteinCount = 0;
        for (ResourceLocation ingredientId : creditedIngredients)
        {
            final Item item = BuiltInRegistries.ITEM.getOptional(ingredientId).orElse(null);
            if (item != null && new ItemStack(item).is(ModTags.ITEMS.PROTEIN_TAG))
            {
                proteinCount++;
            }
        }
        return proteinCount;
    }

    /**
     * Determines whether the pot contents meet at least the Basic stew requirements.
     *
     * @return true when the accumulated contents may be portioned into bowls
     */
    public boolean isStewQualified()
    {
        return qualifiesForTier(StewTier.BASIC, building == null ? 1 : building.getBuildingLevel());
    }

    /**
     * Gets the whole number of servings that may currently be portioned.
     *
     * @return qualified whole servings, or zero while the pot contains unservable broth
     */
    public int getServableStewQuantityBowlsWorth()
    {
        return isStewQualified() ? getStewQuantityBowlsWorth() : 0;
    }

    /**
     * Checks whether an ingredient item is already represented in the current pot.
     *
     * @param stack ingredient to inspect
     * @return true when the ingredient has already been credited
     */
    @SuppressWarnings("null")
    public boolean isIngredientCredited(final ItemStack stack)
    {
        return stack != null && !stack.isEmpty() && creditedIngredients.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /**
     * Sets the requested tier after enforcing the current kitchen-level limit.
     *
     * @param requestedTier tier requested by the player
     */
    public void setDesiredStewTier(final StewTier requestedTier)
    {
        final int kitchenLevel = building == null ? 1 : building.getBuildingLevel();
        desiredStewTier = requestedTier.getMinimumKitchenLevel() <= kitchenLevel ? requestedTier : StewTier.maxForKitchenLevel(kitchenLevel);
        reconcileActualTier();
        markDirty();
    }

    /**
     * Credits a successfully consumed ingredient toward the current pot's qualifications.
     *
     * @param stack consumed ingredient
     */
    @SuppressWarnings("null")
    public void creditIngredient(final ItemStack stack)
    {
        if (stack == null || stack.isEmpty()) return;

        creditedIngredients.add(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        reconcileActualTier();
        markDirty();
    }

    /**
     * Recalculates desired and actual tiers from kitchen level and pot credits.
     *
     * @return true when either tier changed
     */
    public boolean reconcileActualTier()
    {
        final StewTier oldDesired = desiredStewTier;
        final StewTier oldActual = actualStewTier;
        final int kitchenLevel = building == null ? 1 : building.getBuildingLevel();
        final StewTier supportedDesired = desiredStewTier.getMinimumKitchenLevel() <= kitchenLevel
            ? desiredStewTier : StewTier.maxForKitchenLevel(kitchenLevel);

        if (supportedDesired.getLevel() < desiredStewTier.getLevel())
        {
            desiredStewTier = supportedDesired;
        }

        StewTier qualified = null;
        for (StewTier tier : StewTier.values())
        {
            if (qualifiesForTier(tier, kitchenLevel)) qualified = tier;
        }
        actualStewTier = qualified == null ? StewTier.BASIC
            : StewTier.fromLevel(Math.min(supportedDesired.getLevel(), qualified.getLevel()));
        return oldDesired != desiredStewTier || oldActual != actualStewTier;
    }

    /**
     * Tests the ingredient and building requirements for one stew tier.
     * Protein ingredients are a subset of, rather than additions to, the total distinct ingredients.
     *
     * @param tier tier whose requirements are tested
     * @param kitchenLevel current kitchen level
     * @return true when all requirements are satisfied
     */
    private boolean qualifiesForTier(final StewTier tier, final int kitchenLevel)
    {
        return kitchenLevel >= tier.getMinimumKitchenLevel()
            && getCreditedIngredientCount() >= tier.getRequiredDistinctIngredients()
            && getCreditedProteinIngredientCount() >= tier.getRequiredProteinIngredients();
    }

    /**
     * Adjusts the amount of stewing in the stewpot by the given amount.
     * If the amount of stewing exceeds the seasoning level, it will
     * decrement the seasoning level and subtract the seasoning level from
     * the amount of stewing.
     * 
     * @param stewAmount the amount of stewing to add
     */
    public void useSeasoning(float stewAmount)
    {
        if (stewAmount > 0)
        {
            unseasonedQuantity = unseasonedQuantity + stewAmount;

            if (unseasonedQuantity >= STEW_SEASONING_LEVEL)
            {
                seasoningLevel--;
                unseasonedQuantity -= STEW_SEASONING_LEVEL;
            }
        }

        markDirty();
    }

    public int getSeasoningLevel()
    {
        return seasoningLevel;
    }

    public void setSeasoningLevel(int level)
    {
        seasoningLevel = level;
        markDirty();
    }

    /**
     * Adds an ingredient to the list of ingredients in the module.
     * 
     * @param ingredient the ingredient to add.
     */
    public void addIngredient(ItemStorage ingredient) 
    {
        ingredientSet.add(ingredient);
        markDirty();
    }

    /**
     * Removes an ingredient from the list of ingredients in the module.
     * 
     * @param ingredient the ingredient to remove.
     */
    public void removeIngredient(ItemStorage ingredient) 
    {
        ingredientSet.remove(ingredient);
        markDirty();
    }


    /**
     * Marks the module as dirty and updates the stew level.
     * This method is called when the module's state changes in a way that should be reflected on the client.
     * It will call the parent class's markDirty method, then update the stew level based on the current stew quantity.
     * If the stew quantity is greater than 50, the stew level will be set to 3.
     * If the stew quantity is greater than 25, the stew level will be set to 2.
     * If the stew quantity is greater than 0, the stew level will be set to 1.
     * If the stew quantity is 0 or less, the stew level will be set to 0.
     */
    @Override
    public void markDirty()
    {
        super.markDirty();

        Level level = building.getColony().getWorld();
        BlockPos stewPos = stewpotLocation;

        if (level == null || level.isClientSide || stewPos == null) 
        {
            return;
        }
        
        int stewLevel = 0;

        if (stewQuantity > STEW_LEVEL_2)
        {
            stewLevel = 3;
        }
        else if (stewQuantity > STEW_LEVEL_1)
        {
            stewLevel = 2;
        }
        else if (stewQuantity > STEW_EMPTY)
        {
            stewLevel = 1;
        }

        setStewLevel(level, stewPos, stewLevel, actualStewTier.getLevel());
    }

    /**
     * Sets the level of a stewpot block at the given position in the given level.
     * If the block at the given position is not a StewpotBlock, this method does nothing.
     * 
     * @param level the level to modify.
     * @param pos the position of the block to modify.
     * @param newLevel the new level of the stewpot block.
     */
    @SuppressWarnings("null")
    public static void setStewLevel(
        final @Nonnull Level level,
        final @Nonnull BlockPos pos,
        final int newStewLevel,
        final int newStewTier)
    {
        BlockState state = level.getBlockState(pos);
        final int clamped = Mth.clamp(newStewLevel, 0, 3);

        // If target is empty, always end as a vanilla cauldron.
        if (clamped == 0)
        {
            if (!state.is(Blocks.CAULDRON))
            {
                // Only swap if currently our filled stewpot (or something else you choose to normalize).
                if (state.is(MCTradePostMod.STEWPOT_FILLED.get()) || state.getBlock() instanceof StewpotBlock)
                {
                    level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 2 | 16);
                }
            }
            return;
        }

        // clamped is 1..3 here.
        // Ensure we're using the filled stewpot block.
        if (state.is(Blocks.CAULDRON))
        {
            state = MCTradePostMod.STEWPOT_FILLED.get()
                .defaultBlockState()
                .setValue(StewpotBlock.LEVEL, clamped)
                .setValue(StewpotBlock.STEW_TIER, Mth.clamp(newStewTier, 1, 3));

            level.setBlock(pos, state, 2 | 16);
            return;
        }

        // If it's not our filled stewpot, do nothing (avoid mutating other cauldron-like blocks).
        if (!state.is(MCTradePostMod.STEWPOT_FILLED.get()) && !(state.getBlock() instanceof StewpotBlock))
        {
            return;
        }

        // Update existing filled stewpot level.
        final BlockState updated = state.setValue(StewpotBlock.LEVEL, clamped)
            .setValue(StewpotBlock.STEW_TIER, Mth.clamp(newStewTier, 1, 3));
        if (updated != state)
        {
            level.setBlock(pos, updated, 2 | 16);
        }
    }


    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        ingredientSet.clear();
        final ListTag ingredientTagList = compound.getList(TAG_INGREDIENTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < ingredientTagList.size(); i++)
        {
            final CompoundTag compoundNBT = ingredientTagList.getCompound(i);
            final CompoundTag ingredientItemTag = compoundNBT.getCompound(NbtTagConstants.STACK);

            if (ingredientItemTag.isEmpty())
            {
                continue;
            }

            int protectedQuantity = compoundNBT.getInt(TAG_PROTECTED_QUANTITY);
            ItemStorage tradeItem = new ItemStorage(ItemStack.parseOptional(NullnessBridge.assumeNonnull(provider), ingredientItemTag));
            tradeItem.setAmount(protectedQuantity);

            ingredientSet.add(tradeItem);
        }
        stewpotLocation = BlockPosUtil.readFromNBT(compound, TAG_STEWPOT_LOCATION);
        stewQuantity = compound.getFloat(TAG_STEW_QUANTITY);
        seasoningLevel = compound.getInt(TAG_SEASONING_LEVEL);
        desiredStewTier = StewTier.fromLevel(compound.contains(TAG_DESIRED_STEW_TIER) ? compound.getInt(TAG_DESIRED_STEW_TIER) : 1);
        actualStewTier = StewTier.fromLevel(compound.contains(TAG_ACTUAL_STEW_TIER) ? compound.getInt(TAG_ACTUAL_STEW_TIER) : 1);
        creditedIngredients.clear();
        final ListTag creditedTagList = compound.getList(TAG_CREDITED_INGREDIENTS, Tag.TAG_STRING);
        for (int i = 0; i < creditedTagList.size(); i++)
        {
            String tagStr = creditedTagList.getString(i);

            if (tagStr == null) continue;

            final ResourceLocation id = ResourceLocation.tryParse(tagStr);
            if (id != null) creditedIngredients.add(id);
        }
        if (stewQuantity < 1.0f)
        {
            creditedIngredients.clear();
            actualStewTier = StewTier.BASIC;
        }
        reconcileActualTier();
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
        @NotNull final ListTag ingredientTagList = new ListTag();
        for (ItemStorage ingredient : ingredientSet)
        {
            final CompoundTag compoundNBT = new CompoundTag();
            final Tag storedItem = ingredient.getItemStack().saveOptional(NullnessBridge.assumeNonnull(provider));
            compoundNBT.put(NbtTagConstants.STACK, NullnessBridge.assumeNonnull(storedItem));
            compoundNBT.putInt(TAG_PROTECTED_QUANTITY, ingredient.getAmount());
            ingredientTagList.add(compoundNBT);
        }
        BlockPosUtil.writeToNBT(compound, TAG_STEWPOT_LOCATION, stewpotLocation);
        compound.put(TAG_INGREDIENTS, ingredientTagList);
        compound.putFloat(TAG_STEW_QUANTITY, stewQuantity);
        compound.putInt(TAG_SEASONING_LEVEL, seasoningLevel);
        compound.putInt(TAG_DESIRED_STEW_TIER, desiredStewTier.getLevel());
        compound.putInt(TAG_ACTUAL_STEW_TIER, actualStewTier.getLevel());
        final ListTag creditedTagList = new ListTag();
        for (ResourceLocation id : creditedIngredients)
        {
            String tagStr = id.toString();

            if (tagStr == null) continue;

            creditedTagList.add(net.minecraft.nbt.StringTag.valueOf(tagStr));
        }
        compound.put(TAG_CREDITED_INGREDIENTS, creditedTagList);
    }

    /**
     * Serializes the trade list to the given RegistryFriendlyByteBuf for
     * transmission to the client. 
     *
     * @param buf the buffer to serialize the trade list to.
     */
    @SuppressWarnings("null")
    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf)
    {
        // Sort ingredients by display name (case-insensitive, locale-safe)
        final List<ItemStorage> sorted =
            ingredientSet.stream()
                .sorted(Comparator.comparing(
                    ingredient -> ingredient.getItemStack().getHoverName().getString(),
                    String.CASE_INSENSITIVE_ORDER
                ))
                .toList();

        buf.writeInt(sorted.size());
        final IWareHouse warehouse = stewpotLocation == null || BlockPos.ZERO.equals(stewpotLocation)
            ? null
            : building.getColony().getServerBuildingManager().getClosestWarehouseInColony(stewpotLocation);
        buf.writeBoolean(warehouse != null);
        for (ItemStorage ingredient : sorted)
        {
            final ItemStack ingredientStack = ingredient.getItemStack();

            if (ingredientStack == null) continue;

            Utils.serializeCodecMess(buf, ingredientStack);
            buf.writeInt(ingredient.getAmount());
            final int warehouseCount = warehouse == null ? 0 : InventoryUtils.getItemCountInProvider(warehouse,
                stack -> stack != null && ItemStack.isSameItem(stack, ingredientStack));
            final int protectedCount = ingredient.getAmount() * ingredientStack.getMaxStackSize();
            buf.writeInt(warehouseCount);
            buf.writeInt(Math.max(0, warehouseCount - protectedCount));
        }
        final Map<ResourceLocation, Integer> warehouseItemCounts = getWarehouseItemCounts(warehouse);
        buf.writeInt(warehouseItemCounts.size());
        for (Map.Entry<ResourceLocation, Integer> entry : warehouseItemCounts.entrySet())
        {
            buf.writeResourceLocation(entry.getKey());
            buf.writeInt(entry.getValue());
        }
        buf.writeInt(desiredStewTier.getLevel());
        buf.writeInt(actualStewTier.getLevel());
        buf.writeInt(getCreditedIngredientCount());
        buf.writeInt(getCreditedProteinIngredientCount());
        buf.writeBoolean(isStewQualified());
        buf.writeInt(building.getBuildingLevel());
        buf.writeInt(getStewQuantityBowlsWorth());

        boolean potIdentified = stewpotLocation != null && !BlockPos.ZERO.equals(stewpotLocation);
        buf.writeBoolean(potIdentified);

        buf.writeBlockPos(stewpotLocation == null ? BlockPos.ZERO : stewpotLocation);
    }

    /**
     * Aggregates raw item quantities from the warehouse used by the Stewmelier.
     * Counts are keyed by item identifier so matching component variants contribute
     * to the same selection-screen total.
     *
     * @param warehouse warehouse selected relative to the claimed stewpot
     * @return raw item counts, or an empty map when no warehouse is available
     */
    @SuppressWarnings("null")
    private Map<ResourceLocation, Integer> getWarehouseItemCounts(final IWareHouse warehouse)
    {
        final Map<ResourceLocation, Integer> counts = new HashMap<>();
        if (warehouse == null) return counts;

        for (int slot = 0; slot < warehouse.getItemHandlerCap().getSlots(); slot++)
        {
            final ItemStack stack = warehouse.getItemHandlerCap().getStackInSlot(slot);
            if (stack == null || stack.isEmpty() || !stack.is(ModTags.ITEMS.STEW_INGREDIENTS_TAG)) continue;
            counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount(), Integer::sum);
        }
        return counts;
    }


    /**
     * Finds the closest kitchen to the given cauldron position.
     *
     * @param level the level containing the cauldron.
     * @param cauldronPos the position of the cauldron.
     * @return the kitchen building if found, null otherwise.
     */
    public static StewmelierIngredientModule kitchenFromCauldronPosition(@Nonnull Level level, final @Nonnull BlockPos cauldronPos)
    {
        IColony colony = IColonyManager.getInstance().getClosestColony(level, cauldronPos);
        if (colony == null) return null;

        IBuilding preferredBuilding = null;
        StewmelierIngredientModule preferredModule = null;
        for (IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building instanceof BuildingKitchen)
            {
                StewmelierIngredientModule module = building.getModule(MCTPBuildingModules.STEWMELIER_INGREDIENTS);

                if (module != null && module.getStewpotLocation().equals(cauldronPos))
                {
                    if (preferredBuilding == null || compareStewpotOwners(building, preferredBuilding, cauldronPos) < 0)
                    {
                        preferredBuilding = building;
                        preferredModule = module;
                    }
                }
            }
        }

        return preferredModule;
    }

    /**
     * Checks whether another kitchen has already claimed a stewpot.
     *
     * @param claimant kitchen attempting to use the pot
     * @param cauldronPos candidate stewpot position
     * @return true when a different kitchen currently references the position
     */
    public static boolean isClaimedByOtherKitchen(final IBuilding claimant, final BlockPos cauldronPos)
    {
        for (IBuilding colonyBuilding : claimant.getColony().getServerBuildingManager().getBuildings().values())
        {
            if (colonyBuilding == claimant || !(colonyBuilding instanceof BuildingKitchen)
                || !colonyBuilding.hasModule(MCTPBuildingModules.STEWMELIER_INGREDIENTS))
            {
                continue;
            }

            final StewmelierIngredientModule module = colonyBuilding.getModule(MCTPBuildingModules.STEWMELIER_INGREDIENTS);
            if (module != null && cauldronPos.equals(module.getStewpotLocation())) return true;
        }
        return false;
    }

    /**
     * Atomically claims an unowned stewpot for a kitchen.
     *
     * @param claimant kitchen claiming the pot
     * @param cauldronPos candidate stewpot position
     * @return true when the claim was recorded
     */
    public static synchronized boolean tryClaimStewpot(final IBuilding claimant, final BlockPos cauldronPos)
    {
        if (isClaimedByOtherKitchen(claimant, cauldronPos)) return false;
        final StewmelierIngredientModule module = claimant.getModule(MCTPBuildingModules.STEWMELIER_INGREDIENTS);
        if (module == null) return false;
        module.setStewpotLocation(cauldronPos);
        return true;
    }

    /**
     * Resolves duplicate claims from older saves in favor of the closest kitchen.
     * Building positions provide a stable tie-breaker for equally distant kitchens.
     *
     * @param claimant kitchen whose existing claim is being checked
     * @param cauldronPos claimed stewpot position
     * @return true when the claimant is the preferred owner
     */
    public static boolean isPreferredOwner(final IBuilding claimant, final BlockPos cauldronPos)
    {
        for (IBuilding colonyBuilding : claimant.getColony().getServerBuildingManager().getBuildings().values())
        {
            if (colonyBuilding == claimant || !(colonyBuilding instanceof BuildingKitchen)
                || !colonyBuilding.hasModule(MCTPBuildingModules.STEWMELIER_INGREDIENTS))
            {
                continue;
            }
            final StewmelierIngredientModule module = colonyBuilding.getModule(MCTPBuildingModules.STEWMELIER_INGREDIENTS);
            if (module != null && cauldronPos.equals(module.getStewpotLocation())
                && compareStewpotOwners(colonyBuilding, claimant, cauldronPos) < 0)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares two stewpot claimants by distance and then stable building position.
     *
     * @param first first claimant
     * @param second second claimant
     * @param cauldronPos claimed stewpot
     * @return standard comparator result
     */
    private static int compareStewpotOwners(final IBuilding first, final IBuilding second, final @Nonnull BlockPos cauldronPos)
    {
        final int distanceComparison = Double.compare(first.getPosition().distSqr(cauldronPos), second.getPosition().distSqr(cauldronPos));
        return distanceComparison != 0 ? distanceComparison : Long.compare(first.getPosition().asLong(), second.getPosition().asLong());
    }
}
