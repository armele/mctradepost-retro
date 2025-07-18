package com.deathfrog.mctradepost.apiimp.initializer;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.jobs.MCTPModJobs;
import com.deathfrog.mctradepost.core.colony.jobs.JobAnimalTrainer;
import com.deathfrog.mctradepost.core.colony.jobs.JobBartender;
import com.deathfrog.mctradepost.core.colony.jobs.JobGuestServices;
import com.deathfrog.mctradepost.core.colony.jobs.JobRecyclingEngineer;
import com.deathfrog.mctradepost.core.colony.jobs.JobShopkeeper;
import com.deathfrog.mctradepost.core.colony.jobs.JobStationMaster;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.jobs.views.CrafterJobView;
import com.minecolonies.core.colony.jobs.views.DefaultJobView;

import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
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

        MCTPModJobs.guestservices = register(DEFERRED_REGISTER, MCTPModJobs.GUESTSERVICES_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobGuestServices::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(MCTPModJobs.GUESTSERVICES_ID)
          .createJobEntry());

        MCTPModJobs.bartender = register(DEFERRED_REGISTER, MCTPModJobs.BARTENDER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobBartender::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(MCTPModJobs.BARTENDER_ID)
          .createJobEntry());

        MCTPModJobs.recyclingengineer = register(DEFERRED_REGISTER, MCTPModJobs.RECYCLINGENGINEER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobRecyclingEngineer::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(MCTPModJobs.RECYCLINGENGINEER_ID)
          .createJobEntry());

        MCTPModJobs.stationmaster = register(DEFERRED_REGISTER, MCTPModJobs.STATIONMASTER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobStationMaster::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(MCTPModJobs.STATIONMASTER_ID)
          .createJobEntry());

        MCTPModJobs.animaltrainer = register(DEFERRED_REGISTER, MCTPModJobs.ANIMALTRAINER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobAnimalTrainer::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(MCTPModJobs.ANIMALTRAINER_ID)
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
        MCTradePostMod.LOGGER.info("Registering job: " + path);
        return deferredRegister.register(path, supplier);
    }
        
    public static void logRegisteredJobEntries(ServerLevel level) {
        Registry<JobEntry> jobRegistry = level.registryAccess().registryOrThrow(CommonMinecoloniesAPIImpl.JOBS);

        MCTradePostMod.LOGGER.info("=== Registered JobEntry objects in MineColonies ===");
        for (JobEntry entry : jobRegistry) {
            MCTradePostMod.LOGGER.info("JobEntry ID: {}", jobRegistry.getKey(entry));
        }
    }
}