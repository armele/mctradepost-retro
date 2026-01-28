package com.deathfrog.mctradepost.api.entity.pets.goals;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.entity.pets.PetRoles;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.PetUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;

public class ForageGoal<P extends Animal & ITradePostPet> extends Goal
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final String ITEMS_SCAVENGED = "items_scavenged";

    private final P pet;
    private final int searchRadius;
    private final double maxLight;
    private final float chanceToFind;
    private final Predicate<BlockPos> locationPredicate;
    private final Consumer<BlockPos> successAction;
    private final int cooldownTicks;

    private BlockPos targetPos;
    private boolean hasArrived = false;
    private long lastScavengeTime = 0;

    public ForageGoal(P pet,
        int searchRadius,
        double maxLight,
        float chanceToFind,
        Predicate<@NotNull BlockPos> locationPredicate,
        Consumer<@NotNull BlockPos> successAction,
        int cooldownTicks)
    {
        this.pet = pet;
        this.searchRadius = searchRadius;
        this.maxLight = maxLight;
        this.chanceToFind = chanceToFind;
        this.locationPredicate = locationPredicate;
        this.successAction = successAction;
        this.cooldownTicks = cooldownTicks;
        this.setFlags(NullnessBridge.assumeNonnull(EnumSet.of(Goal.Flag.MOVE)));
    }

    /**
     * Determines if the scavenge goal can be used. This goal can be used if the pet is not on the client side, not a passenger, not leashed, and is alive.
     * Additionally, there must be a valid scavenging role for the pet from its work location, and the pet must not have scavenged within the cooldown period.
     * If the pet is near a suitable location, the goal will begin pathing to that location.
     * 
     * @return true if the scavenge goal can be used, false otherwise
     */
    @Override
    public boolean canUse()
    {
        final Level level = pet.level();
        if (level == null || level.isClientSide) return false;

        if (!pet.isAlive() || pet.isPassenger() || pet.isLeashed()) return false;
        if (pet.getPetData() == null) return false;

        if (!PetRoles.SCAVENGE_LAND.equals(pet.getPetData().roleFromWorkLocation(level))) return false;

        long gameTime = level.getGameTime();
        if (gameTime - lastScavengeTime < cooldownTicks) return false;

        targetPos = findSuitableLocation();
        return targetPos != null;
    }


    /**
     * Starts the goal by moving the pet to the target position if it is not null.
     * The target position is set by the canUse() method.
     * If the target position is null, the pet does not move.
     */
    @Override
    public void start()
    {
        final Level level = pet.level();
        if (level.isClientSide) return;

        hasArrived = false;

        if (targetPos != null)
        {
            pet.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 1.0);
        }
    }

    /**
     * Determines if the goal can continue to run.
     * This goal can continue to run if the target position is not null, the pet's navigation is not done, and the pet has not arrived.
     * @return true if the goal can continue to run, false otherwise
     */
    @Override
    public boolean canContinueToUse()
    {
        final Level level = pet.level();
        if (level.isClientSide) return false;

        return targetPos != null && !pet.getNavigation().isDone() && !hasArrived;
    }

    /**
     * Ticks the scavenging goal.
     * <p>This method is called every tick that the goal is active. It checks if the target position is null, if the pet is on the client side, if the pet has arrived at the target position, and if the pet should pick up items and scavenge.</p>
     * <p>If the pet has arrived, it attempts to pick up existing items in the area and scavenges for items. If scavenging is successful, it executes the success action and harvests the resources.</p>
     */
    @Override
    public void tick()
    {
        final Level level = pet.level();
        if (level.isClientSide) return;

        final BlockPos localTargetPos = this.targetPos;

        if (localTargetPos == null) return;

        @Nonnull Vec3 centerOf = NullnessBridge.assumeNonnull(Vec3.atCenterOf(localTargetPos));

        if (pet.blockPosition().closerToCenterThan(centerOf, 1.5))
        {
            hasArrived = true;

            // Pick up existing items
            if (pet.level() instanceof ServerLevel serverLevel)
            {
                AABB box = new AABB(localTargetPos).inflate(1.0);

                List<ItemEntity> items = serverLevel.getEntitiesOfClass(ItemEntity.class,
                    NullnessBridge.assumeNonnull(box),
                    item -> !item.hasPickUpDelay() && item.isAlive());

                PetUtil.insertItems(pet, items);
            }

            // Scavenge chance
            if (pet.getRandom().nextFloat() < chanceToFind)
            {
                successAction.accept(localTargetPos);
                harvest(localTargetPos);
            }
        }
    }

    /**
     * Harvests blocks adjacent to the target block simulating the pet digging up the resource. Drops the harvested resources as items,
     * and attempts to insert them directly into the pet's inventory.
     */
    protected void harvest(@Nonnull BlockPos harvestTarget)
    {
        if (pet.level() instanceof ServerLevel serverLevel)
        {
            BlockState plantedState = serverLevel.getBlockState(harvestTarget);
            Block plantedBlock = plantedState.getBlock();

            BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();

            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dy = -1; dy <= 1; dy++)
                {
                    for (int dz = -1; dz <= 1; dz++)
                    {
                        scanPos.set(harvestTarget.getX() + dx, harvestTarget.getY() + dy, harvestTarget.getZ() + dz);

                        if (scanPos.equals(harvestTarget)) continue; // skip the one we just planted

                        final BlockState scanState = serverLevel.getBlockState(scanPos);
                        final Block scanBlock = scanState.getBlock();

                        if (scanBlock == plantedBlock)
                        {
                            final Item item = scanBlock.asItem();
                            if (scanBlock.isEmpty(scanState) || item == null) continue;

                            // Remove the block (simulate harvesting)
                            serverLevel.removeBlock(scanPos, false);

                            // Drop the harvested block as an item
                            ItemStack dropped = new ItemStack(item);
                            ItemEntity itemEntity =
                                new ItemEntity(serverLevel, scanPos.getX() + 0.5, scanPos.getY() + 0.5, scanPos.getZ() + 0.5, dropped);
                            itemEntity.setPickUpDelay(0);
                            serverLevel.addFreshEntity(itemEntity);

                            PetUtil.insertItem(pet, itemEntity);
                        }
                    }
                }
            }
        }
    }


    /**
     * Resets the scavenge goal state. Called when the goal is stopped.
     * <p>This method is called when the goal is stopped, either because the target has been reached or because the goal was cancelled.
     * It resets the target position to null, sets the hasArrived flag to false, and records the current game time as the last scavenge time.
     * </p>
     */
    @Override
    public void stop()
    {
        targetPos = null;
        hasArrived = false;
        if (pet.level() != null)
        {
            lastScavengeTime = pet.level().getGameTime();
        }
    }

    /**
     * Finds a suitable location for the pet to scavenge for resources within
     * the given search radius. A suitable location is one that is not too
     * bright (i.e. has a light level less than or equal to maxLight) and
     * satisfies the given location predicate.
     *
     * @return a suitable location for scavenging resources, or null if no
     *         suitable location is found.
     */
    private BlockPos findSuitableLocation()
    {
        final Level level = pet.level();
        BlockPos origin = pet.getWorkLocation();

        if (origin == null || BlockPos.ZERO.equals(origin)) return null;

        for (int tries = 0; tries < 20; tries++)
        {
            final BlockPos candidate = origin.offset(pet.getRandom().nextInt(searchRadius * 2) - searchRadius,
                pet.getRandom().nextInt(6) - 3,
                pet.getRandom().nextInt(searchRadius * 2) - searchRadius);

            if (candidate == null) continue;

            if (level.getMaxLocalRawBrightness(candidate) <= maxLight && locationPredicate.test(candidate))
            {
                return candidate;
            }
        }
        return null;
    }
}
