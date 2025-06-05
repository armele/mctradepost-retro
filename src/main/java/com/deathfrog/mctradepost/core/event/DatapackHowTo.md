# Defining Data-Driven Logic
See BurnoutRemedyManager.java for an implementation of all steps excep registering with the event bus in one spot.

## Define a "Manager" for the logic
* This will be a class that extends SimpleJsonResourceReloadListener (or other resource listener)
** The call to the .super constructor will identify the folder you're listening to (we'll call this <sourcefolder>)
* Logic for parsing the data file will be in the 'apply' method of this class (and will reference the codec created for the record in subsequent steps)
* Logic for how to access the information defined in your datafiles will be in this manager.

## Register a Listener
* Create a class that has a method with this signature, annotated with @SubscribeEvent: public static void methodName(@NotNull final AddReloadListenerEvent event)
* In the mod constructor, register this class with the Event Bus: NeoForge.EVENT_BUS.register(MyListenerClass.class);
* Add a listener to the event, telling it what the 'Manager' of this logic will be (the class defined above): event.addListener(new MyManager())

## Create a record for the data
* Define a record that will hold the information in your JSON files defined in the datapack
* Define the codec that will be used to serialize the data into this record.

## Create Data Files
* Under <sourcefolder> as passed to SimpleJsonResourceReloadListener's constructor, place your JSON files with the information that will populate the records defined above.