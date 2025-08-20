package com.deathfrog.mctradepost.api.entity.pets.goals;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.entity.pets.PetRoles;
import com.deathfrog.mctradepost.api.util.PetUtil;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.StatsUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ScavengeWaterResourceGoal<P extends Animal & ITradePostPet> extends Goal
{
    public static final String WATER_SCAVENGE_TAG = "amphibious_scavenge";
    public static final String LOOT_BASE = "pet/" + WATER_SCAVENGE_TAG;

    private final P pet;
    private final int searchRadius;
    private final float chanceToFind;
    private final IBuilding trainerBuilding;
    private final int cooldownTicks;

    private BlockPos targetPos;
    private boolean hasArrived = false;
    private long lastScavengeTime = 0;

    public ScavengeWaterResourceGoal(P pet, int searchRadius, float chanceToFind, IBuilding trainerBuilding, int cooldownTicks)
    {
        this.pet = pet;
        this.searchRadius = searchRadius;
        this.chanceToFind = chanceToFind;
        this.trainerBuilding = trainerBuilding;
        this.cooldownTicks = cooldownTicks;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
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
        if (!pet.isAlive() || pet.isPassenger() || pet.isLeashed())
        {
            return false;
        }

        if (pet.getPetData() == null || pet.level() == null)
        {
            return false;
        }

        if (!PetRoles.SCAVENGE_WATER.equals(pet.getPetData().roleFromWorkLocation(pet.level())))
        {
            return false;
        }

        long gameTime = pet.level().getGameTime();

        if (gameTime - lastScavengeTime < cooldownTicks)
        {
            return false;
        }

        targetPos = findWaterScavengeLocation();

        return targetPos != null;
    }

    /**
     * Begins moving the pet to the target scavenge location if it exists.
     */
    @Override
    public void start()
    {
        if (targetPos != null)
        {
            pet.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 1.0);
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
        return targetPos != null && !pet.getNavigation().isDone() && !hasArrived;
    }

    /**
     * Executes the tick logic for this goal. This function is called once per tick for the AI to perform its actions. It checks if the
     * pet has arrived at the target scavenge location. If it has, it checks for items in the area and attempts to pick them up. If a
     * scavenge is successful, it executes the success action and harvests the resources.
     */
    @Override
    public void tick()
    {
        if (targetPos != null && pet.blockPosition().closerToCenterThan(Vec3.atCenterOf(targetPos), 1.5))
        {
            hasArrived = true;

            if (pet.level() instanceof ServerLevel serverLevel)
            {
                List<ItemEntity> items = serverLevel.getEntitiesOfClass(ItemEntity.class,
                    new AABB(targetPos).inflate(1.0),
                    item -> !item.hasPickUpDelay() && item.isAlive());

                PetUtil.insertItems(pet, items);
            }

            if (pet.getRandom().nextFloat() < chanceToFind)
            {
                harvest(targetPos);
            }
        }
    }

    /**
     * Harvests the block at the given position and drops the resulting items into the pet's inventory. 
     * The block must be a member of
     * the water scavenge tag.
     *
     * @param pos the position of the block to be harvested
     */
    protected void harvest(BlockPos pos)
    {
        if (!(pet.level() instanceof ServerLevel level)) return;

        Block block = level.getBlockState(pos).getBlock();

        if (!isScavengableWaterMaterial(block))
        {
            return;
        }

        // Use block ID (e.g. "minecraft:clay") to build a loot table ID (e.g. "mctradepost:pet/amphibious_scavenge/clay")
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        String lootPath = LOOT_BASE + "/" + blockId.getPath();
        ResourceLocation lootTableLocation = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, lootPath);
        ResourceKey<LootTable> lootTableKey = ResourceKey.create(Registries.LOOT_TABLE, lootTableLocation);

        // ✅ Access the loot table from MinecraftServer correctly
        LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(lootTableKey);

        LootParams lootParams = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .withOptionalParameter(LootContextParams.THIS_ENTITY, pet)
            .create(LootContextParamSets.FISHING);

        List<ItemStack> drops = lootTable.getRandomItems(lootParams);

        for (ItemStack drop : drops)
        {
            ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop.copy());
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
        level.sendParticles(ParticleTypes.BUBBLE,
            pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
            20, 0.5, 0.5, 0.5, 0.1);
        level.playSound(null, pos, SoundEvents.SHOVEL_FLATTEN, SoundSource.BLOCKS, 1.0f, 1.0f);

    }

    /**
     * Stops the goal and resets state variables. Resets the target position, hasArrived flag, and updates the last scavenge time based
     * on the current game time.
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
     * Finds a suitable location for scavenging water resources. This method iterates 20 times, checking for a suitable location based
     * on the following criteria: - The block at the candidate position is shallow water (Blocks.WATER) and the block above it is air.
     * - The block at the candidate position or any of its neighboring blocks is a scavengable water material.
     *
     * @return the suitable location for scavenging water resources, or null if no suitable location is found.
     */
    private BlockPos findWaterScavengeLocation()
    {
        final Level level = pet.level();
        final BlockPos origin = pet.getWorkLocation();

        for (int tries = 0; tries < 20; tries++)
        {
            BlockPos candidate = origin.offset(pet.getRandom().nextInt(searchRadius * 2) - searchRadius,
                pet.getRandom().nextInt(4) - 2,
                pet.getRandom().nextInt(searchRadius * 2) - searchRadius);

            BlockState state = level.getBlockState(candidate);
            BlockState above = level.getBlockState(candidate.above());

            boolean shallowWater = state.is(Blocks.WATER) && above.isAir();
            boolean hasScavengeMaterials =
                Stream
                    .of(state.getBlock(),
                        level.getBlockState(candidate.below()).getBlock(),
                        level.getBlockState(candidate.north()).getBlock(),
                        level.getBlockState(candidate.south()).getBlock(),
                        level.getBlockState(candidate.east()).getBlock(),
                        level.getBlockState(candidate.west()).getBlock())
                    .anyMatch(this::isScavengableWaterMaterial);

            if (shallowWater && hasScavengeMaterials)
            {
                return candidate;
            }
        }

        return null;
    }

    /**
     * Determines if a block is a scavengable material from shallow water.
     *
     * @param block the block to check
     * @return true if the block is clay, kelp, prismarine, sea pickle, or seagrass, false otherwise
     */
    private boolean isScavengableWaterMaterial(Block block)
    {
        return block.defaultBlockState()
            .is(TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, WATER_SCAVENGE_TAG)));
    }
}
