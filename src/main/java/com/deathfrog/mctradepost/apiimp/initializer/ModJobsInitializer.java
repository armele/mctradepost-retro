package com.deathfrog.mctradepost.apiimp.initializer;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.jobs.MCTPModJobs;
import com.deathfrog.mctradepost.core.colony.jobs.JobShopkeeper;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.jobs.views.CrafterJobView;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public final class ModJobsInitializer
{
    public final static DeferredRegister<JobEntry> DEFERRED_REGISTER = DeferredRegister.create(CommonMinecoloniesAPIImpl.JOBS, MCTradePostMod.MODID);

    private ModJobsInitializer()
    {
        throw new IllegalStateException("Tried to initialize: ModJobsInitializer but this is a Utility class.");
    }
    
    static
    {
        MCTPModJobs.shopkeeper = register(DEFERRED_REGISTER, MCTPModJobs.SHOPKEEPER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobShopkeeper::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(MCTPModJobs.SHOPKEEPER_ID)
          .createJobEntry());
    }

    /**
     * Register a job at the deferred registry and store the job token in the job list.
     * @param deferredRegister the registry,
     * @param path the path.
     * @param supplier the supplier of the entry.
     * @return the registry object.
     */
    private static DeferredHolder<JobEntry, JobEntry> register(final DeferredRegister<JobEntry> deferredRegister, final String path, final Supplier<JobEntry> supplier)
    {
        return deferredRegister.register(path, supplier);
    }    
}