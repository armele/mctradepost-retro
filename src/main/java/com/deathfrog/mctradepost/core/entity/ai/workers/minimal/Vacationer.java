package com.deathfrog.mctradepost.core.entity.ai.workers.minimal;

import java.util.ArrayList;
import java.util.List;

import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingResort;
import com.deathfrog.mctradepost.core.event.burnout.BurnoutRemedyManager;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.citizen.Skill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import java.util.Objects;
import java.util.function.Predicate;

/*
 * Modeled on Disease and it's associate handler, this class allows us to handle burnout without 
 * accidentally sending someone to the hospital instead of the resort...
 */
public class Vacationer 
{
    private final int civilianId;
    private VacationState state;
    public static final String BURNOUT_NAME = "com.mctradepost.resort.burnout";
    protected Skill burntSkill;
    protected int targetLevel = -1;
    protected BuildingResort resort =  null;
    boolean currentlyAtResort = false;

    /*
     * Don't confuse these with AI states... they're just internal markers used to track the progress of the vacation and are not used to generate AI actions directly.
     */
    public enum VacationState 
    {
        BROWSING,       // You wandered over the the resort to take a look, but you don't need a vacation yet.
        RESERVED,       // You need a vacation!  Reservations made.
        CHECKED_IN,     // You have checked in at the resort.
        REQUESTED,      // You have ordered your services.
        PENDING,        // We need something to be provided that isn't available.
        TREATED,        // You have been served.
        CHECKED_OUT     // You have checked out of the resort.
    }

    public Vacationer(int civilianId, Skill burntSkill) 
    {
        this.civilianId = civilianId;
        this.state = VacationState.RESERVED;
        this.burntSkill = burntSkill;
    }

   public Vacationer(int civilianId) 
   {
      this.state = VacationState.BROWSING;
      this.civilianId = civilianId;
   }

   public Vacationer(CompoundTag vacationCompound) 
   {
      this.state = VacationState.BROWSING;
      this.civilianId = vacationCompound.getInt("id");
      this.state = VacationState.values()[vacationCompound.getInt("status")];
      String skillname = vacationCompound.getString("skill");
      this.burntSkill = skillname.length() == 0 ? null : Skill.valueOf(skillname);
      this.targetLevel = vacationCompound.getInt("targetLevel");
      this.currentlyAtResort = vacationCompound.getBoolean("currentlyAtResort");
   }

   public int getCivilianId() 
   {
      return this.civilianId;
   }

   public VacationState getState() 
   {
      return this.state;
   }

   public void setState(VacationState state) 
   {
      this.state = state;
   }

    /**
     * Serializes the state of this Vacationer into the given CompoundTag.
     * 
     * @param compoundNBT the CompoundTag to write the Vacationer's data into.
     */
   public void write(CompoundTag compoundNBT) 
   {
      compoundNBT.putInt("id", this.civilianId);
      compoundNBT.putInt("status", this.state.ordinal());
      compoundNBT.putString("skill", burntSkill == null ? "" : burntSkill.name() + "");
      compoundNBT.putInt("targetLevel", targetLevel);
      compoundNBT.putBoolean("currentlyAtResort", currentlyAtResort);
   }

    public Skill getBurntSkill() 
    {
        return burntSkill;
    }

    public void setBurntSkill(Skill burntSkill)
    {
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
    public List<ItemStorage> getRemedyItems() 
    {
        Skill localSkill = this.burntSkill;

        if (localSkill == null)
        {
            return new ArrayList<ItemStorage>();
        }

        List<ItemStorage> remedyItems = BurnoutRemedyManager.getRemedy(localSkill);

        return remedyItems;
    }

    /**
     * Returns a Component which is the name of the Burnout condition for the
     * given burntSkill. Varies by skill being repaired.
     * 
     * @param burntSkill the skill for which the name is requested.
     * @return a Component which is the name of the Burnout condition.
     */
    public Component name() 
    {
        return Component.literal(burntSkill.name() + "");
    }

    /**
     * Returns a string which is a comma-separated list of the items
     * required to cure the given burntSkill.
     *
     * @param burntSkill the skill for which the cure is requested.
     * @return a string giving the items required to cure the given skill.
     */
    public Component getRemedyString()
    {
        final List<ItemStorage> remedyItems = getRemedyItems();
        final MutableComponent cureString = Component.literal("");
        
        for (int i = 0; i < remedyItems.size(); i++)
        {
            final ItemStorage cureStack = remedyItems.get(i);
            ItemStack itemstack = cureStack.getItemStack().copy();

            if (itemstack.isEmpty())
            {
                continue;
            }

            cureString.append(String.valueOf(itemstack.getCount()) + "").append(" ").append(itemstack.getHoverName() + "");
            if (i != remedyItems.size() - 1)
            {
                cureString.append(" + ");
            }
        }
        return cureString;
    }

    public boolean isCurrentlyAtResort() 
    {
        return currentlyAtResort;
    }

    public void setCurrentlyAtResort(boolean currentlyAtResort) 
    {
        this.currentlyAtResort = currentlyAtResort;
    }
    
    /**
     * Predicate for the different usages to check if inventory contains a cure.
     *
     * @param cure the expected cure item.
     * @return the predicate for checking if the cure exists.
     */
    public static Predicate<ItemStack> hasRemedyItem(final ItemStorage cure) 
    {
        return stack -> isRemedyItem(stack, cure);
    }

    /**
     * Check if the given item is a cure item.
     *
     * @param stack the input stack.
     * @param cure  the cure item.
     * @return true if so.
     */
    public static boolean isRemedyItem(final ItemStack stack, final ItemStorage cure) 
    {
        return Objects.equals(new ItemStorage(stack), cure);
    }

    /**
     * Returns the target level for the burnout condition being repaired.
     * @return the target level for the burnout condition.
     */
    public int getTargetLevel() {
        return targetLevel;
    }

    /**
     * Sets the target level for the burnout condition being repaired.
     * @param targetLevel the target level for the burnout condition.
     */
    public void setTargetLevel(int targetLevel) 
    {
        this.targetLevel = targetLevel;
    }
    
    /**
     * Assigns a resort to this vacationer.
     * 
     * @param resort the resort to be assigned.
     */

    public void setResort(BuildingResort resort) 
    {
        this.resort = resort;
    }

    /**
     * Gets the resort assigned to this vacationer.
     * @return the assigned resort.
     */
    public BuildingResort getResort() 
    {
        return resort;
    }

    /**
     * Resets the state of this vacationer.  This is used to
     * clear out the state when the AI is reset.
     */
    public void reset() {
        this.state = VacationState.CHECKED_OUT;
        this.burntSkill = null;
        this.targetLevel = 0;
        this.currentlyAtResort = false;

        if (resort != null) 
        {
            resort.removeGuestFile(civilianId);
        }

        this.resort = null;
    }

    /**
     * Returns a string representation of the vacationer, including the
     * civilian ID, current state, burnt skill, target level, current
     * resort status, and assigned resort. The string is formatted as
     * "Vacationer {civilianId=int, state=VacationState, burntSkill=Skill,
     * targetLevel=int, currentlyAtResort=boolean, resort=BuildingResort}".
     *
     * @return the string representation of the vacationer.
     */
    @Override
    public String toString() 
    {
        return "Vacationer {" +
            "civilianId=" + civilianId +
            ", state=" + state +
            ", burntSkill=" + burntSkill +
            ", targetLevel=" + targetLevel +
            ", currentlyAtResort=" + currentlyAtResort +
            ", resort=" + resort +
            '}';
    }
}
