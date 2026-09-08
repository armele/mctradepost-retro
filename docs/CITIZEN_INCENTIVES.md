# Citizen incentives

Open Town Hall by interacting with its hut block; the new tab appears on the right side of the book. Core Town Hall windows hide normal building-module tabs, so Trade Post adds this tab using public client interaction and screen events. Those events retain the exact clicked hut across Town Hall pages. An external tool opening a Town Hall remotely can supply the same context through `TownHallIncentiveTab.rememberTownHall(buildingView)`; without known context, Trade Post does not guess which colony's treasury to edit.

Open **Town Hall -> Citizen Incentives**. Before Incentive Plans is researched, the tab retains treasury, pay-cycle, and scheduled-incentive context while explaining how to unlock the feature; search and citizen controls remain hidden. After unlocking it, search by citizen name or job and choose **Edit**. The editor uses the core citizen screen's icons and compact single-line rows, with all eleven skills visible together. Each row shows the proposed next-day level, its bonus in green, and always-visible minus/plus buttons on the right. Hover the level to see its normal value and current paid bonus. Minus stops at zero; plus stops at MineColonies' maximum skill level (currently 99) and the colony's researched per-cycle incentive cap.

The economic research chain begins beneath Capitalism. Incentive Plans (tier 2) permits 10,000 per cycle; Big Bonuses (tier 3) permits 100,000 per cycle; Golden Handcuffs (tier 4) permits 1,000,000 per cycle; and Golden Parachute (tier 5) permits 100,000,000 per cycle. Each tier requires matching Marketplace and Town Hall levels from 2 through 5.

The roster uses two lines per citizen: name and right-aligned per-cycle amount, then job and controls. Hover the amount for payment status and the number of days until the next payment, or the name/job for the full text. The pencil opens the editor; the small +/− controls move payment priority earlier/later. In the editor, hover the citizen's name for their current job. Underlined skill names identify primary and secondary skills from the core worker module; hover a name or icon for the applicable relationship. Citizens without an available worker module have no job skill markings.

**Save** schedules the selected package for next dawn. A new or edited plan starts a complete pay cycle at that dawn; unused days from the former cycle are neither credited nor refunded. **Stop tomorrow** schedules cancellation without removing the current benefit immediately or refunding its cost. The red corner button discards unsaved edits and returns to the roster. Citizens with saved plans appear first. The roster total is the colony's aggregate per-cycle commitment using current normal levels and the current configured price.

## Configuration

The server's Trade Post configuration contains:

```toml
[incentives]
    incentiveCostPerSkillPoint = 1000
    incentivePayCycleDays = 5
```

Price is in colony economic units per point per pay cycle, independent of the Trade Coin denomination. The pay cycle can range from 1 to 30 MineColonies days and defaults to 5. For example, +2 Strength and +2 Dexterity cost 4,000 for the entire five-day cycle at the defaults. Changing the cycle length affects each citizen at their next renewal; it does not shorten, extend, or rebill a cycle already purchased.

## Payment rules

- At the next observed colony dawn, a new or edited plan expires the previous adjustments and purchases a complete cycle against the citizen's current normal levels. Only points that fit below the core cap are purchased.
- A cycle beginning on day D covers days D through D + N - 1. Renewal is due on day D + N. The bonus remains applied between payment dates without additional withdrawals.
- Process packages in displayed priority order. Each citizen's package is all-or-nothing. A successful payment can leave the treasury at exactly zero.
- New or edited commitments cannot exceed the researched colony-wide per-cycle incentive cap. Every scheduled plan reserves its per-cycle cost even when its payment is not due that day. If research is reset or the cap otherwise falls below saved commitments, plans retain capacity in priority order until the cap is reached. Remaining plans lose their current boost at dawn but stay scheduled and are reconsidered on later dawns. With no unlocked effect, all plans are deferred this way.
- If a package cannot be funded, its adjustments end and its plan stops. Later deposits do not restart it. Open the editor and save to reactivate it for the next dawn; the previous requested amounts remain available in the editor.
- Employment, sickness, mourning, vacation, job changes, and citizen entity unloading do not pause payments. The selected skills remain fixed when a citizen changes jobs.
- Use MineColonies' colony-day counter, not wall-clock days. If several days are skipped, one cycle is purchased on the next observed dawn; no missed-cycle debt accumulates. Payroll can run while the Town Hall chunk is unloaded if the colony is active.
- A stale multiplayer edit or an edit crossing dawn is rejected by the server. Reopen the editor to work from the latest settings.
- When at least one package is paid or renewed, colony message recipients receive one aggregate `Incentives Paid` message for the actual amount withdrawn that dawn.

Other NeoForge mods can subscribe to `CitizenIncentivesPaidEvent` on `NeoForge.EVENT_BUS`. It is posted on the server thread after cycle withdrawals and skill adjustments complete. `getPayments()` returns an immutable list in payment-priority order; each entry provides the live `ICitizenData` and the actual per-cycle amount paid. `getTotalAmount()` returns the aggregate amount withdrawn. The event is not posted on dawns with no successful activation, edit, or renewal payment.

```java
NeoForge.EVENT_BUS.addListener(Integration::onIncentivesPaid);

private static void onIncentivesPaid(final CitizenIncentivesPaidEvent event)
{
    event.getPayments().forEach(payment ->
        handlePayment(payment.getCitizen(), payment.getAmount()));
}
```

## Skills and persistence

This feature uses public MineColonies APIs and contains no mixins or core patches. It changes stored skill levels with `ICitizenSkillHandler.incrementLevel` and saves the exact applied amounts in the Town Hall module. Core saves building modules, citizens, and treasury statistics together in its colony NBT snapshot.

Expiration subtracts the recorded adjustment rather than restoring a snapshot: 20 + 5 -> naturally earns level 26 -> expires to 21. Existing partial XP is retained. As agreed, core progression uses boosted levels, including XP requirements, housing training limits, Intelligence effects, and the maximum level. Other core consumers, including inheritance, also see boosted levels. Bonuses do not guarantee a fixed percentage increase in production.

Trade Post's vacation eligibility and recovery targets use normal levels so an incentive change cannot itself count as recovery. XP recovery still obeys core's boosted-level training limits; a worker boosted to the cap cannot gain XP until their boost is reduced or ended.

The persisted plan includes citizen ID and UUID, requested/applied boosts keyed by skill names, pending activation day, cycle start, next payment day, last processed day, and insufficient-funds status. Reloading does not reapply or rebill the current cycle. Saves created before pay cycles renew once at the first observed dawn after upgrading. Job attribute caches are refreshed after changing skills. Destroying/removing Town Hall removes adjustments immediately. Citizen death cleanup runs on core's KILLED removal event before grave creation, preventing resurrection with orphaned boosted stats.

For an external mod that transfers citizens by copying their data, call `CitizenIncentiveModule.releaseCitizen(citizen)` on the source Town Hall module **before** copying. Core exposes no generic pre-copy transfer event; arbitrary third-party copies cannot be reconciled automatically. Removing Trade Post itself also removes the cleanup code: cancel incentives and allow the next dawn before uninstalling it.

## Validation

Automated tests exercise the actual core skill handler for earned levels, partial XP, save/reload, cycle renewal, next-day edits, cancellation, insufficient funds and manual reactivation, no catch-up billing, skill-cap adjustment, and overflow-safe prices. Module tests cover payment order, researched per-cycle limits, research rollback, retained deferrals, and lifecycle cleanup.

In-game acceptance checks:

1. In an existing colony, open the new tab, filter citizens, and schedule +2 Strength/+2 Dexterity. Confirm no immediate withdrawal or skill change.
2. Advance through dawn: verify a 4,000 withdrawal, boosted citizen screens, and the expense statistic. Advance through the paid cycle without another charge, then verify renewal on the next payment day.
3. Edit or cancel during a paid cycle; verify the old benefit remains until dawn and unused days receive no refund. Save/reload before and after dawn.
4. With two packages and funds for one, change priority and verify which is funded. Deposit after a failure and confirm manual reactivation is necessary.
5. Keep workers active outside Town Hall's loaded chunk across dawn. Verify expiration and billing still happen.
6. Test a guard/courier's cached attributes, natural level gains, capped skills, vacation recovery, Town Hall removal, death/resurrection, and conflicting edits by two permitted players.
