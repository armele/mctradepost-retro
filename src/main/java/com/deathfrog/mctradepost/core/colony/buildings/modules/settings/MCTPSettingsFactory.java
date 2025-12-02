package com.deathfrog.mctradepost.core.colony.buildings.modules.settings;

import javax.annotation.Nonnull;
import com.google.common.reflect.TypeToken;
import com.minecolonies.api.util.constant.SerializationIdentifierConstants;
import com.minecolonies.core.colony.buildings.modules.settings.SettingsFactories.AbstractBoolSettingFactory;

public class MCTPSettingsFactory 
{
    public static class SortSettingFactory extends AbstractBoolSettingFactory<SortSetting>
    {
        @Nonnull
        @Override
        public TypeToken<SortSetting> getFactoryOutputType()
        {
            return TypeToken.of(SortSetting.class);
        }

        @Nonnull
        @Override
        public SortSetting getNewInstance(final boolean value, final boolean def)
        {
            return new SortSetting();
        }

        @Override
        public short getSerializationId()
        {
            // Using the negative of the constant to avoid ID conflicts with the existing BooleanSettings factory.
            return -SerializationIdentifierConstants.BOOLEAN_SETTINGS_ID;
        }
    }
}
