# Creating a New Item
* If the item requires custom functionality, implement it as a new class in com.deathfrog.mctradepost.item.  This class will be what is used for the declaration and registration in the next step. 
** Many items may be able to use "Item" or "ItemFood" or similar base classes if they simply provide a variation on that functionality. In this case, no new class is needed.
* Declare and register it in MCTradePostMod constructor.
* Make it available on the creative tab in MCTradePostModClientModEvents.addCreative (optional)
* Add a translation key to en_us.json of the form "item.mctradepost.<item_registration_key>"
* Add a JSON file at src/main/resources/assets/mctradepost/models/item that points to the graphic that will be used for this item.
** The file name should match the registration keyused in step 4 
** The layer specified here is of the form "<modid>:item/<item_registration_key>".
* Add the .PNG (16x16 or 32x32) to src/main/resources/assets/mctradepost/textures/item/ with a name matching <item_registration_key>.png
* Define the crafting pattern for the item by creating a file named <item_registration_key>.json at /src/main/resources/data/mctradepost/recipe
* Optional: If the recipe needs to be given to or excluded from a specific Minecolonies job recipe list, edit the relevent file at src\main\resources\data\mctradepost\tags\item
* Optional: Specify an item value for the marketplace in item_values.json.  (It will be derived dynamically from the ingredients if not added here.)

* Optional: If the item requires item decorations, connect them in MCTradePostMod.ClientModEvents.onRegisterItemDecorations