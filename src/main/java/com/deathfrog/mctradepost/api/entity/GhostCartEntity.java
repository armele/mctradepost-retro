package com.deathfrog.mctradepost.api.entity;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.sounds.MCTPModSoundEvents;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickRateConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_CART;

public class GhostCartEntity extends AbstractMinecart implements IEntityWithComplexSpawn
{
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final int PARTICLE_PERIOD = 3;   // every 3 game-ticks
    private static final int SOUND_PERIOD = 40;  // once every 2 seconds

    private static final TicketType<GhostCartEntity> CART_TICKET =
        TicketType.create("ghost_cart_follow", Comparator.comparingInt(System::identityHashCode), 3);

    private ChunkPos lastTicketPos;
    private boolean spawnPosFixed = false;

    private ImmutableList<BlockPos> path;
    private int startIdx = 0;     // start node index for the *current stride span*
    private int targetIdx = 0;     // target node index for the *current stride span*
    private int desiredIdx = 0;     // latest segment requested by the driver
    private long strideStartTick;       // gameTime when current stride began
    private double strideLength;         // total polyline length for this stride (blocks)
    private final int COLONY_T = TickRateConstants.MAX_TICKRATE; // e.g. 500 ticks
    protected boolean reversed = false;

    // --- NEW: fractional start support (smooth mid-stride retiming) ---
    // We may start a stride partway along the edge startIdx -> startIdx+1.
    // startT is 0..1 measuring how far from startIdx toward startIdx+1.
    private double startT = 0.0;

    // These cache the last computed edge position so setSegment() can rebase mid-stride
    private int lastEdgeA = -1;   // index of edge start we were on last tick
    private int lastEdgeB = -1;   // = lastEdgeA + 1
    private double lastEdgeT = 0.0;  // 0..1 along that edge

    int ticksWithNoPath = 0;
    private static final int MAX_TICKS_WITH_NO_PATH = 100;

    private static final EntityDataAccessor<ItemStack> TRADE_ITEM =
        SynchedEntityData.defineId(GhostCartEntity.class, EntityDataSerializers.ITEM_STACK);

    public GhostCartEntity(EntityType<? extends GhostCartEntity> type, Level level)
    {
        super(type, level);
        this.noPhysics = true;   // no collision resolution or gravity
        this.setInvulnerable(true);
        this.setSilent(true);
        
        logConstructionTrace();
    }

    public void setPath(List<BlockPos> path)
    {
        setPath(path, false);
    }

    /** Direction-aware setter */
    public void setPath(List<BlockPos> path, boolean reverse)
    {
        this.reversed = reverse;
        List<BlockPos> effective = reverse ? Lists.reverse(path) : path;
        this.path = ImmutableList.copyOf(effective);

        // Reset run state
        this.startIdx = 0;
        this.targetIdx = 0;
        this.desiredIdx = 0;
        this.startT = 0.0;
        this.strideStartTick = level().getGameTime();
        this.strideLength = 0.0;
        this.lastEdgeA = -1;
        this.lastEdgeB = -1;
        this.lastEdgeT = 0.0;
    }


    /**
     * Returns true if the cart has a valid path set.
     * This path might not be currently traversable, but it is not null or empty.
     * @return true if the cart has a valid path set, false otherwise
     */

    public boolean hasPath()
    {
        return this.path != null && !this.path.isEmpty();
    }


    public static GhostCartEntity spawn(ServerLevel level, List<BlockPos> path)
    {
        return spawn(level, path, false);
    }

    public static GhostCartEntity spawn(ServerLevel level, List<BlockPos> path, boolean reverse)
    {
        GhostCartEntity e = MCTradePostMod.GHOST_CART.get().create(level, null, path.get(0), MobSpawnType.EVENT, false, false);
        if (e == null)
        {
            LOGGER.error("Failed to spawn GhostCartEntity.");
            return null;
        }

        e.setPath(path, reverse);
        e.setPos(Vec3.atCenterOf(path.get(0)));
        e.setRot(0, 0);

        TraceUtils.dynamicTrace(TRACE_CART, () -> LOGGER.info("[GhostCart {}] Cart spawn helper called with first path point: {}", e.getId(), path.get(0)));

        level.addFreshEntity(e);
        return e;
    }

    /**
     * Request movement toward a segment. The distance from the cart's *current* fractional position to the requested segment will be
     * traversed over exactly one COLONY_T (e.g., 500 ticks) with smooth speed.
     */
    public void setSegment(int segment)
    {
        if (path == null || path.isEmpty()) return;

        int clamped = Mth.clamp(segment, 0, path.size() - 1);
        if (clamped <= desiredIdx && targetIdx >= desiredIdx)
        {
            return; // nothing new to do
        }

        desiredIdx = clamped;

        // --- Rebase the stride to our *current* fractional position ---
        // If we have a valid last-edge cache (from tick), start from there.
        // Otherwise, start from the current integer node without fraction.
        int newStartIdx = startIdx;
        double newStartT = startT;

        if (lastEdgeA >= 0 && lastEdgeB == lastEdgeA + 1)
        {
            newStartIdx = lastEdgeA;
            newStartT = lastEdgeT;
        }

        this.startIdx = newStartIdx;
        this.startT = Mth.clamp(newStartT, 0.0, 1.0);
        this.targetIdx = desiredIdx;
        this.strideStartTick = level().getGameTime();
        this.strideLength = lengthBetweenFractional(startIdx, startT, targetIdx);
    }

    @Override
    public void tick()
    {
        super.tick();

        if (level().isClientSide) return;

        if (path == null || path.isEmpty())
        {
            if (ticksWithNoPath++ > MAX_TICKS_WITH_NO_PATH)
            {   
                discard();
            }
            return;
        }

        ticksWithNoPath = 0;

        // We only finish when we've arrived *and* no further desire remains.
        if (startIdx >= path.size() - 1 && startIdx >= desiredIdx && startT >= 1.0 - 1e-6)
        {
            endingEffects();
            discard();
            return;
        }

        // If there's nothing to do yet
        if (startIdx >= targetIdx && startIdx >= desiredIdx)
        {
            keepChunkLoaded();
            return; // idle until first setSegment
        }

        // Ensure we have an active stride
        if (startIdx >= targetIdx && startIdx < desiredIdx)
        {
            this.targetIdx = desiredIdx;
            this.strideStartTick = level().getGameTime();
            this.strideLength = lengthBetweenFractional(startIdx, startT, targetIdx);
        }

        if (strideLength <= 1e-9)
        {
            // Degenerate: snap to target and await further requests
            setPos(Vec3.atCenterOf(path.get(targetIdx)));
            startIdx = targetIdx;
            startT = 0.0;
            keepChunkLoaded();
            return;
        }

        // --- Smooth pacing: always COLONY_T ticks per stride ---
        long dt = level().getGameTime() - strideStartTick;
        double u = Mth.clamp(dt / (double) COLONY_T, 0.0, 1.0);
        double travelled = u * strideLength;

        // Walk along the polyline from (startIdx + startT) toward targetIdx
        int i = startIdx;
        double t = startT;

        // distance from current fractional point to end of edge i -> i+1
        while (i < targetIdx)
        {
            Vec3 pa = Vec3.atCenterOf(path.get(i));
            Vec3 pb = Vec3.atCenterOf(path.get(i + 1));

            Vec3 edgeStart = pa.lerp(pb, t);
            double edgeLen = edgeStart.distanceTo(pb);

            if (travelled <= edgeLen + 1e-9)
            {
                // We're inside this edge at fraction t' = t + (travelled / fullEdgeLen)*(1 - t)
                double fullEdgeLen = pa.distanceTo(pb);
                double tPrime;
                if (fullEdgeLen <= 1e-9)
                {
                    tPrime = 1.0;
                }
                else
                {
                    // portion along this edge, accounting for starting at t
                    double alongThisEdge = (travelled / fullEdgeLen);
                    tPrime = Mth.clamp(t + alongThisEdge, 0.0, 1.0);
                }

                Vec3 pos = pa.lerp(pb, tPrime);
                setPos(pos);

                Vec3 dir = pb.subtract(pa).normalize();
                setYRot((float) (Math.atan2(dir.z, dir.x) * 180 / Math.PI) - 90);
                setDeltaMovement(dir.scale(strideLength / Math.max(1.0, COLONY_T))); // interpolation hint

                // cache fractional edge for mid-stride rebasing
                lastEdgeA = i;
                lastEdgeB = i + 1;
                lastEdgeT = tPrime;

                keepChunkLoaded();

                long gameTime = level().getGameTime();
                if ((gameTime % PARTICLE_PERIOD) == 0) spawnTrailParticle();
                if ((gameTime % SOUND_PERIOD) == 0) playRollingSound();
                return;
            }

            // Consume this edge remainder and move to the next
            travelled -= edgeLen;
            i += 1;
            t = 0.0;
        }

        // If we fell through, we've completed the stride: snap to target node
        setPos(Vec3.atCenterOf(path.get(targetIdx)));
        startIdx = targetIdx;
        startT = 0.0;

        lastEdgeA = Math.max(0, targetIdx - 1);
        lastEdgeB = targetIdx;
        lastEdgeT = 1.0;

        keepChunkLoaded();

        // If there's already a newer desiredIdx, immediately kick next stride (no gap).
        if (startIdx < desiredIdx)
        {
            this.targetIdx = desiredIdx;
            this.strideStartTick = level().getGameTime();
            this.strideLength = lengthBetweenFractional(startIdx, startT, targetIdx);
        }
    }

    /**
     * Length from a fractional point (a, tA) to node b along the path. a must be <= b, and tA in [0,1]. If a == b, returns 0.
     */
    private double lengthBetweenFractional(int a, double tA, int b)
    {
        if (a >= b) return 0.0;

        double sum = 0.0;

        // partial first edge: from lerp(a->a+1, tA) to (a+1)
        {
            Vec3 pa = Vec3.atCenterOf(path.get(a));
            Vec3 pb = Vec3.atCenterOf(path.get(a + 1));
            Vec3 p0 = pa.lerp(pb, Mth.clamp(tA, 0.0, 1.0));
            sum += p0.distanceTo(pb);
        }

        // full edges in between
        for (int i = a + 1; i < b; i++)
        {
            Vec3 p = Vec3.atCenterOf(path.get(i));
            Vec3 q = Vec3.atCenterOf(path.get(i + 1));
            sum += p.distanceTo(q);
        }

        return sum;
    }

    private void spawnTrailParticle()
    {
        Vec3 behind = position().subtract(getForward().scale(0.9));  // small offset
        ((ServerLevel) level())
            .sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, behind.x(), behind.y() + 0.1, behind.z(), 1, 0.1, 0.1, 0.1, 0.0);
    }

    private void playRollingSound()
    {
        level().playSound(null,
            getX(),
            getY(),
            getZ(),
            SoundEvents.MINECART_RIDING,
            net.minecraft.sounds.SoundSource.NEUTRAL,
            0.3F,
            1.0F);
    }

    private void endingEffects()
    {
        level().playSound(null,
            getX(),
            getY(),
            getZ(),
            MCTPModSoundEvents.CASH_REGISTER,
            net.minecraft.sounds.SoundSource.NEUTRAL,
            0.3F,
            1.0F);

        ((ServerLevel) level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY(), getZ(), 4, 0.3, 0.3, 0.3, 0.0);
    }

    private void keepChunkLoaded()
    {
        ChunkPos here = new ChunkPos(blockPosition());
        if (!here.equals(lastTicketPos))
        {
            if (lastTicketPos != null)
            {
                ((ServerLevel) level()).getChunkSource().removeRegionTicket(CART_TICKET, lastTicketPos, 3, this);
            }
            ((ServerLevel) level()).getChunkSource().addRegionTicket(CART_TICKET, here, 3, this); // radius 3 chunks
            lastTicketPos = here;
        }
    }

    // --- Vanilla/boilerplate below ---

    @Override
    public boolean isPickable()
    {
        return false;
    }

    @Override
    public boolean shouldBeSaved() 
    {
        // Ghost carts are transient and should never be chunk-saved
        return false;
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    public void push(@Nonnull Entity e)
    { /* NO-OP */ }

    @Override
    public boolean canBeCollidedWith()
    {
        return false;
    }

    @Override
    public boolean canAddPassenger(@Nonnull Entity e)
    {
        return false;
    }

    @Override
    public boolean hurt(@Nonnull DamageSource s, float f)
    {
        return false;
    }

    @Override
    public InteractionResult interact(@Nonnull Player p, @Nonnull InteractionHand h)
    {
        return InteractionResult.FAIL;
    }

    @Override
    public Type getMinecartType()
    {
        return Type.RIDEABLE;
    }

    @Override
    protected Item getDropItem()
    {
        return null;
    }

    public void setTradeItem(ItemStack stack)
    {
        entityData.set(TRADE_ITEM, stack.copyWithCount(1));
    }

    public ItemStack getTradeItem()
    {
        return entityData.get(TRADE_ITEM);
    }

    @Override
    protected void defineSynchedData(@Nonnull SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(TRADE_ITEM, ItemStack.EMPTY);
    }

    /*
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(@Nonnull ServerEntity serverEntity)
    {
        int data = this.getMinecartType().ordinal();
        BlockPos pos = this.blockPosition();
        return new ClientboundAddEntityPacket(this, data, pos);
    }
    */

    @Override
    public void startSeenByPlayer(@Nonnull ServerPlayer player)
    {
        super.startSeenByPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(@Nonnull ServerPlayer player)
    {
        super.stopSeenByPlayer(player);
    }

    @Override
    public void recreateFromPacket(@Nonnull ClientboundAddEntityPacket pkt)
    {
        super.recreateFromPacket(pkt);
        if (level().isClientSide)
        {
            // Align previous and current so first-frame interpolation is valid
            this.xo = this.getX();
            this.yo = this.getY();
            this.zo = this.getZ();
            this.xRotO = this.getXRot();
            this.yRotO = this.getYRot();
        }
    }

    @Override
    public void onAddedToLevel()
    {
        super.onAddedToLevel();
        if (!level().isClientSide && !spawnPosFixed)
        {
            // If we somehow came in with a bad Y, snap to the first path node now.
            if (path != null && !path.isEmpty())
            {
                TraceUtils.dynamicTrace(TRACE_CART, () -> LOGGER.info("[GhostCart {}] Cart added to level with first path point: {}", this.getId(), path.get(0)));

                setPos(Vec3.atCenterOf(path.get(0)));
                setRot(0, 0);
            }
            else
            {
                TraceUtils.dynamicTrace(TRACE_CART, () -> LOGGER.info("[GhostCart {}] Cart added to level with no path!", this.getId()));
            }
            spawnPosFixed = true;
        }

        // Client nicety (prevents first-frame interpolation glitches)
        if (level().isClientSide)
        {
            this.xo = getX();
            this.yo = getY();
            this.zo = getZ();
            this.xRotO = getXRot();
            this.yRotO = getYRot();
        }
    }

    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() 
    {
        return super.getBoundingBoxForCulling().inflate(0.25);
    }

    @Override
    public void writeSpawnData(@Nonnull RegistryFriendlyByteBuf buf)
    {
        // Write the registry name of the carried item (e.g., "minecraft:iron_ingot")
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(getTradeItem().getItem());
        buf.writeUtf(id == null ? "" : id.toString());
        buf.writeVarInt(this.desiredIdx);
    }

    @Override
    public void readSpawnData(@Nonnull RegistryFriendlyByteBuf buf)
    {
        String id = buf.readUtf();
        if (!id.isEmpty())
        {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl))
            {
                this.setTradeItem(new ItemStack(BuiltInRegistries.ITEM.get(rl)));
            }
            else
            {
                this.setTradeItem(ItemStack.EMPTY);
            }
        }
        else
        {
            this.setTradeItem(ItemStack.EMPTY);
        }

        this.desiredIdx = buf.readVarInt();
    }

    @Override
    public void remove(@Nonnull RemovalReason reason)
    {
        if (reason == RemovalReason.DISCARDED)
        {
            logDiscardTrace(reason);
        }
        super.remove(reason);
    }

    private void logDiscardTrace(RemovalReason reason)
    {
        String stack = Arrays.stream(Thread.currentThread().getStackTrace())
            .skip(2) // skip current frames
            .map(ste -> "    at " + ste)
            .collect(Collectors.joining("\n"));

        TraceUtils.dynamicTrace(TRACE_CART, () -> LOGGER.warn(
            "[GhostCart {}] DISCARD at dim={} pos=({}, {}, {}) " +
                "reason={} startIdx={} targetIdx={} desiredIdx={} pathSize={} gameTime={}\n{}",
            this.getId(),
            level().dimension().location(),
            String.format("%.2f", getX()),
            String.format("%.2f", getY()),
            String.format("%.2f", getZ()),
            reason,
            startIdx,
            targetIdx,
            desiredIdx,
            (path == null ? -1 : path.size()),
            level().getGameTime(),
            stack));
    }

    private void logConstructionTrace()
    {
        String stack = Arrays.stream(Thread.currentThread().getStackTrace())
            .skip(2) // skip current frames
            .map(ste -> "    at " + ste)
            .collect(Collectors.joining("\n"));

        TraceUtils.dynamicTrace(TRACE_CART, () -> LOGGER.warn(
            "[GhostCart {}] CREATE at dim={} pos=({}, {}, {}) " +
                "startIdx={} targetIdx={} desiredIdx={} pathSize={} gameTime={}\n{}",
            this.getId(),
            level().dimension().location(),
            String.format("%.2f", getX()),
            String.format("%.2f", getY()),
            String.format("%.2f", getZ()),
            startIdx,
            targetIdx,
            desiredIdx,
            (path == null ? -1 : path.size()),
            level().getGameTime(),
            stack));
    }
}
