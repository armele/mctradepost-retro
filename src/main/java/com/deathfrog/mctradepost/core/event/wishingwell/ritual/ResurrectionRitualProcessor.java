package com.deathfrog.mctradepost.core.event.wishingwell.ritual;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.advancements.MCTPAdvancementTriggers;
import com.deathfrog.mctradepost.api.items.datacomponent.ResurrectionWishRecord;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.event.wishingwell.WishingWellHandler;
import com.deathfrog.mctradepost.core.event.wishingwell.resurrection.BuriedCitizenRegistry;
import com.deathfrog.mctradepost.core.event.wishingwell.resurrection.BuriedCitizenRegistry.Burial;
import com.deathfrog.mctradepost.item.WishResurrectionItem;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.util.AdvancementUtils;
import com.minecolonies.api.advancements.AdvancementTriggers;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;

/** Performs the dynamic-cost, single-use resurrection wish. */
public final class ResurrectionRitualProcessor
{
    private ResurrectionRitualProcessor() {}

    @SuppressWarnings("null")
    public static RitualState.RitualResult process(@Nonnull BuildingMarketplace marketplace, @Nonnull BlockPos wellPos,
        @Nonnull RitualState state)
    {
        ServerLevel level = (ServerLevel) marketplace.getColony().getWorld();
        ItemEntity wishEntity = state.companionItems.stream()
            .filter(entity -> entity.getItem().is(MCTradePostMod.WISH_RESURRECTION.get()))
            .findFirst().orElse(null);
        ResurrectionWishRecord binding = wishEntity == null ? null : WishResurrectionItem.binding(wishEntity.getItem());
        UUID thrower = wishEntity != null && wishEntity.getOwner() != null ? wishEntity.getOwner().getUUID() : null;
        UUID notificationPlayer = thrower != null ? thrower : binding == null ? null : binding.bindingPlayer();

        if (binding == null)
        {
            return fail(level, notificationPlayer, "ritual.mctradepost.resurrection.unbound");
        }
        if (!level.dimension().location().toString().equals(binding.dimension()))
        {
            return fail(level, notificationPlayer, "ritual.mctradepost.resurrection.wrong_dimension");
        }
        if (binding.colonyId() != marketplace.getColony().getID())
        {
            return fail(level, notificationPlayer, "ritual.mctradepost.resurrection.wrong_colony");
        }
        if (!marketplace.getColony().getPermissions().getRank(binding.bindingPlayer()).isColonyManager())
        {
            return fail(level, notificationPlayer, "ritual.mctradepost.resurrection.unauthorized");
        }
        if (thrower != null && !marketplace.getColony().getPermissions().getRank(thrower).isColonyManager())
        {
            return fail(level, notificationPlayer, "ritual.mctradepost.resurrection.unauthorized");
        }

        BuriedCitizenRegistry registry = BuriedCitizenRegistry.get(level);
        Burial burial = registry.get(binding.burialId()).orElse(null);
        if (burial == null)
        {
            return fail(level, notificationPlayer, "ritual.mctradepost.resurrection.consumed");
        }
        if (burial.colonyId() != binding.colonyId() || !burial.gravePos().equals(binding.gravePos()))
        {
            return fail(level, notificationPlayer, "ritual.mctradepost.resurrection.stale");
        }

        int cost = WishResurrectionItem.burialCost(marketplace.getColony().getDay(), burial.burialDay());
        if (cost < 0)
        {
            return fail(level, notificationPlayer, "ritual.mctradepost.resurrection.expired");
        }
        if (!level.getBlockState(burial.gravePos()).is(ModBlocks.blockNamedGrave))
        {
            return fail(level, notificationPlayer, "ritual.mctradepost.resurrection.missing_grave");
        }

        ItemStorage requiredCoins = new ItemStorage(MCTradePostMod.MCTP_COIN_DIAMOND.get(), cost);
        if (!state.meetsRequirements(requiredCoins))
        {
            return fail(level, notificationPlayer, "ritual.mctradepost.resurrection.coins", cost);
        }

        if (registry.remove(burial.id()).isEmpty())
        {
            return fail(level, notificationPlayer, "ritual.mctradepost.resurrection.consumed");
        }

        // The first diamond-coin entity is where burnCoins starts consuming the payment.
        // Capture its position before that entity can be discarded.
        ItemEntity consumedCoin = state.diamondCoins.getFirst();
        double arrivalX = consumedCoin.getX();
        double arrivalY = consumedCoin.getY() + 1.0;
        double arrivalZ = consumedCoin.getZ();

        final ICitizenData citizen;
        try
        {
            citizen = marketplace.getColony().getCitizenManager().resurrectCivilianData(
                burial.citizenNbt().copy(), true, level, wellPos.above(2));
        }
        catch (Exception exception)
        {
            registry.restore(burial);
            MCTradePostMod.LOGGER.error("Failed to resurrect burial {} at wishing well {}", burial.id(), wellPos, exception);
            return fail(level, notificationPlayer, "ritual.mctradepost.resurrection.failed");
        }

        // MineColonies may register the citizen without creating an entity when
        // the Town Hall's Move In setting is off. A forced spawn uses the already
        // restored citizen data rather than resurrecting the same citizen twice.
        AbstractEntityCitizen spawnedEntity = citizen.getEntity().orElse(null);
        if (spawnedEntity == null)
        {
            try
            {
                marketplace.getColony().getCitizenManager().spawnOrCreateCivilian(
                    citizen, level, List.of(wellPos.above(2)), true);
                spawnedEntity = citizen.getEntity().orElse(null);
            }
            catch (Exception exception)
            {
                MCTradePostMod.LOGGER.error("Forced spawn failed for restored citizen {}", citizen.getName(), exception);
            }
        }
        boolean appearedAtWell = spawnedEntity != null;
        if (appearedAtWell)
        {
            spawnedEntity.teleportTo(arrivalX, arrivalY, arrivalZ);
            spawnedEntity.setDeltaMovement(0, 0, 0);
        }
        else
        {
            MCTradePostMod.LOGGER.warn("Citizen {} was restored, but MineColonies could not spawn an entity near wishing well {}",
                citizen.getName(), wellPos);
        }

        marketplace.getColony().getCitizenManager().updateCitizenMourn(citizen, false);
        level.setBlockAndUpdate(burial.gravePos(), Blocks.AIR.defaultBlockState());

        // IDEA: pending base mod API change: remove the citizen name from
        // GraveyardManagementModule's private restingCitizen collection here.

        state.burnCoins(requiredCoins);
        AdvancementUtils.TriggerAdvancementPlayersForColony(marketplace.getColony(),
            player -> AdvancementTriggers.CITIZEN_RESURRECT.get().trigger(player));
        ServerPlayer wishingPlayer = level.getServer().getPlayerList().getPlayer(binding.bindingPlayer());
        if (wishingPlayer != null)
        {
            MCTPAdvancementTriggers.RESURRECT_CITIZEN_WISH.get().trigger(wishingPlayer);
        }
        WishingWellHandler.showRitualEffect(level, wellPos);
        notify(level, notificationPlayer, appearedAtWell
            ? "ritual.mctradepost.resurrection.success"
            : "ritual.mctradepost.resurrection.restored_without_entity", citizen.getName());
        return RitualState.RitualResult.COMPLETED;
    }

    private static RitualState.RitualResult fail(ServerLevel level, UUID playerId, String key, Object... args)
    {
        notify(level, playerId, key, args);
        return RitualState.RitualResult.FAILED;
    }

    @SuppressWarnings("null")
    private static void notify(ServerLevel level, UUID playerId, String key, Object... args)
    {
        if (playerId == null) return;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) player.sendSystemMessage(Component.translatable(key, args));
    }
}
