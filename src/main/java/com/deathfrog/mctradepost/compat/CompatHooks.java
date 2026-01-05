package com.deathfrog.mctradepost.compat;

// com.deathfrog.mctradepost.compat.CompatHooks
public final class CompatHooks
{
    private CompatHooks()
    {
    }

    public static void refreshRitualsJei()
    {
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT)
        {
            return;
        }

        if (!net.neoforged.fml.ModList.get().isLoaded("jei"))
        {
            return;
        }

        try
        {
            Class<?> c = Class.forName("com.deathfrog.mctradepost.compat.jei.JEIMCTPPlugin");
            c.getMethod("refreshRitualRecipes").invoke(null);
        }
        catch (Throwable t)
        {
            com.deathfrog.mctradepost.MCTradePostMod.LOGGER.warn("Failed to refresh JEI rituals", t);
        }
    }
}
