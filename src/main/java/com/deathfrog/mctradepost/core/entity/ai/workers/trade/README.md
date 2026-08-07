# Trade path connection architecture

This package contains the route-discovery layer used to decide whether two trade-capable buildings are connected and to describe how
a shipment travels between them. A route can combine rail, tagged roads, navigable water, modal handoffs, and Overworld/Nether
transfers.

This document is aimed at developers changing pathfinding, adding a transport mode, or diagnosing a station that does not recognize a
route.

## End-to-end flow

The normal station workflow is:

1. `EntityAIWorkStationMaster.checkConnection()` asks its building for a cached `TrackConnectionResult` for the remote station.
2. If no connected result exists, it calls `TrackRouteConnection.findRoute(...)`. A first search may load rail chunks; retries of an
   existing disconnected result use the less invasive search policy selected by the caller.
3. If a connected result is cached, the AI validates it instead of running the full searches again. Legacy path-only results use
   `TrackPathConnection.validateExistingPath(...)`; segmented results use `TrackRouteConnection.validateExistingRoute(...)`.
4. The building stores the result. `BuildingStation` also serializes segmented routes to NBT so connections survive a reload.
5. When a shipment is visualized, `ExportData` consumes the `TrackRoute`, spawns the vehicle appropriate to each traversable segment,
   performs effects at docks and interchanges, and removes/recreates the vehicle across dimensional transfers.

In abbreviated form:

```text
station AI
   |
   +-- cached route? -- yes --> lightweight validation
   |                            |-- rail position checks
   |                            |-- road/water position checks
   |                            `-- handoff/transfer endpoint checks
   |
   `-- no/invalid ------------> TrackRouteConnection.findRoute
                                  |
                                  +-- same-dimension multimodal search
                                  `-- dimensional-linkage composition
                                                |
                                                v
                                      TrackConnectionResult + TrackRoute
                                                |
                                  cache / NBT / shipment visualization
```

## Core model

### `TrackRoute`

`TrackRoute` is the canonical dimension-aware representation. It is an ordered immutable list of `TrackRoute.Segment` values. Segment
types are:

- `RAIL`, `ROAD`, and `WATER`: positive-distance paths whose distance is `path.size() - 1`.
- `DOCK`: a zero-distance handoff involving water transport.
- `INTERCHANGE`: a zero-distance rail/road handoff.
- `TRANSFER`: a one-step transition between dimensional linkage endpoints.

Handoff segments are explicit even though they add no distance. Consumers use them to distinguish a genuine mode transition from two
unrelated adjacent path segments. Transfer segments retain both `DimPos` endpoints because their origin and destination are in
different dimensions.

`TrackRoute.reversed()` reverses segment order and local paths and swaps transfer endpoints for return shipments. `firstPath()` and
`firstRailPath()` exist primarily for compatibility with code written before segmented routes were introduced.

### `TrackConnectionResult`

`TrackPathConnection.TrackConnectionResult` is the compatibility envelope returned throughout the trade system. Important fields are:

- `connected`: whether discovery or the latest validation succeeded.
- `closestPoint`: useful diagnostic state for a failed rail search.
- `path`: the first local path, retained for legacy callers.
- `route`: the complete segmented route when available.
- `lastChecked`: game time of the search or validation.

New route-aware code should use `getRoute()` and `getRouteDistance()`. Be aware that `getRoute()` wraps a legacy non-empty `path` as a
single Overworld rail route; that fallback cannot recover a dimension from the legacy value.

## Same-dimension discovery

`MultimodalRouteConnection.findRoute(...)` builds a small weighted graph and applies Dijkstra's algorithm.

The graph contains the requested start and end plus registered docks and interchanges. Candidate handoff blocks are ordered by their
distance to the nearer endpoint and capped to bound the complete pairwise graph. Registry entries are checked against the live block
state before use, so stale saved-data entries do not become graph nodes.

For each pair of nodes, the edge search behaves as follows:

- Two docks may connect through `ModalPathConnection.water(...)`, subject to `maximumWaterRouteDistance`.
- Every node pair is tested for a rail path with `TrackPathConnection.arePointsConnectedByTracks(...)`.
- Every node pair is also tested for a tagged-road path with `ModalPathConnection.road(...)`.
- If both rail and road connect a pair, the shorter segment wins.

After Dijkstra selects the least-distance node sequence, explicit `DOCK` or `INTERCHANGE` segments are inserted at intermediate
handoff nodes. Start and end blocks that are themselves docks or interchanges are classified as such for endpoint resolution, but an
extra handoff segment is only needed between traversable legs.

`BlockTradeDock` supplies separate land and water connection positions. An interchange uses its own position for both rail and road.

## Modal path searches

`TrackPathConnection` performs the rail breadth-first search. It explores horizontal neighbors and one-block slopes, accepts an end
position adjacent to the final track, and is bounded by `MAX_DEPTH`. When `loadChunks` is true it adds temporary rail-search chunk
tickets and releases every ticket in a `finally` block.

`ModalPathConnection` contains the non-rail searches:

- Road search is a bounded breadth-first search over blocks in `ModTags.BLOCKS.TRADE_ROADS_TAG`. It permits one-block elevation
  changes and does not load missing chunks.
- Water search is bounded A* over horizontal water positions with empty collision above the water. It may load chunks encountered by
  the search and is bounded by both the configured maximum distance and the global visited-node limit.

All reconstructed paths include their requested endpoints. Validation deliberately ignores the first and last path positions because
they may be station, dock, interchange, or other connection blocks rather than the traversable material itself.

## Cross-dimension discovery

`TrackRouteConnection` is the top-level orchestrator. It first attempts a direct same-dimension multimodal route. If that fails, it
collects complete dimensional linkage items installed at the participating stations and tries routes composed from local multimodal
segments and `TRANSFER` segments.

Supported compositions are:

- Overworld to Nether, or Nether to Overworld: local route, one transfer, local route.
- Overworld to Overworld through the Nether: local route, transfer to Nether, Nether route, transfer back, local route.

Only loaded invalid linkage endpoints are rejected. Unloaded endpoints are provisionally accepted, matching cached-route validation's
optimistic policy. A route-search context caches repeated same-dimension segment searches and caps linkage-pair attempts at 64 to keep
the combinatorial search bounded.

When changing linkage selection, preserve the distinction between linkages installed at an endpoint and the combined fallback set.
The endpoint-owned pair is preferred before the broader candidate search.

## Caching and validation

Validation is intentionally cheaper and less disruptive than discovery:

- Rail, road, and water segments check loaded interior path positions and treat unloaded positions optimistically.
- Dock and interchange segments require their corresponding block at the handoff position.
- Transfer endpoints require a valid transport anchor adjacent to an active portal when loaded; unloaded endpoints remain
  provisionally valid.
- A missing dimension invalidates its segment and therefore the route.

This policy avoids loading a route's entire span every time a station worker checks its cache. Consequently, a cached route can remain
provisionally connected until the chunk containing a broken section loads and validation runs again.

`BuildingStation.writeRoute(...)` and `readRoute(...)` persist the segment type, dimension, path, and transfer endpoint data. If a new
segment type is introduced, update both persistence methods as well as reversal, validation, debug serialization, and shipment
consumption.

## Runtime consumers

`ExportData` turns route distance into visible shipment progress. Traversable segments spawn the matching ghost vehicle; zero-distance
handoff segments trigger transition effects; transfer segments remove the vehicle in one dimension and allow it to reappear for the
next local leg. Return shipments use a reversed route.

Other notable consumers are:

- `BuildingStation` and `BuildingOutpost`, which create and cache station connections.
- `CommandTradePath`, which performs an ad hoc same-dimension multimodal search and displays its result.
- `CommandStationRoutes`, which reports cached segmented station routes.
- `TradePathDebugPacket` and `TradePathDebugOverlay`, which serialize and render route segments for client-side inspection.
- The wishing-well outpost ritual, which still calls the lower-level rail-only connection search directly.

## Registries

`TradeDockRegistry` and `TradeInterchangeRegistry` are per-dimension `SavedData` indexes. Their blocks are responsible for adding and
removing immutable positions as block lifecycle events occur. The multimodal search still verifies block states, but accurate removal
keeps candidate selection fast and prevents stale entries from consuming the candidate cap.

## Performance and chunk-loading rules

Pathfinding executes on the server thread, so every search must remain bounded.

- Rail BFS is limited to 10,000 visited positions.
- Road search uses a 10,000-step distance bound and the modal visited-node cap.
- Water search uses `maximumWaterRouteDistance` and the modal visited-node cap.
- Dock and interchange candidate lists are capped before graph construction.
- Cross-dimension linkage-pair attempts are capped and local segment results are cached for one top-level search.

Do not casually make all modes load chunks. Rail chunk loading is caller-controlled because it uses tickets; roads intentionally stop
at unloaded chunks. Water currently loads chunks directly, so increasing its configured distance can materially affect server stalls
and memory pressure.

## Diagnostics

The `trackpath` trace key enables detailed route and rail-search logging. Logs include route IDs, linkage candidates, segment-cache
hits, linkage-pair limits, BFS progress, chunk-load attempts, and elapsed time. Use these identifiers to separate concurrent station
searches.

The `tradepath` command is useful for visualizing a fresh same-dimension route. The station-routes command is better for examining what
a building actually cached, including transfer segments.

## Adding or changing a transport mode

At minimum, review all of the following:

1. Add the segment type and factory/invariants in `TrackRoute`.
2. Implement bounded discovery and cached validation.
3. Add graph nodes or edge rules in `MultimodalRouteConnection`.
4. Define distance, reversal, endpoint, and unloaded-chunk semantics.
5. Update `TrackRouteConnection.validateExistingRoute(...)`.
6. Update `BuildingStation` NBT read/write handling.
7. Update `ExportData` vehicle selection and handoff behavior.
8. Update `TradePathDebugPacket` and `TradePathDebugOverlay`.
9. Add configuration bounds and tracing where searches could become expensive.

Keep `TrackConnectionResult.path` compatibility in mind, but treat `TrackRoute` as the source of truth for all new behavior.
