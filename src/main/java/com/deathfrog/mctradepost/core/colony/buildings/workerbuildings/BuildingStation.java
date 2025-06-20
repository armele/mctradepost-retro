package com.deathfrog.mctradepost.core.colony.buildings.workerbuildings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.api.colony.buildings.jobs.MCTPModJobs;
import com.deathfrog.mctradepost.api.research.MCTPResearchConstants;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IVisitorData;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.DefaultBuildingInstance;
import com.minecolonies.core.colony.buildings.modules.TavernBuildingModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.eventhooks.citizenEvents.VisitorSpawnedEvent;
import com.minecolonies.core.colony.interactionhandling.RecruitmentInteraction;
import com.minecolonies.core.datalistener.CustomVisitorListener;
import com.minecolonies.core.datalistener.RecruitmentItemsListener;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static com.minecolonies.api.util.constant.Constants.MAX_STORY;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_STATION;

public class BuildingStation extends AbstractBuilding 
{
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * List of additional citizens
     */
    private final List<Integer> externalCitizens = new ArrayList<>();

    /**
     * Delay for spawning more visitors when a spot opens up.
     */
    private int noVisitorTime = 10000;

    public BuildingStation(@NotNull IColony colony, BlockPos pos) 
    {
        super(colony, pos);
    }

    /**
     * Retrieves the schematic name for this building.
     *
     * @return The schematic name as defined in ModBuildings.
     */

    @Override
    public String getSchematicName() 
    {
        return ModBuildings.STATION_ID;
    }

    /**
     * Called every tick that the colony updates.
     * 
     * @param colony the colony that this building is a part of
     */
    @Override
    public void onColonyTick(@NotNull final IColony colony)
    {
        double tourismLevel = this.getColony().getResearchManager().getResearchEffects().getEffectStrength(MCTPResearchConstants.TOURISTS);

        if ((this.getBuildingLevel() > 0) && (externalCitizens.size() < tourismLevel * this.getBuildingLevel()) && isOpenForBusiness() && noVisitorTime <= 0)
        {

            TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Checking for station visitors, with a tourism level of {}.", tourismLevel));


            final IVisitorData visitorData = spawnVisitor();

            if (noVisitorTime > 0)
            {
                noVisitorTime -= 500;
            }

            if (visitorData != null && !CustomVisitorListener.chanceCustomVisitors(visitorData))
            {
                visitorData.triggerInteraction(new RecruitmentInteraction(Component.translatable(
                    "com.minecolonies.coremod.gui.chat.recruitstory" + (this.getColony().getWorld().random.nextInt(MAX_STORY) + 1), visitorData.getName().split(" ")[0]),
                    ChatPriority.IMPORTANT));
            }

            noVisitorTime =
                colony.getWorld().getRandom().nextInt(3000) + (6000 / this.getBuildingLevel()) * colony.getCitizenManager().getCurrentCitizenCount() / colony.getCitizenManager()
                                                                                                                                                    .getMaxCitizens();
        }
    }

    /**
     * Spawns a visitor citizen that can be recruited.
     */
    @Nullable
    public IVisitorData spawnVisitor()
    {
        final int recruitLevel = this.getColony().getWorld().random.nextInt(10 * this.getBuildingLevel()) + 15;
        final RecruitmentItemsListener.RecruitCost cost = RecruitmentItemsListener.getRandomRecruitCost(this.getColony().getWorld().getRandom(), recruitLevel);
        if (cost == null)
        {
            return null;
        }

        final IVisitorData newCitizen = (IVisitorData) this.getColony().getVisitorManager().createAndRegisterCivilianData();
        newCitizen.setBedPos(this.getPosition());
        newCitizen.setHomeBuilding(this);
        newCitizen.getCitizenSkillHandler().init(recruitLevel);
        newCitizen.setRecruitCosts(cost.toItemStack(recruitLevel));

        BlockPos spawnPos = BlockPosUtil.findSpawnPosAround(this.getColony().getWorld(), this.getPosition());
        if (spawnPos == null)
        {
            spawnPos = this.getPosition();
        }

        this.getColony().getVisitorManager().spawnOrCreateCivilian(newCitizen, this.getColony().getWorld(), spawnPos, true);
        if (newCitizen.getEntity().isPresent())
        {
            newCitizen.getEntity().get().setItemSlot(EquipmentSlot.HEAD, getHats(recruitLevel));
        }
        this.getColony().getEventDescriptionManager().addEventDescription(new VisitorSpawnedEvent(spawnPos, newCitizen.getName()));

        externalCitizens.add(newCitizen.getId());
        return newCitizen;
    }

    /**
     * Get the hat for the given recruit level.
     *
     * @param recruitLevel the input recruit level.
     * @return the itemstack for the boots.
     */
    private ItemStack getHats(final int recruitLevel)
    {
        ItemStack hat = ItemStack.EMPTY;
        if (recruitLevel > TavernBuildingModule.LEATHER_SKILL_LEVEL)
        {
            // Leather
            hat = new ItemStack(Items.LEATHER_HELMET);
        }
        if (recruitLevel > TavernBuildingModule.GOLD_SKILL_LEVEL)
        {
            // Gold
            hat = new ItemStack(Items.GOLDEN_HELMET);
        }
        if (recruitLevel > TavernBuildingModule.IRON_SKILL_LEVEL)
        {
            // Iron
            hat = new ItemStack(Items.IRON_HELMET);
        }
        if (recruitLevel > TavernBuildingModule.DIAMOND_SKILL_LEVEL)
        {
            // Diamond
            hat = new ItemStack(Items.DIAMOND_HELMET);
        }
        return hat;
    }

    /**
     * Returns true if the station is open for business, i.e. if it has a
     * stationmaster assigned and they are working.
     * @return true if the station is open for business, false otherwise.
     */
    public boolean isOpenForBusiness() 
    {
        List<ICitizenData> employees = this.getModuleMatching(WorkerBuildingModule.class, m -> m.getJobEntry() == MCTPModJobs.stationmaster.get()).getAssignedCitizen();
        
        if (employees.isEmpty()) {
            return false;
        }
    
        final Optional<AbstractEntityCitizen> optionalEntityCitizen = employees.get(0).getEntity();

        if (!optionalEntityCitizen.isPresent()) {
            return false;
        }
        
        AbstractEntityCitizen stationmaster = optionalEntityCitizen.get();

        IState workState = ((EntityCitizen) stationmaster).getCitizenAI().getState();

        return CitizenAIState.WORKING.equals(workState);
    }
}
