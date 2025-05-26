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

    public static final ResourceLocation SHOPKEEPER_ID = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, SHOPKEEPER_TAG);
    public static final ResourceLocation GUESTSERVICES_ID = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, GUESTSERVICES_TAG);

    public static DeferredHolder<JobEntry, JobEntry> shopkeeper;
    public static DeferredHolder<JobEntry, JobEntry> guestservices;

    private MCTPModJobs()
    {
        throw new IllegalStateException("Tried to initialize: ModJobs but this is a Utility class.");
    }

    public static List<ResourceLocation> getJobs()
    {
        List<ResourceLocation> jobs = new ArrayList<>() { };
        jobs.add(SHOPKEEPER_ID);
        jobs.add(GUESTSERVICES_ID);        
        return jobs;
    }
}

