package com.deathfrog.mctradepost.api.entity.pets.goals;

import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.entity.pets.PetTypes;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.entity.ai.workers.crafting.EntityAIWorkAnimalTrainer;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.InventoryUtils;
import com.mojang.logging.LogUtils;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_ANIMALTRAINER;

import java.util.EnumSet;
import java.util.Objects;

import org.slf4j.Logger;

/**
 * Pet self-heal goal: when damaged, check the pet's inventory for a matching food item, consume 1, and heal up to 2.0 health.
 *
 * @param <P> your pet type that has an inventory via ITradePostPet#getInventory()
 */
public class EatFromInventoryHealGoal<P extends Animal & ITradePostPet> extends Goal
{
    public static final Logger LOGGER = LogUtils.getLogger();
    
    private final P pet;
    private final int cooldownTicks;          // Minimum ticks between eats, to prevent spamming
    private final int windupTicks;            // Small delay to simulate "eating" (anim/sound timing)

    private long lastEatTick = -200L;
    private int eatAtTick = -1;
    private int cachedSlot = -1;
    private ItemStorage foodFilter = null;

    /**
     * @param pet           the pet entity
     * @param cooldownTicks how long to wait between self-heals (e.g., 200 = 10s)
     * @param windupTicks   small delay before consuming (e.g., 10)
     */
    public EatFromInventoryHealGoal(P pet, int cooldownTicks, int windupTicks)
    {
        this.pet = pet;
        this.cooldownTicks = Math.max(0, cooldownTicks);
        this.windupTicks = Math.max(0, windupTicks);

        // We don't need to move for this; set LOOK for potential head motion if you do anims later.
        this.setFlags(EnumSet.of(Goal.Flag.LOOK));
    }

    /**
     * Determines if the eat-from-inventory goal can be used. This goal can be used if the pet is injured and there is a matching food item in its inventory.
     * The cooldown period must have elapsed since the last use.
     * <p>
     * This goal is a one-shot behavior, not a continuous goal.
     * @return true if the goal can be used, false otherwise
     */
    @Override
    public boolean canUse()
    {
        if (!pet.isAlive() || pet.level().isClientSide()) return false;

        // Only when actually injured
        if (pet.getHealth() >= pet.getMaxHealth()) return false;

        // Respect cooldown
        if (pet.tickCount - lastEatTick < cooldownTicks) return false;

        if (foodFilter == null)
        {
            foodFilter = new ItemStorage(PetTypes.foodForPet(pet.getClass()), EntityAIWorkAnimalTrainer.PETFOOD_SIZE);
        }

        cachedSlot = InventoryUtils.findFirstSlotInItemHandlerNotEmptyWith(pet.getInventory(), stack -> Objects.equals(new ItemStorage(stack), foodFilter));

        TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Want to heal - food slot: {}.", cachedSlot));

        return cachedSlot >= 0;
    }

    @Override
    public boolean canContinueToUse()
    {
        // One-shot behavior with a brief windup
        return eatAtTick >= 0 && pet.isAlive() && !pet.level().isClientSide();
    }

    @Override
    public void start()
    {
        // Schedule the consume moment after a short windup
        eatAtTick = pet.tickCount + windupTicks;

        // Optional: look "down" a bit like vanilla eat behavior
        pet.getLookControl().setLookAt(pet.getX(), pet.getEyeY() - 0.6, pet.getZ(), 10.0F, pet.getMaxHeadXRot());
    }

    @Override
    public void tick()
    {
        if (pet.level().isClientSide()) return;

        if (eatAtTick >= 0 && pet.tickCount >= eatAtTick)
        {
            // Re-check that we’re still injured and have the item
            if (pet.getHealth() < pet.getMaxHealth() && cachedSlot >= 0)
            {
                consumeOneAndHeal(pet, cachedSlot, foodFilter);
            }
            // End the goal
            eatAtTick = -1;
        }
    }

    @Override
    public void stop()
    {
        cachedSlot = -1;
        eatAtTick = -1;
    }

    @Override
    public boolean isInterruptable()
    {
        return true;
    }


    /**
     * Helper to consume one of the food item and heal the pet.
     * <p>
     * This is called by the goal when it is time to actually eat one of the food items.
     * <p>
     * The method will first simulate taking one of the item to ensure it can be taken.
     * If the item can be taken, it will actually remove one of the item, heal the pet
     * by up to 2.0 health, and play a small eat sound.
     *
     * @param entity the pet to heal
     * @param slot   the slot to take the item from
     * @param food   the food item to take (used for logging and for checking if the
     *               item is actually the food we want)
     */
    private void consumeOneAndHeal(LivingEntity entity, int slot, ItemStorage foodToEat)
    {
        TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Healing pet - food slot: {} ({}).", slot, foodToEat));

        IItemHandler inv = pet.getInventory();
        if (inv == null) return;

        // Simulate first to ensure there is at least 1 to take
        ItemStack simulated = inv.extractItem(slot, 1, /*simulate*/ true);
        if (simulated.isEmpty() || !(simulated.getItem().equals(foodToEat.getItem()))) return;

        // Actually remove one item
        ItemStack removed = inv.extractItem(slot, 1, /*simulate*/ false);
        if (removed.isEmpty()) return;

        // Heal up to 2.0 health but not past max
        float missing = entity.getMaxHealth() - entity.getHealth();
        float healAmount = Math.min(2.0F, Math.max(0.0F, missing));
        if (healAmount > 0.0F)
        {
            entity.heal(healAmount);

            // Play a small eat sound
            entity.level()
                .playSound(null,
                    entity.blockPosition(),
                    SoundEvents.GENERIC_EAT,
                    SoundSource.NEUTRAL,
                    0.8F,
                    entity.getRandom().nextFloat() * 0.2F + 0.9F);
        }

        lastEatTick = pet.tickCount;
    }
}
