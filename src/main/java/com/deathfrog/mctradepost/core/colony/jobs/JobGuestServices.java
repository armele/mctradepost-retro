package com.deathfrog.mctradepost.core.colony.jobs;

import com.deathfrog.mctradepost.core.entity.ai.workers.EntityAIWorkGuestServices;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJob;

public class JobGuestServices extends AbstractJob<EntityAIWorkGuestServices, JobGuestServices> {

    public JobGuestServices(ICitizenData entity) {
        super(entity);
    }

    @Override
    public EntityAIWorkGuestServices generateAI() {
        return new EntityAIWorkGuestServices(this);
    }
    
}
