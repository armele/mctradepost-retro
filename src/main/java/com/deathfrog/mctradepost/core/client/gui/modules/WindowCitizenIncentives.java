package com.deathfrog.mctradepost.core.client.gui.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.EconomicValueFormatter;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.CitizenIncentiveModuleView;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.CitizenIncentiveModuleView.CitizenSkills;
import com.deathfrog.mctradepost.core.colony.buildings.modules.CitizenIncentiveMessage;
import com.deathfrog.mctradepost.core.colony.buildings.modules.IncentivePlan;
import com.deathfrog.mctradepost.core.client.gui.TownHallIncentiveTab;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.Image;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.controls.TextField;
import com.ldtteam.blockui.controls.Tooltip;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;

/** Town Hall roster and per-skill next-dawn editor. Drafts never mutate the synchronized view. */
public class WindowCitizenIncentives extends AbstractModuleWindow<CitizenIncentiveModuleView>
{
    private static final String ID_CITIZENS = "citizens";
    private static final String ID_SKILLS = "skills";
    private static final String ID_EDIT = "edit";
    private static final String ID_UP = "up";
    private static final String ID_DOWN = "down";
    private static final String ID_PLUS = "plus";
    private static final String ID_MINUS = "minus";
    private static final String ID_SAVE = "save";
    private static final String ID_STOP = "stop";
    private static final String ID_BACK = "back";
    private static final String ID_NAME = "name";
    private static final String ID_JOB = "job";
    private static final String ID_STATUS = "status";
    private static final String ID_TITLE = "title";
    private static final String ID_SKILL_NAME = "skillName";
    private static final String ID_SKILL_ICON = "skillIcon";
    private static final String SKILL_ICON_PATH = "textures/entity/skills/small/";
    private static final String ID_LEVELS = "levels";
    private static final String ID_BOOST = "boost";
    private static final String ID_COST = "cost";
    private static final String ID_PRICE = "price";
    private static final String ID_EDITOR_HINT = "editorHint";
    private static final String ID_SEARCH = "search";
    private static final String ID_BALANCE = "balance";
    private static final String ID_TOTAL = "total";
    private static final String ID_PAY_CYCLE = "payCycle";
    private static final String ID_ROSTER = "roster";
    private static final String ID_EDITOR = "editor";
    private static final String ID_SELECTED_NAME = "selectedName";
    private static final String ID_UNLOCKED = "unlocked";
    private static final String ID_LOCKED = "locked";

    private final ScrollingList roster;
    private final ScrollingList skills;
    private final List<Integer> visibleCitizens = new ArrayList<>();
    private final Map<Skill, Integer> draft = new EnumMap<>(Skill.class);
    private int selectedId = -1;
    private UUID selectedUuid;
    private long editingRevision;

    /**
     * Creates the roster and next-dawn skill editor for a Town Hall.
     *
     * @param view synchronized incentive module view
     */
    public WindowCitizenIncentives(final CitizenIncentiveModuleView view)
    {
        super(view, ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "gui/layouthuts/layoutcitizenincentives.xml"));
        TownHallIncentiveTab.rememberTownHall(view.getBuildingView());
        roster = findPaneOfTypeByID(ID_CITIZENS, ScrollingList.class);
        skills = findPaneOfTypeByID(ID_SKILLS, ScrollingList.class);
        registerButton(ID_EDIT, button -> edit(rowCitizen(button)));
        registerButton(ID_UP, button -> move(button, -1));
        registerButton(ID_DOWN, button -> move(button, 1));
        registerButton(ID_PLUS, button -> adjust(button, 1));
        registerButton(ID_MINUS, button -> adjust(button, -1));
        registerButton(ID_SAVE, button -> save(false));
        registerButton(ID_STOP, button -> save(true));
        registerButton(ID_BACK, button -> closeEditor());
        roster.setDataProvider(new ScrollingList.DataProvider()
        {
            /** {@inheritDoc} */
            @Override
            public int getElementCount()
            {
                return visibleCitizens.size();
            }

            /** {@inheritDoc} */

            @SuppressWarnings("null")
            @Override
            public void updateElement(final int index, final Pane row)
            {
                final int id = visibleCitizens.get(index);
                final ICitizenDataView citizen = moduleView.getColony().getCitizens().get(id);
                if (citizen == null) return;
                final IncentivePlan plan = moduleView.plans().get(id);
                final List<Integer> priority = new ArrayList<>(moduleView.plans().keySet());
                final int position = priority.indexOf(id);
                row.findPaneOfTypeByID(ID_NAME, Text.class).setText(Component.literal(citizen.getName()));
                setTooltip(row.findPaneByID(ID_NAME), Component.literal(citizen.getName()));
                row.findPaneOfTypeByID(ID_JOB, Text.class).setText(citizen.getJobComponent());
                setTooltip(row.findPaneByID(ID_JOB), citizen.getJobComponent());
                final long cost = plan == null ? 0 : plan.cycleCost(moduleView.price(), moduleView.citizens().get(id)::level);
                final String state = plan == null ? "none" : plan.endedForFunds() ? "unfunded" : plan.limitedByCap() ? "capped" :
                    plan.hasApplied() ? (plan.isScheduled() ? "active" : "ending") :
                    "scheduled";
                row.findPaneOfTypeByID(ID_STATUS, Text.class)
                    .setText(
                        Component.translatable(plan == null ? "mctradepost.incentives.status.none" : "mctradepost.incentives.amount",
                            EconomicValueFormatter.compact(cost)));
                final int daysUntilPayment = plan == null ? 0 : Math.max(0, plan.nextPaymentDay() - moduleView.colonyDay());
                setTooltip(row.findPaneByID(ID_STATUS), Component.translatable("mctradepost.incentives.status." + state,
                    EconomicValueFormatter.exact(cost), daysUntilPayment));
                row.findPaneOfTypeByID(ID_UP, Button.class).setEnabled(position > 0);
                row.findPaneOfTypeByID(ID_DOWN, Button.class).setEnabled(position >= 0 && position < priority.size() - 1);
            }
        });
        skills.setDataProvider(new ScrollingList.DataProvider()
        {
            /** {@inheritDoc} */
            @Override
            public int getElementCount()
            {
                return selectedId < 0 ? 0 : Skill.values().length;
            }

            /** {@inheritDoc} */

            @SuppressWarnings("null")
            @Override
            public void updateElement(final int index, final Pane row)
            {
                if (!moduleView.citizens().containsKey(selectedId)) return;
                final Skill skill = Skill.values()[index];
                final int normal = moduleView.normalLevel(selectedId, skill);
                final int amount = draft.getOrDefault(skill, 0);
                final IncentivePlan plan = moduleView.plans().get(selectedId);
                final String skillName = skill.name().toLowerCase(Locale.ROOT);
                final Component roles = skillRoles(skill);
                row.findPaneOfTypeByID(ID_SKILL_NAME, Text.class)
                    .setText(Component.translatable("com.minecolonies.coremod.gui.citizen.skills." + skillName)
                        .withStyle(style -> style.withUnderlined(!roles.getString().isEmpty())));
                setTooltip(row.findPaneByID(ID_SKILL_NAME), roles);
                setTooltip(row.findPaneByID(ID_SKILL_ICON), roles);
                row.findPaneOfTypeByID(ID_SKILL_ICON, Image.class)
                    .setImage(ResourceLocation.fromNamespaceAndPath("minecolonies", SKILL_ICON_PATH + skillName + ".png"), false);
                final Text levelLabel = row.findPaneOfTypeByID(ID_LEVELS, Text.class);
                levelLabel.setText(Component.literal(Integer.toString(normal + amount)));
                final Tooltip details = levelLabel.getHoverPane() instanceof Tooltip tooltip ? tooltip :
                    PaneBuilders.tooltipBuilder().hoverPane(levelLabel).build();
                details.setText(Component.translatable("mctradepost.incentives.skilldetails",
                    normal,
                    normal + amount,
                    amount,
                    plan == null ? 0 : plan.applied(skill)));
                row.findPaneOfTypeByID(ID_BOOST, Text.class).setText(Component.literal(amount == 0 ? "" : "+" + amount));
                row.findPaneOfTypeByID(ID_MINUS, Button.class).setEnabled(amount > 0);
                row.findPaneOfTypeByID(ID_PLUS, Button.class).setEnabled(
                    IncentivePlan.availableBoost(normal, amount + 1) > amount && projectedTotalCost(draftCost() + moduleView.price()) <= moduleView.incentiveCap());
            }
        });
    }

    /** {@inheritDoc} */

    @Override
    public void onOpened()
    {
        super.onOpened();
        closeEditor();
        refreshRoster();
    }

    /** {@inheritDoc} */

    @Override
    public void onUpdate()
    {
        super.onUpdate();
        if (selectedId >= 0)
        {
            if (!moduleView.isUnlocked())
            {
                closeEditor();
                refreshRoster();
                return;
            }
            final CitizenSkills citizen = moduleView.citizens().get(selectedId);
            if (citizen == null || !citizen.uuid().equals(selectedUuid)) closeEditor();
            else
            {
                // Preserve the draft on concurrent changes; the server rejects a stale save with an explanation.
                setEconomicValue(ID_COST, "mctradepost.incentives.cost", draftCost());
                setEconomicValue(ID_PRICE, "mctradepost.incentives.price", moduleView.price());
                findPaneOfTypeByID(ID_EDITOR_HINT, Text.class).setText(Component.translatable(
                    editingRevision == moduleView.revision() ? "mctradepost.incentives.nextdawn" : "mctradepost.incentives.changed"));
                skills.refreshElementPanes();
                refreshSelectedName();
            }
        }
        else refreshRoster();
    }

    /** Rebuilds the filtered roster in payment order and refreshes the displayed treasury and cycle estimate. */
    private void refreshRoster()
    {
        findPaneByID(ID_UNLOCKED).setVisible(moduleView.isUnlocked());
        findPaneByID(ID_LOCKED).setVisible(!moduleView.isUnlocked());
        final String filter = findPaneOfTypeByID(ID_SEARCH, TextField.class).getText().toLowerCase(Locale.ROOT);
        visibleCitizens.clear();
        final Map<Integer, ICitizenDataView> citizens = moduleView.getColony().getCitizens();
        final List<Integer> priority = new ArrayList<>(moduleView.plans().keySet());
        moduleView.citizens()
            .keySet()
            .stream()
            .filter(citizens::containsKey)
            .filter(id -> (citizens.get(id).getName() + " " + citizens.get(id).getJobComponent().getString()).toLowerCase(Locale.ROOT)
                .contains(filter))
            .sorted(Comparator.<Integer>comparingInt(id -> priority.contains(id) ? priority.indexOf(id) : Integer.MAX_VALUE)
                .thenComparing(id -> citizens.get(id).getName())
                .thenComparingInt(id -> id))
            .forEach(visibleCitizens::add);
        final long total = totalScheduledCost();
        setEconomicValue(ID_BALANCE, "mctradepost.incentives.balance", moduleView.balance());
        findPaneOfTypeByID(ID_PAY_CYCLE, Text.class).setText(
            Component.translatable("mctradepost.incentives.paycycle", moduleView.payCycleDays()));
        final Text totalLabel = findPaneOfTypeByID(ID_TOTAL, Text.class);
        totalLabel.setText(Component.translatable("mctradepost.incentives.total", EconomicValueFormatter.compact(total),
            EconomicValueFormatter.compact(moduleView.incentiveCap())));
        setTooltip(totalLabel, Component.translatable("mctradepost.incentives.total", EconomicValueFormatter.exact(total),
            EconomicValueFormatter.exact(moduleView.incentiveCap())));
        roster.refreshElementPanes();
    }

    /** @return aggregate cost of all currently scheduled plans in the synchronized snapshot */
    private long totalScheduledCost()
    {
        long total = 0;
        for (final Map.Entry<Integer, IncentivePlan> entry : moduleView.plans().entrySet())
        {
            final CitizenSkills snapshot = moduleView.citizens().get(entry.getKey());
            if (snapshot != null) total += entry.getValue().cycleCost(moduleView.price(), snapshot::level);
        }
        return total;
    }

    /**
     * Replaces the selected citizen's synchronized cost with a prospective draft cost.
     * @param proposedDraftCost prospective per-cycle cost for the open editor
     * @return projected colony-wide scheduled payroll
     */
    private long projectedTotalCost(final long proposedDraftCost)
    {
        long total = totalScheduledCost();
        final IncentivePlan current = moduleView.plans().get(selectedId);
        final CitizenSkills snapshot = moduleView.citizens().get(selectedId);
        if (current != null && snapshot != null) total -= current.cycleCost(moduleView.price(), snapshot::level);
        return total + proposedDraftCost;
    }

    /**
     * Resolves a roster button to the citizen represented by its row.
     *
     * @param button clicked roster control
     * @return colony-local citizen ID, or minus one when the row is no longer present
     */
    private int rowCitizen(final Button button)
    {
        final int index = roster.getListElementIndexByPane(button);
        return index >= 0 && index < visibleCitizens.size() ? visibleCitizens.get(index) : -1;
    }

    /**
     * Opens a draft for a citizen using the latest synchronized normal levels and requested bonuses.
     *
     * @param id colony-local citizen ID
     */
    private void edit(final int id)
    {
        if (!moduleView.isUnlocked()) return;
        final CitizenSkills citizen = moduleView.citizens().get(id);
        if (citizen == null) return;
        selectedId = id;
        selectedUuid = citizen.uuid();
        editingRevision = moduleView.revision();
        draft.clear();
        final IncentivePlan plan = moduleView.plans().get(id);
        for (final Skill skill : Skill.values()) draft.put(skill,
            IncentivePlan.availableBoost(moduleView.normalLevel(id, skill), plan == null ? 0 : plan.requested(skill)));
        findPaneByID(ID_ROSTER).hide();
        findPaneByID(ID_TITLE).hide();
        findPaneByID(ID_EDITOR).show();
        refreshSelectedName();
        setEconomicValue(ID_PRICE, "mctradepost.incentives.price", moduleView.price());
        skills.refreshElementPanes();
    }

    /**
     * Shows a compact economic label with its exact amount available on hover.
     *
     * @param id          label component ID
     * @param translation translation key accepting the formatted amount
     * @param value       economic amount
     */
    private void setEconomicValue(final String id, final @Nonnull String translation, final long value)
    {
        final Text label = findPaneOfTypeByID(id, Text.class);
        label.setText(Component.translatable(translation, EconomicValueFormatter.compact(value)));
        setTooltip(label, Component.translatable(translation, EconomicValueFormatter.exact(value)));
    }

    /** Refreshes the selected citizen's name and current job tooltip. */
    @SuppressWarnings("null")
    private void refreshSelectedName()
    {
        final ICitizenDataView citizen = moduleView.getColony().getCitizens().get(selectedId);
        if (citizen == null) return;
        final Text name = findPaneOfTypeByID(ID_SELECTED_NAME, Text.class);
        name.setText(Component.literal(citizen.getName()));
        setTooltip(name, citizen.getJobComponent());
    }

    /**
     * Updates a reusable tooltip, removing it when there is no applicable information.
     *
     * @param pane control receiving the tooltip
     * @param text tooltip contents, or empty text to remove it
     */
    private static void setTooltip(final Pane pane, final Component text)
    {
        if (text.getString().isEmpty())
        {
            pane.setHoverPane(null);
            return;
        }
        final Tooltip tooltip =
            pane.getHoverPane() instanceof Tooltip existing ? existing : PaneBuilders.tooltipBuilder().hoverPane(pane).build();
        tooltip.setText(text);
    }

    /**
     * Resolves every job-related role for a skill using the core worker module and skill relationships.
     *
     * @param skill displayed skill
     * @return newline-separated roles, or empty text for an unrelated skill or an unavailable job
     */
    private Component skillRoles(final Skill skill)
    {
        final ICitizenDataView citizen = moduleView.getColony().getCitizens().get(selectedId);
        if (citizen == null || citizen.getJobView() == null) return Component.empty();
        final IBuildingView building = moduleView.getColony().getClientBuildingManager().getBuilding(citizen.getWorkBuilding());
        if (building == null) return Component.empty();
        final WorkerBuildingModuleView worker = building.getModuleViewMatching(WorkerBuildingModuleView.class,
            candidate -> candidate.getJobEntry() == citizen.getJobView().getEntry());
        if (worker == null) return Component.empty();
        final List<Component> roles = new ArrayList<>();
        appendSkillRoles(roles, skill, worker.getPrimarySkill(), "primary");
        appendSkillRoles(roles, skill, worker.getSecondarySkill(), "secondary");
        final MutableComponent result = Component.empty();
        for (final Component role : roles)
        {
            if (role == null) continue;

            if (!result.getString().isEmpty()) result.append("\n");
            result.append(role);
        }
        return result;
    }

    /**
     * Adds direct and offset roles relative to one of the job's main skills.
     *
     * @param roles    destination for all matching descriptions
     * @param skill    displayed skill
     * @param jobSkill primary or secondary skill defined by the worker module
     * @param role     translation suffix identifying primary or secondary
     */
    private static void appendSkillRoles(final List<Component> roles, final Skill skill, final Skill jobSkill, final String role)
    {
        if (jobSkill == null) return;
        final Component mainRole = Component.translatable("mctradepost.incentives.skillrole." + role);
        if (skill == jobSkill) roles.add(mainRole);
        if (skill == jobSkill.getAdverse()) roles.add(Component.translatable("mctradepost.incentives.skillrole.adverse", mainRole));
    }

    /** Discards the local draft and returns to the citizen roster. */
    private void closeEditor()
    {
        selectedId = -1;
        draft.clear();
        findPaneByID(ID_EDITOR).hide();
        findPaneByID(ID_TITLE).show();
        findPaneByID(ID_ROSTER).show();
    }

    /**
     * Changes one draft skill bonus while enforcing zero and the core skill cap.
     *
     * @param button    clicked skill-row control
     * @param direction signed one-point change
     */
    private void adjust(final Button button, final int direction)
    {
        final int row = skills.getListElementIndexByPane(button);
        if (row < 0 || row >= Skill.values().length || !moduleView.citizens().containsKey(selectedId)) return;
        final Skill skill = Skill.values()[row];
        draft.put(skill,
            IncentivePlan.availableBoost(moduleView.normalLevel(selectedId, skill), draft.getOrDefault(skill, 0) + direction));
        skills.refreshElementPanes();
    }

    /** @return total economic units per pay cycle for the unsaved draft at the current synchronized price */
    @SuppressWarnings("null")
    private long draftCost()
    {
        return draft.values().stream().mapToLong(Integer::longValue).sum() * moduleView.price();
    }

    /**
     * Submits next-dawn instructions and returns to the roster; the server confirms or rejects the request.
     *
     * @param cancel whether to submit zero bonuses instead of the current draft
     */
    private void save(final boolean cancel)
    {
        if (selectedId < 0) return;
        new CitizenIncentiveMessage(moduleView
            .getBuildingView(), selectedId, selectedUuid, editingRevision, 0, cancel ? Map.of() : draft).sendToServer();
        closeEditor();
    }

    /**
     * Requests a one-position change to the citizen's payment priority.
     *
     * @param button    clicked roster control
     * @param direction minus one for earlier payment or plus one for later payment
     */
    private void move(final Button button, final int direction)
    {
        final int id = rowCitizen(button);
        final CitizenSkills citizen = moduleView.citizens().get(id);
        if (citizen == null) return;
        new CitizenIncentiveMessage(moduleView.getBuildingView(), id, citizen.uuid(), moduleView.revision(), direction, Map.of())
            .sendToServer();
    }
}
