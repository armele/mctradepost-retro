package com.deathfrog.mctradepost.core.colony.buildings.modules;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingResort;
import com.deathfrog.mctradepost.core.entity.ai.workers.minimal.Vacationer;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.entity.citizen.Skill;

import net.minecraft.network.RegistryFriendlyByteBuf;

public class ResortGuestListModule extends AbstractBuildingModule implements IPersistentModule
{
    /**
     * Serializes the trade list to the given RegistryFriendlyByteBuf for
     * transmission to the client. 
     *
     * @param buf the buffer to serialize the trade list to.
     */
    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf)
    {
        if (!(building instanceof BuildingResort resort))
        {
            buf.writeInt(0);
            return;
        }

        buf.writeInt(resort.getGuests().size());

        for (Vacationer vacationer : resort.getGuests())
        {
            buf.writeInt(vacationer.getCivilianId());
            buf.writeInt(vacationer.getState().ordinal());

            Skill skill = vacationer.getBurntSkill();
            buf.writeBoolean(skill != null);
            if (skill != null)
            {
                String name = skill.name();

                if (name == null)
                {
                    name = "Unknown";
                }

                buf.writeUtf(name);
            }

            buf.writeInt(vacationer.getTargetLevel());
            buf.writeBoolean(vacationer.isCurrentlyAtResort());
        }
    }
}
