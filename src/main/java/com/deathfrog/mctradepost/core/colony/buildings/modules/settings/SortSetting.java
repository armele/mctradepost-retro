package com.deathfrog.mctradepost.core.colony.buildings.modules.settings;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingsModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.colony.buildings.modules.settings.BoolSetting;
import com.minecolonies.core.network.messages.server.colony.building.warehouse.SortBuildingMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class SortSetting extends BoolSetting
{
    private static final String BUILDING_SORTED = "com.minecolonies.coremod.setting.mctradepost:buildingsort";
    protected IBuildingView buildingView = null;

    public SortSetting()
    {
        super(true);
    }

    @Override
    public void trigger()
    {
        new SortBuildingMessage(this.buildingView).sendToServer();
        MessageUtils.format(BUILDING_SORTED).sendTo(Minecraft.getInstance().player);
    }

    @Override
    public void render(ISettingKey<?> key, Pane pane, ISettingsModuleView settingsModuleView, IBuildingView building, BOWindow window)
    {
        ButtonImage triggerButton = (ButtonImage) pane.findPaneOfTypeByID("trigger", ButtonImage.class);
        triggerButton.setEnabled(this.isActive(settingsModuleView));
        triggerButton.setText(Component.translatable("com.minecolonies.coremod.setting.mctradepost:sort_label"));
        this.setHoverPane(key, triggerButton, settingsModuleView);
    }

    /**
     * Sets up the handler for the given setting on the client side. This method is overridden to store the building view as an
     * instance variable.
     * 
     * @param key                the setting key
     * @param pane               the pane containing the setting
     * @param settingsModuleView the settings module view
     * @param building           the building view
     * @param window             the window containing the setting
     */
    @OnlyIn(Dist.CLIENT)
    @Override
    public void setupHandler(final ISettingKey<?> key,
        final Pane pane,
        final ISettingsModuleView settingsModuleView,
        final IBuildingView building,
        final BOWindow window)
    {
        this.buildingView = building;
        super.setupHandler(key, pane, settingsModuleView, building, window);
    }
}
