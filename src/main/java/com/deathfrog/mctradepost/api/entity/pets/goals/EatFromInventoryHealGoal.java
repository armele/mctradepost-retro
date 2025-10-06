package com.deathfrog.mctradepost.api.entity.pets.goals;

import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.entity.pets.PetTypes;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.minecolonies.api.util.InventoryUtils;
import com.mojang.logging.LogUtils;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_PETGOALS;

import org.slf4j.Logger;

/**
 * Pet self-heal goal: when damaged, check the pet's inventory for a matching food item, consume 1, and heal up to 2.0 health.
 *
 * @param <P> your pet type that has an inventory via ITradePostPet#getInventory()
 */
public class EatFromInventoryHealGoal<P extends Animal & ITradePostPet> extends Goal
{
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final int LOG_THROTTLE = 200;
    private final P pet;
    private final int cooldownTicks;          // Minimum ticks between eats, to prevent spamming

    private int eatTickCooldown = 0;
    private Item foodItem = null;

    private int logThrottleCounter = 0;
    

    /**
     * @param pet           the pet entity
     * @param cooldownTicks how long to wait between self-heals (e.g., 200 = 10s)
     * @param windupTicks   small delay before consuming (e.g., 10)
     */
    public EatFromInventoryHealGoal(P pet, int cooldownTicks)
    {
        this.pet = pet;
        this.cooldownTicks = Math.max(0, cooldownTicks);
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
        if (pet.getHealth() >= pet.getMaxHealth()) 
        {
            if (eatTickCooldown > 0) 
            {
                eatTickCooldown--;
            }
            return false;
        }

        // Respect cooldown
        if (eatTickCooldown-- > 0) 
        {
            return false;
        }

        eatTickCooldown = cooldownTicks;

        if (foodItem == null)
        {
            foodItem = PetTypes.foodForPet(pet.getClass());
            if (foodItem == null) 
            {
                LOGGER.warn("No food item is registered for pet class: {}", pet.getClass().getName());
                return false;
            }
        }

        IItemHandler inv = pet.getInventory();
        if (inv == null) 
        {
            LOGGER.warn("No inventory found for pet: {}", pet.getClass().getName());
            return false;
        }

        int numFood = InventoryUtils.getItemCountInItemHandler(inv, foodItem);

        if (logThrottleCounter <= 0)
        {
            TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("Want to heal - food item: {} (have {}).", foodItem, numFood));
            logThrottleCounter = LOG_THROTTLE;
        }

        logThrottleCounter--;

        return numFood > 0;
    }

    @Override
    public boolean canContinueToUse()
    {
        return false;
    }

    @Override
    public void start() 
    {        
        // Cosmetic look
        pet.getLookControl().setLookAt(pet.getX(), pet.getEyeY() - 0.6, pet.getZ(), 10.0F, pet.getMaxHeadXRot());

            // Re-check that we’re still injured and have the item
        if (pet.getHealth() < pet.getMaxHealth())
        {
            consumeOneAndHeal();
        }
        
        // End the goal
        eatTickCooldown = cooldownTicks;
    }

    /**
     * Called once per tick while this goal is active.
     *
     * On the tick where we were scheduled to eat, we consume the food and heal the pet.  If the pet is not injured or the food is gone, we end the goal.
     */
    @Override
    public void tick()
    {
        if (pet.level().isClientSide())
        { 
            return;
        }
    }

    @Override
    public void stop()
    {
        eatTickCooldown = cooldownTicks;
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
    private void consumeOneAndHeal()
    {
        TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("Healing pet - food: {}.", foodItem));

        IItemHandler inv = pet.getInventory();
        if (inv == null) 
        {
            LOGGER.warn("No inventory found for pet: {}", pet.getClass().getName());
            return;
        }

        boolean didEat = InventoryUtils.attemptReduceStackInItemHandler(pet.getInventory(), new ItemStack(foodItem, 1), 1);

        if (!didEat)
        {
            return;
        }

        // Heal up to 2.0 health but not past max
        float missing = pet.getMaxHealth() - pet.getHealth();
        float healAmount = Math.min(2.0F, Math.max(0.0F, missing));
        
        if (healAmount > 0.0F)
        {
            pet.heal(healAmount);

            // Play a small eat sound
            pet.level()
                .playSound(null,
                    pet.blockPosition(),
                    SoundEvents.GENERIC_EAT,
                    SoundSource.NEUTRAL,
                    0.8F,
                    pet.getRandom().nextFloat() * 0.2F + 0.9F);
        }

    }
}
