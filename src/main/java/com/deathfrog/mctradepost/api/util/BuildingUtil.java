package com.deathfrog.mctradepost.api.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.buildings.modules.ExportData;
import com.google.common.collect.ImmutableCollection;
import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.requestsystem.StandardFactoryController;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.requestsystem.requesters.BuildingBasedRequester;
import com.minecolonies.core.tileentities.TileEntityRack;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

public class BuildingUtil {
    public static final String TAG_DIMENSION = "dimension";
    public static final String TAG_POSITION = "position";
    public static final String TAG_BUILDING_ID = "building_id";
    public static final String TAG_COLONY_ID = "colony_id";

    public static final int PICKUP_PRIORITY = 14;

    public static CompoundTag uniqueBuildingNBT(@Nonnull IBuilding building)
    { 
        CompoundTag tag = new CompoundTag();

        if (building != null)
        {
            tag.putString(TAG_DIMENSION, building.getColony().getDimension().location().toString());
            tag.putInt(TAG_COLONY_ID, building.getColony().getID());
            BlockPosUtil.write(tag, TAG_BUILDING_ID, building.getID());
            BlockPosUtil.write(tag, TAG_POSITION, building.getPosition());
        }
        
        return tag;
    }

    /**
     * Retrieves the building associated with the given dimension and position.
     *
     * @param dimensionKey the key of the dimension where the building is located.
     * @param buildingId the position identifier of the building within the dimension.
     * @return the building located at the specified dimension and position, or null if no building or colony is found.
     */
    public static IBuilding buildingFromDimPos(ResourceKey<Level> dimensionKey, BlockPos buildingId)
    {
        if (buildingId == null || BlockPos.ZERO.equals(buildingId))
        {
            return null;
        }

        IColony colony = IColonyManager.getInstance().getColonyByPosFromDim(dimensionKey, buildingId);

        if (colony == null)
        {
            MCTradePostMod.LOGGER.warn("No colony identifiable from dimension {} and position {}. {}", dimensionKey, buildingId, new Exception());
            return null;
        } 

        IBuilding building = colony.getBuildingManager().getBuilding(buildingId);
        return building;
    }
    
    /**
     * Retrieves the building from the given NBT data.
     * 
     * @param tag the CompoundTag containing the data for the building.
     * @return the building, or null if none found from the given data.
     */
    public static IBuilding buildingFromNBT(@Nonnull CompoundTag tag) 
    { 
        IBuilding building = null;
        String dimension = tag.getString(TAG_DIMENSION);
        ResourceLocation level = ResourceLocation.parse(dimension);
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, level);

        BlockPos buildingId = BlockPosUtil.read(tag, TAG_BUILDING_ID);

        if (buildingId != null && !BlockPos.ZERO.equals(buildingId))
        {
            building = buildingFromDimPos(levelKey, buildingId);
        }

        return building;
    }   

    /**
     * Retrieves the building view associated with the given dimension and position.
     * 
     * @param dimensionKey the key of the dimension where the building is located.
     * @param buildingId the position identifier of the building within the dimension.
     * @return the building view located at the specified dimension and position, or null if no building view or colony is found.
     */
    public static IBuildingView buildingViewFromDimPos(ResourceKey<Level> dimensionKey, BlockPos buildingId)
    {
        IBuildingView buildingView = IColonyManager.getInstance().getBuildingView(dimensionKey, buildingId);

        if (buildingView == null)
        {
            // MCTradePostMod.LOGGER.warn("No building view identifiable from ID {} with position {}.", buildingId, buildingId);
        }
        
        return buildingView;
    }

    /**
     * Retrieves the building view from the given NBT data.
     * 
     * @param tag the CompoundTag containing the data for the building.
     * @return the building, or null if none found from the given data.
     */
    public static IBuildingView buildingViewFromNBT(@Nonnull CompoundTag tag) 
    { 
        String dimension = tag.getString(TAG_DIMENSION);
        ResourceLocation level = ResourceLocation.parse(dimension);
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, level);

        BlockPos buildingId = BlockPosUtil.read(tag, TAG_BUILDING_ID);

        IBuildingView buildingView = buildingViewFromDimPos(levelKey, buildingId);

        return buildingView;
    } 

    /**
     * Requests that the given ItemStack be delivered from the given sending building to the nearest warehouse in the same colony.
     * 
     * @param sendingBuilding the building from which the item is being sent.
     * @param stackToTake the ItemStack to be delivered.
     * @return the request token for the delivery request, or null if the colony does not have a warehouse.
     */
    public static <R extends IRequestable> IToken<?> bringThisToTheWarehouse(@Nonnull IBuilding sendingBuilding, @Nonnull final ItemStack stackToTake)
    {
        final IColony colony = sendingBuilding.getColony();

        if (!colony.hasWarehouse())
        {
            return null;
        }

        IBuilding warehouse = colony.getBuildingManager().getClosestWarehouseInColony(sendingBuilding.getPosition());

        // If the warehouse exists but hasn't been built yet, you might still get a null here.
        if (warehouse == null)
        {
            return null;
        }

        IRequester requestingWarehouse = StandardFactoryController.getInstance().getNewInstance(TypeToken.of(BuildingBasedRequester.class), warehouse);
        final Delivery delivery = new Delivery(sendingBuilding.getLocation(), requestingWarehouse.getLocation(), stackToTake, PICKUP_PRIORITY);


        final IToken<?> requestToken = warehouse.createRequest(delivery, true);

        sendingBuilding.markDirty();
        warehouse.markDirty();

        return requestToken;
    }

    /**
     * Counts the number of items in the given {@link ItemStorage} that are available in the given {@link IBuilding} and its assigned worker.
     * 
     * @param building The building to check.
     * @param stack    The item storage to check.
     * @return The count of available items.
     */
    public static int availableCount(IBuilding buildingStation, ItemStorage stack)
    {
        int amountInBuilding = 0;
        int amountInWorkerInventory = 0;
        
        if (buildingStation != null)
        {
            amountInBuilding =
                InventoryUtils.getItemCountInItemHandler(buildingStation.getItemHandlerCap(), ExportData.hasExportItem(stack));

            if (buildingStation.getAllAssignedCitizen().isEmpty())
            {
                amountInWorkerInventory = 0;
            }
            else
            {
                ICitizenData  worker = buildingStation.getAllAssignedCitizen().toArray(ICitizenData[]::new)[0];
                amountInWorkerInventory = InventoryUtils.getItemCountInItemHandler(worker.getInventory(), ExportData.hasExportItem(stack));  
            }


        }
        
        return amountInBuilding + amountInWorkerInventory;
    }

    /**
     * Retrieves all request resolvers for all buildings in the given colony.
     * 
     * @param colony The colony to retrieve the resolvers from.
     * @return A collection of request resolvers for all buildings in the colony.
     */
    public static Collection<IToken<?>> getAllBuildingResolversForColony(IColony colony)
    {
        Collection<IToken<?>> resolvers = new ArrayList<>();
        Collection<IBuilding> buildings = colony.getBuildingManager().getBuildings().values();

        for (IBuilding building : buildings)
        {
            ImmutableCollection<IRequestResolver<?>> buildingResolvers = building.getResolvers();

            for (IRequestResolver<?> resolver : buildingResolvers)
            {
                resolvers.add(resolver.getId());
            }   
        }

        final IBuilding townHall = colony.getBuildingManager().getTownHall();
        if (townHall != null) {
            for (IRequestResolver<?> resolver : townHall.getResolvers())
            {
                resolvers.add(resolver.getId());
            }   
        }

        return resolvers;
    }

    public static List<Tuple<ItemStack, BlockPos>> getMatchingItemStacksInBuilding(@Nonnull IBuilding building, @Nonnull final Predicate<ItemStack> itemStackSelectionPredicate)
    {
        Level level = building.getColony().getWorld();
        List<Tuple<ItemStack, BlockPos>> found = new ArrayList<>();
        
        if (building != null)
        {
            for (@Nonnull final BlockPos pos : building.getContainers())
            {
                if (WorldUtil.isBlockLoaded(level, pos))
                {
                    final BlockEntity entity = level.getBlockEntity(pos);
                    if (entity instanceof final TileEntityRack rack && !rack.isEmpty() && rack.getItemCount(itemStackSelectionPredicate) > 0)
                    {
                        for (final ItemStack stack : (InventoryUtils.filterItemHandler(rack.getInventory(), itemStackSelectionPredicate)))
                        {
                            found.add(new Tuple<>(stack, pos));
                        }
                    }
                }
            }
        }
        return found;
    }

}
