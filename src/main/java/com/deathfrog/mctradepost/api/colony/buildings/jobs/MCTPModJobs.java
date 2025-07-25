package com.deathfrog.mctradepost.api.colony.buildings.jobs;

import java.util.ArrayList;
import java.util.List;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.minecolonies.api.colony.jobs.registry.JobEntry;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class MCTPModJobs
{

    public static final String SHOPKEEPER_TAG = "shopkeeper";
    public static final String GUESTSERVICES_TAG = "guestservices";
    public static final String BARTENDER_TAG = "bartender";
    public static final String RECYCLINGENGINEER_TAG = "recyclingengineer";
    public static final String STATIONMASTER_TAG = "stationmaster";
    public static final String ANIMALTRAINER_TAG = "animaltrainer";

    public static final ResourceLocation SHOPKEEPER_ID = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, SHOPKEEPER_TAG);
    public static final ResourceLocation GUESTSERVICES_ID = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, GUESTSERVICES_TAG);
    public static final ResourceLocation BARTENDER_ID = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, BARTENDER_TAG);
    public static final ResourceLocation RECYCLINGENGINEER_ID = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, RECYCLINGENGINEER_TAG);
    public static final ResourceLocation STATIONMASTER_ID = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, STATIONMASTER_TAG);
    public static final ResourceLocation ANIMALTRAINER_ID = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, ANIMALTRAINER_TAG);

    public static DeferredHolder<JobEntry, JobEntry> shopkeeper;
    public static DeferredHolder<JobEntry, JobEntry> guestservices;
    public static DeferredHolder<JobEntry, JobEntry> bartender;
    public static DeferredHolder<JobEntry, JobEntry> recyclingengineer;
    public static DeferredHolder<JobEntry, JobEntry> stationmaster;
    public static DeferredHolder<JobEntry, JobEntry> animaltrainer;

    private MCTPModJobs()
    {
        throw new IllegalStateException("Tried to initialize: ModJobs but this is a Utility class.");
    }

    public static List<ResourceLocation> getJobs()
    {
        List<ResourceLocation> jobs = new ArrayList<>() { };
        jobs.add(SHOPKEEPER_ID);
        jobs.add(GUESTSERVICES_ID);
        jobs.add(BARTENDER_ID);
        jobs.add(RECYCLINGENGINEER_ID);
        jobs.add(STATIONMASTER_ID);
        jobs.add(ANIMALTRAINER_ID);

        return jobs;
    }
}

