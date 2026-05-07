package com.deathfrog.mctradepost.core.outpost;

import java.util.Map;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost;
import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blockentities.interfaces.IBlueprintDataProviderBE;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers;
import com.ldtteam.structurize.storage.StructurePacks;
import com.ldtteam.structurize.util.BlockInfo;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.Utils;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Places and adopts MineColonies child buildings that are embedded in an outpost blueprint.
 * <p>
 * The bootstrapper intentionally handles only the initial outpost bootstrap case: level 0
 * outposts that contain a builder hut in their schematic. It leaves the actual construction
 * work to MineColonies after the child builder hut has been registered.
 */
public final class OutpostChildBuildingBootstrapper
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation BUILDER_HUT_BLOCK_ID = ResourceLocation.fromNamespaceAndPath("minecolonies", "blockhutbuilder");
    private static final ResourceLocation BUILDER_BUILDING_TYPE_ID = ResourceLocation.fromNamespaceAndPath("minecolonies", "builder");
    private static final String TAG_BUILDING_TYPE = "type";

    /**
     * Utility class.
     */
    private OutpostChildBuildingBootstrapper()
    {
    }

    /**
     * Loads the outpost's stored blueprint and attempts to initialize its embedded child buildings.
     *
     * @param outpost the level 0 outpost to initialize.
     * @param actor the player that triggered initialization, if any.
     * @return a summary of the initialization attempt.
     */
    public static BootstrapResult initExisting(final BuildingOutpost outpost, @Nullable final ServerPlayer actor)
    {
        final Level world = outpost.getColony().getWorld();
        if (world == null || world.isClientSide)
        {
            return BootstrapResult.failed("Outpost world is not available.");
        }

        try
        {
            final String bootstrapPath = getLevelOneBlueprintPath(outpost.getBlueprintPath());
            final Blueprint blueprint = StructurePacks.getBlueprint(outpost.getStructurePack(), bootstrapPath, world.registryAccess());
            if (blueprint == null)
            {
                return BootstrapResult.failed("Could not load outpost blueprint.");
            }

            final BootstrapResult result = initFromBlueprint(outpost, blueprint, outpost.getRotationMirror(), actor);
            result.structurePack = outpost.getStructurePack();
            result.blueprintPath = bootstrapPath;
            return result;
        }
        catch (final Exception e)
        {
            LOGGER.warn("Unable to initialize outpost children for {}.", outpost.getPosition(), e);
            return BootstrapResult.failed("Could not load outpost blueprint: " + e.getMessage());
        }
    }

    /**
     * Scans the supplied outpost blueprint for embedded builder huts and places or adopts them.
     *
     * @param outpost the level 0 outpost that will own the child builder hut.
     * @param blueprint the already-loaded outpost blueprint.
     * @param rotationMirror the rotation and mirroring used for the placed outpost.
     * @param actor the player that triggered initialization, if any.
     * @return a summary of scanned, placed, adopted, skipped, and failed child buildings.
     */
    public static BootstrapResult initFromBlueprint(
        final BuildingOutpost outpost,
        final Blueprint blueprint,
        final RotationMirror rotationMirror,
        @Nullable final ServerPlayer actor)
    {
        final Level world = outpost.getColony().getWorld();
        if (world == null || world.isClientSide)
        {
            return BootstrapResult.failed("Outpost world is not available.");
        }

        if (outpost.getBuildingLevel() != 0)
        {
            return BootstrapResult.skipped("Outpost is not level 0.");
        }

        final RotationMirror effectiveRotationMirror = rotationMirror == null ? RotationMirror.NONE : rotationMirror;
        blueprint.setRotationMirror(effectiveRotationMirror, world);

        final BootstrapResult result = new BootstrapResult();
        for (final Map.Entry<BlockPos, BlockInfo> entry : blueprint.getBlockInfoAsMap().entrySet())
        {
            final BlockPos localPos = entry.getKey();
            final BlockInfo blockInfo = entry.getValue();
            final BlockState childState = blockInfo.getState();
            if (!isBuilderHut(blockInfo) || childState == null)
            {
                continue;
            }

            BlockPos blockOffset = blueprint.getPrimaryBlockOffset();

            if (blockOffset == null) continue;

            if (localPos.equals(blockOffset))
            {
                continue;
            }

            result.scanned++;
            final BlockPos worldPos = outpost.getPosition().subtract(blockOffset).offset(localPos);

            if (worldPos == null) continue;

            final IBuilding existing = outpost.getColony().getServerBuildingManager().getBuilding(worldPos);
            if (existing != null)
            {
                if (existing.getParent().equals(outpost.getID()))
                {
                    outpost.addBootstrappedChild(worldPos);
                    result.adopted++;
                }
                else
                {
                    result.collisions++;
                    result.message = "Existing building at " + worldPos.toShortString() + " is not an outpost child.";
                }
                continue;
            }

            if (!canPlaceChildAt(world, worldPos, childState))
            {
                result.collisions++;
                result.message = "Cannot place builder hut at " + worldPos.toShortString() + ".";
                continue;
            }

            if (placeChildBuilder(outpost, blueprint, effectiveRotationMirror, actor, localPos, worldPos, childState))
            {
                result.placed++;
                outpost.addBootstrappedChild(worldPos);
            }
            else
            {
                result.failed++;
            }
        }

        if (result.scanned == 0)
        {
            result.message = "No embedded builder hut found in outpost blueprint.";
        }
        else if (result.placed == 0 && result.adopted == 0 && result.message == null)
        {
            result.message = "No builder hut was placed or adopted.";
        }

        if (result.placed > 0 || result.adopted > 0)
        {
            outpost.markChildBootstrapComplete();
        }

        return result;
    }

    /**
     * Resolves the parent outpost blueprint that MineColonies will use for the level 0 build target.
     * <p>
     * Work orders rewrite the final digit of a building path to the target level. This mirrors that behavior so
     * retroactive bootstrap scans the level 1 outpost structure instead of a stored higher-level path.
     *
     * @param blueprintPath the outpost building's stored blueprint path.
     * @return the same path with its final level digit replaced by 1, when possible.
     */
    private static String getLevelOneBlueprintPath(final String blueprintPath)
    {
        if (blueprintPath == null || blueprintPath.isEmpty())
        {
            return blueprintPath;
        }

        final String suffix = ".blueprint";
        final String pathWithoutSuffix = blueprintPath.endsWith(suffix)
            ? blueprintPath.substring(0, blueprintPath.length() - suffix.length())
            : blueprintPath;

        if (pathWithoutSuffix.isEmpty() || !Character.isDigit(pathWithoutSuffix.charAt(pathWithoutSuffix.length() - 1)))
        {
            return blueprintPath;
        }

        return pathWithoutSuffix.substring(0, pathWithoutSuffix.length() - 1) + "1" + suffix;
    }

    /**
     * Checks whether a blueprint block info entry represents the embedded outpost builder hut.
     *
     * @param blockInfo the rotated blueprint block information to inspect.
     * @return true if the entry is the MineColonies builder hut or has builder building metadata.
     */
    private static boolean isBuilderHut(final BlockInfo blockInfo)
    {
        final BlockState childState = blockInfo.getState();
        if (childState == null)
        {
            return false;
        }

        Block childBlock = childState.getBlock();

        if (childBlock == null) return false;

        final ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(childBlock);
        if (BUILDER_HUT_BLOCK_ID.equals(blockId))
        {
            return true;
        }

        final CompoundTag tileEntityData = blockInfo.getTileEntityData();
        if (tileEntityData == null || !tileEntityData.contains(IBlueprintDataProviderBE.TAG_BLUEPRINTDATA))
        {
            return false;
        }

        final CompoundTag blueprintData = tileEntityData.getCompound(IBlueprintDataProviderBE.TAG_BLUEPRINTDATA);
        final ResourceLocation buildingType = ResourceLocation.tryParse(blueprintData.getString(TAG_BUILDING_TYPE) + "");
        return BUILDER_BUILDING_TYPE_ID.equals(buildingType);
    }

    /**
     * Checks whether the builder hut block can be placed at the target location without destroying an existing building block.
     *
     * @param world the server world containing the target position.
     * @param worldPos the world position where the child hut would be placed.
     * @param childState the builder hut block state from the rotated blueprint.
     * @return true if the position is empty, replaceable, or already contains the same hut block.
     */
    private static boolean canPlaceChildAt(final Level world, final @Nonnull BlockPos worldPos, final BlockState childState)
    {
        final BlockState existingState = world.getBlockState(worldPos);
        return existingState.isAir()
            || existingState.canBeReplaced()
            || existingState.getBlock() == childState.getBlock();
    }

    /**
     * Places one embedded builder hut, applies its schematic metadata, and registers it as an outpost child.
     *
     * @param outpost the outpost that should own the child builder hut.
     * @param blueprint the rotated outpost blueprint being scanned.
     * @param rotationMirror the rotation and mirroring to apply to the child hut.
     * @param actor the player that triggered placement, if any.
     * @param localPos the child hut's local position inside the rotated blueprint.
     * @param worldPos the child hut's target world position.
     * @param childState the builder hut block state to place.
     * @return true if the hut was placed and registered as a MineColonies building.
     */
    private static boolean placeChildBuilder(
        final BuildingOutpost outpost,
        final Blueprint blueprint,
        final RotationMirror rotationMirror,
        @Nullable final ServerPlayer actor,
        final BlockPos localPos,
        final @Nonnull BlockPos worldPos,
        final @Nonnull BlockState childState)
    {
        final Level world = outpost.getColony().getWorld();
        BlockPos blockOffset = blueprint.getPrimaryBlockOffset();

        if (blockOffset == null) return false;

        final CompoundTag tileEntityData = blueprint.getTileEntityData(outpost.getPosition().subtract(blockOffset), localPos);

        if (!world.setBlockAndUpdate(worldPos, childState))
        {
            return false;
        }

        if (tileEntityData != null)
        {
            PlacementHandlers.handleTileEntityPlacement(tileEntityData, world, worldPos, rotationMirror);
        }

        final BlockEntity blockEntity = world.getBlockEntity(worldPos);
        if (blockEntity instanceof IBlueprintDataProviderBE blueprintData)
        {
            blueprintData.setPackName(blueprint.getPackName());
            blueprintData.setBlueprintPath(resolveChildBlueprintPath(blueprint, blueprintData));
        }

        final Block block = childState.getBlock();
        if (block instanceof AbstractBlockHut<?> hut)
        {
            hut.setPlacedBy(world, worldPos, childState, actor, null);
        }

        final IBuilding childBuilding = outpost.getColony().getServerBuildingManager().getBuilding(worldPos);
        if (childBuilding == null)
        {
            LOGGER.warn("Placed outpost builder hut at {}, but no MineColonies building was registered.", worldPos);
            return false;
        }

        childBuilding.setStructurePack(blueprint.getPackName());
        if (blockEntity instanceof IBlueprintDataProviderBE blueprintData)
        {
            childBuilding.setBlueprintPath(blueprintData.getBlueprintPath());
        }
        childBuilding.setRotationMirror(rotationMirror);
        childBuilding.setParent(outpost.getID());
        childBuilding.markDirty();
        outpost.registerBlockPosition(childState, worldPos, world);
        return true;
    }

    /**
     * Resolves the child hut blueprint path using the same convention as MineColonies' hut placement handler.
     *
     * @param blueprint the parent outpost blueprint.
     * @param blueprintData the child hut block entity data after tile entity placement.
     * @return the level 1 blueprint path for the embedded child hut.
     */
    private static String resolveChildBlueprintPath(final Blueprint blueprint, final IBlueprintDataProviderBE blueprintData)
    {
        final String partialPath;
        if (blueprintData.getSchematicName().isEmpty())
        {
            final String[] elements = Utils.splitPath(blueprintData.getBlueprintPath());
            partialPath = StructurePacks.getStructurePack(blueprint.getPackName())
                .getSubPath(blueprint.getFilePath().resolve(elements[elements.length - 1].replace(".blueprint", "")));
        }
        else
        {
            partialPath = StructurePacks.getStructurePack(blueprint.getPackName())
                .getSubPath(Utils.resolvePath(blueprint.getFilePath(), blueprintData.getSchematicName()));
        }

        if (blueprintData instanceof TileEntityColonyBuilding tileEntityColonyBuilding)
        {
            tileEntityColonyBuilding.setSchematicName("");
        }

        return partialPath.substring(0, partialPath.length() - 1) + "1.blueprint";
    }

    /**
     * Mutable summary of one outpost child-building initialization attempt.
     */
    public static final class BootstrapResult
    {
        public String structurePack;
        public String blueprintPath;
        public int scanned;
        public int placed;
        public int adopted;
        public int collisions;
        public int failed;
        public String message;

        /**
         * Creates a result for an initialization attempt that could not run.
         *
         * @param message the user-facing failure reason.
         * @return a failed result with the reason attached.
         */
        static BootstrapResult failed(final String message)
        {
            final BootstrapResult result = new BootstrapResult();
            result.failed = 1;
            result.message = message;
            return result;
        }

        /**
         * Creates a result for an initialization attempt that was valid but intentionally skipped.
         *
         * @param message the user-facing skip reason.
         * @return a skipped result with the reason attached.
         */
        static BootstrapResult skipped(final String message)
        {
            final BootstrapResult result = new BootstrapResult();
            result.message = message;
            return result;
        }

        /**
         * Checks whether initialization changed the colony by placing or adopting a builder hut.
         *
         * @return true if at least one child builder hut was placed or adopted.
         */
        public boolean changed()
        {
            return placed > 0 || adopted > 0;
        }
    }
}
