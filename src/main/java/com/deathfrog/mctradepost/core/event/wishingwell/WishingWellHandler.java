// Part 1: WishingWellHandler to manage fountain rituals
package com.deathfrog.mctradepost.core.event.wishingwell;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.advancements.MCTPAdvancementTriggers;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.blocks.BlockMixedStone;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.entity.CoinEntity;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.CommunityRitualProcessor;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.OutpostRitualProcessor;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualDefinitionHelper;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualManager;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualState;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualState.RitualResult;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.SummonRitualProcessor;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.TransformRitualProcessor;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.WeatherRitualProcessor;
import com.deathfrog.mctradepost.item.CoinItem;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.util.AdvancementUtils;
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
                for (IBuilding building : colony.getServerBuildingManager().getBuildings().values())
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
    public static void showRitualEffect(@Nonnull ServerLevel level, @Nonnull BlockPos pos)
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
                        if (SummonRitualProcessor.processRitualSlay(level, pos, ritual))
                        {
                            result = RitualResult.COMPLETED;
                        }
                        else
                        {
                            result = RitualResult.FAILED;
                        }
                        break;

                    case RitualManager.RITUAL_EFFECT_WEATHER:
                        if (WeatherRitualProcessor.processRitualWeather(level, pos, ritual))
                        {
                            result = RitualResult.COMPLETED;
                        }
                        else
                        {
                            result = RitualResult.FAILED;
                        }
                        break;

                    case RitualManager.RITUAL_EFFECT_SUMMON:
                        if (SummonRitualProcessor.processRitualSummon(level, pos, ritual, state))
                        {
                            result = RitualResult.COMPLETED;
                        }
                        else
                        {
                            result = RitualResult.FAILED;
                        }
                        break;

                    case RitualManager.RITUAL_EFFECT_TRANSFORM:
                        result = TransformRitualProcessor.processRitualTransform(level, pos, ritual, state);
                        break;

                    case RitualManager.RITUAL_EFFECT_COMMUNITY:
                        result = CommunityRitualProcessor.processRitualCommunity(marketplace, pos, ritual, state);
                        break;

                    case RitualManager.RITUAL_EFFECT_OUTPOST:
                        result = OutpostRitualProcessor.processRitualOutpost(marketplace, pos, ritual, state);
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
