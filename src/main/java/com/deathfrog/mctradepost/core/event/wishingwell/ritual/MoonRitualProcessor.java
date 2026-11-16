package com.deathfrog.mctradepost.core.event.wishingwell.ritual;

import java.util.List;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.event.wishingwell.WishingWellHandler;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualState.RitualResult;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.SleepAuditService.SleepAuditEntry;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.MessageUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class MoonRitualProcessor
{
    private static final int AUDIT_START_TIME = 15000;
    private static final int DAY_LENGTH = 24000;
    private static final int GLOW_DURATION_TICKS = 20 * 120;

    private MoonRitualProcessor()
    {
    }

    /**
     * Audits the colony for non-military citizens who have not reached sleep, reports them to the colony, and highlights loaded offenders.
     * The ritual is rejected before the late-night grace period has elapsed.
     *
     * @param marketplace marketplace whose colony owns the wishing well
     * @param pos wishing-well position used for the ritual effect
     * @param ritual ritual definition containing the required companion count
     * @param state current wishing-well ritual state
     * @return the ritual result, completed only when the audit runs successfully
     */
    public static RitualResult process(@Nonnull BuildingMarketplace marketplace, @Nonnull BlockPos pos,
        RitualDefinitionHelper ritual, RitualState state)
    {
        IColony colony = marketplace.getColony();
        ServerLevel level = (ServerLevel) colony.getWorld();

        if (state.getCompanionCount() < ritual.companionItemCount())
        {
            return RitualResult.NEEDS_INGREDIENTS;
        }

        long timeOfDay = Math.floorMod(level.getDayTime(), DAY_LENGTH);
        if (timeOfDay < AUDIT_START_TIME)
        {
            MessageUtils.format("ritual.mctradepost.wish_moon.too_early").sendTo(colony).forAllPlayers();
            return RitualResult.FAILED;
        }

        List<SleepAuditEntry> entries = SleepAuditService.audit(colony);
        if (entries.isEmpty())
        {
            MessageUtils.format("ritual.mctradepost.wish_moon.all_asleep").sendTo(colony).forAllPlayers();
        }
        else
        {
            MessageUtils.format("ritual.mctradepost.wish_moon.heading", entries.size()).sendTo(colony).forAllPlayers();
            for (SleepAuditEntry entry : entries)
            {
                reportEntry(colony, entry);
                AbstractEntityCitizen entity = entry.entity();
                if (entity != null)
                {
                    entity.addEffect(new MobEffectInstance(NullnessBridge.assumeNonnull(MobEffects.GLOWING), GLOW_DURATION_TICKS));
                }
            }
        }

        WishingWellHandler.showRitualEffect(level, pos);
        return RitualResult.COMPLETED;
    }

    private static void reportEntry(IColony colony, SleepAuditEntry entry)
    {
        BlockPos position = entry.position();
        String key = switch (entry.status())
        {
            case NO_HOME -> "ritual.mctradepost.wish_moon.no_home";
            case TRAVELLING_HOME -> "ritual.mctradepost.wish_moon.travelling";
            case AT_HOME_AWAKE -> "ritual.mctradepost.wish_moon.at_home";
            case ENTITY_UNLOADED -> "ritual.mctradepost.wish_moon.unloaded";
        };

        if (entry.status() == SleepAuditService.SleepAuditStatus.TRAVELLING_HOME)
        {
            MessageUtils.format(key, entry.citizenName(), entry.jobName(), entry.distanceFromHome(),
                position.getX(), position.getY(), position.getZ()).sendTo(colony).forAllPlayers();
        }
        else
        {
            MessageUtils.format(key, entry.citizenName(), entry.jobName(), position.getX(), position.getY(), position.getZ())
                .sendTo(colony).forAllPlayers();
        }
    }
}
