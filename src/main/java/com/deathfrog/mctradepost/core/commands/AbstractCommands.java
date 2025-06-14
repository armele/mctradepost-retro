package com.deathfrog.mctradepost.core.commands;

import com.minecolonies.core.commands.commandTypes.IMCColonyOfficerCommand;

public abstract class AbstractCommands implements IMCColonyOfficerCommand {
    protected String name = "";

    protected AbstractCommands() 
    {
        // Do not use.
    }

    public AbstractCommands(String name) {
        this.name = name;
    }


    /**
     * Returns the name of the command.
     * 
     * @return the name of the command.
     */
    @Override
    public String getName()
    {
        return name;
    }
}
