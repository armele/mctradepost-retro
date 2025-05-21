package com.deathfrog.mctradepost.core.event.wishingwell.ritual;

import net.minecraft.resources.ResourceLocation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record RitualDefinition (
    ResourceLocation companionItem,
    String effect,
    String target,
    int radius,
    int requiredCoins
) {

    public static final Codec<RitualDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ResourceLocation.CODEC.fieldOf("companion_item").forGetter(RitualDefinition::companionItem),
        Codec.STRING.fieldOf("effect_type").forGetter(RitualDefinition::effect),
        Codec.STRING.fieldOf("target").forGetter(RitualDefinition::target),
        Codec.INT.fieldOf("radius").forGetter(RitualDefinition::radius),
        Codec.INT.fieldOf("coin_cost").forGetter(RitualDefinition::requiredCoins)
    ).apply(instance, RitualDefinition::new));
}

