package com.deathfrog.mctradepost.item;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.blocks.ExtendedTimberFrameType;
import com.deathfrog.mctradepost.core.blocks.ExtendedTimberFrameBlock;
import com.google.common.collect.ImmutableList;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.item.interfaces.IDoItem;
import com.ldtteam.domumornamentum.util.BlockUtils;
import com.ldtteam.domumornamentum.util.MaterialTextureDataUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.util.List;

import javax.annotation.Nonnull;

public class ExtendedTimberframeItem extends BlockItem implements IDoItem {
    private final ExtendedTimberFrameBlock sourceBlock;

    public ExtendedTimberframeItem(final ExtendedTimberFrameBlock blockIn, final Properties builder)
    {
        super(blockIn, builder);
        this.sourceBlock = blockIn;
    }

    @Override
    public Component getName(final ItemStack stack)
    {
        final MaterialTextureData textureData = MaterialTextureData.readFromItemStack(stack);

        final IMateriallyTexturedBlockComponent centerComponent = sourceBlock.getComponents().get(1);
        final Block centerBlock = textureData.getTexturedComponents().getOrDefault(centerComponent.getId(), centerComponent.getDefault());
        final Component centerBlockName = BlockUtils.getHoverName(centerBlock);

        return Component.translatable(com.ldtteam.domumornamentum.util.Constants.MOD_ID + ".timber.frame.name.format", centerBlockName);
    }

    @Override
    public void appendHoverText(final @Nonnull ItemStack stack, final  @Nonnull TooltipContext tooltipContext, final  @Nonnull List<Component> tooltip, final  @Nonnull TooltipFlag flagIn)
    {
        super.appendHoverText(stack, tooltipContext, tooltip, flagIn);

        MaterialTextureData textureData = MaterialTextureData.readFromItemStack(stack);
        if (textureData.isEmpty()) {
            textureData = MaterialTextureDataUtil.generateRandomTextureDataFrom(stack);
        }

        final ExtendedTimberFrameType type = sourceBlock.getExtendedTimberFrameType();
        tooltip.add(Component.translatable(MCTradePostMod.MODID + ".origin.tooltip"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.translatable(MCTradePostMod.MODID + ".timber.frame.header"));
        tooltip.add(Component.translatable(MCTradePostMod.MODID + ".timber.frame.type.format", Component.translatable(MCTradePostMod.MODID + ".timber.frame.type." + type.getName())));

        final IMateriallyTexturedBlockComponent frameComponent = sourceBlock.getComponents().get(0);
        final Block frameBlock = textureData.getTexturedComponents().getOrDefault(frameComponent.getId(), frameComponent.getDefault());
        final Component frameBlockName = BlockUtils.getHoverName(frameBlock);
        tooltip.add(Component.translatable(MCTradePostMod.MODID + ".desc.frame", Component.translatable(com.ldtteam.domumornamentum.util.Constants.MOD_ID + ".desc.material", frameBlockName)));

        final IMateriallyTexturedBlockComponent centerComponent = sourceBlock.getComponents().get(1);
        final Block centerBlock = textureData.getTexturedComponents().getOrDefault(centerComponent.getId(), centerComponent.getDefault());
        final Component centerBlockName = BlockUtils.getHoverName(centerBlock);
        tooltip.add(Component.translatable(MCTradePostMod.MODID + ".desc.center", Component.translatable(com.ldtteam.domumornamentum.util.Constants.MOD_ID + ".desc.material", centerBlockName)));
    }

    @Override
    public List<ResourceLocation> getInputIds()
    {
        return ImmutableList.of(
            ResourceLocation.fromNamespaceAndPath(com.ldtteam.domumornamentum.util.Constants.MOD_ID, "frame"), 
            ResourceLocation.fromNamespaceAndPath(com.ldtteam.domumornamentum.util.Constants.MOD_ID, "center"));
    }

    @Override
    public ResourceLocation getGroup()
    {
        return ResourceLocation.fromNamespaceAndPath(com.ldtteam.domumornamentum.util.Constants.MOD_ID, "btimberframe");
    }
}
