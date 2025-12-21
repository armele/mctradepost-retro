package com.deathfrog.mctradepost.core.client.model;

import com.minecolonies.api.client.render.modeltype.CitizenModel;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.minecraft.client.model.geom.ModelPart;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

@OnlyIn(Dist.CLIENT)
public class MaleShopkeeperModel extends CitizenModel<AbstractEntityCitizen>
{
  public MaleShopkeeperModel(final ModelPart part)
  {
    super(part);
    hat.visible = false;
  }
}
