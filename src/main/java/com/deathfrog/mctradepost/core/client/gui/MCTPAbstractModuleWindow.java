package com.deathfrog.mctradepost.core.client.gui;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.client.gui.modules.WindowEconModule;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.ButtonImage;
import com.minecolonies.api.colony.buildings.modules.IBuildingModuleView;
import com.minecolonies.api.colony.buildings.modules.IModuleWindow;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;
import java.util.Random;

import static com.minecolonies.api.util.constant.translation.GuiTranslationConstants.LABEL_MAIN_TAB_NAME;

/**
 * Generic module window class. This creates the navigational menu.
 */
// TODO: Remove this upon confirmation of icon effectiveness
public abstract class MCTPAbstractModuleWindow extends AbstractWindowSkeleton implements IModuleWindow
{
    /**
     * Building view matching the module.
     */
    protected final IBuildingView buildingView;

    /**
     * Constructor for the window of the the filterable lists.
     *
     * @param building   {@link AbstractBuildingView}.
     * @param res        the resource String.
     */
    public MCTPAbstractModuleWindow(final IBuildingView building, final String res)
    {
        super(res);
        this.buildingView = building;
        final Random random = new Random(building.getID().hashCode());
        int offset = 0;

        boolean anyVisible = false;

        for (IBuildingModuleView view : building.getAllModuleViews())
        {
            if (view.isPageVisible())
            {
                anyVisible = true;
                break;
            }
        }

        // This class could be greatly simplified (with code duplication reduced) by refactoring AbstractModuleWindow.
        if (building.getAllModuleViews().size() > 0 && anyVisible)
        {
            final ButtonImage image = new ButtonImage();
            image.setImage(ResourceLocation.parse(Constants.MOD_ID + ":textures/gui/modules/tab_side" + (random.nextInt(3) + 1) + ".png"));
            image.setPosition(-20, 10 + offset);
            image.setSize(32, 26);
            image.setHandler(button -> building.getWindow().open());

            final ButtonImage iconImage = new ButtonImage();
            iconImage.setImage(ResourceLocation.parse(Constants.MOD_ID + ":textures/gui/modules/main.png"));
            iconImage.setID("main");
            iconImage.setPosition(-15, 13 + offset);
            iconImage.setSize(20, 20);
            iconImage.setHandler(button -> building.getWindow().open());

            offset += image.getHeight() + 2;

            this.addChild(image);
            this.addChild(iconImage);

            PaneBuilders.tooltipBuilder().hoverPane(iconImage).build().setText(Component.translatableEscape(LABEL_MAIN_TAB_NAME));
        }

        for (IBuildingModuleView view : building.getAllModuleViews())
        {
            if (!view.isPageVisible()) continue;

            final ButtonImage image = new ButtonImage();
            image.setImage(ResourceLocation.parse(Constants.MOD_ID + ":textures/gui/modules/tab_side" + (random.nextInt(3) + 1) + ".png"));
            image.setPosition(-20, 10 + offset);
            image.setSize(32, 26);
            image.setHandler(button -> {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
                view.getWindow().open();
            });

            String namespace = "minecolonies";
            /*if (view instanceof MCTPAbstractBuildingModuleView) {
                namespace = "mctradepost";
            }
                */

            final String icon = view.getIcon();
            final ButtonImage iconImage = new ButtonImage();
            iconImage.setImage(ResourceLocation.parse(namespace + ":textures/gui/modules/" + icon + ".png"));
            iconImage.setSize(20, 20);
            iconImage.setID(icon);
            iconImage.setPosition(-15, 13 + offset);
            iconImage.setHandler(button -> {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
                view.getWindow().open();
            });

            offset += image.getHeight() + 2;

            this.addChild(image);
            this.addChild(iconImage);

            PaneBuilders.tooltipBuilder().hoverPane(iconImage).build().setText(Component.translatableEscape(view.getDesc().toLowerCase(Locale.US)));
        }
    }
}
