package com.deathfrog.mctradepost.api.entity.pets.goals;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.slf4j.Logger;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.entity.pets.PetRoles;
import com.deathfrog.mctradepost.api.util.PetUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.minecraft.server.level.ServerLevel;

public class ScavengeForResourceGoal<P extends Animal & ITradePostPet> extends Goal
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

    public ScavengeForResourceGoal(P pet,
        int searchRadius,
        double maxLight,
        float chanceToFind,
        Predicate<BlockPos> locationPredicate,
        Consumer<BlockPos> successAction,
        int cooldownTicks)
    {
        this.pet = pet;
        this.searchRadius = searchRadius;
        this.maxLight = maxLight;
        this.chanceToFind = chanceToFind;
        this.locationPredicate = locationPredicate;
        this.successAction = successAction;
        this.cooldownTicks = cooldownTicks;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        if (!pet.isAlive() || pet.isPassenger() || pet.isLeashed()) return false;
        if (pet.getPetData() == null || pet.level() == null) return false;

        if (!PetRoles.SCAVENGE_LAND.equals(pet.getPetData().roleFromWorkLocation(pet.level()))) return false;

        long gameTime = pet.level().getGameTime();
        if (gameTime - lastScavengeTime < cooldownTicks) return false;

        targetPos = findSuitableLocation();
        return targetPos != null;
    }

    @Override
    public void start()
    {
        if (targetPos != null)
        {
            pet.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 1.0);
        }
    }

    @Override
    public boolean canContinueToUse()
    {
        return targetPos != null && !pet.getNavigation().isDone() && !hasArrived;
    }

    @Override
    public void tick()
    {
        if (targetPos != null && pet.blockPosition().closerToCenterThan(Vec3.atCenterOf(targetPos), 1.5))
        {
            hasArrived = true;

            // Pick up existing items
            if (pet.level() instanceof ServerLevel serverLevel)
            {
                List<ItemEntity> items = serverLevel.getEntitiesOfClass(ItemEntity.class,
                    new AABB(targetPos).inflate(1.0),
                    item -> !item.hasPickUpDelay() && item.isAlive());

                PetUtil.insertItems(pet, items);
            }

            // Scavenge chance
            if (pet.getRandom().nextFloat() < chanceToFind)
            {
                successAction.accept(targetPos);
                harvest(targetPos);
            }
        }
    }

    /**
     * Harvests blocks adjacent to the target block simulating the pet digging up the resource. Drops the harvested resources as items,
     * and attempts to insert them directly into the pet's inventory.
     */
    protected void harvest(BlockPos harvestTarget)
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

                        BlockState scanState = serverLevel.getBlockState(scanPos);
                        Block scanBlock = scanState.getBlock();

                        if (scanBlock == plantedBlock)
                        {
                            // Remove the block (simulate harvesting)
                            serverLevel.removeBlock(scanPos, false);

                            // Drop the harvested block as an item
                            ItemStack dropped = new ItemStack(scanBlock.asItem());
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

    private BlockPos findSuitableLocation()
    {
        final Level level = pet.level();
        BlockPos origin = pet.getWorkLocation();
        for (int tries = 0; tries < 20; tries++)
        {
            BlockPos candidate = origin.offset(pet.getRandom().nextInt(searchRadius * 2) - searchRadius,
                pet.getRandom().nextInt(6) - 3,
                pet.getRandom().nextInt(searchRadius * 2) - searchRadius);

            if (level.getMaxLocalRawBrightness(candidate) <= maxLight && locationPredicate.test(candidate))
            {
                return candidate;
            }
        }
        return null;
    }
}
