package com.deathfrog.mctradepost.core.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;

import java.util.ArrayList;
import java.util.List;

/*
 * Copied from com.minecolonies.core.commands.CommandTree, as the constructors 
 * there are not public.;
 */
public class CommandTree
{
    /**
     * Tree root node
     */
    private final LiteralArgumentBuilder<CommandSourceStack> rootNode;
    /**
     * List of child trees, commands are directly baked into rootNode
     */
    private final List<CommandTree>                     childNodes;

    /**
     * Creates new command tree.
     *
     * @param commandName root vertex name
     */
    public CommandTree(final String commandName)
    {
        rootNode = LiteralArgumentBuilder.literal(commandName);
        childNodes = new ArrayList<>();
    }

    /**
     * Adds new tree as leaf into this tree.
     *
     * @param tree new tree to add
     * @return this
     */
    public CommandTree addNode(final CommandTree tree)
    {
        childNodes.add(tree);
        return this;
    }

    /**
     * Adds new command as leaf into this tree.
     *
     * @param command new commnad to add
     * @return this
     */
    public CommandTree addNode(final LiteralArgumentBuilder<CommandSourceStack> command)
    {
        rootNode.then(command.build());
        return this;
    }

    /**
     * Builds whole tree for dispatcher.
     *
     * @return tree as command node
     */
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        for (final CommandTree ct : childNodes)
        {
            addNode(ct.build());
        }
        return rootNode;
    }
}
