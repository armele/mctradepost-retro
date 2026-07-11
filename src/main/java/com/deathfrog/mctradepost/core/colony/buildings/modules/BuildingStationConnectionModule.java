package com.deathfrog.mctradepost.core.colony.buildings.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.items.datacomponent.DimensionalLinkageRecord;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.DimPos;
import com.deathfrog.mctradepost.item.DimensionalLinkageItem;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
/**
 * Station module that stores and validates installed dimensional linkage items.
 * <p>
 * Installed linkage items allow a station to include Nether transfer hops while discovering rail routes to other stations.
 */
public class BuildingStationConnectionModule extends AbstractBuildingModule implements IPersistentModule
{
    private static final String TAG_LINKAGES = "dimensional_linkages";
    private static final String TAG_STACK = "stack";

    private final List<ItemStack> dimensionalLinkages = new ArrayList<>();

    /**
     * Validation status for an installed dimensional linkage.
     */
    public enum LinkageStatus
    {
        OK,
        INCOMPLETE,
        INVALID_DIMENSIONS,
        MISSING_LEVEL,
        UNLOADED,
        MISSING_TRACK,
        MISSING_PORTAL,
        WRONG_ITEM
    }

    /**
     * Result of validating one installed linkage stack.
     *
     * @param stack installed linkage stack
     * @param status validation status
     * @param messageKey translation key describing the status
     */
    public record LinkageValidation(ItemStack stack, LinkageStatus status, String messageKey)
    {
        /**
         * @return true when the linkage is usable for route discovery
         */
        public boolean isOk()
        {
            return status == LinkageStatus.OK;
        }
    }

    public BuildingStationConnectionModule()
    {
        super();
    }

    /**
     * @return immutable view of installed dimensional linkage stacks
     */
    public List<ItemStack> getDimensionalLinkages()
    {
        return Collections.unmodifiableList(dimensionalLinkages);
    }

    /**
     * @return maximum number of linkages this station may hold at its current building level
     */
    public int getDimensionalLinkageLimit()
    {
        return Math.max(0, building == null ? 0 : building.getBuildingLevel());
    }

    /**
     * Tests whether the supplied stack can be installed into this station.
     *
     * @param stack candidate linkage item stack
     * @return true when the stack is complete and capacity remains
     */
    public boolean canInstallLinkage(ItemStack stack)
    {
        return dimensionalLinkages.size() < getDimensionalLinkageLimit() && DimensionalLinkageItem.isComplete(stack);
    }

    /**
     * Installs one linkage item into this module and consumes one item from the source stack.
     *
     * @param stack player-held linkage stack
     * @return true when one linkage was installed
     */
    public boolean installLinkage(ItemStack stack)
    {
        if (!canInstallLinkage(stack))
        {
            return false;
        }

        ItemStack installed = stack.copyWithCount(1);
        DimensionalLinkageItem.assignFreshInstalledIdentity(installed);
        dimensionalLinkages.add(installed);
        stack.shrink(1);
        markDirty();
        return true;
    }

    /**
     * Removes an installed linkage by index.
     *
     * @param index zero-based installed linkage index
     * @return removed linkage stack, or empty when the index is invalid
     */
    public ItemStack removeLinkage(int index)
    {
        if (index < 0 || index >= dimensionalLinkages.size())
        {
            return ItemStack.EMPTY;
        }

        ItemStack removed = dimensionalLinkages.remove(index);
        DimensionalLinkageItem.clearInstalledIdentity(removed);
        markDirty();
        return removed;
    }

    /**
     * Validates every installed linkage for GUI status display and route discovery.
     *
     * @return validation result for each installed linkage
     */
    public List<LinkageValidation> validateLinkages()
    {
        List<LinkageValidation> validations = new ArrayList<>();
        for (ItemStack linkage : dimensionalLinkages)
        {
            validations.add(validateLinkage(linkage));
        }
        return validations;
    }

    /**
     * Validates one installed linkage item.
     *
     * @param stack installed linkage stack
     * @return validation result including status and translated message key
     */
    @SuppressWarnings("null")
    public LinkageValidation validateLinkage(ItemStack stack)
    {
        if (stack == null || stack.isEmpty() || !stack.is(MCTradePostMod.DIMENSIONAL_LINKAGE.get()))
        {
            return new LinkageValidation(ItemStack.EMPTY, LinkageStatus.WRONG_ITEM, "mctradepost.linkage.status.wrong_item");
        }

        DimensionalLinkageRecord record = DimensionalLinkageItem.linkageRecord(stack);
        if (!record.isComplete())
        {
            return new LinkageValidation(stack, LinkageStatus.INCOMPLETE, "mctradepost.linkage.status.incomplete");
        }

        DimPos overworld = record.overworldEndpoint().get();
        DimPos nether = record.netherEndpoint().get();
        if (!overworld.isOverworld() || !nether.isNether())
        {
            return new LinkageValidation(stack, LinkageStatus.INVALID_DIMENSIONS, "mctradepost.linkage.status.invalid_dimensions");
        }

        MinecraftServer server = building == null || building.getColony() == null || building.getColony().getWorld() == null
            ? null
            : building.getColony().getWorld().getServer();
        if (server == null)
        {
            return new LinkageValidation(stack, LinkageStatus.MISSING_LEVEL, "mctradepost.linkage.status.missing_level");
        }

        LinkageStatus overworldStatus = validateEndpoint(server, overworld);
        if (overworldStatus != LinkageStatus.OK)
        {
            return new LinkageValidation(stack, overworldStatus, messageKeyFor(overworldStatus, "overworld"));
        }

        LinkageStatus netherStatus = validateEndpoint(server, nether);
        if (netherStatus != LinkageStatus.OK)
        {
            return new LinkageValidation(stack, netherStatus, messageKeyFor(netherStatus, "nether"));
        }

        return new LinkageValidation(stack, LinkageStatus.OK, "mctradepost.linkage.status.ok");
    }

    private LinkageStatus validateEndpoint(MinecraftServer server, DimPos endpoint)
    {
        ServerLevel level = server.getLevel(endpoint.dimension());
        if (level == null)
        {
            return LinkageStatus.MISSING_LEVEL;
        }

        if (!level.isLoaded(endpoint.pos()))
        {
            return LinkageStatus.UNLOADED;
        }

        if (!DimensionalLinkageItem.isTrackBlock(level, endpoint.pos()))
        {
            return LinkageStatus.MISSING_TRACK;
        }

        if (!DimensionalLinkageItem.isAdjacentToActivePortal(level, endpoint.pos()))
        {
            return LinkageStatus.MISSING_PORTAL;
        }

        return LinkageStatus.OK;
    }

    private static String messageKeyFor(LinkageStatus status, String dimensionName)
    {
        return "mctradepost.linkage.status." + dimensionName + "." + status.name().toLowerCase();
    }

    @SuppressWarnings("null")
    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf, final boolean fullSync)
    {
        List<LinkageValidation> validations = validateLinkages();
        buf.writeInt(validations.size());
        for (LinkageValidation validation : validations)
        {
            writeItemStack(buf, validation.stack());
            buf.writeUtf(validation.status().name());
            buf.writeUtf(validation.messageKey());
        }
        buf.writeInt(getDimensionalLinkageLimit());
    }

    @Override
    public void deserializeNBT(@NotNull HolderLookup.Provider provider, CompoundTag compound)
    {
        dimensionalLinkages.clear();
        ListTag list = compound.getList(TAG_LINKAGES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            if (provider == null) continue;
            CompoundTag stackTag = list.getCompound(i).getCompound(TAG_STACK);

            if (stackTag == null) continue;

            ItemStack stack = ItemStack.parseOptional(provider, stackTag);
            if (!stack.isEmpty() && stack.is(NullnessBridge.assumeNonnull(MCTradePostMod.DIMENSIONAL_LINKAGE.get())))
            {
                dimensionalLinkages.add(stack.copyWithCount(1));
            }
        }
    }

    @SuppressWarnings("null")
    @Override
    public void serializeNBT(@NotNull HolderLookup.Provider provider, CompoundTag compound)
    {
        ListTag list = new ListTag();
        for (ItemStack stack : dimensionalLinkages)
        {
            if (stack.isEmpty())
            {
                continue;
            }

            CompoundTag entry = new CompoundTag();
            entry.put(TAG_STACK, stack.copyWithCount(1).saveOptional(provider));
            list.add(entry);
        }
        compound.put(TAG_LINKAGES, list);
    }

    /**
     * Reads an item stack serialized by this module into a building module view sync buffer.
     *
     * @param buf network buffer containing an item stack NBT payload
     * @return decoded item stack, or empty when the payload is absent
     */
    @SuppressWarnings("null")
    public static ItemStack readItemStack(@Nonnull RegistryFriendlyByteBuf buf)
    {
        CompoundTag stackTag = buf.readNbt();
        if (stackTag == null)
        {
            return ItemStack.EMPTY;
        }
        return ItemStack.parseOptional(buf.registryAccess(), stackTag);
    }

    @SuppressWarnings("null")
    private static void writeItemStack(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull ItemStack stack)
    {
        buf.writeNbt(stack.copyWithCount(1).saveOptional(buf.registryAccess()));
    }
}
