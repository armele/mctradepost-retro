package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.client.gui.modules.WindowCitizenIncentives;
import com.deathfrog.mctradepost.core.colony.buildings.modules.IncentivePlan;
import com.deathfrog.mctradepost.core.colony.buildings.modules.CitizenIncentiveModule;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.minecolonies.api.entity.citizen.Skill;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Client snapshot of Town Hall payroll, payment priority, and citizen skill levels. */
public class CitizenIncentiveModuleView extends AbstractBuildingModuleView
{
    private final Map<Integer, IncentivePlan> plans = new LinkedHashMap<>();
    private final Map<Integer, CitizenSkills> citizens = new LinkedHashMap<>();
    private long revision;
    private int price;
    private int balance;

    /** @return saved plans in payment order, exposed through an unmodifiable map */
    public Map<Integer, IncentivePlan> plans()
    {
        return java.util.Collections.unmodifiableMap(plans);
    }

    /** @return synchronized citizen identities and stored skill levels */
    public Map<Integer, CitizenSkills> citizens()
    {
        return java.util.Collections.unmodifiableMap(citizens);
    }

    /** @return server revision used when submitting edits */
    public long revision()
    {
        return revision;
    }

    /** @return synchronized economic units per boosted skill point */
    public int price()
    {
        return price;
    }

    /** @return colony treasury balance at the last module synchronization */
    public int balance()
    {
        return balance;
    }

    /**
     * Gets a synchronized citizen's skill without its paid incentive adjustment.
     * 
     * @param id    colony-local ID present in this snapshot
     * @param skill skill to inspect
     * @return normal skill level, with a minimum of one
     */
    public int normalLevel(final int id, final Skill skill)
    {
        final IncentivePlan plan = plans.get(id);
        return IncentivePlan.normalLevel(citizens.get(id).level(skill), plan == null ? 0 : plan.applied(skill));
    }

    /** {@inheritDoc} */
    @Override
    public void deserialize(final RegistryFriendlyByteBuf buf)
    {
        revision = buf.readLong();
        price = buf.readInt();
        balance = buf.readInt();
        plans.clear();
        final CompoundTag tag = buf.readNbt();
        if (tag != null)
        {
            final var list = tag.getList(CitizenIncentiveModule.TAG_INCENTIVES, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++)
            {
                final CompoundTag entry = list.getCompound(i);
                plans.put(entry.getInt(CitizenIncentiveModule.TAG_CITIZEN_ID), IncentivePlan.read(entry));
            }
        }
        citizens.clear();
        final int count = buf.readInt();
        for (int i = 0; i < count; i++)
        {
            final int id = buf.readInt();
            final UUID uuid = buf.readUUID();
            final int[] levels = new int[Skill.values().length];
            for (int s = 0; s < levels.length; s++) levels[s] = buf.readInt();
            citizens.put(id, new CitizenSkills(uuid, levels));
        }
    }

    /** {@inheritDoc} */
    @Override
    public ResourceLocation getIconResourceLocation()
    {
        return ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/gui/modules/econ.png");
    }

    /** {@inheritDoc} */
    @Override
    public Component getDesc()
    {
        return Component.translatable("mctradepost.incentives.title");
    }

    /** {@inheritDoc} */
    @Override
    public BOWindow getWindow()
    {
        return new WindowCitizenIncentives(this);
    }

    /**
     * Skill snapshot synchronized with the adjustment ledger.
     * 
     * @param uuid   persistent citizen identity
     * @param levels stored levels indexed by core skill ordinal
     */
    public record CitizenSkills(UUID uuid, int[] levels)
    {
        /**
         * Looks up a stored level in this snapshot.
         * 
         * @param skill skill to inspect
         * @return stored level including any active incentive
         */
        public int level(final Skill skill)
        {
            return levels[skill.ordinal()];
        }
    }
}
