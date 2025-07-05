package com.deathfrog.mctradepost.core.blocks;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum ExtendedTimberFrameType implements StringRepresentable {
   DISTRESSED(BlockDistressed.DISTRESSED_ID, "Distressed", false),
   STACKED_SLAB(BlockStackedSlab.STACKED_SLAB_ID, "Stacked Slab", false),
   SIDE_SLAB(BlockSideSlab.SIDE_SLAB_ID, "Side Slab", false),
   SIDE_SLAB_INTERLEAVED(BlockSideSlabInterleaved.SIDE_SLAB_INTERLEAVED_ID, "Side Slab Interleaved", false),
   GLAZED(BlockGlazed.GLAZED_ID, "Glazed", false);;


   private final String name;
   private final String langName;
   private final boolean rotatable;

   private ExtendedTimberFrameType(final String name, final String langName, final boolean rotatable) {
      this.name = name;
      this.langName = langName;
      this.rotatable = rotatable;
   }

   public ExtendedTimberFrameType getPrevious() {
      return this.ordinal() - 1 < 0 ? values()[values().length - 1] : values()[(this.ordinal() - 1) % values().length];
   }

   public String getSerializedName() {
      return this.name;
   }

   public @NotNull String getName() {
      return this.name;
   }

   public String getLangName() {
      return this.langName;
   }

   public boolean isRotatable() {
      return this.rotatable;
   }
}
