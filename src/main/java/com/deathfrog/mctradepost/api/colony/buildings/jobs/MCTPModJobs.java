package com.deathfrog.mctradepost.api.colony.buildings.jobs;

import java.util.ArrayList;
import java.util.List;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class MCTPModJobs
{

    public static final ResourceLocation SHOPKEEPER_ID = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID,"shopkeeper");
    public static final ResourceLocation GUESTSERVICES_ID = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID,"guestservices");

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

