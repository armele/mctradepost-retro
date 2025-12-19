package com.deathfrog.mctradepost.core.event.wishingwell.ritual;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.StringUtils;
import com.deathfrog.mctradepost.item.CoinItem;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import static com.deathfrog.mctradepost.core.event.wishingwell.WishingWellHandler.RAIDER_TAG;

public class RitualDefinitionHelper
{
    protected String originatingModId;
    protected String fileName;
    protected RitualDefinition ritualDefinition;

    public RitualDefinitionHelper(ResourceLocation id, @Nonnull RitualDefinition definition) 
    {
        this.ritualDefinition = definition;
        this.originatingModId = id.getNamespace();
        this.fileName = id.getPath();
    }

    /**
     * Returns the filename of the ritual definition file that this helper object is associated with.
     * This is useful for logging and debugging purposes.
     * @return The filename of the ritual definition file that this helper object is associated with.
     */
    public String getFileName() 
    {
        return this.fileName;
    }

    /**
     * Retrieves the target item for the ritual as an Item object.
     * If the target specified in the ritual definition is invalid or not found,
     * logs a warning and returns null.
     * 
     * @return The Item corresponding to the ritual target, or null if the target is invalid or unknown.
     */
    @Nullable
    public Item getTargetAsItem() 
    {
        String target = this.ritualDefinition.target();

        if (target == null || target.isEmpty()) 
        {
            return null;
        }

        ResourceLocation itemLocation = ResourceLocation.tryParse(target);

        if (itemLocation == null) 
        {
            MCTradePostMod.LOGGER.warn("{}: Invalid target item {} identified in ritual with companion item: {}", this.fileName, target, this.ritualDefinition.companionItem());
            return null;
        }

        Item item = BuiltInRegistries.ITEM.get(itemLocation);

        if (item == null || item.equals(Items.AIR)) 
        {
            return null;
        }
        
        return item;

    }

    /**
     * Retrieves the coin item for the ritual as an Item object.
     * If the coin specified in the ritual definition is invalid or not found,
     * logs a warning and returns null.
     * If the coin type matches the internal coin item ID, returns the internal coin.
     * Otherwise, looks up the coin in the item registry using the provided ID.
     * If the coin is not found, logs a warning and returns null.
     * 
     * @return The Item corresponding to the ritual coin, or null if the coin is invalid or unknown.
     */
    @Nullable
    public Item getCoinAsItem() 
    {
        Item coinItem = null;
        String coinType = this.ritualDefinition.coinType();

        if (coinType == null || coinType.isEmpty()) 
        {
            MCTradePostMod.LOGGER.warn("{}: No coin item specified in ritual with companion item: {}", this.fileName, this.ritualDefinition.coinType(), this.ritualDefinition.companionItem());
            return null;
        }

        ResourceLocation itemLocation = ResourceLocation.tryParse(coinType);

        if (itemLocation == null) 
        {
            coinItem = MCTradePostMod.MCTP_COIN_ITEM.get();
        }
        else 
        {
            coinItem = BuiltInRegistries.ITEM.get(itemLocation);
        }
        
        if (!(coinItem instanceof CoinItem))
        {
            MCTradePostMod.LOGGER.warn("{}: Unknown coin item {} identified in ritual with companion item: {}", this.fileName, this.ritualDefinition.coinType(), this.ritualDefinition.companionItem());
            return null;
        }

        return coinItem;
    }

    /**
     * Returns the EntityType that is targeted by this ritual, or null if the target is unknown.
     * @return The EntityType targeted by this ritual, or null if the target is unknown.
     */
    @Nullable
    public EntityType<?> getTargetAsEntityType() 
    {
       ResourceLocation entityTypeId = null;

        try 
        {
            String target = this.ritualDefinition.target();

            if (target == null || target.isEmpty()) 
            {
                return null;
            }

            entityTypeId = ResourceLocation.parse(target);  // Intentional use of parse.
        } 
        catch (IllegalArgumentException e) 
        {
            MCTradePostMod.LOGGER.warn("{}: Unknown target entity type {} identified in ritual with companion item: {}", this.fileName,this.ritualDefinition.target(), this.ritualDefinition.companionItem());
            return null;
        }
        
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(entityTypeId);

        return entityType;
    }


    /**
     * Returns a human-readable description of the effect of this ritual.
     * If the effect is "slay", the description will include the type of entity
     * to be slayed and the radius of the effect.
     * @return A human-readable description of the effect of this ritual.
     */
    @Nonnull
    public String describe() 
    {
        String text = "Not Defined: " + this.ritualDefinition.effect();
        EntityType<?> entityType = null;
        Item item = null;

        switch (this.ritualDefinition.effect()) 
        {
            case RitualManager.RITUAL_EFFECT_SLAY:
                entityType = getTargetAsEntityType();

                if (entityType == null) 
                {
                    return this.fileName + ": !UNDEFINED!";
                }

                text = "Slays " + entityType.toShortString().replace("_", " ") + "s within a " + this.ritualDefinition.radius() + " block radius.";
                break;
            
            case RitualManager.RITUAL_EFFECT_SUMMON:
                String summonTarget = "";
                if (RAIDER_TAG.equals(this.ritualDefinition.target())) 
                {
                    summonTarget = "raiders";
                }
                else 
                {
                    entityType = getTargetAsEntityType();
                    if (entityType == null) 
                    {
                        return "!UNDEFINED!";
                    }
                    summonTarget = StringUtils.getPluralEntityName(entityType);
                }

                String range = "";
                if (this.ritualDefinition.radius() > 0) 
                {
                    range = " within a " + this.ritualDefinition.radius() + " block radius.";
                } 
                else
                {
                    range = ", wherever they may be.";
                }
                text = "Finds and summons " + summonTarget + range;
                break;
            
            case RitualManager.RITUAL_EFFECT_WEATHER:
                text = "Sets the weather to " + this.ritualDefinition.target() + " for the rest of the day.";
                break;

            case RitualManager.RITUAL_EFFECT_TRANSFORM:
                item = getTargetAsItem();
                if (item == null) 
                {
                    text = "Broken ritual! Target item not recognized: " + this.ritualDefinition.target();
                }
                else
                {
                    ItemStack stack = new ItemStack(item);
                    text = "Transforms these into " + stack.getHoverName().getString();
                }
                break;

            case RitualManager.RITUAL_EFFECT_COMMUNITY:
                String benefit = "";
                Item companionItem = BuiltInRegistries.ITEM.get(companionItem());

                if (companionItem.equals(MCTradePostMod.WISH_PLENTY.get())) 
                {
                    benefit = "feeds all citizens.";    
                }
                else if (companionItem.equals(MCTradePostMod.WISH_HEALTH.get())) 
                {
                    benefit = "cures all sick citizens.";
                }
                else
                {
                    benefit = "UNKNOWN (" + companionItem().toString() + ")) report to mod author.";
                }

                text = "Colony community benefit: " + benefit;
                break;

            case RitualManager.RITUAL_EFFECT_OUTPOST:
                text = "Claims an outpost at the location marked by an Outpost Claim Marker.";
                break;

            default:
                text = "Broken ritual! Ritual type not recognized: " + ritualDefinition.effect();
                break;
        }

        return text;
    }

    public String getRitualTexture()
    {
        String ritualTexture =  this.originatingModId + ":" + "textures/rituals/" + this.ritualDefinition.effect() + "/" + fileName + ".png";

        return ritualTexture;
    }

    public ResourceLocation companionItem() 
    {
        return this.ritualDefinition.companionItem();
    } 
   
    public int companionItemCount() 
    {
        return this.ritualDefinition.companionItemCount();
    }

    public String effect() 
    {
        return this.ritualDefinition.effect();
    }

    public String coinType() 
    {
        return this.ritualDefinition.coinType();
    }

    public String target() 
    {
        return this.ritualDefinition.target();
    }
    
    public int radius() 
    {
        return this.ritualDefinition.radius();
    }
    
    public int requiredCoins() 
    {
        return this.ritualDefinition.requiredCoins();
    }

    public RitualDefinition getDefinition() 
    {
        return this.ritualDefinition;
    }

}
