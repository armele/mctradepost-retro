package com.deathfrog.mctradepost.core.colony.requestsystem.resolvers;

import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.requestsystem.factory.IFactoryController;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolverFactory;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.util.constant.SerializationIdentifierConstants;
import com.minecolonies.api.util.constant.TypeConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public class OutpostRequestResolverFactory implements IRequestResolverFactory<OutpostRequestResolver>
{
    public static final int OUTPOST_REQUEST_RESOLVER_ID = -SerializationIdentifierConstants.DELIVERY_REQUEST_RESOLVER_ID;

    ////// --------------------------- NBTConstants --------------------------- \\\\\\
    private static final String NBT_TOKEN    = "Token";
    private static final String NBT_LOCATION = "Location";
    ////// --------------------------- NBTConstants --------------------------- \\\\\\

    @NotNull
    @Override
    public TypeToken<? extends OutpostRequestResolver> getFactoryOutputType()
    {
        return TypeToken.of(OutpostRequestResolver.class);
    }

    @NotNull
    @Override
    public TypeToken<? extends ILocation> getFactoryInputType()
    {
        return TypeConstants.ILOCATION;
    }

    @NotNull
    @Override
    public OutpostRequestResolver getNewInstance(
      @NotNull final IFactoryController factoryController,
      @NotNull final ILocation iLocation,
      @NotNull final Object... context)
      throws IllegalArgumentException
    {
        return new OutpostRequestResolver(iLocation, factoryController.getNewInstance(TypeConstants.ITOKEN));
    }

    @NotNull
    @Override
    public CompoundTag serialize(
      @NotNull final HolderLookup.Provider provider, @NotNull final IFactoryController controller, @NotNull final OutpostRequestResolver deliveryRequestResolver)
    {
        final CompoundTag compound = new CompoundTag();
        compound.put(NBT_TOKEN, controller.serializeTag(provider, deliveryRequestResolver.getId()));
        compound.put(NBT_LOCATION, controller.serializeTag(provider, deliveryRequestResolver.getLocation()));
        return compound;
    }

    @NotNull
    @Override
    public OutpostRequestResolver deserialize(@NotNull final HolderLookup.Provider provider, @NotNull final IFactoryController controller, @NotNull final CompoundTag nbt)
    {
        final IToken<?> token = controller.deserializeTag(provider, nbt.getCompound(NBT_TOKEN));
        final ILocation location = controller.deserializeTag(provider, nbt.getCompound(NBT_LOCATION));

        return new OutpostRequestResolver(location, token);
    }

    @Override
    public void serialize(IFactoryController controller, OutpostRequestResolver input, RegistryFriendlyByteBuf packetBuffer)
    {
        controller.serialize(packetBuffer, input.getId());
        controller.serialize(packetBuffer, input.getLocation());
    }

    @Override
    public OutpostRequestResolver deserialize(IFactoryController controller, RegistryFriendlyByteBuf buffer) throws Throwable
    {
        final IToken<?> token = controller.deserialize(buffer);
        final ILocation location = controller.deserialize(buffer);

        return new OutpostRequestResolver(location, token);
    }

    @Override
    public short getSerializationId()
    {
        return OUTPOST_REQUEST_RESOLVER_ID;
    }

    
}
