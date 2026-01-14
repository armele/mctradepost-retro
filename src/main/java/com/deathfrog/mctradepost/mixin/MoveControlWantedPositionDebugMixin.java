package com.deathfrog.mctradepost.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logs whenever MoveControl#setWantedPosition receives non-finite coordinates or speed,
 * which is a very common NaN-injection site (normalize(0), divide-by-distance, etc.).
 *
 * This is especially useful for diagnosing:
 *  - Door/gate approach goals (OpenGateOrDoorGoal)
 *  - Custom steering / AI roles that compute approach vectors
 *  - Navigation implementations that sometimes feed NaNs to MoveControl
 *
 * Note: This logs to the normal log file via LogUtils/SLF4J.
 */
@Mixin(MoveControl.class)
public abstract class MoveControlWantedPositionDebugMixin
{
    @Unique private static final Logger LOG = LogUtils.getLogger();

    // Per-entity cooldown to avoid spamming if something is repeatedly injecting NaNs.
    @Unique private static final long COOLDOWN_MS = 3_000L;
    @Unique private static final ConcurrentHashMap<UUID, Long> LAST_LOG = new ConcurrentHashMap<>();

    @Inject(method = "setWantedPosition(DDDD)V", at = @At("HEAD"))
    private void mctp$logNonFiniteWantedPosition(double x, double y, double z, double speed, CallbackInfo ci)
    {
        if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Double.isFinite(speed))
        {
            return;
        }

        final MoveControl self = (MoveControl) (Object) this;

        // In vanilla, MoveControl has a protected final Mob field named "mob".
        // We'll fetch it reflectively to avoid mapping name brittleness in your dev env.
        final Mob mob = tryGetMob(self);

        // If we can't identify the owner, still log.
        final UUID uuid = mob != null ? mob.getUUID() : new UUID(0L, 0L);
        if (mob != null && !shouldLog(uuid))
        {
            return;
        }

        String navSummary = "n/a";
        if (mob != null)
        {
            navSummary = buildNavSummary(mob);
        }

        final String entitySummary;
        if (mob != null)
        {
            entitySummary = "type=" + mob.getType()
                + " id=" + mob.getId()
                + " uuid=" + mob.getUUID()
                + " name='" + safeName(mob) + "'"
                + " mobClass=" + mob.getClass().getName();
        }
        else
        {
            entitySummary = "mob=null moveControlClass=" + self.getClass().getName();
        }

        final Vec3 dm = mob != null ? mob.getDeltaMovement() : null;

        LOG.error(
            "### NON-FINITE MoveControl#setWantedPosition ### {} " +
                "wanted=[{}, {}, {}] speed={} " +
                "pos=[{}] delta=[{}] yRot={} xRot={} onGround={} inWater={} {}",
            entitySummary,
            fmt(x), fmt(y), fmt(z), fmt(speed),
            (mob == null ? "n/a" : (fmt(mob.getX()) + ", " + fmt(mob.getY()) + ", " + fmt(mob.getZ()))),
            (dm == null ? "n/a" : (fmt(dm.x) + ", " + fmt(dm.y) + ", " + fmt(dm.z))),
            (mob == null ? "n/a" : fmtF(mob.getYRot())),
            (mob == null ? "n/a" : fmtF(mob.getXRot())),
            (mob == null ? "n/a" : String.valueOf(mob.onGround())),
            (mob == null ? "n/a" : String.valueOf(mob.isInWater())),
            navSummary,
            new RuntimeException("Non-finite setWantedPosition stack trace")
        );
    }

    /* ---------------- helpers ---------------- */

    @Unique
    private static boolean shouldLog(final UUID uuid)
    {
        final long now = System.currentTimeMillis();
        final Long last = LAST_LOG.get(uuid);
        if (last != null && (now - last) < COOLDOWN_MS)
        {
            return false;
        }
        LAST_LOG.put(uuid, now);
        return true;
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
    private static String fmt(final double d)
    {
        return Double.isFinite(d) ? String.valueOf(d) : ("!!!" + d + "!!!");
    }

    @Unique
    private static String fmtF(final float f)
    {
        return Float.isFinite(f) ? String.valueOf(f) : ("!!!" + f + "!!!");
    }

    @Unique
    private static Mob tryGetMob(final MoveControl moveControl)
    {
        // Vanilla (Mojang mappings) uses "mob" as the field name. If it changes, add more fallbacks.
        try
        {
            final var f = MoveControl.class.getDeclaredField("mob");
            f.setAccessible(true);
            final Object o = f.get(moveControl);
            return (o instanceof Mob m) ? m : null;
        }
        catch (Throwable ignored)
        {
            // If this fails in your mappings, you can replace this with a @Shadow field instead.
        }
        return null;
    }

    @Unique
    private static String buildNavSummary(final Mob mob)
    {
        try
        {
            final PathNavigation nav = mob.getNavigation();
            final MoveControl moveControl = mob.getMoveControl();
            if (nav == null)
            {
                return "nav=null";
            }

            String path = "null";
            final Path navPath = nav.getPath();
            if (navPath != null)
            {
                path = "nodeCount=" + navPath.getNodeCount()
                    + " next=" + navPath.getNextNodeIndex()
                    + " done=" +navPath.isDone();
            }

            return "navClass=" + nav.getClass().getName()
                + " navDone=" + nav.isDone()
                + " navSpeedMod=" + fmt(moveControl.getSpeedModifier())
                + " path=" + path;
        }
        catch (Throwable t)
        {
            return "navERR(" + t.getClass().getSimpleName() + ":" + t.getMessage() + ")";
        }
    }
}
