// Part 1: WishingWellHandler to manage fountain rituals
package com.deathfrog.mctradepost.core.event.wishingwell;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.advancements.MCTPAdvancementTriggers;
import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.api.research.MCTPResearchConstants;
import com.deathfrog.mctradepost.api.util.MCTPInventoryUtils;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.SoundUtils;
import com.deathfrog.mctradepost.core.blocks.BlockMixedStone;
import com.deathfrog.mctradepost.core.blocks.BlockOutpostMarker;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.entity.CoinEntity;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualDefinitionHelper;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualManager;
import com.minecolonies.api.colony.ICitizenData;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualState;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualState.RitualResult;
import com.deathfrog.mctradepost.item.CoinItem;
import com.deathfrog.mctradepost.item.OutpostClaimMarkerItem;
import com.deathfrog.mctradepost.item.WishGatheringItem;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.colonyEvents.IColonyEvent;
import com.minecolonies.api.colony.colonyEvents.IColonyRaidEvent;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenDiseaseHandler;
import com.minecolonies.api.entity.citizen.happiness.ExpirationBasedHappinessModifier;
import com.minecolonies.api.entity.citizen.happiness.StaticHappinessSupplier;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.constant.HappinessConstants;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.datalistener.model.Disease;
import com.minecolonies.core.network.messages.client.CircleParticleEffectMessage;
import com.minecolonies.core.util.AdvancementUtils;
import com.minecolonies.core.util.ChunkDataHelper;
import com.mojang.logging.LogUtils;
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
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.*;
import javax.annotation.Nonnull;
import org.slf4j.Logger;

@EventBusSubscriber(modid = MCTradePostMod.MODID)
public class WishingWellHandler
{
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final int MAX_WISHINGWELL_COOLDOWN = 100;
    private static int wishingWellCooldown = MAX_WISHINGWELL_COOLDOWN;

    public static final String RAIDER_TAG = "minecolonies:raider";

    /**
     * Handles level tick events for server-side wishing well structures. This method checks each registered wishing well location to
     * ensure it is a valid structure. It identifies items within the well's vicinity, differentiating between coin items and companion
     * items. If both types of items are present, it triggers an effect, removes the items, and updates the ritual state for the well.
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

        if (level == null)
        {
            return;
        }

        IColonyManager colonyManager = IColonyManager.getInstance();
        List<IColony> colonyList = colonyManager.getAllColonies();

        if (colonyList != null)
        {
            for (IColony colony : colonyList)
            {
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
     * Processes the rituals for a given marketplace building. This method goes through each registered wishing well location in the
     * marketplace and checks for the presence of both coin items and companion items. If both are present, it triggers an effect and
     * updates the ritual state for the well.
     *
     * @param level       The server level containing the marketplace building.
     * @param marketplace The marketplace building containing the wishing wells.
     */
    protected static void processMarketplaceRituals(@Nonnull ServerLevel level, @Nonnull BuildingMarketplace marketplace)
    {
        WellLocations data = marketplace.getRitualData();
        Set<BlockPos> wells = data.getKnownWells();
        Map<BlockPos, RitualState> rituals = data.getActiveRituals();

        CoinItem coinItem = MCTradePostMod.MCTP_COIN_ITEM.get();
        CoinItem goldCoin = MCTradePostMod.MCTP_COIN_GOLD.get();
        CoinItem diamondCoin = MCTradePostMod.MCTP_COIN_DIAMOND.get();

        if (coinItem == null || goldCoin == null || diamondCoin == null)
        {
            throw new IllegalStateException("Trade Post Coin items not initialized. This should never happen. Please report.");
        }

        for (BlockPos pos : wells)
        {
            if (pos == null || BlockPos.ZERO.equals(pos))
            {
                continue;
            }

            AABB wellBox = new AABB(pos).inflate(1.5);
            List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, NullnessBridge.assumeNonnull(wellBox));

            final List<ItemEntity> baseCoinItems = new ArrayList<>();
            final List<ItemEntity> goldCoinItems = new ArrayList<>();
            final List<ItemEntity> diamondCoinItems = new ArrayList<>();
            final List<ItemEntity> companions = new ArrayList<>();

            for (ItemEntity ent : items)
            {
                ItemStack stack = ent.getItem();
                if (ent instanceof CoinEntity)
                {
                    if (stack.is(coinItem))
                    {
                        baseCoinItems.add(ent);
                    }
                    else if (stack.is(goldCoin))
                    {
                        goldCoinItems.add(ent);
                    }
                    else if (stack.is(diamondCoin))
                    {
                        diamondCoinItems.add(ent);
                    }
                }
                else
                {
                    companions.add(ent);
                }
            }

            int totalCoinStacks = baseCoinItems.size() + goldCoinItems.size() + diamondCoinItems.size();

            if (totalCoinStacks > 0 && !companions.isEmpty())
            {
                Item companionType = companions.getFirst().getItem().getItem();

                List<ItemEntity> sameTypeCompanions = companions.stream().filter(e -> e.getItem().getItem() == companionType).toList();

                BlockPos center = pos.immutable();

                if (center == null || center.equals(BlockPos.ZERO))
                {
                    continue;
                }

                RitualState state = rituals.computeIfAbsent(center, k -> new RitualState());

                state.baseCoins = baseCoinItems;
                state.goldCoins = goldCoinItems;
                state.diamondCoins = diamondCoinItems;
                state.companionItems = sameTypeCompanions;
                state.lastUsed = System.currentTimeMillis();

                RitualResult result = triggerRitual(marketplace, state, center, companionType);

                switch (result)
                {
                    case FAILED:
                        ejectItems(level, baseCoinItems, center);
                        ejectItems(level, goldCoinItems, center);
                        ejectItems(level, diamondCoinItems, center);
                        ejectItems(level, sameTypeCompanions, center);
                        break;

                    case COMPLETED:
                        LOGGER.info("Wishing well {} at {} with companion item {}", result, center, companionType);
                        sameTypeCompanions.forEach(Entity::discard); // remove every companion
                        rituals.remove(pos);

                        AdvancementUtils.TriggerAdvancementPlayersForColony(marketplace.getColony(), player -> {
                            if (player != null)
                            {
                                MCTPAdvancementTriggers.MAKE_WISH.get().trigger(player);
                            }
                        });

                        break;

                    case UNRECOGNIZED:
                        // An unrecognized companion item has been used, or something else caused the ritual to fail.
                        // Discard the companion item, but leave the ritual active for a valid companion item to be added.
                        LOGGER.warn("Wishing well activated with unknown ritual at {} with companion item {}", center, companionType);
                        ejectItems(level, sameTypeCompanions, center);
                        break;

                    case NEEDS_INGREDIENTS:
                        // We're just waiting for more coins to be added...
                        break;
                }
            }
        }
    }

    /**
     * Visual effect for the wishing well when a ritual is triggered. A lightning bolt is spawned at the center of the well, and
     * additional particles and a sound effect are played.
     * 
     * @param level the server level
     * @param pos   the position of the well
     */
    private static void showRitualEffect(@Nonnull ServerLevel level, @Nonnull BlockPos pos)
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
        level.sendParticles(NullnessBridge
            .assumeNonnull(ParticleTypes.ENCHANT), pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 20, 0.5, 0.5, 0.5, 0.1);
        level.playSound(null, pos, NullnessBridge.assumeNonnull(SoundEvents.TOTEM_USE), SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    /**
     * Registers a BlockPos as a valid wishing well location. This is used to identify wells in the world as they are used, so that the
     * wishing well handler can find them later when checking for active rituals.
     * 
     * @param pos The BlockPos of the center of the wishing well structure.
     */
    public static void registerWell(BuildingMarketplace marketplace, BlockPos pos)
    {
        Set<BlockPos> wells = marketplace.getRitualData().getKnownWells();
        wells.add(pos);

        ServerLevel level = (ServerLevel) marketplace.getColony().getWorld();

        // Additional particles and sound
        level.sendParticles(NullnessBridge
            .assumeNonnull(ParticleTypes.GLOW), pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 20, 0.5, 0.5, 0.5, 0.1);
        level.playSound(null, pos, NullnessBridge.assumeNonnull(SoundEvents.GENERIC_SPLASH), SoundSource.BLOCKS, 1.0f, 1.0f);

        LOGGER.info("Registered wishing well at {}", pos);
    }

    /**
     * Unregisters a BlockPos as a valid wishing well location, if it exists. This allows for removing wells that have been destroyed.
     * 
     * @param pos The BlockPos of the center of the wishing well structure.
     */
    public static void unregisterWell(BuildingMarketplace marketplace, BlockPos pos)
    {
        Set<BlockPos> wells = marketplace.getRitualData().getKnownWells();

        if (wells.contains(pos))
        {
            wells.remove(pos);
            LOGGER.info("Removed invalid wishing well at {}", pos);
        }
        else
        {
            LOGGER.info("No valid wishing well discovered at {}", pos);
        }
    }

    /**
     * Checks if there is a valid wishing well structure nearby. If there is, the center of the structure is added to the set of valid
     * wishing well locations. This is used to discover wishing wells in the world as they are used, so that the wishing well handler
     * can find them later when checking for active rituals.
     */
    public static void downInAWell(Level level, BuildingMarketplace marketplace, BlockPos pos)
    {
        boolean discovery = false;

        if (marketplace == null)
        {
            LOGGER.warn("Attempting to evaluate a wishing well without a Marketplace at {}", pos);
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

        if (!discovery)
        {
            unregisterWell(marketplace, pos);
        }
    }

    /**
     * Checks if a given BlockPos is the center of a valid wishing well structure. A valid wishing well structure has water in the
     * bottom 2x2 square, and stone brick blocks in the 3x3 ring around it.
     * 
     * @param level  the level to check in
     * @param center the BlockPos of the center of the structure to check
     * @return true if the BlockPos is the center of a valid wishing well structure, false otherwise
     */
    public static boolean isWishingWellStructure(Level level, BlockPos center)
    {
        // Check 2x2 water at center
        for (int dx = -1; dx <= 0; dx++)
        {
            for (int dz = -1; dz <= 0; dz++)
            {
                BlockPos base = center.offset(dx, 0, dz);

                if (base == null || BlockPos.ZERO.equals(base)) continue;

                BlockState state = level.getBlockState(base);

                if (!(state.getBlock() instanceof LiquidBlock) ||
                    !state.getFluidState().is(NullnessBridge.assumeNonnull(FluidTags.WATER)))
                {
                    // LOGGER.info("This is not water at {} during well structure identification.", base);
                    return false;
                }
            }
        }

        BlockMixedStone mixedStoneBlock = MCTradePostMod.MIXED_STONE.get();

        if (mixedStoneBlock == null)
        {
            throw new IllegalStateException("Trade Post mixed stone block not found. This should never happen. Please report this.");
        }

        // Check surrounding 4x4 ring of stone bricks
        for (int dx = -2; dx <= 1; dx++)
        {
            for (int dz = -2; dz <= 1; dz++)
            {
                // Skip 2x2 center
                if (dx >= -1 && dx <= 0 && dz >= -1 && dz <= 0) continue;

                BlockPos ring = center.offset(dx, 0, dz);

                if (ring == null || BlockPos.ZERO.equals(ring)) continue;

                if (!level.getBlockState(ring).is(mixedStoneBlock))
                {
                    return false;
                }
            }
        }

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
    private static boolean processRitualSlay(@Nonnull ServerLevel level, @Nonnull BlockPos pos, RitualDefinitionHelper ritual)
    {
        List<? extends Entity> targets = gatherSummonTargets(level, pos, ritual);

        targets.forEach(Entity::discard);

        LOGGER.info("Slay ritual of {} completed at {} with a radius of {}, slaying {}",
            ritual.target(),
            pos,
            ritual.radius(),
            targets.size());
        showRitualEffect(level, pos);

        return true;
    }

    /**
     * Processes a weather ritual at the specified BlockPos within the ServerLevel. Based on the ritual definition, it sets the weather
     * to clear, rain, or storm for the remainder of the day. If the target weather type is unknown, the ritual is ignored.
     *
     * @param level  the ServerLevel where the ritual is taking place
     * @param pos    the BlockPos of the wishing well structure
     * @param ritual the ritual definition containing the target weather type
     * @return true if the ritual was successfully triggered, false otherwise
     */
    private static boolean processRitualWeather(@Nonnull ServerLevel level, @Nonnull BlockPos pos, RitualDefinitionHelper ritual)
    {
        int restOfDay = 24000 - (int) (level.getDayTime() % 24000);
        int clearTime = 0;
        int weatherTime = 0;
        boolean isRaining = false;
        boolean isThundering = false;

        String weather = ritual.target();
        // LOGGER.info("Ritual target {} resolved to {}", ritual.target(), entityType);

        if (weather == null)
        {
            LOGGER.info("No weather target provided during Weather ritual.");
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
            LOGGER.info("Unknown weather ritual of {} ignored at {} ", weather, pos);
            return false;
        }

        level.setWeatherParameters(clearTime, weatherTime, isRaining, isThundering);

        LOGGER.info("Weather ritual of {} completed at {} ", weather, pos);
        showRitualEffect(level, pos);

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
    private static boolean processRitualSummon(@Nonnull ServerLevel level, @Nonnull BlockPos pos, RitualDefinitionHelper ritual, RitualState state)
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
        showRitualEffect(level, pos);

        return true;
    }

    /**
     * Processes a transformation ritual at the specified BlockPos within the ServerLevel. This ritual transforms companion items into
     * a target item, as defined in the ritual. If the number of companion items is insufficient, the ritual cannot proceed.
     *
     * @param level  the ServerLevel where the ritual is taking place
     * @param pos    the BlockPos of the wishing well structure
     * @param ritual the ritual definition containing the target item and companion item count
     * @param state  the current state of the ritual, including companion item count
     * @return RitualResult indicating whether the ritual was completed, needed ingredients, or failed due to an error
     */
    public static RitualResult processRitualTransform(@Nonnull ServerLevel level,
        @Nonnull BlockPos pos,
        RitualDefinitionHelper ritual,
        RitualState state)
    {
        if (state.getCompanionCount() < ritual.companionItemCount())
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
            LOGGER.error("Failed to drop items for ritual", e);
            return RitualResult.FAILED;
        }
        showRitualEffect(level, pos);
        return RitualResult.COMPLETED;
    }

    /**
     * Processes a community ritual at the specified BlockPos within the ServerLevel. This ritual gives a random effect to each citizen
     * in the colony, based on the companion item used. If the number of companion items is insufficient, the ritual cannot proceed.
     * 
     * @param marketplace the marketplace where the ritual is taking place
     * @param pos         the BlockPos of the wishing well structure
     * @param ritual      the ritual definition containing the target item and companion item count
     * @param state       the current state of the ritual, including companion item count
     * @return RitualResult indicating whether the ritual was completed, needed ingredients, or failed due to an error
     */
    public static RitualResult processRitualCommunity(@Nonnull BuildingMarketplace marketplace,
        @Nonnull BlockPos pos,
        RitualDefinitionHelper ritual,
        RitualState state)
    {
        ServerLevel currentLevel = (ServerLevel) marketplace.getColony().getWorld();

        if (currentLevel == null)
        {
            return RitualResult.FAILED;
        }

        if (state.getCompanionCount() < ritual.companionItemCount())
        {
            return RitualResult.NEEDS_INGREDIENTS;
        }

        try
        {
            Item companionItem = BuiltInRegistries.ITEM.get(ritual.companionItem());
            List<ICitizenData> citizens = marketplace.getColony().getCitizenManager().getCitizens();

            for (ICitizenData citizen : citizens)
            {
                if (!citizen.getEntity().isPresent())
                {
                    continue;
                }

                AbstractEntityCitizen entity = citizen.getEntity().get();

                if (companionItem.equals(MCTradePostMod.WISH_PLENTY.get()))
                {
                    if (citizen.getEntity().isPresent())
                    {
                        entity.setHealth(entity.getMaxHealth());
                        citizen.setSaturation(ICitizenData.MAX_SATURATION);
                        citizen.setJustAte(true);
                        citizen.getCitizenHappinessHandler()
                            .addModifier(new ExpirationBasedHappinessModifier(HappinessConstants.HADGREATFOOD,
                                2.0,
                                new StaticHappinessSupplier(2.0),
                                5));

                        entity.playSound(NullnessBridge.assumeNonnull(SoundEvents.NOTE_BLOCK_HARP.value()),
                            (float) SoundUtils.BASIC_VOLUME,
                            (float) com.minecolonies.api.util.SoundUtils.getRandomPentatonic(entity.getRandom()));
                        new CircleParticleEffectMessage(entity.position().add(0, 2, 0), ParticleTypes.HAPPY_VILLAGER, 1)
                            .sendToTrackingEntity(entity);
                    }
                }
                else if (companionItem.equals(MCTradePostMod.WISH_HEALTH.get()))
                {
                    ICitizenDiseaseHandler diseaseHandler = citizen.getCitizenDiseaseHandler();
                    final Disease disease = diseaseHandler.getDisease();
                    if (disease != null)
                    {
                        diseaseHandler.cure();
                        entity.playSound(NullnessBridge.assumeNonnull(SoundEvents.NOTE_BLOCK_HARP.value()),
                            (float) SoundUtils.BASIC_VOLUME,
                            (float) com.minecolonies.api.util.SoundUtils.getRandomPentatonic(entity.getRandom()));
                        new CircleParticleEffectMessage(entity.position().add(0, 2, 0), ParticleTypes.HAPPY_VILLAGER, 1)
                            .sendToTrackingEntity(entity);
                    }
                }
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to process community ritual.", e);
            return RitualResult.FAILED;
        }

        showRitualEffect(currentLevel, pos);
        return RitualResult.COMPLETED;
    }

    /**
     * Processes an outpost ritual at the specified BlockPos within the ServerLevel. This ritual sets up an outpost claim marker at the
     * specified BlockPos and connects it to the nearest station. It handles all validations to ensure outposts are in valid locations.
     * 
     * @param level       the ServerLevel where the ritual is taking place
     * @param pos         the BlockPos of the wishing well structure
     * @param ritual      the ritual definition containing the target item and companion item count
     * @param state       the current state of the ritual, including companion item count
     * @param marketplace the BuildingMarketplace containing the Colony and its buildings
     * @return RitualResult indicating whether the ritual was completed, needed ingredients, or failed due to an error
     **/
    public static RitualResult processRitualOutpost(@Nonnull BuildingMarketplace marketplace,
        @Nonnull BlockPos pos,
        RitualDefinitionHelper ritual,
        RitualState state)
    {
        ServerLevel level = (ServerLevel) marketplace.getColony().getWorld();

        if (level == null)
        {
            return RitualResult.FAILED;
        }

        if (state.getCompanionCount() < ritual.companionItemCount())
        {
            return RitualResult.NEEDS_INGREDIENTS;
        }

        if (!MCTPConfig.outpostEnabled.get())
        {
            LOGGER.warn("The Outpost is disabled on this server.", state.getCompanionCount());
            MessageUtils.format("The Outpost is disabled on this server.").sendTo(marketplace.getColony()).forAllPlayers();
            return RitualResult.FAILED;
        }

        IBuilding outpostBuilding =
            marketplace.getColony().getBuildingManager().getFirstBuildingMatching(b -> b.getBuildingType() == ModBuildings.outpost);
        if (outpostBuilding != null)
        {
            LOGGER.warn("Only one outpost may be claimed per colony.", state.getCompanionCount());
            MessageUtils.format("Only one outpost may be claimed per colony.").sendTo(marketplace.getColony()).forAllPlayers();
            return RitualResult.FAILED;
        }

        double outpostClaimLevel =
            marketplace.getColony().getResearchManager().getResearchEffects().getEffectStrength(MCTPResearchConstants.OUTPOST_CLAIM);

        if (outpostClaimLevel == 0)
        {
            MessageUtils.format("To wish for an outpost claim you must first complete the research.")
                .sendTo(marketplace.getColony())
                .forAllPlayers();
            return RitualResult.FAILED;
        }

        try
        {
            if (state.getCompanionCount() != 1)
            {
                LOGGER.warn(
                    "Outpost ritual called with incorrect number of companion items ({}). One and only one outpost claim marker is required.",
                    state.getCompanionCount());
                MessageUtils.format("One and only one outpost claim marker is required.")
                    .sendTo(marketplace.getColony())
                    .forAllPlayers();
                return RitualResult.FAILED;
            }

            ItemStack companionItem = state.companionItems.get(0).getItem();

            if (!(companionItem.getItem() instanceof OutpostClaimMarkerItem))
            {
                LOGGER.warn("Outpost ritual called with unrecognized companion item.");
                MessageUtils.format("Outpost ritual called with unrecognized companion item.")
                    .sendTo(marketplace.getColony())
                    .forAllPlayers();
                return RitualResult.FAILED;
            }

            BlockPos claimLocation = OutpostClaimMarkerItem.getLinkedBlockPos(companionItem);

            if (claimLocation == null || BlockPos.ZERO.equals(claimLocation))
            {
                LOGGER.warn("Outpost ritual called with unset claim location.");
                MessageUtils.format("Outpost ritual called with unset claim location.")
                    .sendTo(marketplace.getColony())
                    .forAllPlayers();
                return RitualResult.FAILED;
            }

            boolean connected = false;
            Colony colony = (Colony) marketplace.getColony();

            BlockPos colonyCenter = colony.getCenter();
            int maxDistance = MCTPConfig.maxDistance.get();

            if (colonyCenter.distSqr(claimLocation) > (maxDistance * maxDistance))
            {
                LOGGER.warn("Outpost ritual called distance greater than max distance.");
                MessageUtils.format("Outpost distance is too large - the maximum is " + maxDistance + ".")
                    .sendTo(marketplace.getColony())
                    .forAllPlayers();
                return RitualResult.FAILED;
            }

            List<BuildingStation> stations = new ArrayList<>();

            Collection<IBuilding> buildings = colony.getBuildingManager().getBuildings().values();
            for (IBuilding building : buildings)
            {
                if (building instanceof BuildingStation station)
                {
                    stations.add(station);
                    TrackConnectionResult result =
                        TrackPathConnection.arePointsConnectedByTracks((ServerLevel) marketplace.getColony().getWorld(),
                            claimLocation,
                            station.getRailStartPosition(),
                            true);

                    if (result.isConnected())
                    {
                        connected = true;
                        break;
                    }
                }
            }

            if (stations.isEmpty())
            {
                LOGGER.warn("A station is required for the outpost ritual.");
                MessageUtils.format("A station is required for the outpost ritual.").sendTo(marketplace.getColony()).forAllPlayers();
                return RitualResult.FAILED;
            }

            if (connected)
            {
                final int range = 1;
                boolean canClaim = ChunkDataHelper.canClaimChunksInRange(colony.getWorld(), claimLocation, range + 1);

                if (canClaim)
                {
                    ChunkDataHelper.staticClaimInRange(colony, true, claimLocation, range, (ServerLevel) colony.getWorld(), false);
                    BlockOutpostMarker.placeOutpostMarker(level, claimLocation, null);
                }
                else
                {
                    LOGGER.warn("The attempted claim is too close to another colony.");
                    MessageUtils.format("The attempted claim is too close to another colony.")
                        .sendTo(marketplace.getColony())
                        .forAllPlayers();
                    return RitualResult.FAILED;
                }
            }
            else
            {
                LOGGER.warn("The outpost claim at {} must be connected to one of these train stations {} via track.",
                    claimLocation,
                    stations);
                MessageUtils.format("The outpost claim location must be connected to a train station via track.")
                    .sendTo(marketplace.getColony())
                    .forAllPlayers();
                return RitualResult.FAILED;
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to drop items for ritual", e);
            return RitualResult.FAILED;
        }

        showRitualEffect(level, pos);
        MessageUtils.format("An outpost has been claimed at " + pos.toShortString()).sendTo(marketplace.getColony()).forAllPlayers();

        return RitualResult.COMPLETED;
    }

    /**
     * Triggers an effect based on the companion item thrown into the wishing well. If the companion item is rotten flesh, all zombies
     * within a 16-block radius are removed, simulating a "zombie purge" ritual. If the item is an iron sword, a "raid suppression"
     * ritual is logged. If the item is neither, an unknown ritual item message is logged.
     *
     * @param level         the server level where the ritual is taking place
     * @param pos           the position of the wishing well
     * @param companionItem the item thrown into the wishing well alongside the coin
     * @return true if the ritual was triggered, false otherwise
     */
    private static RitualResult triggerRitual(@Nonnull BuildingMarketplace marketplace,
        RitualState state,
        @Nonnull BlockPos pos,
        Item companionItem)
    {
        LOGGER.info("Processing rituals at {} with companion item {} and {} base coins, {} gold coins and {} diamond coins.",
            pos,
            companionItem,
            state.entityCount(state.baseCoins),
            state.entityCount(state.goldCoins),
            state.entityCount(state.diamondCoins));

        ServerLevel level = (ServerLevel) marketplace.getColony().getWorld();

        if (level == null)
        {
            return RitualResult.FAILED;
        }

        Collection<RitualDefinitionHelper> rituals = RitualManager.getAllRituals().values();

        for (RitualDefinitionHelper ritual : rituals)
        {
            Item effectCompanion = BuiltInRegistries.ITEM.get(ritual.companionItem());

            if (effectCompanion.equals(companionItem))
            {
                ItemStorage requiredCoins = new ItemStorage(ritual.getCoinAsItem(), ritual.requiredCoins());

                if (!state.meetsRequirements(requiredCoins))
                {
                    return RitualResult.NEEDS_INGREDIENTS;
                }

                RitualResult result = RitualResult.UNRECOGNIZED;

                /* 
                 * To add a new ritual *type*, it needs an entry here (with associated handler function) 
                 * and in RitualDefintionHelper.describe() to set up the JEI with the description
                 */
                switch (ritual.effect())
                {
                    case RitualManager.RITUAL_EFFECT_SLAY:
                        if (processRitualSlay(level, pos, ritual))
                        {
                            result = RitualResult.COMPLETED;
                        }
                        else
                        {
                            result = RitualResult.FAILED;
                        }
                        break;

                    case RitualManager.RITUAL_EFFECT_WEATHER:
                        if (processRitualWeather(level, pos, ritual))
                        {
                            result = RitualResult.COMPLETED;
                        }
                        else
                        {
                            result = RitualResult.FAILED;
                        }
                        break;

                    case RitualManager.RITUAL_EFFECT_SUMMON:
                        if (processRitualSummon(level, pos, ritual, state))
                        {
                            result = RitualResult.COMPLETED;
                        }
                        else
                        {
                            result = RitualResult.FAILED;
                        }
                        break;

                    case RitualManager.RITUAL_EFFECT_TRANSFORM:
                        result = processRitualTransform(level, pos, ritual, state);
                        break;

                    case RitualManager.RITUAL_EFFECT_COMMUNITY:
                        result = processRitualCommunity(marketplace, pos, ritual, state);
                        break;

                    case RitualManager.RITUAL_EFFECT_OUTPOST:
                        result = processRitualOutpost(marketplace, pos, ritual, state);
                        break;

                    default:
                        LOGGER.warn("Unknown ritual effect: {}", ritual.effect());
                        break;
                }

                if (result == RitualResult.COMPLETED)
                {
                    state.burnCoins(requiredCoins);
                }

                return result;
            }
        }

        return RitualResult.UNRECOGNIZED;
    }

    /**
     * Ejects all items in the provided list to a random location 2-4 blocks away from the well, with a slight upward velocity. Each
     * item is removed from the well and replaced with a new copy at the target location.
     * 
     * @param level   the ServerLevel in which the items are ejected
     * @param items   the list of items to eject
     * @param wellPos the position of the well
     */
    private static void ejectItems(ServerLevel level, List<ItemEntity> items, BlockPos wellPos)
    {
        for (ItemEntity entity : items)
        {
            ItemStack stack = entity.getItem().copy(); // preserve stack

            if (stack.isEmpty())
            {
                continue;
            }

            entity.discard(); // remove from well

            // Random offset from well (2–4 blocks away, random direction)
            double angle = level.random.nextDouble() * 2 * Math.PI;
            double distance = 2.0 + level.random.nextDouble() * 2.0; // between 2 and 4 blocks
            double xOffset = Math.cos(angle) * distance;
            double zOffset = Math.sin(angle) * distance;

            BlockPos target = wellPos.offset((int) xOffset, 1, (int) zOffset);

            // Spawn the new item with a little upward velocity
            ItemEntity ejected = new ItemEntity(level, target.getX() + 0.5, target.getY(), target.getZ() + 0.5, stack);

            ejected.setDeltaMovement(0.05 * -xOffset, 0.2, 0.05 * -zOffset); // toss gently away from the well
            level.addFreshEntity(ejected);
        }
    }
}
