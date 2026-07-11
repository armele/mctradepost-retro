package com.deathfrog.mctradepost.item;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.items.MCTPModDataComponents;
import com.deathfrog.mctradepost.api.items.datacomponent.DimensionalLinkageRecord;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.ModTags;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingStationConnectionModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.DimPos;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Item used to record and install a paired Overworld/Nether rail transfer.
 * <p>
 * Right-clicking a valid track next to an active Nether portal records the track position for that dimension. Right-clicking a
 * completed item on an exact station building position installs it into the station connection module.
 */
public class DimensionalLinkageItem extends Item
{
    /**
     * Creates a dimensional linkage item.
     *
     * @param properties item properties
     */
    public DimensionalLinkageItem(@Nonnull Properties properties)
    {
        super(properties);
    }

    /**
     * Handles recording endpoints on valid portal-adjacent tracks or installing a complete linkage into a station.
     *
     * @param ctx use-on context from the player interaction
     * @return interaction result for the item use
     */
    @SuppressWarnings("null")
    @Override
    public InteractionResult useOn(@Nonnull UseOnContext ctx)
    {
        Player player = ctx.getPlayer();
        Level level = ctx.getLevel();
        ItemStack stack = ctx.getItemInHand();
        BlockPos clicked = ctx.getClickedPos();

        if (level.isClientSide)
        {
            return InteractionResult.SUCCESS;
        }

        if (player == null || stack.isEmpty())
        {
            return InteractionResult.PASS;
        }

        if (tryInstallInStation(level, clicked, stack, player))
        {
            return InteractionResult.SUCCESS;
        }

        if (!Level.OVERWORLD.equals(level.dimension()) && !Level.NETHER.equals(level.dimension()))
        {
            player.displayClientMessage(Component.translatable("item.mctradepost.dimensional_linkage.invalid_dimension"), true);
            return InteractionResult.FAIL;
        }

        if (!isTrackBlock(level, clicked))
        {
            player.displayClientMessage(Component.translatable("item.mctradepost.dimensional_linkage.not_track"), true);
            return InteractionResult.FAIL;
        }

        if (!isAdjacentToActivePortal(level, clicked))
        {
            player.displayClientMessage(Component.translatable("item.mctradepost.dimensional_linkage.no_portal"), true);
            return InteractionResult.FAIL;
        }

        DimPos endpoint = new DimPos(level.dimension(), clicked.immutable());
        DimensionalLinkageRecord record = linkageRecord(stack).withEndpoint(endpoint);
        stack.set(linkageComponent(), record);

        String messageKey = record.isComplete()
            ? "item.mctradepost.dimensional_linkage.complete"
            : "item.mctradepost.dimensional_linkage.endpoint_set";
        player.displayClientMessage(Component.translatable(messageKey, endpoint.shortDescription()), true);

        return InteractionResult.SUCCESS;
    }

    @SuppressWarnings("null")
    private boolean tryInstallInStation(Level level, BlockPos clicked, ItemStack stack, Player player)
    {
        if (!isComplete(stack))
        {
            return false;
        }

        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, clicked);
        if (colony == null)
        {
            return false;
        }

        BuildingStation station = findStationForClickedBlock(colony, clicked);
        if (station == null)
        {
            return false;
        }

        BuildingStationConnectionModule module = station.getModule(MCTPBuildingModules.STATION_CONNECTION);
        if (module == null)
        {
            player.displayClientMessage(Component.translatable("item.mctradepost.dimensional_linkage.install_failed"), true);
            return true;
        }

        if (!module.installLinkage(stack))
        {
            player.displayClientMessage(Component.translatable("item.mctradepost.dimensional_linkage.install_full", module.getDimensionalLinkageLimit()), true);
            return true;
        }

        player.displayClientMessage(Component.translatable("item.mctradepost.dimensional_linkage.installed"), true);
        return true;
    }

    private BuildingStation findStationForClickedBlock(IColony colony, BlockPos clicked)
    {
        IBuilding exact = colony.getServerBuildingManager().getBuilding(clicked);
        if (exact instanceof BuildingStation station)
        {
            return station;
        }

        return null;
    }

    /**
     * Gets the linkage data component from a stack, initializing an empty record when missing.
     *
     * @param stack linkage item stack
     * @return linkage record stored on the stack
     */
    public static DimensionalLinkageRecord linkageRecord(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return DimensionalLinkageRecord.empty();
        }

        DimensionalLinkageRecord record = stack.get(linkageComponent());
        if (record == null)
        {
            record = DimensionalLinkageRecord.uninitialized();
            stack.set(linkageComponent(), record);
        }
        return record;
    }

    /**
     * Assigns a new identity to a copied linkage when it becomes an installed station entry.
     *
     * @param stack installed linkage copy
     */
    public static void assignFreshInstalledIdentity(ItemStack stack)
    {
        DimensionalLinkageRecord record = linkageRecord(stack);
        stack.set(linkageComponent(), record.withFreshIdentity());
    }

    /**
     * Removes an installed-entry identity before a linkage is returned to an inventory.
     *
     * @param stack linkage being uninstalled
     */
    public static void clearInstalledIdentity(ItemStack stack)
    {
        DimensionalLinkageRecord record = linkageRecord(stack);
        stack.set(linkageComponent(), record.withoutIdentity());
    }

    /**
     * Tests whether an item stack is a dimensional linkage with both endpoints recorded.
     *
     * @param stack item stack to test
     * @return true when the stack is a complete dimensional linkage
     */
    public static boolean isComplete(ItemStack stack)
    {
        return !stack.isEmpty() && stack.is(NullnessBridge.assumeNonnull(MCTradePostMod.DIMENSIONAL_LINKAGE.get())) && linkageRecord(stack).isComplete();
    }

    /**
     * Tests whether a block is usable as a track endpoint or route node.
     *
     * @param level level containing the block
     * @param pos block position to test
     * @return true when the block is a vanilla rail or carries the configured track tag
     */
    public static boolean isTrackBlock(@Nonnull Level level, @Nonnull BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        return block instanceof BaseRailBlock || state.is(NullnessBridge.assumeNonnull(ModTags.BLOCKS.TRACK_TAG));
    }

    /**
     * Checks whether a track position is adjacent to an active Nether portal block.
     *
     * @param level level containing the track
     * @param pos track position to test
     * @return true when any adjacent block is a Nether portal block
     */
    public static boolean isAdjacentToActivePortal(@Nonnull Level level, @Nonnull BlockPos pos)
    {
        for (Direction direction : Direction.values())
        {
            if (direction == null) continue;

            BlockPos adjacent = pos.relative(direction);

            if (adjacent == null) continue;

            if (level.getBlockState(adjacent).is(NullnessBridge.assumeNonnull(Blocks.NETHER_PORTAL)))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds the currently recorded Overworld and Nether endpoints to the item tooltip.
     */
    @Override
    public void appendHoverText(@Nonnull ItemStack stack,
        @Nonnull Item.TooltipContext ctx,
        @Nonnull List<Component> tooltip,
        @Nonnull TooltipFlag flag)
    {
        DimensionalLinkageRecord record = linkageRecord(stack);
        appendEndpointTooltip(tooltip, "item.mctradepost.dimensional_linkage.overworld", record.overworldEndpoint());
        appendEndpointTooltip(tooltip, "item.mctradepost.dimensional_linkage.nether", record.netherEndpoint());

        if (record.isComplete())
        {
            tooltip.add(Component.translatable("item.mctradepost.dimensional_linkage.install").withStyle(ChatFormatting.WHITE));
        }
    }

    private static void appendEndpointTooltip(List<Component> tooltip, @Nonnull String key, Optional<DimPos> endpoint)
    {
        if (endpoint.isPresent())
        {
            tooltip.add(Component.translatable(key, endpoint.get().pos().toShortString()).withStyle(ChatFormatting.GRAY));
        }
        else
        {
            tooltip.add(Component.translatable(key, Component.translatable("item.mctradepost.dimensional_linkage.unset")).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static @Nonnull DataComponentType<DimensionalLinkageRecord> linkageComponent()
    {
        return NullnessBridge.assumeNonnull(MCTPModDataComponents.DIMENSIONAL_LINKAGE.get());
    }
}
