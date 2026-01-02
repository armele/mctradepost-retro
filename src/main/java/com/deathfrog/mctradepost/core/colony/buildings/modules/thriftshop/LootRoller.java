package com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop;

import java.util.List;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public final class LootRoller
{
    private static final class WeightedBlockStatePool
    {
        private final java.util.ArrayList<BlockState> states = new java.util.ArrayList<>();
        private final java.util.ArrayList<Integer> cumulative = new java.util.ArrayList<>();
        private int total = 0;

        WeightedBlockStatePool add(BlockState state, int weight)
        {
            if (state == null || weight <= 0) return this;

            total += weight;
            states.add(state);
            cumulative.add(total);
            return this;
        }

        BlockState pick(RandomSource rand)
        {
            if (states.isEmpty())
                return Blocks.SHORT_GRASS.defaultBlockState();

            int r = rand.nextInt(total) + 1; // 1..total
            for (int i = 0; i < cumulative.size(); i++)
            {
                if (r <= cumulative.get(i))
                    return states.get(i);
            }
            return states.get(states.size() - 1);
        }
    }

    private static final String MINECRAFT_NAMESPACE = "minecraft";
    private static final @Nonnull BlockPos ORIGIN_POS = NullnessBridge.assumeNonnull(BlockPos.ZERO);

    // Prefer matching state when table id is a known block loot table.
    private static final java.util.Map<ResourceLocation, BlockState> TABLE_TO_STATE = java.util.Map.ofEntries(
        entry("minecraft:blocks/short_grass", Blocks.SHORT_GRASS.defaultBlockState()),
        entry("minecraft:blocks/tall_grass", Blocks.TALL_GRASS.defaultBlockState()),
        entry("minecraft:blocks/fern", Blocks.FERN.defaultBlockState()),
        entry("minecraft:blocks/large_fern", Blocks.LARGE_FERN.defaultBlockState()),

        entry("minecraft:blocks/oak_leaves", Blocks.OAK_LEAVES.defaultBlockState()),
        entry("minecraft:blocks/spruce_leaves", Blocks.SPRUCE_LEAVES.defaultBlockState()),
        entry("minecraft:blocks/birch_leaves", Blocks.BIRCH_LEAVES.defaultBlockState()),
        entry("minecraft:blocks/jungle_leaves", Blocks.JUNGLE_LEAVES.defaultBlockState()),
        entry("minecraft:blocks/acacia_leaves", Blocks.ACACIA_LEAVES.defaultBlockState()),
        entry("minecraft:blocks/dark_oak_leaves", Blocks.DARK_OAK_LEAVES.defaultBlockState()),
        entry("minecraft:blocks/mangrove_leaves", Blocks.MANGROVE_LEAVES.defaultBlockState()),
        entry("minecraft:blocks/cherry_leaves", Blocks.CHERRY_LEAVES.defaultBlockState()),
        entry("minecraft:blocks/azalea_leaves", Blocks.AZALEA_LEAVES.defaultBlockState()),
        entry("minecraft:blocks/flowering_azalea_leaves", Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState())
    );

    // Weighted fallback pool: grasses much more common than leaves.
    private static final WeightedBlockStatePool DEFAULT_FORAGE_BLOCKS = new WeightedBlockStatePool()
        .add(Blocks.SHORT_GRASS.defaultBlockState(), 40)
        .add(Blocks.TALL_GRASS.defaultBlockState(), 10)
        .add(Blocks.FERN.defaultBlockState(), 12)
        .add(Blocks.OAK_LEAVES.defaultBlockState(), 8)
        .add(Blocks.BIRCH_LEAVES.defaultBlockState(), 6)
        .add(Blocks.SPRUCE_LEAVES.defaultBlockState(), 6)
        .add(Blocks.JUNGLE_LEAVES.defaultBlockState(), 4)
        .add(Blocks.ACACIA_LEAVES.defaultBlockState(), 4)
        .add(Blocks.DARK_OAK_LEAVES.defaultBlockState(), 4)
        .add(Blocks.MANGROVE_LEAVES.defaultBlockState(), 3)
        .add(Blocks.CHERRY_LEAVES.defaultBlockState(), 3)
        .add(Blocks.AZALEA_LEAVES.defaultBlockState(), 2)
        .add(Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState(), 1);


    private LootRoller() {}

    /** Rolls exactly one stack from a chest-style loot table. */
    public static ItemStack rollChestStyle(ServerLevel level, @Nonnull RandomSource rand, @Nonnull ResourceLocation tableId)
    {
        if (level == null || level.isClientSide) return ItemStack.EMPTY;

        final LootTable table = getLootTable(level, tableId);

        if (table == null || table == LootTable.EMPTY) return ItemStack.EMPTY;

        try
        {
            @SuppressWarnings("null")
            final LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(ORIGIN_POS))
                .withLuck(0.0f)
                .create(LootContextParamSets.CHEST);
                
            if (params == null) return ItemStack.EMPTY;

            return pickOne(table, params, rand);
        } 
        catch (Exception e) 
        {
            return ItemStack.EMPTY;
        }
    }

    /**
     * Rolls exactly one stack from a block-style loot table. This method derives a
     * plausible block state for the given table, and then uses this block state
     * to retrieve a single item stack from the loot table.
     *
     * @param level the server level to retrieve the loot table from
     * @param rand the random source to use when retrieving the item
     * @param tableId the resource location of the loot table to retrieve the item from
     * @return a single item stack from the given loot table, or an empty item stack if the
     *         loot table returns no items
     */
    public static ItemStack rollBlockStyle(ServerLevel level, @Nonnull RandomSource rand, @Nonnull ResourceLocation tableId)
    {
        if (level == null || level.isClientSide) return ItemStack.EMPTY;

        final LootTable table = getLootTable(level, tableId);

        if (table == null || table == LootTable.EMPTY) return ItemStack.EMPTY;

        // Derive a plausible block state for this table.
        final BlockState state = deriveBlockStateForTable(tableId, rand);

        try
        {
            @SuppressWarnings("null")
            final LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(ORIGIN_POS))
                .withParameter(LootContextParams.BLOCK_STATE, state)
                .withLuck(0.0f)
                .create(LootContextParamSets.BLOCK);

            if (params == null) return ItemStack.EMPTY;

            return pickOne(table, params, rand);
        }
        catch (Exception e)
        {
            return ItemStack.EMPTY;
        }
    }

    /**
     * Attempts to derive a plausible block state for the given loot table.
     * This method does its best to guess a block state based on the given loot table ID.
     * It first checks if we have an exact mapping for the given table ID (e.g. if we know
     * exactly which block corresponds to the given table ID). If we do, it returns
     * that block state.
     * If we don't know exactly which block corresponds to the given table ID, it then
     * attempts to resolve the block state by name. If the table ID looks like a vanilla
     * block table ("minecraft:blocks/<name>"), it attempts to resolve the block by name.
     * This is best-effort and safe-failing.
     * If all else fails, it falls back to a random "forage" block state from a weighted pool.
     * @param tableId the resource location of the loot table to derive a block state for
     * @param rand the random source to use when deriving the block state
     * @return a plausible block state for the given loot table, or a random "forage" block state
     *         if all else fails
     */
    private static BlockState deriveBlockStateForTable(@Nonnull ResourceLocation tableId, @Nonnull RandomSource rand)
    {
        // 1) If we know exactly which block this table corresponds to, use it.
        BlockState exact = TABLE_TO_STATE.get(tableId);

        if (exact != null) return exact;

        // 2) Otherwise, if it *looks like* a vanilla block table ("minecraft:blocks/<name>"),
        //    attempt to resolve the block by name. This is best-effort and safe-failing.
        //    NOTE: This only helps if the caller uses consistent table names.
        if (MINECRAFT_NAMESPACE.equals(tableId.getNamespace()) && tableId.getPath().startsWith("blocks/"))
        {
            String blockName = tableId.getPath().substring("blocks/".length());

            if (blockName == null) return DEFAULT_FORAGE_BLOCKS.pick(rand);

            ResourceLocation blockId = ResourceLocation.fromNamespaceAndPath(MINECRAFT_NAMESPACE, blockName);

            Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);

            if (block != null) return block.defaultBlockState();
        }

        // 3) Fallback: random "forage" block state from weighted pool.
        return DEFAULT_FORAGE_BLOCKS.pick(rand);
    }


    /** Rolls exactly one stack from a fishing subtable (fish/junk/treasure). */
    public static ItemStack rollFishingStyle(ServerLevel level, @Nonnull RandomSource rand, @Nonnull ResourceLocation tableId, float luck)
    {
        if (level == null || level.isClientSide) return ItemStack.EMPTY;

        final LootTable table = getLootTable(level, tableId);

        if (table == null || table == LootTable.EMPTY) return ItemStack.EMPTY;

        final ItemStack fishingRod = new ItemStack(NullnessBridge.assumeNonnull(Items.FISHING_ROD));

        try
        {
            @SuppressWarnings("null")
            final LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(ORIGIN_POS))
                .withParameter(LootContextParams.TOOL, fishingRod)
                .withLuck(luck)
                .create(LootContextParamSets.FISHING);

            if (params == null) return ItemStack.EMPTY;

            return pickOne(table, params, rand);
        } 
        catch (Exception e) 
        {
            return ItemStack.EMPTY;
        }
    }


    /**
     * Returns a single item stack from the given loot table, using the given loot params and random source.
     * If the loot table returns no items, an empty item stack is returned.
     * <p>
     * This method is intended to be used on the server side. On the client side, it will always return an empty item stack.
     * <p>
     * @param table the loot table to retrieve an item from
     * @param params the loot params to pass to the loot table
     * @param rand the random source to use when retrieving the item
     * @return a single item stack from the given loot table, or an empty item stack if the loot table returns no items
     */
    private static ItemStack pickOne(@Nonnull LootTable table, @Nonnull LootParams params, @Nonnull RandomSource rand)
    {
        final List<ItemStack> out = table.getRandomItems(params, rand);

        if (out.isEmpty())
        {
            return ItemStack.EMPTY;
        }

        return out.get(rand.nextInt(out.size())).copy();
    }


    /**
     * Returns the loot table identified by the given tableId, or null if it does not exist.
     * <p>
     * This method is intended to be used on the server side. On the client side,
     * it will always return null.
     * <p>
     * @param level the server level to access the loot table from
     * @param tableId the resource location of the loot table to retrieve
     * @return the loot table identified by the given tableId, or null if it does not exist
     */
    private static LootTable getLootTable(ServerLevel level, @Nonnull ResourceLocation tableId)
    {
        ResourceKey<LootTable> lootTableKey = ResourceKey.create(NullnessBridge.assumeNonnull(Registries.LOOT_TABLE), tableId);

        if (lootTableKey == null) return null;

        // Access the loot table from MinecraftServer correctly
        LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(lootTableKey);

        return lootTable;
    }


    /**
     * Creates a new entry in a map from the given tableId and BlockState.
     * @param tableId the resource location of the loot table to create an entry for
     * @param state the block state to associate with the given loot table
     * @return a new entry in a map from the given tableId and BlockState
     */
    private static java.util.Map.Entry<ResourceLocation, BlockState> entry(@Nonnull String tableId, BlockState state)
    {
        return java.util.Map.entry(ResourceLocation.parse(tableId), state);
    }

}