package com.deathfrog.mctradepost.core.entity.ai.workers.minimal;

import java.util.ArrayList;
import java.util.List;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.citizen.Skill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.Objects;
import java.util.function.Predicate;

/*
 * Modeled on Disease and it's associate handler, this class allows us to handle burnout without 
 * accidentally sending someone to the hospital instead of the resort...
 */
public class Vacationer {
    private final int civilianId;
    private VacationState state;
    public static final String BURNOUT_NAME = "com.mctradepost.resort.burnout";
    protected Skill burntSkill;

    boolean currentlyAtResort = false;

    public enum VacationState {
        BROWSING,       // You wandered over the the resort to take a look, but you don't need a vacation yet.
        RESERVED,       // You need a vacation!  Reservations made.
        CHECKED_IN,     // You have checked in at the resort.
        REQUESTED,      // You have ordered your services.
        TREATED,        // You have been served.
        CHECKED_OUT     // You have checked out of the resort.
    }

    public Vacationer(int civilianId, Skill burntSkill) {
        this.civilianId = civilianId;
        this.state = VacationState.RESERVED;
        this.burntSkill = burntSkill;
    }

   public Vacationer(int civilianId) {
      this.state = VacationState.BROWSING;
      this.civilianId = civilianId;
   }

   public Vacationer(CompoundTag vacationCompound) {
      this.state = VacationState.BROWSING;
      this.civilianId = vacationCompound.getInt("id");
      this.state = VacationState.values()[vacationCompound.getInt("status")];
      String skillname = vacationCompound.getString("skill");
      this.burntSkill = skillname.length() == 0 ? null : Skill.valueOf(skillname);
   }

   public int getCivilianId() {
      return this.civilianId;
   }

   public VacationState getState() {
      return this.state;
   }

   public void setState(VacationState state) {
      this.state = state;
   }

   public void write(CompoundTag compoundNBT) {
      compoundNBT.putInt("id", this.civilianId);
      compoundNBT.putInt("status", this.state.ordinal());
      compoundNBT.putString("skill", burntSkill == null ? "" : burntSkill.name());
   }

    public Skill getBurntSkill() {
        return burntSkill;
    }

    public void setBurntSkill(Skill burntSkill) {
        this.burntSkill = burntSkill;
    }

    /**
     * Returns a list of items which are the "cure" for the given burntSkill.
     * This is a list of items which are required to cure the citizen of the
     * burnout condition associated with the given skill.
     * 
     * @param burntSkill the skill for which the cure is requested.
     * @return a list of items which are the cure for the given skill.
     */
    public List<ItemStorage> getRemedyItems() {
        // TODO: RESORT Establish a "cure" associated with each of the statistics we're going
        // to be remedying, as determined by the skill to be repaired.

        List<ItemStorage> cureItems = new ArrayList<>();
        cureItems.add(new ItemStorage(new ItemStack(MCTradePostMod.MCTP_COIN_ITEM.get(), 1)));

        return cureItems;
    }

    /**
     * Returns a Component which is the name of the Burnout condition for the
     * given burntSkill. Varies by skill being repaired.
     * 
     * @param burntSkill the skill for which the name is requested.
     * @return a Component which is the name of the Burnout condition.
     */
    public Component name() {
        // TODO: Vary by skill being repaired.
        return Component.translatable(BURNOUT_NAME);
    }

    /**
     * Returns a string which is a comma-separated list of the items
     * required to cure the given burntSkill.
     *
     * @param burntSkill the skill for which the cure is requested.
     * @return a string giving the items required to cure the given skill.
     */
    public Component getRemedyString() {
        String cure = "";
        for (ItemStorage item : getRemedyItems()) {
            if (cure.length() > 0) {
                cure = cure + ", ";
            }
            cure = cure + item.getItemStack().getDisplayName();
        }

        return Component.literal(cure);
    }

    public boolean isCurrentlyAtResort() {
        return currentlyAtResort;
    }

    public void setCurrentlyAtResort(boolean currentlyAtResort) {
        this.currentlyAtResort = currentlyAtResort;
    }
    
    /**
     * Predicate for the different usages to check if inventory contains a cure.
     *
     * @param cure the expected cure item.
     * @return the predicate for checking if the cure exists.
     */
    public static Predicate<ItemStack> hasRemedyItem(final ItemStorage cure) {
        return stack -> isRemedyItem(stack, cure);
    }

    /**
     * Check if the given item is a cure item.
     *
     * @param stack the input stack.
     * @param cure  the cure item.
     * @return true if so.
     */
    public static boolean isRemedyItem(final ItemStack stack, final ItemStorage cure) {
        return Objects.equals(new ItemStorage(stack), cure);
    }

}
