package com.deathfrog.mctradepost.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Logs whenever PathNavigation#setSpeedModifier or MineColonies AdvancedPathNavigate setters
 * receive non-finite (NaN/Infinity) values.
 *
 * Drop-in debugging mixins - remove after root cause found.
 */
@Mixin(Entity.class)
public final class SpeedModifierDebugMixins
{
    private SpeedModifierDebugMixins() {}

    /* =========================================================================================
     * Vanilla: PathNavigation#setSpeedModifier(double)
     * ========================================================================================= */

    @Mixin(PathNavigation.class)
    public static abstract class PathNavigationSpeedModifierMixin
    {
        @Unique private static final Logger MCTP_SPEED_LOG = LogUtils.getLogger();

        // Rate limit per navigation instance (UUID not available); use owner entity UUID if possible, else identity hash.
        @Unique private static final long COOLDOWN_MS = 5_000L;
        @Unique private static final ConcurrentHashMap<String, Long> LAST_LOG = new ConcurrentHashMap<>();

        @Inject(method = "setSpeedModifier(D)V", at = @At("HEAD"))
        private void mctp$logNonFiniteSpeedModifier(double speed, CallbackInfo ci)
        {
            if (Double.isFinite(speed))
            {
                return;
            }

            final PathNavigation self = (PathNavigation) (Object) this;

            // Try to identify the navigating entity via known field names (vanilla has a Mob field).
            // We keep this reflection-based so it works across minor mappings without hard-crashing.
            final Entity owner = tryGetOwnerEntity(self);

            final String key = owner != null
                ? ("uuid:" + owner.getUUID())
                : ("nav@" + System.identityHashCode(self));

            if (!shouldLog(key))
            {
                return;
            }

            MCTP_SPEED_LOG.error(
                "### NON-FINITE PathNavigation#setSpeedModifier ### value={} owner={} ownerId={} ownerUuid={} ownerName='{}' navClass={}",
                speed,
                (owner == null ? "null" : String.valueOf(owner.getType())),
                (owner == null ? "null" : owner.getId()),
                (owner == null ? "null" : owner.getUUID()),
                (owner == null ? "null" : safeName(owner)),
                self.getClass().getName(),
                new RuntimeException("Non-finite PathNavigation#setSpeedModifier stack trace")
            );
        }

        @Unique
        private static boolean shouldLog(final String key)
        {
            final long now = System.currentTimeMillis();
            final Long last = LAST_LOG.get(key);
            if (last != null && (now - last) < COOLDOWN_MS)
            {
                return false;
            }
            LAST_LOG.put(key, now);
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

        /**
         * Vanilla PathNavigation stores the mob/owner as a field (commonly named 'mob').
         * We'll try to read it reflectively to avoid depending on the field name in your mappings.
         */
        @Unique
        private static Entity tryGetOwnerEntity(final PathNavigation nav)
        {
            try
            {
                // Common in Mojmap/Yarn: "mob"
                final var f = nav.getClass().getDeclaredField("mob");
                f.setAccessible(true);
                final Object o = f.get(nav);
                return (o instanceof Entity e) ? e : null;
            }
            catch (Throwable ignored)
            {
                // Try superclass as well (some impls store it there)
            }

            try
            {
                final var c = nav.getClass().getSuperclass();
                if (c != null)
                {
                    final var f2 = c.getDeclaredField("mob");
                    f2.setAccessible(true);
                    final Object o2 = f2.get(nav);
                    return (o2 instanceof Entity e2) ? e2 : null;
                }
            }
            catch (Throwable ignored)
            {
            }

            return null;
        }
    }

    /* =========================================================================================
     * MineColonies: AdvancedPathNavigate setters
     *
     * IMPORTANT:
     *  - You must ensure the target class name matches what exists in your environment.
     * ========================================================================================= */

    @Mixin(targets = "com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate")
    public static abstract class MineColoniesAdvancedPathNavigateSpeedSetterMixin
    {
        @Unique private static final Logger MCTP_SPEED_LOG = LogUtils.getLogger();
        @Unique private static final long COOLDOWN_MS = 5_000L;
        @Unique private static final ConcurrentHashMap<String, Long> LAST_LOG = new ConcurrentHashMap<>();

        @Inject(method = "setSpeedModifier(D)V", at = @At("HEAD"), remap = false)
        private void mctp$logNonFiniteMineColoniesSetSpeedModifier(double speedFactor, CallbackInfo ci)
        {
            if (Double.isFinite(speedFactor))
            {
                return;
            }

            final Object self = this;
            final Entity owner = tryGetOwnerEntity(self);
            final String key = owner != null ? ("uuid:" + owner.getUUID()) : ("mcnav@" + System.identityHashCode(self));

            if (!shouldLog(key))
            {
                return;
            }

            MCTP_SPEED_LOG.error(
                "### NON-FINITE MineColonies setSpeedModifier ### value={} owner={} ownerId={} ownerUuid={} ownerName='{}' class={}",
                speedFactor,
                (owner == null ? "null" : String.valueOf(owner.getType())),
                (owner == null ? "null" : owner.getId()),
                (owner == null ? "null" : owner.getUUID()),
                (owner == null ? "null" : safeName(owner)),
                self.getClass().getName(),
                new RuntimeException("Non-finite MineColonies setSpeedModifier stack trace")
            );
        }

        @Inject(method = "setSwimSpeedFactor(D)V", at = @At("HEAD"), remap = false)
        private void mctp$logNonFiniteMineColoniesSetSwimSpeedFactor(double factor, CallbackInfo ci)
        {
            if (Double.isFinite(factor))
            {
                return;
            }

            final Object self = this;
            final Entity owner = tryGetOwnerEntity(self);
            final String key = owner != null ? ("uuid:" + owner.getUUID()) : ("mcnav@" + System.identityHashCode(self));

            if (!shouldLog(key))
            {
                return;
            }

            MCTP_SPEED_LOG.error(
                "### NON-FINITE MineColonies setSwimSpeedFactor ### value={} owner={} ownerId={} ownerUuid={} ownerName='{}' class={}",
                factor,
                (owner == null ? "null" : String.valueOf(owner.getType())),
                (owner == null ? "null" : owner.getId()),
                (owner == null ? "null" : owner.getUUID()),
                (owner == null ? "null" : safeName(owner)),
                self.getClass().getName(),
                new RuntimeException("Non-finite MineColonies setSwimSpeedFactor stack trace")
            );
        }

        /* ---------------- helpers ---------------- */

        @Unique
        private static boolean shouldLog(final String key)
        {
            final long now = System.currentTimeMillis();
            final Long last = LAST_LOG.get(key);
            if (last != null && (now - last) < COOLDOWN_MS)
            {
                return false;
            }
            LAST_LOG.put(key, now);
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

        /**
         * Attempt to fetch the owner entity from the MineColonies navigation class.
         *
         * Your uploaded class uses a field named `ourEntity`, so we try that first.
         * If that fails, fall back to `mob` (vanilla-like).
         */
        @Unique
        private static Entity tryGetOwnerEntity(final Object nav)
        {
            try
            {
                final var f = nav.getClass().getDeclaredField("ourEntity");
                f.setAccessible(true);
                final Object o = f.get(nav);
                return (o instanceof Entity e) ? e : null;
            }
            catch (Throwable ignored)
            {
            }

            try
            {
                final var f = nav.getClass().getDeclaredField("mob");
                f.setAccessible(true);
                final Object o = f.get(nav);
                return (o instanceof Entity e) ? e : null;
            }
            catch (Throwable ignored)
            {
            }

            return null;
        }
    }
}
