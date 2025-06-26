package com.deathfrog.mctradepost.core.entity.ai.workers.minimal;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.DisplayCase;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.DisplayCase.SaleState;
import com.minecolonies.api.colony.IVisitorData;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.ITickRateStateMachine;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.entity.ai.visitor.EntityAIVisitor.VisitorState;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.mojang.logging.LogUtils;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_SHOPPER;

/* EntityAIShoppingTask inserted into the Visitor AI by the marketplace. */
public class EntityAIShoppingTask
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public enum ShoppingState implements IState
    {
        GOING_SHOPPING,
        IS_SHOPPING,
        PICK_DISPLAY,
        DONE_SHOPPING
    }

    IVisitorData visitor = null;
    BuildingMarketplace marketplace = null;
    private static final int ONE_HUNDRED = 100;

    private DisplayCase currentDisplay = null;
    private int lingerTimer = 0;
    private static final int MAX_LINGER_TIME = 200;
    private static final int SHOPPING_COOLDOWN = MCTPConfig.shoppingCooldown.get();

    // Initialize them to a random point in the cooldown, to promote better distribution of when shopping happens
    private int shoppingTimer = ThreadLocalRandom.current().nextInt(SHOPPING_COOLDOWN); 

    public EntityAIShoppingTask(IVisitorData visitor, @Nonnull BuildingMarketplace marketplace)
    {
        this.visitor = visitor;
        this.marketplace = marketplace;
    }



    /**
     * Initializes the AI state machine transitions for the shopping task.
     * 
     * @param stateMachine the state machine to add the transitions to.
     */
    public void init(ITickRateStateMachine<IState> stateMachine)
    {
        stateMachine.addTransition(new TickingTransition<>(VisitorState.IDLE, this::wantsToShop, this::goingShopping, 150));
        stateMachine.addTransition(new TickingTransition<>(VisitorState.WANDERING, this::wantsToShop, this::goingShopping, 150));
        stateMachine.addTransition(new TickingTransition<>(ShoppingState.GOING_SHOPPING, () -> true, this::goingShopping, 150));
        stateMachine.addTransition(new TickingTransition<>(ShoppingState.PICK_DISPLAY, () -> true, this::pickDisplay, 150));
        stateMachine.addTransition(new TickingTransition<>(ShoppingState.IS_SHOPPING, () -> true, this::isShopping, 150));
        stateMachine.addTransition(new TickingTransition<>(ShoppingState.DONE_SHOPPING, () -> true, this::doneShopping, 150));
    }

    /**
     * Checks if a visitor wants to go shopping at the marketplace. This method is called by the AI when it wants to decide what to do.
     * It will return true if the visitor should go shopping, false otherwise. The decision is based on the time of day (no shopping at
     * night) and a random chance based on the marketplace's shopping chance.
     * 
     * @return true if the visitor should go shopping, false otherwise
     */
    public boolean wantsToShop()
    {
        // No shopping at night.
        if (!WorldUtil.isDayTime(marketplace.getColony().getWorld()))
        {
            TraceUtils.dynamicTrace(TRACE_SHOPPER, () -> LOGGER.info("Visitor {} won't shop at night!", visitor.getEntity().get().getName()));
            return false;
        }

        // No shopping if the marketplace isn't open.
        if (!marketplace.isOpenForBusiness()) 
        {
            TraceUtils.dynamicTrace(TRACE_SHOPPER, () -> LOGGER.info("Visitor {} can't shop - the store is closed.", visitor.getEntity().get().getName()));
            return false;
        }

        shoppingTimer = shoppingTimer - marketplace.getBuildingLevel();

        if (shoppingTimer > 0) {
            TraceUtils.dynamicTrace(TRACE_SHOPPER, () -> LOGGER.info("Visitor {} can't shop - taking a break with cooldown at: {}", visitor.getEntity().get().getName(), shoppingTimer));
            return false;
        }

        if (marketplace.shoppingChance() > ThreadLocalRandom.current().nextDouble() * ONE_HUNDRED)
        {
            LOGGER.trace("Visitor {} is taking a shopping trip!", visitor.getEntity().get().getName());
            return true;
        }
        else
        {
            TraceUtils.dynamicTrace(TRACE_SHOPPER, () -> LOGGER.info("Visitor {} does not need to shop.", visitor.getEntity().get().getName()));
            return false;
        }
    }

    /**
     * Attempts to navigate the visitor to the marketplace location.
     *
     * @return the next shopping state, IS_SHOPPING if the visitor has reached the marketplace, otherwise GOING_SHOPPING if still en
     *         route.
     */
    public IState goingShopping()
    {
        if (EntityNavigationUtils.walkToPos(visitor.getEntity().get(), marketplace.getLocation().getInDimensionLocation(), 3, true))
        {
            return ShoppingState.PICK_DISPLAY;
        }
        else
        {
            return ShoppingState.GOING_SHOPPING;
        }
    }

    /**
     * Attempts to navigate the visitor to the marketplace location.
     *
     * @return the next shopping state, IS_SHOPPING if the visitor has reached 
     *         the marketplace, otherwise GOING_SHOPPING if still en route.
     */
    public IState isShopping() {
        if (currentDisplay == null) {
            return ShoppingState.PICK_DISPLAY;
        }

        SaleState state = currentDisplay.getSaleState();

        if (state == null) {
            currentDisplay = null;
            return ShoppingState.PICK_DISPLAY;
        }

        switch (state) 
        {
            case NONE:
                if (!currentDisplay.getStack().isEmpty()) {
                    TraceUtils.dynamicTrace(TRACE_SHOPPER, () -> LOGGER.info("Visitor {} has made an offer on a display not yet up for sale!", visitor.getEntity().get().getName()));
                    currentDisplay.setSaleState(SaleState.ORDER_PLACED);
                    return ShoppingState.IS_SHOPPING;  
                }
                return ShoppingState.PICK_DISPLAY;

            case FOR_SALE:
                TraceUtils.dynamicTrace(TRACE_SHOPPER, () -> LOGGER.info("Visitor {} has made an offer!", visitor.getEntity().get().getName()));
                currentDisplay.setSaleState(SaleState.ORDER_PLACED);
                return ShoppingState.IS_SHOPPING;

            case ORDER_PLACED:
                if (lingerTimer <= 0) {
                    TraceUtils.dynamicTrace(TRACE_SHOPPER, () -> LOGGER.info("Visitor {} is tired of waiting for service.", visitor.getEntity().get().getName()));
                    currentDisplay.setSaleState(SaleState.FOR_SALE);
                    currentDisplay = null;
                    return ShoppingState.DONE_SHOPPING;
                }
                lingerTimer = lingerTimer - 1;
                return ShoppingState.IS_SHOPPING;

            case ORDER_FULFILLED:
                currentDisplay = null;
                lingerTimer = MAX_LINGER_TIME;

                if (wantsToShop()) 
                {
                    return ShoppingState.GOING_SHOPPING;
                } else {
                    return ShoppingState.DONE_SHOPPING;
                }   

            default:
                return ShoppingState.DONE_SHOPPING;
        }
    }

    /**
     * Attempts to navigate the visitor to a random display case in the marketplace.
     * If the visitor has already navigated to a display case, it will not change targets.
     * If the visitor is not already at the marketplace and the navigation is incomplete, it will
     * remain in the PICK_DISPLAY state and continue travelling.
     *
     * @return the next shopping state, IS_SHOPPING if the visitor has reached the display case, otherwise PICK_DISPLAY if still en route.
     */
    public IState pickDisplay() {
        if (currentDisplay == null && marketplace.getDisplayShelvesWithItemsForSale().size() > 0) {
            final List<DisplayCase> displayPositions = marketplace.getDisplayShelvesWithItemsForSale();
            currentDisplay = displayPositions.get(ThreadLocalRandom.current().nextInt(displayPositions.size()));
        }

        // There's nothing for sale! Done shopping.
        if (currentDisplay == null) {
            return ShoppingState.DONE_SHOPPING;
        }

        if (! EntityNavigationUtils.walkToPos(visitor.getEntity().get(), currentDisplay.getPos(), 2, true)) {
            return ShoppingState.PICK_DISPLAY;
        }
        
        return ShoppingState.IS_SHOPPING;
    }

    /**
     * Called when the visitor is done shopping. This method is called by the AI's state machine when it has finished shopping and is
     * ready to go back to IDLE.
     * 
     * @return the next state to go to, which is always VisitorState.IDLE
     */
    public IState doneShopping()
    {
        shoppingTimer = SHOPPING_COOLDOWN;
        return VisitorState.WANDERING;
    }
}
