package com.deathfrog.mctradepost.core.client.render;

import java.util.List;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.network.TradePathDebugPacket.VisualSegment;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Temporary, player-local visualization of a production trade route. */
@EventBusSubscriber(modid = MCTradePostMod.MODID, value = Dist.CLIENT)
public final class TradePathDebugOverlay
{
    private static final long DURATION_TICKS = 20L * 30L;
    private static List<VisualSegment> segments = List.of();
    private static long expiresAt;

    private TradePathDebugOverlay() { }

    public static void show(List<VisualSegment> newSegments)
    {
        Minecraft minecraft = Minecraft.getInstance();
        segments = List.copyOf(newSegments);
        expiresAt = minecraft.level == null ? DURATION_TICKS : minecraft.level.getGameTime() + DURATION_TICKS;
    }

    public static void clear()
    {
        segments = List.of();
        expiresAt = 0;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || segments.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.getGameTime() >= expiresAt)
        {
            clear();
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        double renderDistance = Math.max(32, minecraft.options.getEffectiveRenderDistance() * 16 + 16);
        double renderDistanceSqr = renderDistance * renderDistance;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        var buffers = minecraft.renderBuffers().bufferSource();
        var lines = buffers.getBuffer(RenderType.lines());

        for (VisualSegment segment : segments)
        {
            if (!minecraft.level.dimension().location().equals(segment.dimension())) continue;
            float[] color = color(segment.type());
            List<BlockPos> path = segment.path();
            for (int index = 0; index < path.size(); index++)
            {
                BlockPos pos = path.get(index);
                if (pos.distToCenterSqr(camera.x, camera.y, camera.z) > renderDistanceSqr) continue;
                LevelRenderer.renderLineBox(pose, lines, new AABB(pos).inflate(0.012D), color[0], color[1], color[2], 0.92F);
                if (index + 1 < path.size()) drawConnection(pose, lines, pos, path.get(index + 1), color);
            }
        }

        BlockPos first = endpoint(true);
        BlockPos last = endpoint(false);
        if (first != null) LevelRenderer.renderLineBox(pose, lines, new AABB(first).inflate(0.045D), 1, 1, 1, 1);
        if (last != null) LevelRenderer.renderLineBox(pose, lines, new AABB(last).inflate(0.045D), 1, 1, 1, 1);
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    private static void drawConnection(PoseStack pose, VertexConsumer lines, BlockPos from, BlockPos to, float[] color)
    {
        float x1 = from.getX() + 0.5F;
        float y1 = from.getY() + 0.5F;
        float z1 = from.getZ() + 0.5F;
        float x2 = to.getX() + 0.5F;
        float y2 = to.getY() + 0.5F;
        float z2 = to.getZ() + 0.5F;
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length == 0) return;
        lines.addVertex(pose.last().pose(), x1, y1, z1).setColor(color[0], color[1], color[2], 1).setNormal(pose.last(), dx / length, dy / length, dz / length);
        lines.addVertex(pose.last().pose(), x2, y2, z2).setColor(color[0], color[1], color[2], 1).setNormal(pose.last(), dx / length, dy / length, dz / length);
    }

    private static BlockPos endpoint(boolean first)
    {
        for (int i = first ? 0 : segments.size() - 1; first ? i < segments.size() : i >= 0; i += first ? 1 : -1)
        {
            List<BlockPos> path = segments.get(i).path();
            if (!path.isEmpty()) return first ? path.getFirst() : path.getLast();
        }
        return null;
    }

    private static float[] color(com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackRoute.SegmentType type)
    {
        return switch (type)
        {
            case RAIL -> new float[] {1.0F, 0.72F, 0.08F};
            case ROAD -> new float[] {0.18F, 0.95F, 0.28F};
            case WATER -> new float[] {0.12F, 0.52F, 1.0F};
            case DOCK -> new float[] {0.05F, 1.0F, 1.0F};
            case INTERCHANGE -> new float[] {0.72F, 0.2F, 1.0F};
            case TRANSFER -> new float[] {1.0F, 0.12F, 0.72F};
        };
    }
}
