package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.client.gui.modules.WindowGuestListModule;
import com.deathfrog.mctradepost.core.entity.ai.workers.minimal.Vacationer;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.minecolonies.api.entity.citizen.Skill;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class ResortGuestListModuleView extends AbstractBuildingModuleView
{
    private final List<Vacationer> guests = new ArrayList<>();

    @Override
    public void deserialize(@NotNull RegistryFriendlyByteBuf buf)
    {
        guests.clear();

        int count = buf.readInt();

        for (int i = 0; i < count; i++)
        {
            int civilianId = buf.readInt();
            Vacationer.VacationState state = Vacationer.VacationState.values()[buf.readInt()];

            Skill skill = null;
            if (buf.readBoolean())
            {
                skill = Skill.valueOf(buf.readUtf());
            }

            int targetLevel = buf.readInt();
            boolean currentlyAtResort = buf.readBoolean();

            Vacationer vacationer = new Vacationer(civilianId);
            vacationer.setState(state);
            vacationer.setBurntSkill(skill);
            vacationer.setTargetLevel(targetLevel);
            vacationer.setCurrentlyAtResort(currentlyAtResort);

            guests.add(vacationer);
        }
    }

    @Override
    public ResourceLocation getIconResourceLocation()
    {
        return ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/gui/modules/guests.png");
    }

    /**
     * Gets the translation key for the description of the module.
     * 
     * @return the translation key.
     */
    @Override
    public Component getDesc()
    {
        return Component.translatable("com.mctradepost.core.gui.modules.guestlist");
    }

    @Override
    public BOWindow getWindow()
    {
        return new WindowGuestListModule(this);
    }
    
    /**
     * Returns a list of all guests currently checked in to this resort.
     * This list is populated when the module is deserialized from the network.
     * The list will contain all guests which are currently checked in to the resort, and will be empty if there are no guests.
     * 
     * @return a list of all guests currently checked in to this resort.
     */
    public List<Vacationer> getGuests()
    {
        return guests;
    }
}
