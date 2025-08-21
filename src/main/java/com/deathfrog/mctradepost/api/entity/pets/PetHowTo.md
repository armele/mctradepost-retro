Adding Pets:
Register the entity type in MCTradePostMod.java (constructor)
Add the entity type to MCTradePostMode.onEntityAttributeCreation
Register the entity renderer in MCTradePostMod.onRegisterRenderers
Add the new pet type to the PetTypes enumeration, and assign it a creation item.
Add the string for the pet in en_us.json (entity.mctradepost.<yournewpet>)
Create a new pet class file at com.deathfrog.mctradepost.api.entity.pets extending the Animal the pet emulates, and implementing ITradePostPet and IHerdingPet
Implement the methods required by those interfaces, modeling after an existing pet.
@Override these methods of the base Animal you're modeling:
- registerGoals: Examine the super class for goals you want to retain, remove the others, and add a null-safe call to petData.assignPetGoals.
- addAdditionalSaveData / readAdditionalSaveData: Persist petData
- tick: implement a tick-time goal registration, which requires resetGoals() 
- remove: Unregister the pet
- createNavigation: use MineColoniesAdavncedPathNavigation
- onClimbable: to let it use ladders
- getInventory and mobInteract: to allow it to have an inventory
Based on the base animal being subclassed, evaluate what environmental adjustments might be required. (Example: making Axolotl immune to freezing damage.)

Adding Pet Goals:
Goal:
(TODO)

Working Location:
Follow the instructions in ItemHowTo.md with these additions:
- Add 3 additional en_us.json lines for the pet working locations per the following examples:
-- "block.mctradepost.trough.shortname" : Used in the assignment window
-- "item.mctradepost.petworkinglocation.blocktrough" : Used in the short JEI explanation
-- "item.mctradepost.petworkinglocation.expanded.blocktrough" : Used in the long JEI explanation
- In PetData.roleFromPosition add the new working location as a determinant for the associated role.
- In AbstractBlockPetWorkingLocation.java add the new working location to getPetWorkBlocks()
