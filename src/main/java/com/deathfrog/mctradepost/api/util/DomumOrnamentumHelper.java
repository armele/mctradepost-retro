package com.deathfrog.mctradepost.api.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import java.util.ArrayList;
import java.util.List;
import com.deathfrog.mctradepost.MCTradePostMod;

public class DomumOrnamentumHelper
{
    public static final String DOMUM_ORNAMENTUM_TEXTURE_TAG = "domum_ornamentum:texture_data";

    /**
     * Extracts the textured block components from a Domum Ornamentum ItemStack.
     *
     * @param stack          The Domum Ornamentum block ItemStack.
     * @param registryAccess The current RegistryAccess (e.g. from Level).
     * @return A list of ItemStacks representing the texture source blocks.
     */
    public static List<ItemStack> getDomumOrnamentumTextureComponents(ItemStack stack, RegistryAccess registryAccess)
    {
        List<ItemStack> textures = new ArrayList<>();

        if (stack == null || stack.isEmpty() || registryAccess == null)
        {
            MCTradePostMod.LOGGER.warn("Invalid arguments for getDomumOrnamentumTextureComponents (something is null).");
            return textures;
        }

        // Get the NBT from the item stack.
        CompoundTag tag = (CompoundTag) stack.save(registryAccess);

        CompoundTag componentsTag = tag.getCompound("components");
        if (componentsTag == null || componentsTag.isEmpty())
        {
            // MCTradePostMod.LOGGER.info("No 'components' found within {} while evaluating domum textures.", tag);
            return textures;
        }

        // Ensure the custom block entity data exists
        if (!componentsTag.contains(DOMUM_ORNAMENTUM_TEXTURE_TAG, Tag.TAG_COMPOUND))
        {
            // MCTradePostMod.LOGGER.info("No 'domum_ornamentum:texture_data' found within {} while evaluating domum textures.",
            // componentsTag);
            return textures;
        }

        CompoundTag innerTag = componentsTag.getCompound(DOMUM_ORNAMENTUM_TEXTURE_TAG);

        for (String key : innerTag.getAllKeys())
        {
            if (key == null)
            {
                continue;
            }

            if (innerTag.contains(key, Tag.TAG_STRING))
            {
                String blockId = innerTag.getString(key);

                if (blockId == null)
                {
                    continue;
                }

                ResourceLocation rl = ResourceLocation.tryParse(blockId);
                if (rl != null)
                {
                    Block block = registryAccess.registryOrThrow(NullnessBridge.assumeNonnull(Registries.BLOCK)).get(rl);
                    if (block != null && block != net.minecraft.world.level.block.Blocks.AIR)
                    {
                        Item item = block.asItem();
                        if (item != null && item != Items.AIR)
                        {
                            textures.add(new ItemStack(item));
                        }
                    }
                }
            }
        }

        return textures;
    }

}
