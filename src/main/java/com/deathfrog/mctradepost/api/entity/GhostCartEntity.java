package com.deathfrog.mctradepost.api.entity;

import java.util.List;
import org.slf4j.Logger;
import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.sounds.MCTPModSoundEvents;
import com.google.common.collect.ImmutableList;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickRateConstants;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class GhostCartEntity extends AbstractMinecart
{
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final int PARTICLE_PERIOD = 3;   // every 3 game-ticks
    private static final int SOUND_PERIOD    = 40;  // once every 2 seconds

    private ImmutableList<BlockPos> path;
    private int startIdx     = 0;    // node where current stride begins
    private int targetIdx    = 1;    // node we must reach this colony-tick
    private long strideStartTick;    // gameTime when stride began
    private double strideLength;     // sum of block distances for start..target
    private final int COLONY_T = TickRateConstants.MAX_TICKRATE;

    private static final EntityDataAccessor<ItemStack> TRADE_ITEM =
        SynchedEntityData.defineId(GhostCartEntity.class, EntityDataSerializers.ITEM_STACK);

    public GhostCartEntity(EntityType<? extends GhostCartEntity> type, Level level)
    {
        super(type, level);
        this.noPhysics = true;   // no collision resolution or gravity
        this.setInvulnerable(true);
        this.setSilent(true);
    }

    public void setPath(List<BlockPos> path)
    {
        this.path = ImmutableList.copyOf(path);
    }

    /**
     * Spawns a GhostCartEntity on the given level at the position of the first BlockPos in the path.
     * The entity is set to be invulnerable and silent and is added to the level as a fresh entity.
     * The stride values are initialized so the first tick doesn’t jump.
     * @param level The ServerLevel to spawn the entity in.
     * @param path The list of BlockPos that the entity should follow.
     * @return The spawned GhostCartEntity.
     */
    public static GhostCartEntity spawn(ServerLevel level, List<BlockPos> path) {
        GhostCartEntity e = MCTradePostMod.GHOST_CART.get()
                            .create(level, null, path.get(0),
                                    MobSpawnType.EVENT, false, false);
        if (e == null) 
        { 
            LOGGER.error("Failed to spawn GhostCartEntity");
            return null; 
        }

        e.setPath(path);
        e.setPos(Vec3.atCenterOf(path.get(0)));
        e.setRot(0, 0);

        /* ❷ initialise stride so first tick doesn’t jump */
        e.startIdx        = 0;
        e.targetIdx       = 0;
        e.strideStartTick = level.getGameTime();
        e.strideLength    = 0;

        level.addFreshEntity(e);
        return e;
    }

    /**
     * Sets the segment that the ghost cart should move to. The segment is the index
     * of the node on the path that the ghost cart should move to. If the segment is
     * less than or equal to the current startIdx, the method does nothing. Otherwise,
     * the targetIdx is set to the given segment, the strideStartTick is set to the
     * current game time, and the strideLength is set to the length of the path
     * between the current startIdx and the new targetIdx.
     * @param segment the index of the node on the path that the ghost cart should move to
     */
    public void setSegment(int segment)
    {
        if (segment <= startIdx) return;              // ignore stale commands

        this.startIdx        = targetIdx;
        this.targetIdx       = Math.min(segment, path.size() - 1);
        this.strideStartTick = level().getGameTime();
        this.strideLength    = lengthBetween(startIdx, targetIdx);  // helper below
    }

    @Override
    public void tick() {
        super.tick();


        if (level().isClientSide) return;

        if (path == null || path.isEmpty()) {
            // We no longer have a valid path, so we can safely discard
            discard();
            return;
        }

        if (targetIdx >= path.size() - 1) {  // reached final node
            endingEffects();
            discard();
            return;
        }

        if (startIdx >= targetIdx) return;            // waiting for next stride

        /* 0-1 progress inside this colony-tick stride */
        long dt = level().getGameTime() - strideStartTick;
        double u = Mth.clamp(dt / (double) COLONY_T, 0.0, 1.0);

        /* convert u into “blocks travelled”, then find where that lands */
        double travelled = u * strideLength;
        BlockPos a = path.get(startIdx);
        for (int i = startIdx; i < targetIdx; i++) {
            BlockPos b = path.get(i + 1);
            double seg = Vec3.atCenterOf(a).distanceTo(Vec3.atCenterOf(b));
            if (travelled <= seg) {
                /* we are somewhere inside this edge */
                Vec3 pos = Vec3.atCenterOf(a).lerp(Vec3.atCenterOf(b), travelled / seg);
                setPos(pos);
                Vec3 dir = Vec3.atCenterOf(b).subtract(Vec3.atCenterOf(a)).normalize();
                setYRot((float)(Math.atan2(dir.z, dir.x) * 180 / Math.PI) - 90);

                long gameTime = level().getGameTime();
                if ((gameTime % PARTICLE_PERIOD) == 0) spawnTrailParticle();
                if ((gameTime % SOUND_PERIOD)    == 0) playRollingSound();
                return;
            }
            travelled -= seg;
            a = b;                                     // step to next edge
        }

        /* If we fell through the loop, clamp to final node */
        setPos(Vec3.atCenterOf(path.get(targetIdx)));
        startIdx = targetIdx;                          // ready for next colony-tick
    }

    /**
     * Sum of Euclidean distances for the consecutive edges
     * path[a]-->path[a+1] … path[b-1]-->path[b].
     *
     * @param a inclusive start index  (must be ≥0 and < path.size())
     * @param b inclusive end   index  (must be ≥a and < path.size())
     * @return total distance in blocks along the path segment
     */
    private double lengthBetween(int a, int b) {
        if (a >= b) return 0.0;                // zero or invalid span

        double sum = 0.0;
        for (int i = a; i < b; i++) {
            Vec3 p  = Vec3.atCenterOf(path.get(i));
            Vec3 q  = Vec3.atCenterOf(path.get(i + 1));
            sum += p.distanceTo(q);            // Euclidean length of this rail
        }
        return sum;                            // keep it as double for precision
    }


    /**
     * Spawns a small cloud particle at a position slightly behind and
     * below the ghost cart's position, to give the illusion of it leaving
     * a trail. The particle is spawned on the server and distributed to
     * all players tracking this entity.
     */
    private void spawnTrailParticle() {
        Vec3 behind = position().subtract(getForward().scale(0.9));  // small offset
        ((ServerLevel) level()).sendParticles(
                ParticleTypes.CAMPFIRE_COSY_SMOKE,
                behind.x(), behind.y() + 0.1, behind.z(),
                1,                           // count
                0.1, 0.1, 0.1,               // x,y,z scatter
                0.0);                        // speed
    }


    /**
     * Plays a sound at the ghost cart's position that is audible to all
     * players currently tracking this entity. The sound is a loopable
     * vanilla sound effect that is suitable for a moving minecart.
     */
    private void playRollingSound() {
        level().playSound(
                null,                         // null = all players tracking this entity
                getX(), getY(), getZ(),
                SoundEvents.MINECART_RIDING,  // loopable vanilla clack-clack
                net.minecraft.sounds.SoundSource.NEUTRAL,
                0.3F,                         // volume
                1.0F);                        // pitch
    }


    /**
     * Called when the trade is completed. Plays a sound and spawns some
     * particles to make it look like the ghost cart is disappearing.
     */
    private void endingEffects()
    {
        level().playSound(
                null,                         // null = all players tracking this entity
                getX(), getY(), getZ(),
                MCTPModSoundEvents.CASH_REGISTER,  // loopable vanilla clack-clack
                net.minecraft.sounds.SoundSource.NEUTRAL,
                0.3F,                         // volume
                1.0F);                        // pitch

        ((ServerLevel) level()).sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                getX(), getY(), getZ(),
                4,                           // count
                0.3, 0.3, 0.3,               // x,y,z scatter
                0.0);                        // speed
    }


    /**
     * Determines if the ghost cart entity can be picked up or interacted with by players.
     *
     * @return false, as this entity is not intended to be pickable.
     */
    @Override
    public boolean isPickable()
    {
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

    /* Minecart type just tells the renderer which model to use */
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
    {     // call on server only
        entityData.set(TRADE_ITEM, stack.copyWithCount(1));
    }

    public ItemStack getTradeItem()
    {
        return entityData.get(TRADE_ITEM);
    }

    /**
     * This method is called from the constructor of the entity class to define the data that this entity will sync from the server to
     * the client. The data is used to represent the trade item that the ghost cart is carrying.
     * 
     * @param builder The builder to use to define the data.
     */
    @Override
    protected void defineSynchedData(@Nonnull SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);  
        builder.define(TRADE_ITEM, ItemStack.EMPTY);
    }

}
