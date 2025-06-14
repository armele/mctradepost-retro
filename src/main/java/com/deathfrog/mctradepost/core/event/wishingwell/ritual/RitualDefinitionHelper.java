package com.deathfrog.mctradepost.core.event.wishingwell.ritual;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.StringUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

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
     * Returns the EntityType that is targeted by this ritual, or null if the target is unknown.
     * @return The EntityType targeted by this ritual, or null if the target is unknown.
     */
    public EntityType<?> getTargetAsEntityType() 
    {
       ResourceLocation entityTypeId = null;

        try 
        {
            entityTypeId = ResourceLocation.parse(this.ritualDefinition.target());  // Intentional use of parse.
        } 
        catch (IllegalArgumentException e) 
        {
            MCTradePostMod.LOGGER.warn("Unknown target entity type {} identified in ritual with companion item: {}", this.ritualDefinition.target(), this.ritualDefinition.companionItem());
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
    public String describe() {
        String text = "Not Defined";

        if (this.ritualDefinition.effect().equals(RitualManager.RITUAL_EFFECT_SLAY)) 
        {
            EntityType<?> entityType = getTargetAsEntityType();

            text = "Slays " + entityType.toShortString().replace("_", " ") + "s within a " + this.ritualDefinition.radius() + " block radius.";
        } 
        else if (this.ritualDefinition.effect().equals(RitualManager.RITUAL_EFFECT_SUMMON)) 
        {
            String summonTarget = "";
            if (RAIDER_TAG.equals(this.ritualDefinition.target())) 
            {
                summonTarget = "raiders";
            }
            else 
            {
                EntityType<?> entityType = getTargetAsEntityType();
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
        } 
        else if (this.ritualDefinition.effect().equals(RitualManager.RITUAL_EFFECT_WEATHER)) 
        {
            text = "Sets the weather to " + this.ritualDefinition.target() + " for the rest of the day.";
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
   
    public String effect() 
    {
        return this.ritualDefinition.effect();
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
}
