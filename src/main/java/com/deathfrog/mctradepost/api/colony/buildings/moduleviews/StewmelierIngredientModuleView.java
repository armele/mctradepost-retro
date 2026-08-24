package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.client.gui.modules.WindowStewmolierIngredientModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.StewTier;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.Utils;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class StewmelierIngredientModuleView extends AbstractBuildingModuleView
{

    private final List<ItemStorage> ingredientList = new ArrayList<>();
    private final List<WarehouseAvailability> warehouseAvailability = new ArrayList<>();
    private final Map<ResourceLocation, Integer> warehouseItemCounts = new HashMap<>();
    private boolean warehouseAvailable;
    private int warehouseSnapshotVersion;
    private StewTier desiredTier = StewTier.BASIC;
    private StewTier actualTier = StewTier.BASIC;
    private int creditedIngredientCount;
    private int creditedProteinIngredientCount;
    private boolean stewQualified;
    private int kitchenLevel;
    private int stewServings;
    private boolean stewpotIdentified;
    private BlockPos stewpotLocation = BlockPos.ZERO;

    /**
     * Read this view from a {@link RegistryFriendlyByteBuf}.
     *
     * @param buf The buffer to read this view from.
     */
    @Override
    public void deserialize(@NotNull final RegistryFriendlyByteBuf buf)
    {
        ingredientList.clear();
        warehouseAvailability.clear();
        warehouseItemCounts.clear();
        final int size = buf.readInt();
        warehouseAvailable = buf.readBoolean();
        for (int i = 0; i < size; i++)
        {
            ItemStack itemStack = Utils.deserializeCodecMess(buf);
            int protectedQuantity = buf.readInt();
            ingredientList.add(new ItemStorage(itemStack, protectedQuantity));
            warehouseAvailability.add(new WarehouseAvailability(buf.readInt(), buf.readInt()));
        }
        final int warehouseItemTypeCount = buf.readInt();
        for (int i = 0; i < warehouseItemTypeCount; i++)
        {
            warehouseItemCounts.put(buf.readResourceLocation(), buf.readInt());
        }
        desiredTier = StewTier.fromLevel(buf.readInt());
        actualTier = StewTier.fromLevel(buf.readInt());
        creditedIngredientCount = buf.readInt();
        creditedProteinIngredientCount = buf.readInt();
        stewQualified = buf.readBoolean();
        kitchenLevel = buf.readInt();
        stewServings = buf.readInt();
        stewpotIdentified = buf.readBoolean();
        stewpotLocation = buf.readBlockPos();
        warehouseSnapshotVersion++;
    }

    /**
     * Gets the description of the module to display in the GUI.
     * 
     * @return The description of the module.
     */
    @Override
    public @Nullable Component getDesc()
    {
        return Component.translatable("com.minecolonies.coremod.gui.stewmelier.ingredients");
    }

    /**
     * Gets the window for this module.
     * 
     * @return The window for this module.
     */
    @Override
    public BOWindow getWindow()
    {
        return new WindowStewmolierIngredientModule(buildingView, this);
    }
    
    /**
     * Get the icon of the module.
     * 
     * @return the icon to show.
     */
    @Override
    public ResourceLocation getIconResourceLocation()
    {
        return ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/gui/modules/stew.png");
    }

    /**
     * Get the ingredient list.
     * 
     * @return the ingredient list.
     */
    public List<ItemStorage> getIngredients()
    {
        return ingredientList;
    }

    /** @return the tier selected by the player. */
    public StewTier getDesiredTier() { return desiredTier; }

    /** @param tier tier to display while the server update is pending. */
    public void setDesiredTier(final StewTier tier) { desiredTier = tier; }

    /** @return the tier currently represented by the pot contents. */
    public StewTier getActualTier() { return actualTier; }

    /** @return the number of distinct ingredients credited to the current pot. */
    public int getCreditedIngredientCount() { return creditedIngredientCount; }

    /** @return the number of distinct credited ingredients that count as proteins. */
    public int getCreditedProteinIngredientCount() { return creditedProteinIngredientCount; }

    /** @return whether the pot contents qualify as a servable stew tier. */
    public boolean isStewQualified() { return stewQualified; }

    /** @return the current kitchen building level. */
    public int getKitchenLevel() { return kitchenLevel; }

    /** @return the whole number of servings currently available in the stewpot. */
    public int getStewServings() { return stewServings; }

    /** @return whether this kitchen currently has a claimed stewpot position. */
    public boolean isStewpotIdentified() { return stewpotIdentified; }

    /** @return the synchronized claimed stewpot position, or {@link BlockPos#ZERO} when unset. */
    public BlockPos getStewpotLocation() { return stewpotLocation; }

    /** @return whether the server found the warehouse used by the Stewmelier AI. */
    public boolean isWarehouseAvailable() { return warehouseAvailable; }

    /**
     * Gets the synchronized warehouse availability for an ingredient row.
     *
     * @param index ingredient row index
     * @return count information for that ingredient
     */
    public WarehouseAvailability getWarehouseAvailability(final int index)
    {
        return warehouseAvailability.get(index);
    }

    /**
     * Gets the raw quantity of an item in the warehouse selected for this Stewmelier.
     *
     * @param stack item whose warehouse quantity is requested
     * @return matching item count, or zero when absent
     */
    @SuppressWarnings("null")
    public int getWarehouseItemCount(final ItemStack stack)
    {
        if (stack == null || stack.isEmpty()) return 0;
        return warehouseItemCounts.getOrDefault(BuiltInRegistries.ITEM.getKey(stack.getItem()), 0);
    }

    /** @return a value incremented whenever fresh warehouse counts are synchronized. */
    public int getWarehouseSnapshotVersion() { return warehouseSnapshotVersion; }

    /**
     * Snapshot of warehouse inventory relevant to one configured ingredient.
     *
     * @param warehouseCount total matching items in the selected warehouse
     * @param availableForStew matching items remaining after protected stacks
     */
    public record WarehouseAvailability(int warehouseCount, int availableForStew) {}

}
