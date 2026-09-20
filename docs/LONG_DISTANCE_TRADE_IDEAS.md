# Long-Distance Trade Design Notes

## Audience and status

This is a shared design notebook for contributors. It records possibilities rather than settled requirements. Names, ranges, costs, and progression are intentionally provisional.

The central problem is how to connect far-away colonies without asking the server to inspect or load many thousands of intervening blocks. A good solution should preserve the value of player-built infrastructure, explain failures clearly, and avoid making roads, rail, water routes, and Nether links obsolete.

## Design principles

- Keep exact block-by-block pathfinding for local infrastructure where players can understand and repair it.
- Represent very long journeys with abstract legs whose endpoints have concrete, locally validated infrastructure.
- Make each mode distinct in range, capacity, construction cost, operating cost, and progression.
- Do not keep intermediate chunks loaded merely to animate a shipment.
- Spawn visual vehicles near departure and arrival; simulate the middle of a long journey.
- Store stable route identities and endpoint data, not enormous paths, for abstract legs.
- Prefer data-driven or registered transport modes so optional compatibility can live outside Trade Post.
- Diagnostics must identify the failing leg and give an operator or player a practical remedy.

## Candidate transport modes

### Airships

Airships are the strongest general-purpose candidate. Each colony constructs a mooring mast linked to its Trade Station. Two valid masts create an abstract air leg with a generous configurable range, perhaps 20,000 to 50,000 blocks.

Do not require the whole station to be at a fixed height such as Y=128. That disproportionately favors mountains and encourages unattractive towers. Prefer local launch conditions:

- the mast can see the sky;
- a configurable clearance volume above it is unobstructed;
- the platform is sufficiently above nearby terrain or the colony building bounds;
- the mast is connected locally to the Trade Station;
- both colonies have the required research;
- optional fuel or currency is available per departure.

Possible progression:

- an early dirigible has limited range, capacity, and speed;
- upgrades increase range and cargo capacity;
- later research reduces fuel cost or weather delay;
- the highest tier may cross dimensions, although that risks encroaching on the Nether link's identity.

The middle of the journey should be simulated. An airship can depart visibly, despawn after leaving the local area, and reappear near the destination according to elapsed trade time. This avoids loading the straight-line corridor.

### Abstract ocean shipping lanes

Separate local water navigation from long-distance sea travel. A normal water search still connects a station or river dock to a Harbor Beacon, Ocean Buoy, or upgraded Trade Dock. Two qualified beacons then form an abstract sea-lane leg:

```text
Station -> local river path -> harbor beacon => sea lane => harbor beacon -> local path -> Station
```

A beacon might qualify through a modest local test for deep/open water, clear sky, and navigable space rather than an unbounded ocean search. Manual linking is another defensible option and is easier for operators to reason about.

This retains the gameplay value of rivers, canals, and ports while avoiding a search along thousands of blocks of coastline. Sea lanes should favor very high cargo capacity and slower transit. Airships should be faster or more flexible but carry less or cost more.

### Relay infrastructure

Allow long journeys to be assembled from bounded legs through explicit relays:

- caravanserais for roads;
- rail depots;
- lighthouses or harbor beacons;
- airship refueling towers;
- upgraded Outposts or Trade Stations.

The existing segmented route concept and shortest-path selection are a natural fit. Limit relay count or penalize handoffs so the graph remains understandable and does not select absurd routes or cycles. A relay network creates useful cooperative server projects, but it does not by itself solve pathfinding cost if every leg is still near the current maximum.

### Long-range caravans

A Caravan Office could abstract the wilderness portion of an overland journey. The station would need a real local road to a caravan gate, after which distance becomes travel time rather than a traversed block list.

Potential costs include food, pack animals, guards, currency, or a risk of delay. This supports servers that want distant trade without rail megaprojects, though it is less physically literal than other Trade Post modes.

### Endgame trade gates

A paired Trade Gate, Ender Exchange, or similar magical structure could provide extremely long or unlimited range at high research and operating cost. Explicit pairing prevents accidental global connectivity. Low throughput, rare fuel, or a per-shipment fee can preserve the value of conventional infrastructure.

This should be an endgame convenience rather than the default answer to distance.

### Scheduled merchant contracts

A contract can represent trade between approved stations without continuous physical connectivity. Distance determines delay and frequency. Operators could authorize a contract or players could prove a connection once and then retain it.

This is technically robust but weakens Trade Post's infrastructure-building identity. It is better as an optional server rule, recovery mechanism, or integration point than as the primary mode.

### Couriers

Trained birds, dragons, or magical couriers could move a very small quantity over long distances. This gives the Animal Trainer another purpose and creates a useful distinction between urgent valuable items and bulk freight. Couriers complement rather than replace ships and airships.

## Possible transport hierarchy

| Mode | Intended range | Infrastructure | Capacity | Route representation |
| --- | ---: | --- | ---: | --- |
| Road | Local | Continuous tagged road | Medium | Exact path |
| Rail | Regional | Continuous track | High | Exact path |
| River/canal | Local or regional | Navigable surface water | High | Exact path |
| Via Romana | Regional or long | Charted qualifying road and linked endpoints | Configurable | External abstract leg |
| Sea lane | Long | Local docks plus paired beacons | Very high | Local exact paths plus abstract leg |
| Airship | Very long | Two clear mooring masts | Medium | Abstract leg |
| Nether link | Very long | Portals and Trade Linkers | High | Existing 8:1 dimensional route |
| Trade gate | Endgame/unlimited | Expensive paired gates | Low or medium | Abstract transfer |

The most promising first-party combination is airships plus abstract sea lanes. They solve different fantasies and technical problems without making the Nether route redundant.

## Via Romana compatibility

### Gameplay concept

[Via Romana](https://github.com/RasaNovum/Via_Romana) grants fast travel after players chart a qualifying road network and link signs near its nodes. Trade Post could treat a confirmed Via Romana connection as proof that overland infrastructure exists, then simulate freight along that network instead of independently walking every road block.

A likely route would be:

```text
Trade Station -> local Trade Post road -> Via Romana-linked sign
              => abstract Via Romana leg =>
Via Romana-linked sign -> local Trade Post road -> Trade Station
```

Open design questions:

- Must each station have a dedicated linked sign, or may the nearest sign within a configured radius be used?
- Must the local station-to-sign leg use `#mctradepost:trade_roads`, or is proximity enough?
- Does Via Romana travel distance control shipment time, or only connectivity?
- Are player/claim permissions relevant to automated colony freight?
- Does breaking a road invalidate Trade Post immediately, or only when Via Romana invalidates its own charted network?
- Should Via Romana routes require research, a station upgrade, a shipping fee, or lower capacity?

The compatibility should respect Via Romana's own charted network as the authority. Trade Post should not rescan the physical road or attempt to reproduce Via Romana's qualification rules.

### Why a separate compatibility mod is preferable

A separate compat mod keeps Trade Post free of a compile-time dependency, isolates version churn in Via Romana, and allows the integration to ship on only the Minecraft loaders and versions supported by both projects. It also gives the integration its own configuration and release cadence.

Trade Post should nevertheless expose a small, stable transport-provider API. Without one, the compat mod would need mixins or access transformers into route discovery, route storage, validation, shipment visualization, and diagnostics. That would be brittle and would make every new compatibility repeat the same invasive work.

### What Trade Post should expose

The preferred model is a registered **route contributor**, not a Via Romana-specific hook. A contributor adds external anchors and weighted edges to Trade Post's route graph.

Conceptual API:

```java
public interface TradeRouteContributor {
    ResourceLocation id();

    Collection<TradeRouteAnchor> findAnchors(RouteSearchContext context,
                                              TradeRouteEndpoint endpoint);

    Optional<ExternalTradeLeg> findLeg(RouteSearchContext context,
                                       TradeRouteAnchor from,
                                       TradeRouteAnchor to);

    ExternalLegValidation validate(RouteValidationContext context,
                                   ExternalTradeLeg leg);
}
```

The exact signatures can change, but the contract needs to expose:

- source and destination dimension/position;
- station identity and colony identity where permission decisions require them;
- whether discovery may load chunks;
- a time or work budget and diagnostic collector;
- stable contributor, anchor, and external-route identifiers;
- an effective distance or travel-time weight;
- endpoint positions used for local handoffs and visualization;
- reversal rules, including whether the leg is bidirectional;
- validation without requiring a complete rediscovery when possible;
- a translatable display name and diagnostic reason;
- optional metadata encoded as namespaced NBT or a contributor codec;
- invalidation events or a revision token so cached routes can be retired promptly.

Trade Post should own the overall route and shipment lifecycle. The provider should answer connectivity and metadata questions; it should not be handed mutable Trade Post internals.

### Extensible route representation

The current fixed `TrackRoute.SegmentType` enum is suitable for first-party modes but cannot safely be extended by a separate mod. An external leg therefore needs a namespaced identity such as `via_romana_compat:charted_road`, with endpoint and distance data stored independently of a Java enum ordinal.

Possible directions:

1. Add a generic `EXTERNAL` segment whose metadata contains the registered provider ID and a provider-owned payload.
2. Replace or supplement the enum with a registry of namespaced segment types.
3. Keep built-in segment types fixed but allow an external route proposal to be represented as a standard `TRANSFER`-like abstract segment with an explicit mode ID.

Option 1 is probably the smallest safe evolution. Trade Post must still define generic behavior for:

- NBT serialization and missing-provider recovery;
- route reversal;
- distance and shipment timing;
- cached-route validation;
- debug overlay representation;
- vehicle disappearance and reappearance;
- station route diagnostics;
- invalidation after either mod reloads or updates data.

If the compat mod is removed, a saved external segment should deserialize into an invalid route and trigger rediscovery, not crash or silently behave as rail.

### What Via Romana would ideally expose

The Via Romana repository is public, but no stable addon API was identified during this design pass. The compatibility layer would ideally consume an upstream API with operations similar to:

```java
Optional<ViaAnchor> findLinkedAnchor(ServerLevel level, BlockPos near, int radius);
Optional<ViaConnection> findConnection(ViaAnchor from, ViaAnchor to);
boolean isConnectionValid(ResourceLocation connectionId);
```

Useful returned data would include:

- a stable anchor or linked-sign ID;
- dimension and safe endpoint position;
- whether two anchors belong to a connected charted path network;
- charted path length or another cost metric;
- directionality, ownership, and permission information;
- a stable connection ID or network revision;
- events for anchor removal, unlinking, conversion, or network replacement;
- a guarantee that read-only queries do not teleport entities, open UI, or unexpectedly load an unbounded region.

The API does not need to expose every charted node. In fact, a high-level connectivity query is preferable because it preserves Via Romana's authority over its internal data model.

If Via Romana cannot expose an API, the compat mod could temporarily adapt documented public classes or use narrowly contained reflection. That fallback should:

- be version-gated against known Via Romana releases;
- fail closed and report an actionable diagnostic;
- keep all reflective access in one adapter package;
- include integration tests or startup probes;
- never copy or fork Via Romana's path-validation algorithm;
- be treated as transitional rather than as Trade Post's permanent API design.

### Suggested division of responsibility

**Trade Post:**

- publishes the provider registration API;
- discovers and combines native and external route legs;
- stores provider IDs and opaque versioned payloads;
- calculates shipment progress and performs exports/imports;
- handles missing providers and route invalidation safely;
- presents unified diagnostics and route overlays.

**Via Romana compatibility mod:**

- depends optionally on both mods;
- locates Via Romana anchors near Trade Stations;
- asks Via Romana whether anchors are connected;
- translates the result into an external Trade Post leg;
- validates cached legs using stable Via Romana IDs or revisions;
- supplies Via Romana-specific configuration, text, diagnostics, and visuals.

**Via Romana or its public API:**

- remains authoritative for charted roads, nodes, signs, connectivity, and permissions;
- supplies stable read-only queries and invalidation signals;
- does not need to know about colonies, cargo, or Trade Post shipment state.

## Diagnostics expected from every long-distance mode

Every provider should be able to explain at least:

- no valid departure infrastructure was found;
- no valid arrival infrastructure was found;
- endpoints are not linked;
- direct or effective distance exceeds the configured range;
- research, fuel, capacity, ownership, or permission requirements failed;
- an endpoint is obstructed or no longer valid;
- the provider is missing or incompatible;
- the external network changed and the cached route was invalidated.

The operator-facing command should show the best partial chain and the failing leg instead of flattening all failures into "No trade route found."

## Recommended next design steps

1. Decide whether abstract legs are a first-class Trade Post feature or only an integration mechanism.
2. Prototype a generic external segment and provider registry before implementing a particular transport mode.
3. Use a small built-in test provider to exercise persistence, reversal, validation, diagnostics, and missing-provider behavior.
4. Discuss a stable read-only network API with the Via Romana authors before depending on internal classes.
5. Prototype airship masts as the first native abstract mode; they will test nearly the same Trade Post extension points needed by Via Romana compatibility.
6. Define balance separately from connectivity: range, duration, throughput, research, and operating cost should remain tunable without changing provider code.

