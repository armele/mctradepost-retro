package com.deathfrog.mctradepost.api.items;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.items.datacomponent.RecyclableRecord;
import com.deathfrog.mctradepost.item.SouvenirItem.SouvenirRecord;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class MCTPModDataComponents {
    public static final DeferredRegister.DataComponents REGISTRAR = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MCTradePostMod.MODID);


    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SouvenirRecord>> SOUVENIR_COMPONENT = REGISTRAR.registerComponentType(
        "souvenir",
        builder -> builder
            // The codec to read/write the data to disk
            .persistent(SouvenirRecord.CODEC)
            // The codec to read/write the data across the network
            .networkSynchronized(SouvenirRecord.STREAM_CODEC)
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RecyclableRecord>> RECYCLABLE_COMPONENT = REGISTRAR.registerComponentType(
        "recyclable",
        builder -> builder
            // The codec to read/write the data to disk
            .persistent(RecyclableRecord.CODEC)
            // The codec to read/write the data across the network
            .networkSynchronized(RecyclableRecord.STREAM_CODEC)
    );

}
