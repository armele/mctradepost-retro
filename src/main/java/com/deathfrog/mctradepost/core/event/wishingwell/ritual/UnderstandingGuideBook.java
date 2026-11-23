package com.deathfrog.mctradepost.core.event.wishingwell.ritual;

import java.util.List;

import com.deathfrog.mctradepost.api.util.NullnessBridge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

/** Creates the written guide awarded by the Wish of Understanding ritual. */
public final class UnderstandingGuideBook
{
    public static final String TITLE = "Wish of Understanding";
    public static final String AUTHOR = "Hollis Keensight";
    public static final String WIKI_URL = "https://github.com/armele/mctradepost-retro/wiki/Wishing-Well";

    private UnderstandingGuideBook()
    {
    }

    /**
     * Creates a signed guide book containing a brief introduction and a clickable link to the Wishing Well wiki.
     *
     * @return configured written book
     */
    @SuppressWarnings("null")
    public static ItemStack create()
    {
        MutableComponent introduction = Component.literal("Trade Post extends MineColonies with a colony economy built around Trade "
            + "Coins. Use them for inter-colony trade, wishing-well rituals, resorts, recycling, and specialized building materials.");
        MutableComponent explanation = Component.literal("This Wish of Understanding has revealed the Trade Post guide. Select the "
            + "link below to learn how Wishing Wells and their rituals work.\n\n");
        MutableComponent link = Component.literal("Open the Wishing Well guide")
            .withStyle(style -> style.withColor(ChatFormatting.BLUE)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, WIKI_URL)));

        List<Filterable<Component>> pages = List.of(
            Filterable.passThrough(introduction),
            Filterable.passThrough(explanation.append(link)));
        WrittenBookContent content = new WrittenBookContent(Filterable.passThrough(TITLE), AUTHOR, 0, pages, true);
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(NullnessBridge.assumeNonnull(DataComponents.WRITTEN_BOOK_CONTENT), content);
        return book;
    }
}
