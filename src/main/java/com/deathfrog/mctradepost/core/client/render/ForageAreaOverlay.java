package com.deathfrog.mctradepost.core.client.render;

import java.util.OptionalDouble;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.blocks.BlockFeeder;
import com.deathfrog.mctradepost.core.blocks.BlockDredger;
import com.deathfrog.mctradepost.api.entity.pets.goals.scavenge.DredgerForageRange;
import com.deathfrog.mctradepost.api.entity.pets.goals.scavenge.ScavengeHarvestability;
import com.deathfrog.mctradepost.api.entity.pets.goals.scavenge.VegetationForageRange;
import com.deathfrog.mctradepost.api.entity.pets.PetRoles;
import com.deathfrog.mctradepost.core.entity.pets.scavenge.PetForagingJeiCache;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Renders a client-local wireframe around the forage-search volume associated
 * with a pet working block.
 * <p>
 * At most one area is displayed at a time. Feeders use the dimensions from
 * {@link VegetationForageRange}, including the different height for hanging
 * feeders, while dredgers use {@link DredgerForageRange}. The selected origin
 * is not persisted or sent to the server; it remains active only for the
 * current client session until disabled, replaced, or found to be invalid.
 * </p>
 */
@EventBusSubscriber(modid = MCTradePostMod.MODID, value = Dist.CLIENT)
public final class ForageAreaOverlay
{
    private static final int MAX_TARGET_HIGHLIGHTS = 2048;
    /**
     * Main-target line type that draws only fragments behind existing world
     * depth. This makes obscured boundary segments visible without allowing the
     * bright pass to show through terrain.
     */
    @SuppressWarnings("null")
    private static final RenderType OBSCURED_LINES = RenderType.create(
        "mctradepost_forage_area_obscured_lines",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        VertexFormat.Mode.LINES,
        1536,
        RenderType.CompositeState.builder()
            .setShaderState(RenderType.RENDERTYPE_LINES_SHADER)
            .setLineState(new RenderType.LineStateShard(OptionalDouble.empty()))
            .setLayeringState(RenderType.VIEW_OFFSET_Z_LAYERING)
            .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
            .setOutputState(RenderType.MAIN_TARGET)
            .setDepthTestState(RenderType.GREATER_DEPTH_TEST)
            .setWriteMaskState(RenderType.COLOR_WRITE)
            .setCullState(RenderType.NO_CULL)
            .createCompositeState(false));

    /**
     * Main-target line type that explicitly tests against the world depth
     * buffer, allowing this bright pass to cover only directly visible parts
     * of the darker complete boundary.
     */
    @SuppressWarnings("null")
    private static final RenderType VISIBLE_LINES = RenderType.create(
        "mctradepost_forage_area_visible_lines",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        VertexFormat.Mode.LINES,
        1536,
        RenderType.CompositeState.builder()
            .setShaderState(RenderType.RENDERTYPE_LINES_SHADER)
            .setLineState(new RenderType.LineStateShard(OptionalDouble.empty()))
            .setLayeringState(RenderType.VIEW_OFFSET_Z_LAYERING)
            .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
            .setOutputState(RenderType.MAIN_TARGET)
            .setDepthTestState(RenderType.LEQUAL_DEPTH_TEST)
            .setWriteMaskState(RenderType.COLOR_WRITE)
            .setCullState(RenderType.NO_CULL)
            .createCompositeState(false));

    /** Dimension containing the currently selected working block. */
    private static ResourceKey<Level> dimension;

    /** Position of the currently selected feeder or dredger. */
    private static BlockPos origin;

    /** Focus item captured when the player enabled this snapshot. */
    private static Item snapshotFocusItem;

    /** Harvestable focused-source positions captured when the overlay was enabled. */
    private static List<BlockPos> focusedTargets = List.of();

    /** Prevents instantiation of this event-driven utility class. */
    private ForageAreaOverlay() { }

    /**
     * Checks whether the overlay is currently enabled for a particular working
     * block.
     *
     * @param level client level containing the working block, or {@code null}
     * @param pos position of the working block to test
     * @return {@code true} when the supplied dimension and position match the
     *         currently selected overlay origin
     */
    public static boolean isEnabled(@Nullable Level level, BlockPos pos)
    {
        return level != null && pos != null && level.dimension().equals(dimension) && pos.equals(origin);
    }

    /**
     * Enables or disables the forage-area overlay.
     * <p>
     * Enabling replaces any previously selected origin. Disabling, or passing
     * a missing level or position, clears the current selection.
     * </p>
     *
     * @param level client level containing the selected working block
     * @param pos position of the selected feeder or dredger
     * @param focusStack focus item whose currently harvestable sources should
     *        be captured, or an empty stack to capture only the range
     * @param enabled {@code true} to display this area; {@code false} to clear
     *        the overlay
     */
    public static void setEnabled(
        @Nullable Level level, @Nullable BlockPos pos, final ItemStack focusStack, boolean enabled)
    {
        if (!enabled || level == null || pos == null)
        {
            clear();
            return;
        }
        dimension = level.dimension();
        origin = pos.immutable();
        snapshotFocusItem = focusStack == null || focusStack.isEmpty() ? null : focusStack.getItem();

        if (origin == null) return;

        focusedTargets = captureFocusedTargets(level, origin, snapshotFocusItem);
    }

    /**
     * Verifies that an open working-block screen still has the focus used by
     * the current snapshot. A changed focus invalidates the complete overlay.
     *
     * @return {@code true} when the overlay remains active and its focus agrees
     */
    public static boolean validateFocus(
        @Nullable final Level level, @Nullable final BlockPos pos, final ItemStack currentFocus)
    {
        if (!isEnabled(level, pos)) return false;
        final Item currentItem = currentFocus == null || currentFocus.isEmpty() ? null : currentFocus.getItem();
        if (currentItem == snapshotFocusItem) return true;
        clear();
        return false;
    }

    private static void clear()
    {
        dimension = null;
        origin = null;
        snapshotFocusItem = null;
        focusedTargets = List.of();
    }

    @SuppressWarnings("null")
    private static List<BlockPos> captureFocusedTargets(
        final Level level, final @Nonnull BlockPos workPos, @Nullable final Item focusItem)
    {
        if (focusItem == null) return List.of();
        final BlockState workState = level.getBlockState(workPos);
        final PetRoles role;
        final int radius;
        final int minY;
        final int maxY;
        if (workState.getBlock() instanceof BlockFeeder)
        {
            final boolean hanging = workState.hasProperty(BlockFeeder.HANGING) && workState.getValue(BlockFeeder.HANGING);
            role = PetRoles.SCAVENGE_VEGETATION;
            radius = VegetationForageRange.HORIZONTAL_RADIUS;
            minY = VegetationForageRange.MIN_VERTICAL_OFFSET;
            maxY = VegetationForageRange.maxVerticalOffset(hanging);
        }
        else if (workState.getBlock() instanceof BlockDredger)
        {
            role = PetRoles.SCAVENGE_WATER;
            radius = DredgerForageRange.HORIZONTAL_RADIUS;
            minY = DredgerForageRange.MIN_VERTICAL_OFFSET;
            maxY = DredgerForageRange.MAX_VERTICAL_OFFSET;
        }
        else
        {
            return List.of();
        }

        final Set<Block> sources = new HashSet<>();
        PetForagingJeiCache.getEntries().stream()
            .filter(entry -> entry.role() == role)
            .filter(entry -> entry.outputs().stream().anyMatch(output -> output.getItem() == focusItem))
            .forEach(entry -> sources.add(BuiltInRegistries.BLOCK.get(entry.sourceBlock())));
        if (sources.isEmpty()) return List.of();

        final List<BlockPos> matches = new ArrayList<>();
        for (BlockPos candidate : BlockPos.betweenClosed(
            workPos.offset(-radius, minY, -radius), workPos.offset(radius, maxY, radius)))
        {
            if (candidate == null) continue;

            if (!level.isLoaded(candidate)) continue;
            final BlockState state = level.getBlockState(candidate);
            if (!sources.contains(state.getBlock())) continue;
            final boolean harvestable = role == PetRoles.SCAVENGE_VEGETATION
                ? ScavengeHarvestability.isVegetationHarvestable(state)
                : ScavengeHarvestability.isDredgerHarvestable(state);
            if (!harvestable) continue;
            matches.add(candidate.immutable());
            if (matches.size() >= MAX_TARGET_HIGHLIGHTS) break;
        }
        return List.copyOf(matches);
    }

    /**
     * Draws the selected forage volume after block entities have rendered.
     * This stage retains the solid-world depth buffer and avoids the unreliable
     * Fabulous transparency target used while translucent blocks are rendered.
     * <p>
     * Rendering is skipped when the selection belongs to another dimension or
     * its chunk is not loaded. If the selected block is no longer a feeder or
     * dredger, the stale selection is cleared automatically.
     * </p>
     *
     * @param event level-render stage event providing the camera and pose stack
     */
    @SuppressWarnings("null")
    @SubscribeEvent
    public static void render(RenderLevelStageEvent event)
    {
        BlockPos localOrigin = origin;

        if (localOrigin == null) return;

        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || origin == null) return;
        final Minecraft minecraft = Minecraft.getInstance();
        final Level level = minecraft.level;
        if (level == null || !level.dimension().equals(dimension) || !level.isLoaded(localOrigin)) return;

        final BlockState state = level.getBlockState(localOrigin);
        final int radius;
        final int minY;
        final int maxY;
        if (state.getBlock() instanceof BlockFeeder)
        {
            final boolean hanging = state.hasProperty(BlockFeeder.HANGING) && state.getValue(BlockFeeder.HANGING);
            radius = VegetationForageRange.HORIZONTAL_RADIUS;
            minY = VegetationForageRange.MIN_VERTICAL_OFFSET;
            maxY = VegetationForageRange.maxVerticalOffset(hanging);
        }
        else if (state.getBlock() instanceof BlockDredger)
        {
            radius = DredgerForageRange.HORIZONTAL_RADIUS;
            minY = DredgerForageRange.MIN_VERTICAL_OFFSET;
            maxY = DredgerForageRange.MAX_VERTICAL_OFFSET;
        }
        else
        {
            clear();
            return;
        }
        final AABB area = new AABB(
            localOrigin.getX() - radius, localOrigin.getY() + minY, localOrigin.getZ() - radius,
            localOrigin.getX() + radius + 1, localOrigin.getY() + maxY + 1, localOrigin.getZ() + radius + 1);
        final Vec3 camera = event.getCamera().getPosition();
        final PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        final var buffers = minecraft.renderBuffers().bufferSource();

        // Complementary depth tests split the boundary into obscured and
        // directly visible fragments without relying on draw-order overlap.
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(OBSCURED_LINES), area,
            0.04F, 0.28F, 0.07F, 0.65F);
        buffers.endBatch(OBSCURED_LINES);
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(VISIBLE_LINES), area,
            0.2F, 1.0F, 0.25F, 0.9F);
        buffers.endBatch(VISIBLE_LINES);

        for (BlockPos target : focusedTargets)
        {
            LevelRenderer.renderLineBox(pose, buffers.getBuffer(OBSCURED_LINES),
                new AABB(target).inflate(0.003D), 0.32F, 0.16F, 0.02F, 0.7F);
        }
        buffers.endBatch(OBSCURED_LINES);
        for (BlockPos target : focusedTargets)
        {
            LevelRenderer.renderLineBox(pose, buffers.getBuffer(VISIBLE_LINES),
                new AABB(target).inflate(0.003D), 1.0F, 0.62F, 0.08F, 0.95F);
        }
        buffers.endBatch(VISIBLE_LINES);
        pose.popPose();
    }
}
