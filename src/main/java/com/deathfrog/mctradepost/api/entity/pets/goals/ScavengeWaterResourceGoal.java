package com.deathfrog.mctradepost.api.entity.pets.goals;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_PETGOALS;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.entity.pets.PetRoles;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.PathingUtil;
import com.deathfrog.mctradepost.api.util.PetUtil;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.StatsUtil;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ScavengeWaterResourceGoal<P extends Animal & ITradePostPet> extends Goal
{
    public static final Logger LOGGER = LogUtils.getLogger();
    // check every 8 ticks (~0.4s)
    private static final int CANUSE_PERIOD = 8;
    
    // how long we’ll allow a pre-empted run to resume (ticks)
    private static final int RESUME_GRACE_TICKS = 100; // ~5s

    // Number of tries to search for items at a given target position before moving on.
    private static final int MAX_SEARCH_TRIES = 10;

    public static final String WATER_SCAVENGE_TAG = "amphibious_scavenge";
    public static final String LOOT_BASE = "pet/" + WATER_SCAVENGE_TAG;
    private static final ResourceLocation WATER_SCAVENGE_TAG_RESOURCE = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, WATER_SCAVENGE_TAG);
    private static final TagKey<Block> WATER_SCAVENGE_BLOCK_TAG =
        TagKey.create(NullnessBridge.assumeNonnull(Registries.BLOCK), NullnessBridge.assumeNonnull(WATER_SCAVENGE_TAG_RESOURCE));


    private final P pet;
    private final int searchRadius;
    private final float chanceToFind;
    private final IBuilding trainerBuilding;
    private final int cooldownTicks;

    private long resumeUntilTick = 0L;   // <= this tick we can resume a paused run
    private BlockPos targetPos;
    private BlockPos lastPetPos;
    private int stuckCount = 0;
    private BlockPos navigationPos;
     // remembers target used at start()
    private BlockPos lastStartTarget = null;
    private boolean hasArrived = false;
    private long lastScavengeTime = 0;
    private long nextGateTime = 0L; 
    private int searchTries = MAX_SEARCH_TRIES;

    public ScavengeWaterResourceGoal(P pet, int searchRadius, float chanceToFind, IBuilding trainerBuilding, int cooldownTicks)
    {
        this.pet = pet;
        this.searchRadius = searchRadius;
        this.chanceToFind = chanceToFind;
        this.trainerBuilding = trainerBuilding;
        this.cooldownTicks = cooldownTicks;
        this.lastScavengeTime = -cooldownTicks;
        this.setFlags(NullnessBridge.assumeNonnull(EnumSet.of(Goal.Flag.MOVE)));
    }

    /**
     * Determines if the scavenge goal can be used. This goal can be used if the pet is not leashed, not a passenger, and is alive.
     * Additionally, there must be a valid scavenging role for the pet from its work location, and the pet must not have scavenged
     * within the cooldown period.
     *
     * @return true if the scavenge goal can be used, false otherwise
     */
    @Override
    public boolean canUse()
    {
        final Level level = pet.level();
        if (level == null) 
        {
            logBlock("No level for pet.");
            return false;
        }

        if (level.isClientSide)
        {
            return false;
        }

        if (!pet.isAlive() || pet.isPassenger() || pet.isLeashed())
        {
            logBlock("Can't scavenge: pet is dead, leashed, or a passenger");
            return false;
        }

        if (pet.getPetData() == null || pet.level() == null)
        {
             logBlock("Can't scavenge: pet data or level is null");
            return false;
        }

        if (!PetRoles.SCAVENGE_WATER.equals(pet.getPetData().roleFromWorkLocation(pet.level())))
        {
             logBlock("Can't scavenge: pet is not a water scavenger");
            return false;
        }

        // Resume only if we’re within the grace period
        if (this.targetPos != null && this.navigationPos != null && this.searchTries > 0)
        {
            long now = pet.level().getGameTime();
            if (now <= this.resumeUntilTick)
            {
                TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("Pet {} is resuming scavenge at {}", pet.getUUID(), this.targetPos));
                return true;
            }
            else
            {
                reset();
            }
        }

        long gameTime = pet.level().getGameTime();
        long cooldownCounter = gameTime - lastScavengeTime;

        if (cooldownCounter < cooldownTicks)
        {
            logBlock("Can't scavenge: cooldown {} of {}", cooldownCounter, cooldownTicks);
            return false;
        }

        boolean periodicGate = true;
        boolean probablisticGate = true;

        if (nextGateTime == 0L) 
        {
            // per-entity jitter so pets don't all align
            int jitter = (pet.getId() & (CANUSE_PERIOD - 1)); // 0..7 when period=8
            nextGateTime = gameTime + jitter;
        }

        if (gameTime < nextGateTime) 
        {
            periodicGate = false;
        }
        else 
        {
            nextGateTime = gameTime + CANUSE_PERIOD;
        }

        // Cheap probabilistic gate
        if (pet.getRandom().nextInt(4) != 0) 
        {
            probablisticGate = false;
        }

        if (!periodicGate || !probablisticGate)
        {
            final boolean finalPeriodicGate = periodicGate;
            final boolean finalProbablisticGate = probablisticGate;
            logBlock("Can't scavenge: periodic gate {} probablistic gate {}", finalPeriodicGate, finalProbablisticGate);
            return false;
        }

        targetPos = findWaterScavengeLocation();
        if (targetPos == null) 
        {
            logBlock("Can't scavenge: targetPosition is null");
            return false;
        }

        navigationPos = PathingUtil.findTopOfWaterColumn(pet.level(), targetPos);

        // If our target position is already at the top, use it as the navigation position.
        if (navigationPos == null) 
        {
            navigationPos = targetPos;
        }

        TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("Target position found during ScavengeWaterResourceGoal.canUse: {} - navigation to {}", targetPos, navigationPos));

        return true;
    }

    /**
     * Logs a message when the scavenge water resource goal is blocked.
     * 
     * <p>This is only active when the TRACING_PETGOALS trace is enabled.</p>
     * 
     * @param reason the reason the goal is blocked
     */
    private void logBlock(String format, Object... args) 
    {
        boolean debugging = TraceUtils.isTracing(TraceUtils.TRACE_PETGOALS);

        if (debugging)
        {
            LOGGER.info("[SCAVENGE-WATER][BLOCK] uuid={} tick={} reason=" + format, pet.getUUID(), pet.tickCount, args);
        }
    }

    /**
     * Begins moving the pet to the target scavenge location if it exists.
     */
    @Override
    public void start()
    {
        final P localPet = this.pet;

        if (localPet == null) return;

        final Level level = pet.level();
        if (level == null || level.isClientSide) return;

        boolean targetChanged = (lastStartTarget == null || !lastStartTarget.equals(this.targetPos));
        if (targetChanged)
        {
            this.hasArrived = false;
            this.searchTries = MAX_SEARCH_TRIES;
            this.lastStartTarget = this.targetPos == null ? null : this.targetPos.immutable();
            this.lastPetPos = null;
            this.stuckCount = 0;
        }
        if (this.targetPos == null) return;

        // Recompute nav anchor (water surface / ice edge); fallback to targetPos
        this.navigationPos = PathingUtil.findTopOfWaterColumn(pet.level(), targetPos);
        BlockPos anchor = (this.navigationPos != null) ? this.navigationPos : this.targetPos;

        boolean moved = anchor != null && PathingUtil.flexiblePathing(localPet, anchor, 1.0);

        if (!moved)
        {
            // TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("Could not path to {}, backing off (tries left: {})", anchor, searchTries - 1));
            // Don’t wipe target completely; let tick() try again next cycle
            if (--searchTries <= 0) 
            {
                reset();
            }
        }
    }

    /**
     * Determines whether the pet can continue using this goal.
     * 
     * @return true if the pet has a valid target position, the navigation is not done, and the pet has not arrived.
     */
    @Override
    public boolean canContinueToUse()
    {
        final Level level = pet.level();
        if (level == null || level.isClientSide) return false;

        if (targetPos == null)
        {
            // TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("No Target position found during ScavengeWaterResourceGoal.canContinueToUse"));

            return false;
        }

        // TraceUtils.dynamicTrace(TRACE_PETGOALS, 
        //    () -> LOGGER.info("Continuation of ScavengeWaterResourceGoal.canContinueToUse at {}: navigation done: {}, hasArrived: {}, searchTries: {}"
        //    , targetPos, pet.getNavigation().isDone(), hasArrived, searchTries));

        if (!hasArrived)
        {
            return !pet.getNavigation().isDone() || searchTries > 0;
        }
        else
        {
            // arrived → continue while we still have local tries to roll
            return searchTries > 0;
        }
    }

    /**
     * Executes the tick logic for this goal. This function is called once per tick for the AI to perform its actions. It checks if the
     * pet has arrived at the target scavenge location. If it has, it checks for items in the area and attempts to pick them up. If a
     * scavenge is successful, it executes the success action and harvests the resources.
     */
    @Override
    public void tick()
    {
        final Level level = pet.level();
        if (level == null || level.isClientSide) return;

        final BlockPos localTargetPos = targetPos;
        final BlockPos localNavigationPos = navigationPos;

        if (localTargetPos == null || localNavigationPos == null) 
        {
            reset();
            return;
        }

        // Re-path if the path is done but we haven't arrived yet (common with underwater floors)
        if (!hasArrived && pet.getNavigation().isDone())
        {
            // try again (nudge Y up a bit so land navigators don’t stop short on water floors)
            pet.getNavigation().moveTo(localNavigationPos.getX() + 0.5, localNavigationPos.getY() + 1.0, localNavigationPos.getZ() + 0.5, 1.0);

            // also guard against loops: burn a try occasionally when we hit "done but not arrived"
            if (searchTries > 0)
            {
                long t = pet.level().getGameTime();
                // Stagger by ID so pets don’t all decrement on the same ticks
                if (((t + (pet.getId() % 5)) % 5) == 0)
                {
                    searchTries--;
                }
            }
            return;
        }

        @Nonnull final Vec3 centerOfNavPos = NullnessBridge.assumeNonnull(Vec3.atCenterOf(localNavigationPos));

        if (hasArrived && !pet.blockPosition().closerToCenterThan(centerOfNavPos, 2.25))
        {
            // We slid by or got pushed too far.  Let's nudge back in range.
            pet.getNavigation().moveTo(localNavigationPos.getX() + 0.5, localNavigationPos.getY() + 1.0, localNavigationPos.getZ() + 0.5, .3);
        }

    
        if ((pet.blockPosition().closerToCenterThan(centerOfNavPos, 2.25)) || hasArrived)
        {
            hasArrived = true;

            float roll = pet.getRandom().nextFloat();
            pet.swing(InteractionHand.MAIN_HAND);

            TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("Reached target position during ScavengeWaterResourceGoal.tick. Harvest roll is: {} with a chance of {}", roll, chanceToFind));

            if (roll < chanceToFind)
            {
                TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("Successful harvest attempt."));

                searchTries = 0;
                harvest(localTargetPos);
            }
            else
            {
                searchTries--;
            }

            if (pet.level() instanceof ServerLevel serverLevel)
            {
                @Nonnull AABB box = NullnessBridge.assumeNonnull(new AABB(localTargetPos).inflate(1.0));
                List<ItemEntity> items = serverLevel.getEntitiesOfClass(ItemEntity.class,
                    box,
                    item -> !item.hasPickUpDelay() && item.isAlive());

                PetUtil.insertItems(pet, items);
            }

            if (searchTries <= 0)
            {
                reset();
            }
        }

        if (!hasArrived && !pet.getNavigation().isDone() && lastPetPos != null && lastPetPos.equals(pet.blockPosition()))
        {
            stuckCount++;
        }
        else
        {
            stuckCount = 0;
        }

        if (stuckCount > 4)
        {
            // 40 ticks of no motion burns a search try.
            searchTries--;
        }

        if (pet.level().getGameTime() % 10 == 0)
        {
            lastPetPos = pet.blockPosition(); 
        }
    }

    /**
     * Harvests the block at the given position and drops the resulting items into the pet's inventory. 
     * The block must be a member of
     * the water scavenge tag.
     *
     * @param pos the position of the block to be harvested
     */
    protected void harvest(@Nonnull BlockPos pos)
    {
        if (!(pet.level() instanceof ServerLevel level)) return;

        BlockState harvestSpotState = level.getBlockState(pos);
        final Block harvestSpot = harvestSpotState.getBlock();

        if (harvestSpot == null || !harvestSpotState.is(NullnessBridge.assumeNonnull(WATER_SCAVENGE_BLOCK_TAG)))
        {
            return;
        }

        // Use block ID (e.g. "minecraft:clay") to build a loot table ID (e.g. "mctradepost:pet/amphibious_scavenge/clay")
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(harvestSpot);
        String lootPath = LOOT_BASE + "/" + blockId.getPath();
        ResourceLocation lootTableLocation = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, lootPath);

        if (lootTableLocation == null) return;

        ResourceKey<LootTable> lootTableKey = ResourceKey.create(NullnessBridge.assumeNonnull(Registries.LOOT_TABLE), lootTableLocation);

        if (lootTableKey == null) return;

        // Access the loot table from MinecraftServer correctly
        LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(lootTableKey);

        // TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("Loot table Key: {} is {}", lootTableKey, lootTable));

        if (lootTable == null || lootTable == LootTable.EMPTY) return;

        Vec3 centerPos = NullnessBridge.assumeNonnull(Vec3.atCenterOf(pos));

        // IDEA: Modify luck based on animal trainer skill level
        LootParams lootParams = new LootParams.Builder(level).withParameter(NullnessBridge.assumeNonnull(LootContextParams.ORIGIN), centerPos)
            .withOptionalParameter(NullnessBridge.assumeNonnull(LootContextParams.THIS_ENTITY), pet)
            .withLuck(0.0f) 
            .create(NullnessBridge.assumeNonnull(LootContextParamSets.EMPTY));

        if (lootParams == null) return;

        List<ItemStack> drops = lootTable.getRandomItems(lootParams);

        for (ItemStack drop : drops)
        {
            ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, NullnessBridge.assumeNonnull(drop.copy()));
            itemEntity.setPickUpDelay(0);
            level.addFreshEntity(itemEntity);

            PetUtil.insertItem(pet, itemEntity);


            // Track the stat with item name
            if (trainerBuilding != null)
            {
                StatsUtil.trackStatByName(trainerBuilding, ScavengeForResourceGoal.ITEMS_SCAVENGED, itemEntity.getDisplayName().getString(), 1);
            }

        }

        // Additional particles and sound
        level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.BUBBLE),
            pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
            20, 0.5, 0.5, 0.5, 0.1);
        level.playSound(null, pos, NullnessBridge.assumeNonnull(SoundEvents.SHOVEL_FLATTEN), SoundSource.BLOCKS, 1.0f, 1.0f);

    }

    /**
     * Called when the goal is stopped, either because the target has been reached or because the goal was cancelled.
     * If the target has been reached, this method will fully reset the goal. If the target has not been reached and
     * there are still tries left, this method will pause the goal, allowing it to resume on the next tick.
     * <p>
     * Pausing instead of resetting allows a higher-priority goal to take over cleanly and then resume this goal
     * when that higher-priority goal is complete.
     */
    @Override
    public void stop()
    {
        final Level level = pet.level();
        if (level == null || level.isClientSide) return;

        // If we were pre-empted while still en route and still have tries, PAUSE
        if (this.targetPos != null && !this.hasArrived && this.searchTries > 0)
        {
            if (pet.level() != null)
            {
                this.resumeUntilTick = pet.level().getGameTime() + RESUME_GRACE_TICKS;
            }
            
            // Don’t touch targetPos/navigationPos/searchTries here.
            // Stop current nav so higher-priority goal can take over cleanly
            if (!pet.getNavigation().isDone())
            {
                pet.getNavigation().stop();
            } 
            // TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("Pausing water scavenge goal"));
            return;
        }

        // Otherwise this is a terminal stop (arrived/failed): full reset'
        // TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("Stopping water scavenge goal"));
        reset();
    }


    /**
     * Resets the state of this goal. Resets the target position, hasArrived flag, the last scavenge time to the current game time,
     * and the number of search tries to the maximum. Also resets the next gate time to 0.
     */
    public void reset()
    {
        targetPos = null;
        lastPetPos = null;
        navigationPos = null;
        lastStartTarget = null;
        hasArrived = false;
        if (pet.level() != null)
        {
            lastScavengeTime = pet.level().getGameTime();
        }
        searchTries = MAX_SEARCH_TRIES;
        pet.getNavigation().stop();
        nextGateTime = 0L;
        stuckCount = 0;
    }


    /**
     * Finds a suitable location for the pet to scavenge for water resources within the given search radius.
     * A suitable location is one that is either:
     * 1) A solid non-water floor with a 1-2 deep column of water/ice above it.
     * 2) A single ice block with open/air above it.
     * <p>
     * The search is done by randomly offsetting from the pet's work location within the search radius,
     * and checking if the resulting location satisfies the above conditions. This is done up to 20 times.
     * If no suitable location is found, null is returned.
     * @return a suitable location for scavenging water resources, or null if no suitable location is found.
     */
    @SuppressWarnings("null")
    private BlockPos findWaterScavengeLocation()
    {
        final Level level = pet.level();
        final BlockPos origin = pet.getWorkLocation();

        if (level == null || origin == null)
        {
            // TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("findWaterScavengeLocation: level or origin is null"));
            return null;
        }

        for (int tries = 0; tries < 20; tries++)
        {
            final BlockPos candidate = origin.offset(pet.getRandom().nextInt(searchRadius * 2) - searchRadius,
                pet.getRandom().nextInt(3) - 2,
                pet.getRandom().nextInt(searchRadius * 2) - searchRadius);

            if (candidate == null) continue;

            BlockState state = level.getBlockState(candidate);
            BlockState above = level.getBlockState(candidate.above());
            BlockState twoAbove = level.getBlockState(candidate.above().above());
            BlockState threeAbove = level.getBlockState(candidate.above().above().above());

            // 1) Solid non-water (true floor): not water, not ice, not air
            boolean floorIsSolidNonWater = !state.getFluidState().is(FluidTags.WATER) && !state.is(PathingUtil.ICY) && !state.isAir();

            // 2) Single ice plate case: a single ice block with open/air above
            boolean icePlateau = state.is(PathingUtil.ICY) && PathingUtil.isOpenOrIce(above);

            // 3) Shallow columns of water/ice 1–2 deep above a solid floor
            boolean depth1 = PathingUtil.isWaterOrIce(above) && PathingUtil.isOpenOrIce(twoAbove);

            boolean depth2 = PathingUtil.isWaterOrIce(above) && PathingUtil.isWaterOrIce(twoAbove) && PathingUtil.isOpenOrIce(threeAbove);

            // Final accept:
            // - Either a solid non-water floor with a 1–2 deep column above,
            // - OR a single ice block “plateau” with air/open above it.
            boolean shallowWater =  (floorIsSolidNonWater && (depth1 || depth2)) || icePlateau;

            BlockState[] neighborhood = new BlockState[] {
                state,
                level.getBlockState(candidate.below()),
                level.getBlockState(candidate.north()),
                level.getBlockState(candidate.south()),
                level.getBlockState(candidate.east()),
                level.getBlockState(candidate.west()),
                level.getBlockState(candidate.above())
            };

            boolean hasScavengeMaterials = false;
            for (BlockState s : neighborhood) 
            {
                if (s.is(WATER_SCAVENGE_BLOCK_TAG)) 
                {
                    hasScavengeMaterials = true;
                    break;
                }
            }

            if (shallowWater && hasScavengeMaterials) {
                return candidate;
            }
        }

        return null;
    }

}
