package com.deathfrog.mctradepost.api.entity;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.sounds.MCTPModSoundEvents;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.ldtteam.blockui.mod.Log;
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
        TicketType.create("ghost_cart_follow", NullnessBridge.assumeNonnull(Comparator.comparingInt(System::identityHashCode)), 3);

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
        SynchedEntityData.defineId(GhostCartEntity.class, NullnessBridge.assumeNonnull(EntityDataSerializers.ITEM_STACK));

    public GhostCartEntity(EntityType<? extends GhostCartEntity> type, Level level)
    {
        super(type, level);
        this.noPhysics = true;   // no collision resolution or gravity
        this.setInvulnerable(true);
        this.setSilent(true);
        
        logConstructionTrace();
    }


    /**
     * Set the path of the ghost cart. The path is a list of BlockPos that
     * represent the positions of the blocks the ghost cart should follow.
     * The path is treated as a directed path, so the order of the blocks matters.
     * If the path is empty or null, the ghost cart will stop moving and wait
     * for further instructions.
     * 
     * @param path the path to follow
     * @see #setPath(List, boolean)
     */
    public void setPath(List<BlockPos> path)
    {
        setPath(path, false);
    }

    /** Direction-aware setter */
    public void setPath(List<BlockPos> path, boolean reverse)
    {
        if (path == null || path.isEmpty())
        {
            this.path = null;
            return;
        }

        this.reversed = reverse;
        List<BlockPos> effective = reverse ? Lists.reverse(path) : path;
        this.path = ImmutableList.copyOf(NullnessBridge.assumeNonnull(effective));

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


    /**
     * Spawns a GhostCartEntity at the given level and path.
     * This is a convenience wrapper around the full spawn method that
     * sets the "reverse" flag to false.
     * If the path is null or empty, this method will log an error and return null.
     * If the starting position of the path is null, this method will log an error and return null.
     * If the entity cannot be spawned, this method will log an error and return null.
     * @param level the level to spawn the entity at
     * @param path the path to follow
     * @return the spawned entity, or null if spawning failed
     */
    public static GhostCartEntity spawn(@Nonnull ServerLevel level, @Nonnull List<BlockPos> path)
    {
        return spawn(level, path, false);
    }

    /**
     * Spawns a GhostCartEntity at the given level and path.
     * If the path is null or empty, this method will log an error and return null.
     * If the starting position of the path is null, this method will log an error and return null.
     * If the entity cannot be spawned, this method will log an error and return null.
     * 
     * @param level the level to spawn the entity at
     * @param path the path to follow
     * @param reverse whether the path should be followed in reverse order
     * @return the spawned entity, or null if spawning failed
     */
    public static GhostCartEntity spawn(@Nonnull ServerLevel level, @Nonnull List<BlockPos> path, boolean reverse)
    {
        if (path == null || path.isEmpty())
        {
            LOGGER.error("Invalid path for GhostCartEntity - no path or empty path provided.");
            return null;
        }

        BlockPos start = path.get(0);

        if (start == null)
        {
            LOGGER.error("Invalid path for GhostCartEntity - null starting position.");
            return null;
        }
        
        GhostCartEntity e = MCTradePostMod.GHOST_CART.get().create(level, null, start, MobSpawnType.EVENT, false, false);
        if (e == null)
        {
            LOGGER.error("Failed to spawn GhostCartEntity.");
            return null;
        }

        @Nonnull Vec3 centerOfStart = NullnessBridge.assumeNonnull(Vec3.atCenterOf(start));

        e.setPath(path, reverse);
        e.setPos(centerOfStart);
        e.setRot(0, 0);

        TraceUtils.dynamicTrace(TRACE_CART, () -> LOGGER.info("[GhostCart {}] Cart spawn helper called with first path point: {}", e.getId(), start));

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

    /**
     * Ticks the GhostCartEntity, updating its position and velocity based on its currently desired path segment.
     * If the path is null or empty, this method will not do anything until a valid path is set.
     * If the entity has arrived at the end of its path and has no further desires, it will discard itself.
     * If the entity has no active stride (i.e., its start and target indices are equal), it will idle until its desired index is updated.
     * If the entity has an active stride, it will smoothly move along the polyline from its current fractional position toward its target index.
     * Each tick, the entity will walk along the polyline by a fraction of the remaining distance equal to the fraction of COLONY_T ticks that have passed since the stride began.
     * At the end of each stride, the entity will snap to the target node and await further requests.
     * If the entity has already completed its current stride and a newer desired index has been set, it will immediately kick off the next stride with no gap.
     */
    @SuppressWarnings("null")
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

        BlockPos targetPos = path.get(targetIdx);
        Vec3 centerTarget = Vec3.atCenterOf(targetPos);

        if (strideLength <= 1e-9)
        {
            // Degenerate: snap to target and await further requests
            setPos(centerTarget);
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
        setPos(centerTarget);
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
    @SuppressWarnings("null")
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

    /**
     * Spawns a single campfire smoke particle at the position of the cart minus a small offset in the direction of
     * travel. This is used to create a trail of smoke particles when the cart is rolling.
     */
    private void spawnTrailParticle()
    {
        if (level() == null) return;

        Vec3 offset = NullnessBridge.assumeNonnull(getForward().scale(0.9));
        Vec3 behind = position().subtract(offset);
        ((ServerLevel) level())
            .sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.CAMPFIRE_COSY_SMOKE), behind.x(), behind.y() + 0.1, behind.z(), 1, 0.1, 0.1, 0.1, 0.0);
    }

    /**
     * Play a rolling sound effect at the position of the cart.
     * This is used to create the sound of the cart rolling along the path.
     */
    private void playRollingSound()
    {
        level().playSound(null,
            getX(),
            getY(),
            getZ(),
            NullnessBridge.assumeNonnull(SoundEvents.MINECART_RIDING),
            net.minecraft.sounds.SoundSource.NEUTRAL,
            0.3F,
            1.0F);
    }

    /**
     * Called when the ghost cart stops rolling. Plays a cash register sound effect and spawns happy villager particles.
     */
    private void endingEffects()
    {
        level().playSound(null,
            getX(),
            getY(),
            getZ(),
            NullnessBridge.assumeNonnull(MCTPModSoundEvents.CASH_REGISTER),
            net.minecraft.sounds.SoundSource.NEUTRAL,
            0.3F,
            1.0F);

        ((ServerLevel) level()).sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.HAPPY_VILLAGER), getX(), getY(), getZ(), 4, 0.3, 0.3, 0.3, 0.0);
    }

    /**
     * Keep the chunk that this ghost cart is currently in loaded.
     * This is done by adding a region ticket to the chunk source of the level.
     * The ticket is removed when the ghost cart moves to a different chunk.
     * This is necessary because the ghost cart is not a normal entity and would not
     * otherwise keep the chunk loaded.
     */
    private void keepChunkLoaded()
    {
        if (level() == null) return;

        BlockPos pos = blockPosition();

        if (pos == null) return;

        ChunkPos here = new ChunkPos(pos);
        TicketType<GhostCartEntity> ticket = CART_TICKET;

        if (ticket == null) 
        {
            Log.getLogger().error("Cart ticket is null! This shoudn't happen. Report to devs.");
            return;
        }

        if (!here.equals(lastTicketPos))
        {
            ChunkPos localLastTicketPos = lastTicketPos;

            if (localLastTicketPos != null)
            {
                ((ServerLevel) level()).getChunkSource().removeRegionTicket(ticket, localLastTicketPos, 3, this);
            }
            ((ServerLevel) level()).getChunkSource().addRegionTicket(ticket, here, 3, this); // radius 3 chunks
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

    /**
     * Set the item to be traded with the wishing well.
     * @param stack The item to be traded, copied with a count of 1.
     */
    public void setTradeItem(@Nonnull ItemStack stack)
    {
        ItemStack displayStack = stack.copyWithCount(1);
        entityData.set(NullnessBridge.assumeNonnull(TRADE_ITEM), displayStack == null ? NullnessBridge.assumeNonnull(ItemStack.EMPTY) : displayStack);
    }

    /**
     * Get the item which is currently being traded with the wishing well.
     * @return The item being traded, or an empty ItemStack if no item is being traded.
     */
    public ItemStack getTradeItem()
    {
        return entityData.get(NullnessBridge.assumeNonnull(TRADE_ITEM));
    }

    /**
     * Define the synced data for this entity.
     * <p>
     * In this case, it only defines the trade item which is synced from the server to the client.
     * <p>
     * This is used to update the client on changes to the entity's data.
     * @param builder The builder to define the synced data on.
     */
    @Override
    protected void defineSynchedData(@Nonnull SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(NullnessBridge.assumeNonnull(TRADE_ITEM), NullnessBridge.assumeNonnull(ItemStack.EMPTY));
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

    /**
     * Called when the entity is added to the level.
     * <p>
     * This is responsible for two things:
     * <ul>
     *     <li>On the server side, it fixes the cart's position to the first path node if it was initialized with a bad position.</li>
     *     <li>On the client side, it sets up previous and current positions so that first-frame interpolation is valid.</li>
     * </ul>
     */
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

                BlockPos startPos = path.get(0);

                if (startPos != null)
                {
                    Vec3 startVec = Vec3.atCenterOf(startPos);

                    setPos(NullnessBridge.assumeNonnull(startVec));
                    setRot(0, 0);
                }
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

    /**
     * Writes the spawn data of this entity to the given buffer.
     * <p>
     * This includes the registry name of the carried item (e.g., "minecraft:iron_ingot")
     * and the desired index of the item in the carried item list.
     * @param buf The buffer to write the spawn data to.
     */
    @Override
    public void writeSpawnData(@Nonnull RegistryFriendlyByteBuf buf)
    {
        final ItemStack trade = getTradeItem();
        final Item item = (trade == null || trade.isEmpty()) ? null : trade.getItem();

        if (item == null)
        {
            writeNoTradeItem(buf);
            return;
        }

        final ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        buf.writeUtf(NullnessBridge.assumeNonnull(id.toString()));
        buf.writeVarInt(this.desiredIdx);
    }

    /**
     * Writes a "no trade item" to the given buffer.
     * <p>
     * This writes "minecraft:air" as the registry name of the carried item and 0 as the desired index of the item in the carried item list.
     * @param buf The buffer to write the "no trade item" to.
     */
    private static void writeNoTradeItem(@Nonnull RegistryFriendlyByteBuf buf)
    {
        buf.writeUtf("minecraft:air");
        buf.writeVarInt(0);
    }

    /**
     * Reads the spawn data of this entity from the given buffer.
     * <p>
     * This includes the registry name of the carried item (e.g., "minecraft:iron_ingot")
     * and the desired index of the item in the carried item list.
     * @param buf The buffer to read the spawn data from.
     */
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
                this.setTradeItem(NullnessBridge.assumeNonnull(ItemStack.EMPTY));
            }
        }
        else
        {
            this.setTradeItem(NullnessBridge.assumeNonnull(ItemStack.EMPTY));
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
