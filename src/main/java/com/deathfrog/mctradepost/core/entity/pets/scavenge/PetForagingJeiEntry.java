package com.deathfrog.mctradepost.core.entity.pets.scavenge;

import java.util.ArrayList;
import java.util.List;

import com.deathfrog.mctradepost.api.entity.pets.PetRoles;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Display-only representation of one pet foraging outcome for JEI.
 * <p>
 * These entries are derived on the server from block tags and loot-table JSON, then synced to the client so JEI reflects the
 * active server datapack state instead of only the client's local resources.
 * </p>
 *
 * @param id stable display id for this JEI entry
 * @param role pet role that can produce the output
 * @param workLocationBlock block assigned as the pet work location, such as feeder, dredger, or scavenge base
 * @param sourceBlock block whose tag membership/loot table contributes the shown outputs
 * @param sourceTag source tag used to discover the source block
 * @param lootTable derived loot table id used by the matching scavenge profile
 * @param outputs possible outputs extracted from the loot table for display
 * @param noteKey translation key for a short contextual note in JEI
 */
public record PetForagingJeiEntry(
    ResourceLocation id,
    PetRoles role,
    ResourceLocation workLocationBlock,
    ResourceLocation sourceBlock,
    ResourceLocation sourceTag,
    ResourceLocation lootTable,
    List<ItemStack> outputs,
    String noteKey)
{
    /**
     * Network codec used by {@link PetForagingJeiSyncPacket}.
     * <p>
     * This is hand-written because the vanilla {@code StreamCodec.composite} helpers do not support enough fields for this display
     * record.
     * </p>
     */
    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, PetForagingJeiEntry> STREAM_CODEC =
        new StreamCodec<>()
        {
            @Override
            public PetForagingJeiEntry decode(final RegistryFriendlyByteBuf buf)
            {
                final ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buf);
                final PetRoles role = PetRoles.values()[buf.readVarInt()];
                final ResourceLocation workLocationBlock = ResourceLocation.STREAM_CODEC.decode(buf);
                final ResourceLocation sourceBlock = ResourceLocation.STREAM_CODEC.decode(buf);
                final ResourceLocation sourceTag = ResourceLocation.STREAM_CODEC.decode(buf);
                final ResourceLocation lootTable = ResourceLocation.STREAM_CODEC.decode(buf);
                final List<ItemStack> outputs = new ArrayList<>();
                final int outputCount = buf.readVarInt();
                for (int i = 0; i < outputCount; i++)
                {
                    outputs.add(ItemStack.STREAM_CODEC.decode(buf));
                }
                final String noteKey = ByteBufCodecs.STRING_UTF8.decode(buf);

                return new PetForagingJeiEntry(id, role, workLocationBlock, sourceBlock, sourceTag, lootTable, outputs, noteKey);
            }

            @Override
            public void encode(final RegistryFriendlyByteBuf buf, final PetForagingJeiEntry entry)
            {
                ResourceLocation.STREAM_CODEC.encode(buf, entry.id());
                buf.writeVarInt(entry.role().ordinal());
                ResourceLocation.STREAM_CODEC.encode(buf, entry.workLocationBlock());
                ResourceLocation.STREAM_CODEC.encode(buf, entry.sourceBlock());
                ResourceLocation.STREAM_CODEC.encode(buf, entry.sourceTag());
                ResourceLocation.STREAM_CODEC.encode(buf, entry.lootTable());
                buf.writeVarInt(entry.outputs().size());
                for (ItemStack output : entry.outputs())
                {
                    ItemStack.STREAM_CODEC.encode(buf, output);
                }
                ByteBufCodecs.STRING_UTF8.encode(buf, entry.noteKey());
            }
        };

    public PetForagingJeiEntry
    {
        outputs = List.copyOf(outputs);
    }
}
