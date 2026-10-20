package com.deathfrog.mctradepost.core.entity.pets.companion;

import com.deathfrog.mctradepost.api.entity.pets.PetData;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.entity.pets.PetDragon;
import com.deathfrog.mctradepost.api.entity.pets.PetHelper;
import com.deathfrog.mctradepost.api.items.MCTPModDataComponents;
import com.deathfrog.mctradepost.api.items.datacomponent.CompanionStaffRecord;
import com.deathfrog.mctradepost.api.util.ChunkUtil;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingPetshop;
import com.deathfrog.mctradepost.core.entity.pets.companion.CompanionRegistry.CompanionRecord;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.util.InventoryUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Server-side lifecycle, persistence, recall, and delivery operations for Companion Staff dragons. */
public final class CompanionManager
{
    public static final int COOLDOWN_TICKS = 20 * 60 * 2;
    private static final int TICKET_RADIUS = 2;

    @SuppressWarnings("null")
    private static final @Nonnull TicketType<ChunkPos> COMPANION_TICKET =
        TicketType.create("mctradepost_companion", Comparator.comparingLong(ChunkPos::toLong), 20);

    private CompanionManager()
    {
    }

    /**
     * Returns the binding UUID stored on a staff.
     *
     * @param stack staff stack to inspect
     * @return binding UUID, or {@code null} when unbound
     */
    public static @Nullable UUID bindingId(@Nonnull ItemStack stack)
    {
        CompanionStaffRecord reference = stack.get(NullnessBridge.assumeNonnull(MCTPModDataComponents.COMPANION_STAFF.get()));
        return reference == null ? null : reference.bindingId();
    }

    /**
     * Binds or safely rebinds a staff to a pet shop.
     *
     * @param player player performing the binding
     * @param stack staff to mutate
     * @param petshop destination pet shop
     * @return true when the binding succeeds
     */
    public static boolean bind(@Nonnull ServerPlayer player, @Nonnull ItemStack stack, @Nonnull BuildingPetshop petshop)
    {
        final MinecraftServer localServer = player.server;

        if (localServer == null) return false;

        CompanionRegistry registry = CompanionRegistry.get(localServer);
        UUID oldBinding = bindingId(stack);
        if (oldBinding != null)
        {
            CompanionRecord oldRecord = registry.find(oldBinding).orElse(null);
            if (oldRecord != null && (oldRecord.active || hasStoredItems(oldRecord, localServer)))
            {
                message(player, "item.mctradepost.companion_staff.rebind_blocked");
                return false;
            }
            registry.remove(oldBinding);
        }

        @SuppressWarnings("null")
        CompanionRecord record = registry.create(petshop.getColony().getID(),
            petshop.getColony().getDimension().location().toString(), petshop.getID());

        stack.set(NullnessBridge.assumeNonnull(MCTPModDataComponents.COMPANION_STAFF.get()),
            new CompanionStaffRecord(record.bindingId));

        message(player, "item.mctradepost.companion_staff.bound");
        
        return true;
    }

    /**
     * Summons an inactive companion or recalls its active entity to the staff holder.
     *
     * @param player staff holder
     * @param stack bound staff
     * @return true when a summon or recall succeeds
     */
    @SuppressWarnings("null")
    public static boolean summonOrRecall(@Nonnull ServerPlayer player, @Nonnull ItemStack stack)
    {
        UUID binding = bindingId(stack);
        if (binding == null)
        {
            message(player, "item.mctradepost.companion_staff.unbound");
            return false;
        }

        final MinecraftServer localServer = player.server;

        if (localServer == null)
        {
            message(player, "Error: No valid server while using the companion staff.");
            return false;
        }

        CompanionRegistry registry = CompanionRegistry.get(localServer);
        CompanionRecord record = registry.find(binding).orElse(null);
        if (record == null || resolvePetshop(localServer, record, true) == null)
        {
            message(player, "item.mctradepost.companion_staff.shop_missing");
            return false;
        }

        long now = localServer.overworld().getGameTime();
        if (now < record.cooldownUntil)
        {
            long remainingTicks = record.cooldownUntil - now;
            applyVisualCooldown(player, stack, remainingTicks);
            long seconds = (remainingTicks + 19L) / 20L;
            player.displayClientMessage(Component.translatable("item.mctradepost.companion_staff.cooldown", seconds), true);
            return false;
        }

        PetDragon dragon = record.active ? resolveActive(localServer, record) : null;
        if (dragon == null)
        {
            dragon = spawn(player, record);
            if (dragon == null)
            {
                message(player, "item.mctradepost.companion_staff.summon_failed");
                return false;
            }
        }
        else
        {
            dragon = moveToPlayer(dragon, player);
        }

        dragon.setCompanionController(player.getUUID());
        record.active = true;
        record.activeDimension = player.level().dimension().location().toString();
        record.activePosition = player.blockPosition().immutable();
        record.cooldownUntil = now + COOLDOWN_TICKS;
        applyVisualCooldown(player, stack, COOLDOWN_TICKS);
        snapshot(dragon, record);
        registry.changed();
        message(player, "item.mctradepost.companion_staff.summoned");
        return true;
    }

    /**
     * Dismisses the matching companion, delivering its inventory or preserving it for a deferred dismissal.
     *
     * @param player player dismissing the companion
     * @param staff staff used for dismissal
     * @param dragon companion being dismissed
     * @return true when this is the matching staff and dismissal completes
     */
    public static boolean dismiss(@Nonnull ServerPlayer player, @Nonnull ItemStack staff, @Nonnull PetDragon dragon)
    {
        UUID binding = bindingId(staff);
        if (binding == null || !binding.equals(dragon.getCompanionBindingId()))
        {
            message(player, "item.mctradepost.companion_staff.wrong_staff");
            return false;
        }

        final MinecraftServer localServer = player.server;

        if (localServer == null)
        {
            message(player, "Error: No valid server while using the companion staff.");
            return false;
        }

        CompanionRegistry registry = CompanionRegistry.get(localServer);
        CompanionRecord record = registry.find(binding).orElse(null);
        if (record == null)
        {
            message(player, "item.mctradepost.companion_staff.unbound");
            return false;
        }

        snapshot(dragon, record);
        BuildingPetshop petshop = resolvePetshop(localServer, record, true);
        if (petshop == null)
        {
            message(player, "item.mctradepost.companion_staff.shop_missing");
            return false;
        }

        boolean delivered = deliverInventory(localServer, record, dragon, petshop);
        record.active = false;
        record.activeDimension = "";
        record.activePosition = BlockPos.ZERO;
        record.cooldownUntil = localServer.overworld().getGameTime() + COOLDOWN_TICKS;
        applyVisualCooldown(player, staff, COOLDOWN_TICKS);
        snapshot(dragon, record);
        dragon.discardForCompanionDismissal();
        registry.changed();

        message(player, delivered
            ? "item.mctradepost.companion_staff.dismissed"
            : "item.mctradepost.companion_staff.dismissed_deferred");
        
            return true;
    }

    /**
     * Periodically snapshots a live companion so an unexpected unload cannot lose its inventory.
     *
     * @param dragon live companion to snapshot
     */
    public static void synchronize(@Nonnull PetDragon dragon)
    {
        final MinecraftServer localServer = dragon.getServer();

        if (localServer == null)
        {
            return;
        }

        UUID binding = dragon.getCompanionBindingId();
        if (binding == null || dragon.level().isClientSide || localServer == null)
        {
            return;
        }

        CompanionRegistry registry = CompanionRegistry.get(localServer);
        CompanionRecord record = registry.find(binding).orElse(null);
        if (record == null)
        {
            return;
        }
        record.active = true;
        record.activeDimension = dragon.level().dimension().location().toString();
        record.activePosition = dragon.blockPosition().immutable();
        snapshot(dragon, record);
        registry.changed();
    }

    /**
     * Clears active state after an unrecovered companion death; vanilla death handling drops its live inventory.
     *
     * @param dragon dying companion
     */
    public static void onDeath(@Nonnull PetDragon dragon)
    {
        final MinecraftServer localServer = dragon.getServer();

        if (localServer == null)
        {
            return;
        }

        UUID binding = dragon.getCompanionBindingId();
        if (binding == null || localServer == null)
        {
            return;
        }

        CompanionRegistry registry = CompanionRegistry.get(localServer);
        CompanionRecord record = registry.find(binding).orElse(null);

        if (record != null)
        {
            record.active = false;
            record.activeDimension = "";
            record.activePosition = BlockPos.ZERO;
            record.inventory = new CompoundTag();
            registry.changed();
        }
    }

    /**
     * Drops recoverable state and removes the companion when its staff item entity is destroyed.
     *
     * @param staffEntity destroyed staff entity and fallback drop location
     * @param binding staff binding UUID
     */
    public static void destroy(@Nonnull ItemEntity staffEntity, @Nonnull UUID binding)
    {
        if (!(staffEntity.level() instanceof ServerLevel level) || level.getServer() == null)
        {
            return;
        }

        CompanionRegistry registry = CompanionRegistry.get(level.getServer());
        CompanionRecord record = registry.remove(binding).orElse(null);
        if (record == null)
        {
            return;
        }

        PetDragon dragon = resolveActive(level.getServer(), record);
        if (dragon != null)
        {
            dropAndClearInventory((ServerLevel) dragon.level(), dragon.blockPosition(), dragon.getInventory());
            dragon.discardForCompanionDismissal();
            return;
        }

        ItemStackHandler stored = readInventory(level.getServer(), record);
        dropAndClearInventory(level, staffEntity.blockPosition(), stored);
    }

    @SuppressWarnings("null")
    private static PetDragon spawn(ServerPlayer player, CompanionRecord record)
    {
        final Level localLevel = player.level();

        if (localLevel == null)
        {
            return null;
        }

        PetDragon dragon = MCTradePostMod.PET_DRAGON.get().create(localLevel);
        if (dragon == null)
        {
            return null;
        }

        dragon.setUUID(record.companionId);
        dragon.setCompanionBindingId(record.bindingId);
        dragon.setCompanionController(player.getUUID());
        BuildingPetshop petshop = resolvePetshop(player.server, record, false);
        dragon.setTrainerBuilding(petshop);
        readInventory(player.server, record, dragon.getInventory());
        Optional<BlockPos> spawnPosition = PetHelper.findNearbyValidSpawn(dragon, player.blockPosition(), 3);
        BlockPos position = spawnPosition.orElse(player.blockPosition());
        dragon.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);

        if (!player.level().addFreshEntity(dragon))
        {
            return null;
        }
        return dragon;
    }

    private static PetDragon moveToPlayer(PetDragon dragon, ServerPlayer player)
    {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        if (dragon.level() == player.level())
        {
            dragon.teleportTo(x, y, z);
            return dragon;
        }
        @SuppressWarnings("null")
        Entity changed = dragon.changeDimension(new DimensionTransition(player.serverLevel(), new Vec3(x, y, z),
            Vec3.ZERO, dragon.getYRot(), dragon.getXRot(), DimensionTransition.DO_NOTHING));
        return changed instanceof PetDragon moved ? moved : dragon;
    }

    @SuppressWarnings("null")
    private static @Nullable PetDragon resolveActive(MinecraftServer server, CompanionRecord record)
    {
        for (ServerLevel serverLevel : server.getAllLevels())
        {
            Entity loaded = serverLevel.getEntity(record.companionId);
            if (loaded instanceof PetDragon dragon)
            {
                return dragon;
            }
        }

        ServerLevel level = level(server, record.activeDimension);
        
        if (level == null || BlockPos.ZERO.equals(record.activePosition))
        {
            return null;
        }

        ChunkUtil.ensureChunkLoadedByTicket(level, record.activePosition, TICKET_RADIUS, COMPANION_TICKET);
        Entity entity = level.getEntity(record.companionId);
        ChunkUtil.releaseChunkTicket(level, record.activePosition, TICKET_RADIUS, COMPANION_TICKET);
        return entity instanceof PetDragon dragon ? dragon : null;
    }

    @SuppressWarnings("null")
    private static boolean deliverInventory(MinecraftServer server, CompanionRecord record, PetDragon dragon, BuildingPetshop petshop)
    {
        ServerLevel shopLevel = level(server, record.shopDimension);
        if (shopLevel == null || !ChunkUtil.ensureChunkLoadedByTicket(shopLevel, record.shopPosition, TICKET_RADIUS, COMPANION_TICKET))
        {
            return false;
        }
        try
        {
            IWareHouse warehouse = petshop.getColony().getServerBuildingManager().getClosestWarehouseInColony(record.shopPosition);
            if (warehouse instanceof IBuilding warehouseBuilding)
            {
                ChunkUtil.ensureChunkLoadedByTicket(shopLevel, warehouseBuilding.getPosition(), TICKET_RADIUS, COMPANION_TICKET);
                transferAvailable(dragon.getInventory(), warehouseBuilding.getItemHandlerCap());
                warehouseBuilding.markDirty();
                ChunkUtil.releaseChunkTicket(shopLevel, warehouseBuilding.getPosition(), TICKET_RADIUS, COMPANION_TICKET);
            }
            transferAvailable(dragon.getInventory(), petshop.getItemHandlerCap());
            petshop.markDirty();
            dropAndClearInventory(shopLevel, record.shopPosition.above(), dragon.getInventory());
            return true;
        }
        finally
        {
            ChunkUtil.releaseChunkTicket(shopLevel, record.shopPosition, TICKET_RADIUS, COMPANION_TICKET);
        }
    }

    private static void transferAvailable(IItemHandler source, @Nullable IItemHandler destination)
    {
        if (destination == null)
        {
            return;
        }
        for (int slot = 0; slot < source.getSlots(); slot++)
        {
            ItemStack stack = source.getStackInSlot(slot);
            if (stack.isEmpty())
            {
                continue;
            }
            ItemStack remainder = InventoryUtils.transferItemStackIntoNextBestSlotInItemHandlerWithResult(stack, destination);
            int transferred = stack.getCount() - remainder.getCount();
            if (transferred > 0)
            {
                source.extractItem(slot, transferred, false);
            }
        }
    }

    private static void dropAndClearInventory(ServerLevel level, BlockPos position, IItemHandler inventory)
    {
        InventoryUtils.dropItemHandler(inventory, level, position.getX(), position.getY(), position.getZ());
        InventoryUtils.clearItemHandler(inventory);
    }

    @SuppressWarnings("null")
    private static void snapshot(PetDragon dragon, CompanionRecord record)
    {
        record.inventory = dragon.getInventory().serializeNBT(dragon.level().registryAccess());
    }

    private static ItemStackHandler readInventory(MinecraftServer server, CompanionRecord record)
    {
        ItemStackHandler handler = new ItemStackHandler(PetData.BASE_INVENTORY_SIZE);
        readInventory(server, record, handler);
        return handler;
    }

    @SuppressWarnings("null")
    private static void readInventory(MinecraftServer server, CompanionRecord record, ItemStackHandler handler)
    {
        if (!record.inventory.isEmpty())
        {
            handler.deserializeNBT(server.registryAccess(), record.inventory);
        }
    }

    private static boolean hasStoredItems(CompanionRecord record, MinecraftServer server)
    {
        ItemStackHandler handler = readInventory(server, record);
        for (int slot = 0; slot < handler.getSlots(); slot++)
        {
            if (!handler.getStackInSlot(slot).isEmpty())
            {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("null")
    private static @Nullable BuildingPetshop resolvePetshop(MinecraftServer server, CompanionRecord record, boolean loadChunk)
    {
        ServerLevel level = level(server, record.shopDimension);
        if (level == null)
        {
            return null;
        }
        if (loadChunk)
        {
            ChunkUtil.ensureChunkLoadedByTicket(level, record.shopPosition, TICKET_RADIUS, COMPANION_TICKET);
        }
        IColony colony = IColonyManager.getInstance().getColonyByWorld(record.colonyId, level);
        IBuilding building = colony == null ? null : colony.getServerBuildingManager().getBuilding(record.shopPosition);
        if (loadChunk)
        {
            ChunkUtil.releaseChunkTicket(level, record.shopPosition, TICKET_RADIUS, COMPANION_TICKET);
        }
        return building instanceof BuildingPetshop petshop ? petshop : null;
    }

    @SuppressWarnings("null")
    private static @Nullable ServerLevel level(MinecraftServer server, String dimension)
    {
        ResourceLocation location = ResourceLocation.tryParse(dimension);
        return location == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, location));
    }

    @SuppressWarnings("null")
    private static void message(ServerPlayer player, String key)
    {
        player.displayClientMessage(Component.translatable(key), true);
    }

    @SuppressWarnings("null")
    private static void applyVisualCooldown(ServerPlayer player, ItemStack staff, long ticks)
    {
        int visualTicks = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, ticks));
        if (visualTicks > 0)
        {
            player.getCooldowns().addCooldown(staff.getItem(), visualTicks);
        }
    }
}
