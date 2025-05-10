package com.deathfrog.mctradepost.api.colony.buildings.jobs;


import com.deathfrog.mctradepost.MCTradePostMod;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;
import java.util.ArrayList;
import java.util.List;

public final class ModJobs
{

    public static final ResourceLocation SHOPKEEPER_ID = ResourceLocation.parse(MCTradePostMod.MODID + ":shopkeeper");


    public static DeferredHolder<JobEntry, JobEntry> shopkeeper;


    /**
     * List of all jobs.
     */
    public static List<ResourceLocation> jobs = new ArrayList<>() { };

    private ModJobs()
    {
        throw new IllegalStateException("Tried to initialize: ModJobs but this is a Utility class.");
    }

    public static List<ResourceLocation> getJobs()
    {
        return jobs;
    }
}

