package com.deathfrog.mctradepost.core.event.wishingwell.ritual;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.event.wishingwell.WishingWellHandler;
import com.deathfrog.mctradepost.item.WishGatheringItem;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.colonyEvents.IColonyEvent;
import com.minecolonies.api.colony.colonyEvents.IColonyRaidEvent;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
import com.minecolonies.api.util.MessageUtils;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public class SummonRitualProcessor 
{
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String RAIDER_TAG = "minecolonies:raider";
    
    /**
     * Processes a raid end ritual at the specified BlockPos within the ServerLevel. This ritual targets all entities involved in an
     * ongoing raid within the colony located at the given position. The targeted entities are teleported to a random location near the
     * ritual site and subsequently slain.
     *
     * @param level  the ServerLevel where the ritual is taking place
     * @param pos    the BlockPos of the wishing well structure
     * @param ritual the ritual definition containing the effect details
     * @return true if the ritual was successfully triggered, false otherwise
     */
    public static boolean processRitualSummon(@Nonnull ServerLevel level, @Nonnull BlockPos pos, RitualDefinitionHelper ritual, RitualState state)
    {
        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
        if (colony == null)
        {
            LOGGER.warn("No colony found at {} where this ritual was attempted: {}", pos, ritual.describe());
            return false;
        }

        BlockPos targetSummonPosition = pos;
        List<? extends Entity> targets = new ArrayList<>();

        if (RAIDER_TAG.equals(ritual.target()))
        {
            targets = gatherRaidTargets(colony);
            if (targets.size() == 0)
            {
                LOGGER.warn("No raid event found at {} where this ritual was attempted: {}", pos, ritual.describe());
            }
        }
        else
        {
            ItemStack companionItem = state.companionItems.get(0).getItem();

            // Special case for any wish of gathering - use the linked block position
            if (companionItem.getItem() instanceof WishGatheringItem)
            {
                BlockPos gatheringLocation = WishGatheringItem.getLinkedBlockPos(companionItem);

                if (gatheringLocation == null || BlockPos.ZERO.equals(gatheringLocation))
                {
                    LOGGER.warn("Summoning ritual called with unset target location.");
                    MessageUtils.format("Summoning ritual called with unset target location.")
                        .sendTo(colony)
                        .forAllPlayers();
                    return false;
                }

                targetSummonPosition = gatheringLocation;
            }

            targets = gatherSummonTargets(level, targetSummonPosition, ritual);
        }

        for (Entity entity : targets)
        {
            if (entity instanceof AbstractEntityMinecoloniesRaider)
            {
                // Generate random offset within nearby blocks in X and Z
                int offsetX = level.random.nextInt(16) - 8;
                int offsetZ = level.random.nextInt(16) - 8;
                BlockPos targetPos = targetSummonPosition.offset(offsetX, 3, offsetZ);

                // Teleport them near the ritual location
                entity.teleportTo(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
            }
            else    
            {
                // Generate random offset within nearby blocks in X and Z
                int offsetX = level.random.nextInt(2) - 1;
                int offsetZ = level.random.nextInt(2) - 1;

                BlockPos targetPos = targetSummonPosition.offset(offsetX, 1, offsetZ);
                entity.teleportTo(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
            }
        }

        LOGGER.info("Summoning ritual completed at {}, bringing {} entities.", pos, targets.size());
        
        WishingWellHandler.showRitualEffect(level, pos);

        return true;
    }


    /**
     * Process a ritual slay effect at the given BlockPos within the ServerLevel. This will remove all entities of the specified type
     * within the given radius.
     * 
     * @param level  the ServerLevel to process the ritual in
     * @param pos    the BlockPos of the wishing well structure
     * @param ritual the ritual definition containing the target entity type and radius
     * @return true if the ritual was triggered, false otherwise
     */
    public static boolean processRitualSlay(@Nonnull ServerLevel level, @Nonnull BlockPos pos, RitualDefinitionHelper ritual)
    {
        List<? extends Entity> targets = gatherSummonTargets(level, pos, ritual);

        targets.forEach(Entity::discard);

        LOGGER.info("Slay ritual of {} completed at {} with a radius of {}, slaying {}",
            ritual.target(),
            pos,
            ritual.radius(),
            targets.size());
        
            WishingWellHandler.showRitualEffect(level, pos);

        return true;
    }

    /**
     * Gathers the entities participating in the ongoing raid event (if any) within the given colony.
     * 
     * @param colony the colony to search for an ongoing raid event
     * @return a (possibly empty) list of entities participating in the ongoing raid event
     */
    protected static List<? extends Entity> gatherRaidTargets(IColony colony)
    {
        List<? extends Entity> targets = new ArrayList<>();

        // IRaiderManager raidManager = colony.getRaiderManager();
        for (IColonyEvent event : colony.getEventManager().getEvents().values())
        {
            if (event instanceof IColonyRaidEvent)
            {
                targets = ((IColonyRaidEvent) event).getEntities();
                break;
            }
        }

        return targets;
    }

    /**
     * Gathers entities of a specific type within a certain radius of the given BlockPos or globally.
     * 
     * @param level  the ServerLevel to search for entities
     * @param pos    the BlockPos used as the center of the search radius or globally if the radius is negative
     * @param ritual the ritual definition containing the target entity type and radius
     * @return a list of entities of the target type within the search radius
     */
    protected static List<? extends Entity> gatherSummonTargets(@Nonnull ServerLevel level,
        @Nonnull BlockPos pos,
        RitualDefinitionHelper ritual)
    {
        List<? extends Entity> targets = new ArrayList<>();
        EntityType<?> entityType = ritual.getTargetAsEntityType();

        if (entityType == null)
        {
            LOGGER.warn("No entity type found for {} during Slay ritual.", ritual.target());
            return targets;
        }

        Double radius = (double) ritual.radius();

        if (radius < 0)
        {
            // Global: all loaded entities of this type
            targets = level.getEntities(entityType, entity -> entity.getType().equals(entityType));
        }
        else
        {
            // Local purge
            AABB box = new AABB(pos).inflate(radius);
            targets = level.getEntities(entityType, NullnessBridge.assumeNonnull(box), entity -> entity.getType().equals(entityType));
        }

        return targets;
    }
}
