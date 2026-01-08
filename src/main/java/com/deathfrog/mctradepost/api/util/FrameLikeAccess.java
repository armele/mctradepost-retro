package com.deathfrog.mctradepost.api.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

public final class FrameLikeAccess
{
    private FrameLikeAccess() {}

    // FastItemFrames exposes a block tag "fastitemframes:item_frames"
    @SuppressWarnings("null")
    private static final TagKey<Block> FAST_ITEM_FRAMES_BLOCK_TAG =
        TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("fastitemframes", "item_frames"));

    /**
     * Represents either a vanilla ItemFrame entity or a "frame-like" block (e.g., Fast Item Frames).
     */
    public interface FrameHandle
    {
        boolean exists();
        ItemStack getItem();
        boolean setItem(ItemStack stack); // server-side preferred
    }

    /**
     * Resolve a frame-like at/near the given position.
     *
     * Order:
     *  1) UUID -> entity (your current behavior)
     *  2) entity search near pos (handles cases where UUID is stale)
     *  3) FastItemFrames block + block entity at pos
     */
    public static FrameHandle resolve(Level level, BlockPos pos, UUID expectedFrameId)
    {
        if (level == null || pos == null) return new Missing();

        // 1) UUID -> entity
        if (expectedFrameId != null && level instanceof ServerLevel sl)
        {
            var e = sl.getEntity(expectedFrameId);
            if (e instanceof ItemFrame frame)
            {
                return new Vanilla(frame);
            }
        }

        // 2) Fallback: find an ItemFrame entity near the block pos (covers stale UUIDs / respawns)
        {
            AABB box = new AABB(pos).inflate(0.25);
            List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, NullnessBridge.assumeNonnull(box));
            if (!frames.isEmpty())
            {
                return new Vanilla(frames.get(0));
            }
        }

        // 3) Fast Item Frames: block tag + BE reflection
        BlockState state = level.getBlockState(pos);
        if (!state.is(NullnessBridge.assumeNonnull(FAST_ITEM_FRAMES_BLOCK_TAG)))
        {
            return new Missing();
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null)
        {
            // Still "exists" as a frame-like block (block is there),
            // but without a BE we can't set/get item reliably.
            return new FrameBlockWithoutBE();
        }

        return new FastItemFrames(be);
    }

    // --------------------
    // Implementations
    // --------------------

    private static final class Missing implements FrameHandle
    {
        @Override public boolean exists() { return false; }
        @Override public ItemStack getItem() { return ItemStack.EMPTY; }
        @Override public boolean setItem(ItemStack stack) { return false; }
    }

    private static final class FrameBlockWithoutBE implements FrameHandle
    {
        @Override public boolean exists() { return true; }
        @Override public ItemStack getItem() { return ItemStack.EMPTY; }
        @Override public boolean setItem(ItemStack stack) { return false; }
    }

    private static final class Vanilla implements FrameHandle
    {
        private final ItemFrame frame;

        private Vanilla(ItemFrame frame) { this.frame = frame; }

        @Override public boolean exists() { return frame != null && frame.isAlive(); }

        @Override
        public ItemStack getItem()
        {
            return exists() ? frame.getItem() : ItemStack.EMPTY;
        }

        @SuppressWarnings("null")
        @Override
        public boolean setItem(ItemStack stack)
        {
            if (!exists()) return false;
            frame.setItem(stack.copy());
            return true;
        }
    }

    /**
     * Reflection-based integration (keeps FastItemFrames optional at runtime).
     *
     * Expected methods on fuzs.fastitemframes.world.level.block.entity.ItemFrameBlockEntity:
     *  - getEntityRepresentation(): returns an ItemFrame entity instance
     *  - markUpdated(): syncs state to clients / updates blockstate
     *
     * We use the internal ItemFrame entity's getItem()/setItem(...) to read/write.
     */
    private static final class FastItemFrames implements FrameHandle
    {
        private final BlockEntity be;

        private FastItemFrames(BlockEntity be) { this.be = be; }

        @Override public boolean exists() { return be != null && !be.isRemoved(); }

        @Override
        public ItemStack getItem()
        {
            Object entityRep = getEntityRepresentation(be);
            if (entityRep instanceof ItemFrame frame)
            {
                return frame.getItem();
            }
            return ItemStack.EMPTY;
        }

        @SuppressWarnings("null")
        @Override
        public boolean setItem(ItemStack stack)
        {
            if (!(be.getLevel() instanceof ServerLevel)) return false;

            Object entityRep = getEntityRepresentation(be);
            if (!(entityRep instanceof ItemFrame frame))
            {
                return false;
            }

            frame.setItem(stack.copy());
            markUpdated(be);
            return true;
        }

        private static Object getEntityRepresentation(BlockEntity be)
        {
            try
            {
                Class<?> fifBeClass = Class.forName("fuzs.fastitemframes.world.level.block.entity.ItemFrameBlockEntity");
                if (!fifBeClass.isInstance(be)) return null;

                Method m = fifBeClass.getMethod("getEntityRepresentation");
                return m.invoke(be);
            }
            catch (Throwable ignored)
            {
                return null;
            }
        }

        private static void markUpdated(BlockEntity be)
        {
            try
            {
                Class<?> fifBeClass = Class.forName("fuzs.fastitemframes.world.level.block.entity.ItemFrameBlockEntity");
                if (!fifBeClass.isInstance(be)) return;

                Method m = fifBeClass.getMethod("markUpdated");
                m.invoke(be);
            }
            catch (Throwable ignored)
            {
                // If this fails, item might still appear server-side but not sync correctly.
            }
        }
    }
}
