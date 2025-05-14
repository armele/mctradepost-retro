package com.deathfrog.mctradepost.api.colony.buildings.jobs;

import java.util.ArrayList;
import java.util.List;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class MCTPModJobs
{

    public static final ResourceLocation SHOPKEEPER_ID = ResourceLocation.parse(MCTradePostMod.MODID + ":shopkeeper");


    public static DeferredHolder<JobEntry, JobEntry> shopkeeper;


    private MCTPModJobs()
    {
        throw new IllegalStateException("Tried to initialize: ModJobs but this is a Utility class.");
    }

    public static List<ResourceLocation> getJobs()
    {
        List<ResourceLocation> jobs = new ArrayList<>() { };
        jobs.add(SHOPKEEPER_ID);
        
        return jobs;
    }
}

