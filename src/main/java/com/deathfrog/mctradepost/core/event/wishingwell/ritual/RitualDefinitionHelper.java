package com.deathfrog.mctradepost.core.event.wishingwell.ritual;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

public class RitualDefinitionHelper  {
    protected String originatingModId;
    protected String fileName;
    protected RitualDefinition ritualDefinition;

    public RitualDefinitionHelper(ResourceLocation id, @Nonnull RitualDefinition definition) {
        this.ritualDefinition = definition;
        this.originatingModId = id.getNamespace();
        this.fileName = id.getPath();
    }

    // public RitualDefinitionHelper(RitualDefinition definition) {
    //     this.ritualDefinition = definition;
    // }

    /**
     * Returns the EntityType that is targeted by this ritual, or null if the target is unknown.
     * @return The EntityType targeted by this ritual, or null if the target is unknown.
     */
    public EntityType<?> getEntityType() {
       ResourceLocation entityTypeId = null;

        try {
            entityTypeId = ResourceLocation.parse(this.ritualDefinition.target());  // Intentional use of parse.
        } catch (IllegalArgumentException e) {
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

        if (this.ritualDefinition.effect().equals(RitualManager.RITUAL_EFFECT_SLAY)) {
            EntityType<?> entityType = getEntityType();

            text = "Slays " + entityType.toShortString().replace("_", " ") + "s within a " + this.ritualDefinition.radius() + " block radius.";
        }

        return text;
    }

    public String getRitualTexture()
    {
        String ritualTexture =  this.originatingModId + ":" + "textures/rituals/" + this.ritualDefinition.effect() + "/" + fileName + ".png";

        return ritualTexture;
    }

    public ResourceLocation companionItem() {
        return this.ritualDefinition.companionItem();
    } 
   
    public String effect() {
        return this.ritualDefinition.effect();
    }

    public String target() {
        return this.ritualDefinition.target();
    }
    
    public int radius() {
        return this.ritualDefinition.radius();
    }
    
    public int requiredCoins() {
        return this.ritualDefinition.requiredCoins();
    }
}
