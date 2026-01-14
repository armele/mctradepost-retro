package com.deathfrog.mctradepost.mixin;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.minecolonies.core.entity.pathfinding.navigation.MovementHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Enhanced NaN/Infinity rotation + movement forensic logger.
 *
 * Goals:
 *  - When vanilla is about to log invalid rotation, record entity identity + movement/nav state into the log file.
 *  - Detect "first NaN" earlier in tick() for position/deltaMovement to pinpoint the origin.
 *
 * Notes:
 *  - Uses Log4j/SLF4J logger (LogUtils) so it goes to your normal log file.
 *  - Rate limits per entity to avoid log spam.
 */
@Mixin(Entity.class)
public abstract class EntityRotationDebugMixin
{
    @Unique private static final Logger MCTP_NAN_LOG = LogUtils.getLogger();

    // Tune these to control spam.
    @Unique private static final long ROT_LOG_COOLDOWN_MS = 5_000L;
    @Unique private static final long FIRSTNAN_LOG_COOLDOWN_MS = 30_000L;

    // Per-entity last-log timestamps (static so we can rate limit without extra attachments).
    // Uses weak-ish approach: UUID->time. Good enough for debugging; remove after.
    @Unique private static final java.util.concurrent.ConcurrentHashMap<UUID, Long> LAST_ROT_LOG = new java.util.concurrent.ConcurrentHashMap<>();
    @Unique private static final java.util.concurrent.ConcurrentHashMap<UUID, Long> LAST_FIRSTNAN_LOG = new java.util.concurrent.ConcurrentHashMap<>();

    static {
        MCTP_NAN_LOG.info("### EntityRotationDebugMixin LOADED ###");
    }

    /* -------------------------------------------------------------------------
     * Redirect vanilla invalid rotation logging
     * ---------------------------------------------------------------------- */

    @Redirect(
        method = "setYRot(F)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/Util;logAndPauseIfInIde(Ljava/lang/String;)V"
        ),
        require = 1
    )
    private void mctp$traceInvalidYaw(String msg)
    {
        final Entity self = (Entity) (Object) this;

        if (mctp$shouldLogRot(self))
        {
            // This is where vanilla has determined yaw is invalid (NaN/Inf).
            mctp$logRotationIncident("yaw", msg, self);
        }

        // Preserve vanilla behavior (still logs its own line, and pauses in IDE)
        Util.logAndPauseIfInIde(msg + "");
    }

    @Redirect(
        method = "setXRot(F)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/Util;logAndPauseIfInIde(Ljava/lang/String;)V"
        ),
        require = 1
    )
    private void mctp$traceInvalidPitch(String msg)
    {
        final Entity self = (Entity) (Object) this;

        if (mctp$shouldLogRot(self))
        {
            // This is where vanilla has determined pitch is invalid (NaN/Inf).
            mctp$logRotationIncident("pitch", msg, self);
        }

        // Preserve vanilla behavior
        Util.logAndPauseIfInIde(msg + "");
    }

    /* -------------------------------------------------------------------------
     * "First NaN" detector (earlier, higher-signal than rotation failure)
     * ---------------------------------------------------------------------- */

    @Inject(method = "tick", at = @At("HEAD"))
    private void mctp$firstNaNDetector(CallbackInfo ci)
    {
        final Entity self = (Entity) (Object) this;

        // Quick checks: position & delta movement are the most common earliest NaN carriers.
        final Vec3 dm = self.getDeltaMovement();

        final boolean badPos =
            !Double.isFinite(self.getX()) || !Double.isFinite(self.getY()) || !Double.isFinite(self.getZ());

        final boolean badDm =
            dm == null
                || !Double.isFinite(dm.x) || !Double.isFinite(dm.y) || !Double.isFinite(dm.z);

        if (!badPos && !badDm)
        {
            return;
        }

        if (!mctp$shouldLogFirstNaN(self))
        {
            return;
        }

        // Log a "first NaN" style incident. Stack trace helps find who last touched movement.
        MCTP_NAN_LOG.error(
            "### FIRST NaN/INF DETECTED ### entity={} id={} uuid={} name='{}' badPos={} badDelta={} " +
            "pos=[{}, {}, {}] delta=[{}] yRot={} xRot={} level={} isClient={} onGround={} inWater={}",
            self.getType(),
            self.getId(),
            self.getUUID(),
            safeName(self),
            badPos,
            badDm,
            fmt(self.getX()), fmt(self.getY()), fmt(self.getZ()),
            (dm == null ? "null" : (fmt(dm.x) + ", " + fmt(dm.y) + ", " + fmt(dm.z))),
            fmtF(self.getYRot()),
            fmtF(self.getXRot()),
            safeLevel(self),
            self.level().isClientSide,
            self.onGround(),
            self.isInWater(),
            new RuntimeException("First NaN origin stack trace")
        );

        // Also include deeper mob/nav state if applicable.
        mctp$logMobState("firstNaN", self);
    }

    /* -------------------------------------------------------------------------
     * Helpers
     * ---------------------------------------------------------------------- */

    @Unique
    private static boolean mctp$shouldLogRot(final Entity e)
    {
        final long now = System.currentTimeMillis();
        final UUID id = e.getUUID();
        final Long last = LAST_ROT_LOG.get(id);
        if (last != null && (now - last) < ROT_LOG_COOLDOWN_MS)
        {
            return false;
        }
        LAST_ROT_LOG.put(id, now);
        return true;
    }

    @Unique
    private static boolean mctp$shouldLogFirstNaN(final Entity e)
    {
        final long now = System.currentTimeMillis();
        final UUID id = e.getUUID();
        final Long last = LAST_FIRSTNAN_LOG.get(id);
        if (last != null && (now - last) < FIRSTNAN_LOG_COOLDOWN_MS)
        {
            return false;
        }
        LAST_FIRSTNAN_LOG.put(id, now);
        return true;
    }

    @Unique
    private static void mctp$logRotationIncident(final String axis, final String vanillaMsg, final Entity self)
    {
        final Vec3 dm = self.getDeltaMovement();

        // Primary forensic line
        MCTP_NAN_LOG.error(
            "### INVALID ROTATION ({}) ### vanillaMsg='{}' entity={} id={} uuid={} name='{}' " +
            "pos=[{}, {}, {}] delta=[{}] yRot={} xRot={} onGround={} inWater={} noGravity={} " +
            "vehicle={} passengers={} level={} isClient={}",
            axis,
            vanillaMsg,
            self.getType(),
            self.getId(),
            self.getUUID(),
            safeName(self),
            fmt(self.getX()), fmt(self.getY()), fmt(self.getZ()),
            (dm == null ? "null" : (fmt(dm.x) + ", " + fmt(dm.y) + ", " + fmt(dm.z))),
            fmtF(self.getYRot()),
            fmtF(self.getXRot()),
            self.onGround(),
            self.isInWater(),
            self.isNoGravity(),
            safeEntityBrief(self.getVehicle()),
            self.getPassengers() == null ? -1 : self.getPassengers().size(),
            safeLevel(self),
            self.level().isClientSide
        );

        // Deeper mob/nav state if applicable.
        mctp$logMobState(axis, self);

        // Stack trace (this tells you who called setYRot / setXRot with an invalid value)
        MCTP_NAN_LOG.error("### Stack trace for invalid {} ###", axis, new RuntimeException("Invalid " + axis + " stack trace"));
    }

    @Unique
    private static void mctp$logMobState(final String tag, final Entity self)
    {
        if (!(self instanceof Mob mob))
        {
            return;
        }

        // Movement speed attribute value (can itself be NaN/Inf if a mod messed up modifiers)
        final double speedAttr = mob.getAttributeValue(NullnessBridge.assumeNonnull(Attributes.MOVEMENT_SPEED));

        // MoveControl + Navigation (MineColonies custom classes matter here)
        final MovementHandler moveControl = (MovementHandler) mob.getMoveControl();
        final PathNavigation nav = mob.getNavigation();
        final LookControl look = mob.getLookControl();

        // Path details (if any)
        String pathSummary = "null";
        try
        {
            if (nav != null && nav.getPath() != null)
            {
                final Path path = nav.getPath();
                pathSummary =
                    "nodeCount=" + (path == null ? "null" : path.getNodeCount()) +
                    " nextNodeIndex=" + (path == null ? "null" : path.getNextNodeIndex()) +
                    " done=" +  (path == null ? "null" : path.isDone());
            }
        }
        catch (Throwable t)
        {
            pathSummary = "ERR(" + t.getClass().getSimpleName() + ":" + t.getMessage() + ")";
        }

        MCTP_NAN_LOG.error(
            "### MOB STATE [{}] ### mobClass={} moveControl={} navClass={} lookControl={} " +
            "speedAttr={} zza={} xxa={} yya={} navDone={} navSpeedModifier={} path={}",
            tag,
            mob.getClass().getName(),
            (moveControl == null ? "null" : moveControl.getClass().getName()),
            (nav == null ? "null" : nav.getClass().getName()),
            (look == null ? "null" : look.getClass().getName()),
            fmt(speedAttr),
            fmtF(mob.zza),
            fmtF(mob.xxa),
            fmtF(mob.yya),
            (nav == null ? "null" : String.valueOf(nav.isDone())),
            (moveControl == null ? "null" : fmt(moveControl.getSpeedModifier())),
            pathSummary
        );

        // If you suspect MineColonies AdvancedPathNavigate NaN injection, this will show navSpeedModifier as NaN.
    }

    @Unique
    private static String safeName(final Entity e)
    {
        try
        {
            return e.getName().getString();
        }
        catch (Throwable t)
        {
            return "<name-error:" + t.getClass().getSimpleName() + ">";
        }
    }

    @Unique
    private static String safeLevel(final Entity e)
    {
        try
        {
            // dimension/location info without hard dependency on server classes
            return String.valueOf(e.level().dimension().location());
        }
        catch (Throwable t)
        {
            return "<level-error:" + t.getClass().getSimpleName() + ">";
        }
    }

    @Unique
    private static String safeEntityBrief(final Entity e)
    {
        if (e == null) return "null";
        try
        {
            return e.getType() + "(id=" + e.getId() + ",uuid=" + e.getUUID() + ")";
        }
        catch (Throwable t)
        {
            return "<entity-error:" + t.getClass().getSimpleName() + ">";
        }
    }

    @Unique
    private static String fmt(final double d)
    {
        return Double.isFinite(d) ? String.valueOf(d) : ("!!!" + d + "!!!");
    }

    @Unique
    private static String fmtF(final float f)
    {
        return Float.isFinite(f) ? String.valueOf(f) : ("!!!" + f + "!!!");
    }
}
