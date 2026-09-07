# Citizen incentives

Open Town Hall by interacting with its hut block; the new tab appears on the right side of the book. Core Town Hall windows hide normal building-module tabs, so Trade Post adds this tab using public client interaction and screen events. Those events retain the exact clicked hut across Town Hall pages. An external tool opening a Town Hall remotely can supply the same context through `TownHallIncentiveTab.rememberTownHall(buildingView)`; without known context, Trade Post does not guess which colony's treasury to edit.

Open **Town Hall -> Citizen Incentives**, search by citizen name or job, and choose **Edit**. The editor uses the core citizen screen's icons and compact single-line rows, with all eleven skills visible together. Each row shows the proposed next-day level, its bonus in green, and always-visible minus/plus buttons on the right. Hover the level to see its normal value and today's paid bonus; the daily total remains below the stats. Minus stops at zero; plus stops at MineColonies' maximum skill level (currently 99).

The roster uses two lines per citizen: name and right-aligned daily amount, then job and controls. Hover the amount for payment status, or the name/job for the full text. The pencil opens the editor; the small +/− controls move payment priority earlier/later. In the editor, hover the citizen's name for their current job. Underlined skill names identify primary, secondary, and complementary/adverse offset skills from the core worker module; hover a name or icon for every applicable relationship. Citizens without an available worker module have no job skill markings.

**Save** schedules the selected package for next dawn. **Stop tomorrow** schedules cancellation without removing today's paid benefit or refunding its cost. The red corner button discards unsaved edits and returns to the roster. Citizens with saved plans appear first. The roster's daily total is an estimate using current normal levels and the current configured price.

## Configuration

The server's Trade Post configuration contains:

```toml
[incentives]
    incentiveCostPerSkillPoint = 1000
```

Price is in colony economic units per point per colony day, independent of the Trade Coin denomination. For example, +2 Strength and +2 Dexterity cost 4,000/day at the default price. The configured price must be a positive integer. A configuration change affects the next daily purchase; it does not charge again for the current paid day.

## Payment rules

- At the next observed colony dawn, expire the previous adjustments and calculate the new package against the citizen's current normal levels. Only points that fit below the core cap are purchased.
- Process packages in displayed priority order. Each citizen's package is all-or-nothing. A successful payment can leave the treasury at exactly zero.
- If a package cannot be funded, its adjustments end and its plan stops. Later deposits do not restart it. Open the editor and save to reactivate it for the next dawn; the previous requested amounts remain available in the editor.
- Employment, sickness, mourning, vacation, job changes, and citizen entity unloading do not pause payments. The selected skills remain fixed when a citizen changes jobs.
- Use MineColonies' colony-day counter, not wall-clock days. No missed-day debt accumulates. Payroll can run while the Town Hall chunk is unloaded if the colony is active.
- A stale multiplayer edit or an edit crossing dawn is rejected by the server. Reopen the editor to work from the latest settings.

## Skills and persistence

This feature uses public MineColonies APIs and contains no mixins or core patches. It changes stored skill levels with `ICitizenSkillHandler.incrementLevel` and saves the exact applied amounts in the Town Hall module. Core saves building modules, citizens, and treasury statistics together in its colony NBT snapshot.

Expiration subtracts the recorded adjustment rather than restoring a snapshot: 20 + 5 -> naturally earns level 26 -> expires to 21. Existing partial XP is retained. As agreed, core progression uses boosted levels, including XP requirements, housing training limits, Intelligence effects, and the maximum level. Other core consumers, including inheritance, also see boosted levels. Bonuses do not guarantee a fixed percentage increase in production.

Trade Post's vacation eligibility and recovery targets use normal levels so an incentive change cannot itself count as recovery. XP recovery still obeys core's boosted-level training limits; a worker boosted to the cap cannot gain XP until their boost is reduced or ended.

The persisted plan includes citizen ID and UUID, requested/applied boosts keyed by skill names, activation day, last processed day, and insufficient-funds status. Reloading does not reapply or rebill today's adjustments. Job attribute caches are refreshed after changing skills. Destroying/removing Town Hall removes adjustments immediately. Citizen death cleanup runs on core's KILLED removal event before grave creation, preventing resurrection with orphaned boosted stats.

For an external mod that transfers citizens by copying their data, call `CitizenIncentiveModule.releaseCitizen(citizen)` on the source Town Hall module **before** copying. Core exposes no generic pre-copy transfer event; arbitrary third-party copies cannot be reconciled automatically. Removing Trade Post itself also removes the cleanup code: cancel incentives and allow the next dawn before uninstalling it.

## Validation

Automated tests exercise the actual core skill handler for earned levels, partial XP, save/reload, next-day edits, cancellation, insufficient funds and manual reactivation, no catch-up billing, cap adjustment, and overflow-safe prices. Module tests cover payment order and lifecycle cleanup.

In-game acceptance checks:

1. In an existing colony, open the new tab, filter citizens, and schedule +2 Strength/+2 Dexterity. Confirm no immediate withdrawal or skill change.
2. Advance through dawn: verify a 4,000 withdrawal, boosted citizen screens, and the expense statistic. Repeat opening/closing the tab without additional billing.
3. Edit or cancel during the paid day; verify the old benefit remains until dawn. Save/reload before and after dawn.
4. With two packages and funds for one, change priority and verify which is funded. Deposit after a failure and confirm manual reactivation is necessary.
5. Keep workers active outside Town Hall's loaded chunk across dawn. Verify expiration and billing still happen.
6. Test a guard/courier's cached attributes, natural level gains, capped skills, vacation recovery, Town Hall removal, death/resurrection, and conflicting edits by two permitted players.
