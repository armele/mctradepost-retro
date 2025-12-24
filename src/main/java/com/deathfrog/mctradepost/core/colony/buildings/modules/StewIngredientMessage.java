package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.Utils;
import com.minecolonies.core.network.messages.server.AbstractBuildingServerMessage;
import com.mojang.logging.LogUtils;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.slf4j.Logger;

import org.jetbrains.annotations.NotNull;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_STEWMELIER;

public class StewIngredientMessage extends AbstractBuildingServerMessage<IBuilding>
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final PlayMessageType<?> TYPE = PlayMessageType.forServer(MCTradePostMod.MODID, "stew_ingredient_message", StewIngredientMessage::new);

    public enum IngredientAction
    {
        ADD, REMOVE, QUERY
    }

    private static final String INGREDIENTS_UPDATED = "mctradepost.stewmolier.ingredients_updated";

    /**
     * What ingredient are we adding or removing?
     */
    private IngredientAction ingredientAction;

    /**
     * What item are we putting into the stew?
     */
    private ItemStack itemStack;

    /**
     * How many items will we leave in the warehouse untouched?
     */
    private int protectedQuantity;

    /**
     * Creates a Transfer Items request
     *
     * @param itemStack to be take from the player for the building
     * @param cost  coins being exchanged for
     * @param building  the building we're executing on.
     */
    public StewIngredientMessage(final IBuildingView building, IngredientAction action, final ItemStack itemStack, final int protectedQuantity)
    {
        super(TYPE, building);
        this.itemStack = itemStack.copy();
        this.ingredientAction = action;
        this.protectedQuantity = protectedQuantity;
    }

    public StewIngredientMessage(final IBuildingView building, final IngredientAction action)
    {
        super(TYPE, building);
        this.itemStack = ItemStack.EMPTY;
        this.ingredientAction = action;
        this.protectedQuantity = 0;
    }

    protected StewIngredientMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        itemStack = Utils.deserializeCodecMess(buf);
        ingredientAction = IngredientAction.values()[buf.readInt()];
        protectedQuantity = buf.readInt();
    }

    @Override
    protected void toBytes(@NotNull final RegistryFriendlyByteBuf buf)
    {
        super.toBytes(buf);

        Utils.serializeCodecMess(buf, itemStack);
        buf.writeInt(ingredientAction.ordinal());
        buf.writeInt(protectedQuantity);
    }

    /**
     * Server-side handler for the StewIngredientMessage.
     * This method will be called on the server when the client sends a StewIngredientMessage.
     * The method will take the appropriate action based on the value of the ingredientAction field.
     * If the ingredientAction is REMOVE, the method will remove the ingredient from the building's ingredient list.
     * If the ingredientAction is ADD, the method will add the ingredient to the building's ingredient list.
     * After modifying the ingredient list, the method will notify all connected stations of the change.
     * 
     * @param ctxIn the payload context for this message
     * @param player the player who sent the message
     * @param colony the colony the message is for
     * @param building the building that the message is for
     * @see AbstractBuildingServerMessage#onExecute(IPayloadContext, ServerPlayer, IColony, IBuilding)
     */
    @Override
    protected void onExecute(final IPayloadContext ctxIn, final ServerPlayer player, final IColony colony, final IBuilding building)
    {
        if (building.hasModule(MCTPBuildingModules.STEWMELIER_INGREDIENTS))
        {
            if (ingredientAction == IngredientAction.REMOVE)
            {
                TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Executing StewIngredientMessage to remove ingredient."));
                building.getModule(MCTPBuildingModules.STEWMELIER_INGREDIENTS).removeIngredient(new ItemStorage(itemStack, protectedQuantity));
            }
            else
            {
                TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Executing StewIngredientMessage to add ingredient."));
                building.getModule(MCTPBuildingModules.STEWMELIER_INGREDIENTS).addIngredient(new ItemStorage(itemStack, protectedQuantity));
            }

            MessageUtils.format(INGREDIENTS_UPDATED).sendTo(player);
        }
    }
}
