package com.deathfrog.mctradepost.core.colony.buildings.workerbuildings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.api.colony.buildings.jobs.MCTPModJobs;
import com.deathfrog.mctradepost.api.research.MCTPResearchConstants;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IVisitorData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.TavernBuildingModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.eventhooks.citizenEvents.VisitorSpawnedEvent;
import com.minecolonies.core.colony.interactionhandling.RecruitmentInteraction;
import com.minecolonies.core.datalistener.CustomVisitorListener;
import com.minecolonies.core.datalistener.RecruitmentItemsListener;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

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
     * Serialization tag for the stations.
     */
    private final String TAG_STATIONS = "stations";

    /**
     * Structurize position tag for the start of the rail network in this station.
     */
    private final String STATION_START = "station_start";

    /**
     * List of additional stations
     */
    private Map<BlockPos, StationData> stations = new HashMap<>();

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
     * Retrieves the map of stations for this building.
     * The map is keyed by BlockPos, which is the position of the station in the building.
     * The values are the StationData objects associated with the station at that position.
     * @return a reference to the map of stations.
     */
    public Map<BlockPos, StationData> getStations() 
    {
        return stations;
    }

    /**
     * Clears the list of connected stations for this building.
     * This should be called when the building is being destroyed, or when all stations
     * are being cleared from the building for some other reason.
     */
    public void clearConnectedStations() 
    {
        stations.clear();
    }

    /**
     * Adds a station to the list of stations for this building.
     * The station is mapped to its position in the building's station map.
     *
     * @param station The StationData to be added.
     */
    public void addStation(StationData sdata) 
    {
        if (sdata == null) 
        {
            LOGGER.warn("Attempted to add null station to building.");
            return;
        }
        LOGGER.info("Adding station to building: {}", sdata);
        stations.put(sdata.getBuildingPosition(), sdata);
    }


    /**
     * Checks if the given station is contained in the list of stations for this building.
     *
     * @param station The StationData to check.
     * @return true if the station is in the list, false if not.
     */
    public boolean hasStation(StationData station)
    {
        if (station == null)
        {
            return false;
        }

        return stations.containsKey(station.getBuildingPosition());
    }

    /**
     * Checks if there is a station at the given BlockPos in the list of stations for this building.
     *
     * @param pos The BlockPos to check.
     * @return true if a station is at the given position, false if not.
     */
    public boolean hasStationAt(@Nonnull BlockPos pos) 
    {
        return stations.containsKey(pos);
    }

    /**
     * Retrieves the station data at the specified block position.
     *
     * @param pos The block position to retrieve the station data from.
     * @return The StationData object at the given position, or null if no station exists at that position.
     */
    public StationData getStationAt(@Nonnull BlockPos pos)
    {
        return stations.get(pos);
    }


    /**
     * Validates the stations that are connected to this station.
     * 
     * Goes through all the stations in the list and checks if they are still valid.
     * Checks for the following conditions:
     * - The server is not null.
     * - The level is not null.
     * - The colony is not null.
     * - The building is not null and is an instance of BuildingStation.
     * If any of these conditions are not met, the station is removed from the list.
     */
    protected void validateStations()
    {
        for (StationData station : this.stations.values() )
        {
            MinecraftServer server = getColony().getWorld().getServer();

            if (server == null) {
                stations.remove(station.getBuildingPosition());
                BuildingStation.LOGGER.warn("Failed to validate station (no server): {} - removing it.", station);
                continue;
            }

            Level level = server.getLevel(station.getDimension());

            if (level == null) {
                stations.remove(station.getBuildingPosition());
                BuildingStation.LOGGER.warn("Failed to validate station (no level): {} - removing it.", station);
                continue;
            }

            IColony stationColony = IColonyManager.getInstance().getIColony(level, station.getBuildingPosition());

            if (stationColony == null) {
                stations.remove(station.getBuildingPosition());
                BuildingStation.LOGGER.warn("Failed to validate station (no colony): {} - removing it.", station);
                continue;
            }

            IBuilding building = stationColony.getBuildingManager().getBuilding(station.getBuildingPosition());

            if (building == null || !(building instanceof BuildingStation)) {
                stations.remove(station.getBuildingPosition());
                BuildingStation.LOGGER.warn("Failed to validate station (no building): {} - removing it.", station);
                continue;
            }
        }
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

        // Validate Stations
        validateStations();
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

    @Override
    public CompoundTag serializeNBT(final HolderLookup.Provider provider)
    {
        CompoundTag compound = super.serializeNBT(provider);

        final ListTag stationTagList = new ListTag();

        for (Map.Entry<BlockPos, StationData> entry : stations.entrySet())
        {
            BuildingStation.LOGGER.info("Serializing station: {}", entry.getValue());
            stationTagList.add(entry.getValue().toNBT());
        }

        compound.put(TAG_STATIONS, stationTagList);

        return compound;
    }

    /**
     * Deserializes the state of the building from NBT, including the list of stations.
     * @param provider The holder lookup provider for item and block references.
     * @param compound The compound tag containing the serialized state of the building.
     */
    @Override
    public void deserializeNBT(Provider provider, CompoundTag compound)
    {
        super.deserializeNBT(provider, compound);

        ListTag stationTagList = compound.getList(TAG_STATIONS, Tag.TAG_COMPOUND);
        for (int i = 0; i < stationTagList.size(); ++i)
        {
            CompoundTag stationTag = stationTagList.getCompound(i);
            StationData contents = StationData.fromNBT(stationTag);
            if (contents != null)
            {
                MCTradePostMod.LOGGER.warn("Deserialized station {} from tag: {}", contents, stationTag);
                addStation(contents);
            }
            else
            {
                MCTradePostMod.LOGGER.warn("Failed to deserialize station from tag: {}", stationTag);
            }
        }

    }

    /**
     * Serializes the current state of the building, including the list of stations, to the given buffer.
     * The state of the stations is stored under the key TAG_STATIONS in the serialized CompoundTag.
     *
     * @param buf      The buffer to serialize the state of the building into.
     * @param fullSync Whether or not to serialize the full state of the building, or just the delta.
     */
    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf, final boolean fullSync) 
    {
        super.serializeToView(buf, fullSync);
        buf.writeInt(stations.size());
        for (final Entry<BlockPos, StationData> station : stations.entrySet())
        {
            buf.writeNbt(station.getValue().toNBT());
        }
    }

    /**
     * Retrieves the BlockPos of the starting point of the rail network for this train station.
     * If there is no starting point specified, the position of the building itself is returned.
     * If there are multiple starting points specified in the building's NBT, the first one is used.
     * A warning will be logged if there are multiple starting points found.
     * @return The BlockPos of the starting point of the rail network for this train station.
     */
    public BlockPos getRailStartPosition()
    {
        List<BlockPos> locations = getLocationsFromTag(STATION_START);
        if (locations.isEmpty())
        {
            return this.getPosition();
        }
        else if (locations.size() > 1)
        {
            MCTradePostMod.LOGGER.warn("More than one station start location found, using the first one.");
        }
        return locations.get(0);
    }
}
