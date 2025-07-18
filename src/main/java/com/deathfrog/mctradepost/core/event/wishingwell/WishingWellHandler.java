// Part 1: WishingWellHandler to manage fountain rituals
package com.deathfrog.mctradepost.core.event.wishingwell;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.MCTPInventoryUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualDefinitionHelper;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualManager;
import com.deathfrog.mctradepost.item.CoinItem;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.colonyEvents.IColonyEvent;
import com.minecolonies.api.colony.managers.interfaces.IRaiderManager;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
import com.minecolonies.core.colony.events.raid.HordeRaidEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

@EventBusSubscriber(modid = MCTradePostMod.MODID)
public class WishingWellHandler {
    private static final int MAX_WISHINGWELL_COOLDOWN = 100;
    private static int wishingWellCooldown = MAX_WISHINGWELL_COOLDOWN;

    public static final String RAIDER_TAG = "minecolonies:raider";

    /**
     * Handles level tick events for server-side wishing well structures.
     * 
     * This method checks each registered wishing well location to ensure it is a valid structure.
     * It identifies items within the well's vicinity, differentiating between coin items and
     * companion items. If both types of items are present, it triggers an effect, removes the items,
     * and updates the ritual state for the well.
     * 
     * @param event The level tick event containing the level data.
     */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) 
    {
        if (event.getLevel().isClientSide()) 
        {
            return;
        }

        if (wishingWellCooldown > 0) 
        {
            wishingWellCooldown--;
            return;
        }

        ServerLevel level = (ServerLevel) event.getLevel();

        IColonyManager colonyManager = IColonyManager.getInstance();
        List<IColony> colonyList = colonyManager.getAllColonies();

        if (colonyList != null) {
            for (IColony colony : colonyList) {
                for (IBuilding building : colony.getBuildingManager().getBuildings().values()) 
                {
                    if (building instanceof BuildingMarketplace) 
                    {
                        processMarketplaceRituals(level, (BuildingMarketplace) building);
                    }
                }
            }
        }

        wishingWellCooldown = MAX_WISHINGWELL_COOLDOWN;
    }

    /**
     * Processes the rituals for a given marketplace building. This method goes through
     * each registered wishing well location in the marketplace and checks for the presence
     * of both coin items and companion items. If both are present, it triggers an effect
     * and updates the ritual state for the well.
     *
     * @param level The server level containing the marketplace building.
     * @param marketplace The marketplace building containing the wishing wells.
     */
    protected static void processMarketplaceRituals(ServerLevel level, BuildingMarketplace marketplace) 
    {
        WellLocations data = marketplace.getRitualData();
        Set<BlockPos> wells = data.getKnownWells();
        Map<BlockPos, RitualState> rituals = data.getActiveRituals();

        for (BlockPos pos : wells) 
        {
            AABB wellBox = new AABB(pos).inflate(1.5);
            List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, wellBox);

            List<ItemEntity> coinItems = items.stream()
                    .filter(e -> CoinItem.isCoin(e.getItem()))
                    .collect(Collectors.toList());

            List<ItemEntity> companions = items.stream()
                    .filter(e -> !CoinItem.isCoin(e.getItem()))
                    .collect(Collectors.toList());

            if (!coinItems.isEmpty() && !companions.isEmpty()) 
            {
                Item companionType = companions.getFirst().getItem().getItem();

                List<ItemEntity> sameTypeCompanions = companions.stream()
                        .filter(e -> e.getItem().getItem() == companionType)
                        .toList();

                int totalCompanionCount = sameTypeCompanions.stream()
                        .mapToInt(e -> e.getItem().getCount())
                        .sum();

                BlockPos center = pos.immutable();

                RitualState state = rituals.computeIfAbsent(center, k -> new RitualState());

                state.coins = coinItems.stream().mapToInt(e -> e.getItem().getCount()).sum();
                state.companionCount = totalCompanionCount;
                state.lastUsed = System.currentTimeMillis();

                RitualResult result = triggerRitual(level, state, center, companionType);

                switch (result) 
                {
                    case COMPLETED:
                    case FAILED:
                        MCTradePostMod.LOGGER.info("Wishing well {} at {} with companion item {}", result, center, companionType);
                        coinItems.stream().forEach(e -> e.discard());
                        sameTypeCompanions.forEach(Entity::discard); // remove every companion
                        rituals.remove(pos);  
                        break;

                    case UNRECOGNIZED:
                        // An unrecognized companion item has been used, or something else caused the ritual to fail.
                        // Discard the companion item, but leave the ritual active for a valid companion item to be added.
                        MCTradePostMod.LOGGER.warn("Wishing well activated with unknown ritual at {} with companion item {}", center, companionType);
                        sameTypeCompanions.forEach(Entity::discard); // remove every companion
                        break;

                    case NEEDS_INGREDIENTS:
                        // We're just waiting for more coins to be added...
                        break;
                }
            }
        }
    }

    private static void showRitualEffect(ServerLevel level, BlockPos pos) 
    {
        // Lightning visual
        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
        if (lightning != null)
        {
            lightning.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            lightning.setVisualOnly(true);
            level.addFreshEntity(lightning);
        }

        // Additional particles and sound
        level.sendParticles(ParticleTypes.ENCHANT,
            pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
            20, 0.5, 0.5, 0.5, 0.1);
        level.playSound(null, pos, SoundEvents.TOTEM_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    /**
     * Registers a BlockPos as a valid wishing well location. This is used to identify wells in the
     * world as they are used, so that the wishing well handler can find them later when checking for
     * active rituals.
     * 
     * @param pos The BlockPos of the center of the wishing well structure.
     */
    public static void registerWell(BuildingMarketplace marketplace, BlockPos pos) 
    {
        Set<BlockPos> wells = marketplace.getRitualData().getKnownWells();
        wells.add(pos);

        ServerLevel level = (ServerLevel) marketplace.getColony().getWorld();

        // Additional particles and sound
        level.sendParticles(ParticleTypes.GLOW,
            pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
            20, 0.5, 0.5, 0.5, 0.1);
        level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1.0f, 1.0f);

        MCTradePostMod.LOGGER.info("Registered wishing well at {}", pos);
    }

    /**
     * Unregisters a BlockPos as a valid wishing well location, if it exists. This allows
     * for removing wells that have been destroyed.
     * 
     * @param pos The BlockPos of the center of the wishing well structure.
     */
    public static void unregisterWell(BuildingMarketplace marketplace, BlockPos pos) 
    {
        Set<BlockPos> wells = marketplace.getRitualData().getKnownWells();

        if (wells.contains(pos)) 
        {
            wells.remove(pos);
            MCTradePostMod.LOGGER.info("Removed invalid wishing well at {}", pos);
        } 
        else 
        {
            MCTradePostMod.LOGGER.info("No valid wishing well discovered at {}", pos);
        }
    }

    /**
     * Checks if there is a valid wishing well structure nearby.
     * If there is, the center of the structure is added to the set of valid wishing well locations.
     * This is used to discover wishing wells in the world as they are used, so that the wishing well
     * handler can find them later when checking for active rituals.
     */
    public static void downInAWell(Level level, BuildingMarketplace marketplace, BlockPos pos) 
    {
        boolean discovery = false;

        if (marketplace == null) {
            MCTradePostMod.LOGGER.warn("Attempting to evaluate a wishing well without a Marketplace at {}", pos);
            return;
        }

        for (int dx = -2; dx <= 1; dx++) 
        {
            for (int dz = -2; dz <= 1; dz++) 
            {
                BlockPos check = pos.offset(dx, 0, dz);
                if (isWishingWellStructure(level, check)) 
                {
                    registerWell(marketplace, check);
                    discovery = true;
                }
            }
        }

        if (!discovery) {
            unregisterWell(marketplace, pos);
        }
    }

    /**
     * Checks if a given BlockPos is the center of a valid wishing well structure.
     * A valid wishing well structure has water in the bottom 2x2 square, and stone brick blocks in the 3x3 ring around it.
     * @param level the level to check in
     * @param center the BlockPos of the center of the structure to check
     * @return true if the BlockPos is the center of a valid wishing well structure, false otherwise
     */
    public static boolean isWishingWellStructure(Level level, BlockPos center) {
        // Check 2x2 water at center
        for (int dx = -1; dx <= 0; dx++) 
        {
            for (int dz = -1; dz <= 0; dz++) 
            {
                BlockPos base = center.offset(dx, 0, dz);
                BlockState state = level.getBlockState(base);

                if (!(state.getBlock() instanceof LiquidBlock) || !state.getFluidState().is(FluidTags.WATER)) 
                {
                    // MCTradePostMod.LOGGER.info("This is not water at {} during well structure identification.", base);
                    return false;
                }
            }
        }

        // Check surrounding 4x4 ring of stone bricks
        for (int dx = -2; dx <= 1; dx++) 
        {
            for (int dz = -2; dz <= 1; dz++) 
            {
                if (dx >= -1 && dx <= 0 && dz >= -1 && dz <= 0) continue; // Skip 2x2 center
                BlockPos ring = center.offset(dx, 0, dz);
                if (!level.getBlockState(ring).is(MCTradePostMod.MIXED_STONE.get())) 
                {
                    // MCTradePostMod.LOGGER.info("This is not stone brick at {} during well structure identification. Instead it is {}", ring, level.getBlockState(ring));
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Process a ritual summon effect at the given BlockPos within the ServerLevel.
     * This will summon entities of the specified type at or near the target location.
     *
     * @param level the ServerLevel to process the ritual in
     * @param pos the BlockPos of the wishing well structure
     * @param ritual the ritual definition containing the target entity type and summon count
     * 
     * @return true if the ritual was triggered, false otherwise
     */

    /**
     * Process a ritual slay effect at the given BlockPos within the ServerLevel.
     * This will remove all entities of the specified type within the given radius.
     * @param level the ServerLevel to process the ritual in
     * @param pos the BlockPos of the wishing well structure
     * @param ritual the ritual definition containing the target entity type and radius
     * 
     * @return true if the ritual was triggered, false otherwise
     */
    private static boolean processRitualSlay(ServerLevel level, BlockPos pos, RitualDefinitionHelper ritual) 
    {    
        List<? extends Entity> targets = gatherSummonTargets(level, pos, ritual);
        
        targets.forEach(Entity::discard);

        MCTradePostMod.LOGGER.info("Slay ritual of {} completed at {} with a radius of {}, slaying {}", ritual.target(), pos, ritual.radius(), targets.size());
        showRitualEffect(level, pos);

        return true;
    }

    /**
     * Processes a weather ritual at the specified BlockPos within the ServerLevel.
     * Based on the ritual definition, it sets the weather to clear, rain, or storm
     * for the remainder of the day. If the target weather type is unknown, the ritual
     * is ignored.
     *
     * @param level the ServerLevel where the ritual is taking place
     * @param pos the BlockPos of the wishing well structure
     * @param ritual the ritual definition containing the target weather type
     * 
     * @return true if the ritual was successfully triggered, false otherwise
     */
    private static boolean processRitualWeather(ServerLevel level, BlockPos pos, RitualDefinitionHelper ritual) 
    {    

        int restOfDay = 24000 - (int) (level.getDayTime() % 24000);
        int clearTime = 0;
        int weatherTime = 0;
        boolean isRaining = false;
        boolean isThundering = false;


        String weather = ritual.target();
        // MCTradePostMod.LOGGER.info("Ritual target {} resolved to {}", ritual.target(), entityType);

        if (weather == null) 
        {
            MCTradePostMod.LOGGER.info("No weather target provided during Weather ritual.");
            return false;
        } 
        else if (weather.equals("clear")) 
        {
            clearTime = restOfDay;
            weatherTime = 0;
            isRaining = false;
            isThundering = false;
        } 
        else if (weather.equals("rain")) 
        {
            clearTime = 0;
            weatherTime = restOfDay;
            isRaining = true;
            isThundering = false;
        } 
        else if (weather.equals("storm")) 
        {
            clearTime = 0;
            weatherTime = restOfDay;
            isRaining = true;
            isThundering = true;
        } 
        else 
        {
            MCTradePostMod.LOGGER.info("Unknown weather ritual of {} ignored at {} ", weather, pos);
            return false;
        }
        
        level.setWeatherParameters(clearTime, weatherTime, isRaining, isThundering);

        MCTradePostMod.LOGGER.info("Weather ritual of {} completed at {} ", weather, pos);
        showRitualEffect(level, pos);

        return true;
    }


    /**
     * Gathers the entities participating in the ongoing raid event (if any) within the given colony.
     * 
     * @param colony the colony to search for an ongoing raid event
     * @return a (possibly empty) list of entities participating in the ongoing raid event
     */
    protected static @Nonnull List<? extends Entity> gatherRaidTargets(IColony colony)
    {
        List<? extends Entity> targets = new ArrayList<>();

        IRaiderManager raidManager = colony.getRaiderManager();
        for (IColonyEvent event : colony.getEventManager().getEvents().values()) 
        {
            if (event instanceof HordeRaidEvent) 
            {
                targets = ((HordeRaidEvent) event).getEntities();
                break;
            }
        }

        return targets;
    }   

    /**
     * Gathers entities of a specific type within a certain radius of the given BlockPos or globally.
     * @param level the ServerLevel to search for entities
     * @param pos the BlockPos used as the center of the search radius or globally if the radius is negative
     * @param ritual the ritual definition containing the target entity type and radius
     * @return a list of entities of the target type within the search radius
     */
    protected static @Nonnull List<? extends Entity> gatherSummonTargets(ServerLevel level, BlockPos pos, RitualDefinitionHelper ritual)
    {
        List<? extends Entity> targets = new ArrayList<>(); 
        EntityType<?> entityType = ritual.getTargetAsEntityType();

        if (entityType == null) {
            MCTradePostMod.LOGGER.warn("No entity type found for {} during Slay ritual.", ritual.target());
            return targets;
        }

        Double radius = (double) ritual.radius();

        if (radius < 0) {
            // Global purge: all loaded entities of this type
            targets = level.getEntities(entityType, entity -> entity.getType().equals(entityType));
        } else {
            // Local purge
            targets = level.getEntities(entityType, new AABB(pos).inflate(radius), entity ->
                entity.getType().equals(entityType));
        }

        return targets;
    }

    /**
     * Processes a raid end ritual at the specified BlockPos within the ServerLevel.
     * This ritual targets all entities involved in an ongoing raid within the colony
     * located at the given position. The targeted entities are teleported to a random location
     * near the ritual site and subsequently slain.
     *
     * @param level the ServerLevel where the ritual is taking place
     * @param pos the BlockPos of the wishing well structure
     * @param ritual the ritual definition containing the effect details
     * 
     * @return true if the ritual was successfully triggered, false otherwise
     */
    private static boolean processRitualSummon(ServerLevel level, BlockPos pos, RitualDefinitionHelper ritual) 
    {
        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
        if (colony == null) 
        {
            MCTradePostMod.LOGGER.warn("No colony found at {} where this ritual was attempted: {}", pos, ritual.describe());
            return false;
        }

        List<? extends Entity> targets = new ArrayList<>();

        if (RAIDER_TAG.equals(ritual.target()))
        {
            targets = gatherRaidTargets(colony);
            if (targets.size() == 0) 
            {
                MCTradePostMod.LOGGER.warn("No raid event found at {} where this ritual was attempted: {}", pos, ritual.describe());
            }
        } 
        else 
        {
            targets = gatherSummonTargets(level, pos, ritual);
        }

        for (Entity entity : targets) 
        {
            if (entity instanceof AbstractEntityMinecoloniesRaider) 
            {
                // Generate random offset within nearby blocks in X and Z
                int offsetX = level.random.nextInt(16) - 8;
                int offsetZ = level.random.nextInt(16) - 8;
                BlockPos targetPos = pos.offset(offsetX, 3, offsetZ);

                // Teleport them near the ritual location
                entity.teleportTo(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);

            }
        }

        MCTradePostMod.LOGGER.info("Summoning ritual completed at {}, bringing {} entities.", pos, targets.size());
        showRitualEffect(level, pos);

        return true;
    }

    /**
     * Processes a transformation ritual at the specified BlockPos within the ServerLevel.
     * This ritual transforms companion items into a target item, as defined in the ritual.
     * If the number of companion items is insufficient, the ritual cannot proceed.
     *
     * @param level the ServerLevel where the ritual is taking place
     * @param pos the BlockPos of the wishing well structure
     * @param ritual the ritual definition containing the target item and companion item count
     * @param state the current state of the ritual, including companion item count
     * 
     * @return RitualResult indicating whether the ritual was completed, needed ingredients,
     *         or failed due to an error
     */
    public static RitualResult processRitualTransform(ServerLevel level, BlockPos pos, RitualDefinitionHelper ritual, RitualState state)
    {
        if (state.companionCount < ritual.companionItemCount()) 
        {
            return RitualResult.NEEDS_INGREDIENTS;
        }

        try
        {
            Item targetItem = ritual.getTargetAsItem();

            if (targetItem == null) 
            {
                return RitualResult.FAILED;
            }   

            MCTPInventoryUtils.dropItemsInWorld(level, pos, new ItemStack(targetItem, ritual.companionItemCount()));
        }
        catch (Exception e)
        {
            MCTradePostMod.LOGGER.error("Failed to drop items for ritual", e);
            return RitualResult.FAILED;
        }
        showRitualEffect(level, pos);
        return RitualResult.COMPLETED;
    }

    /**
     * Triggers an effect based on the companion item thrown into the wishing well.
     * If the companion item is rotten flesh, all zombies within a 16-block radius
     * are removed, simulating a "zombie purge" ritual. If the item is an iron sword,
     * a "raid suppression" ritual is logged. If the item is neither, an unknown
     * ritual item message is logged.
     *
     * @param level the server level where the ritual is taking place
     * @param pos the position of the wishing well
     * @param companionItem the item thrown into the wishing well alongside the coin
     * 
     * @return true if the ritual was triggered, false otherwise
     */
    private static RitualResult triggerRitual(ServerLevel level, RitualState state, BlockPos pos, Item companionItem) 
    {
        MCTradePostMod.LOGGER.info("Processing rituals at {} with companion item {} and {} coins", pos, companionItem, state.coins);    
        Collection<RitualDefinitionHelper> rituals = RitualManager.getAllRituals().values();

        for (RitualDefinitionHelper ritual : rituals) 
        {
            Item effectCompanion = BuiltInRegistries.ITEM.get(ritual.companionItem());

            if (effectCompanion.equals(companionItem)) 
            {
                if (ritual.requiredCoins() > state.coins) 
                {
                    return RitualResult.NEEDS_INGREDIENTS;
                }
                /* 
                 * To add a new ritual *type*, it needs an entry here (with associated handler function) 
                 * and in RitualDefintionHelper.describe() to set up the JEI with the description
                 */
                switch (ritual.effect()) 
                {
                    case RitualManager.RITUAL_EFFECT_SLAY:
                        if (processRitualSlay(level, pos, ritual)) 
                        {
                            return RitualResult.COMPLETED;
                        } else {
                            return RitualResult.FAILED;
                        }


                    case RitualManager.RITUAL_EFFECT_WEATHER:
                        if (processRitualWeather(level, pos, ritual) ) 
                        {
                            return RitualResult.COMPLETED;
                        } else {
                            return RitualResult.FAILED;
                        }

                    case RitualManager.RITUAL_EFFECT_SUMMON:
                        if (processRitualSummon(level, pos, ritual) ) 
                        {
                            return RitualResult.COMPLETED;
                        } else {
                            return RitualResult.FAILED;
                        }

                    case RitualManager.RITUAL_EFFECT_TRANSFORM:
                        return processRitualTransform(level, pos, ritual, state);

                    default:
                        MCTradePostMod.LOGGER.warn("Unknown ritual effect: {}", ritual.effect());
                        break;
                }
            
            }
        }

        return RitualResult.UNRECOGNIZED;
    }

    public enum RitualResult 
    {
        UNRECOGNIZED,
        NEEDS_INGREDIENTS,
        FAILED,
        COMPLETED
    }
    static public class RitualState 
    {
        public int coins = 0;
        public int companionCount = 0;
        public long lastUsed = 0L;
    }
} 
