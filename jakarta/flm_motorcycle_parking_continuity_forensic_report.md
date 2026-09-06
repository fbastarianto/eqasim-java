# Forensic feasibility report: tour-level private-motorcycle parking continuity

**Repository/module:** `eqasim-java/jakarta`  
**Evidence baseline:** branch `jakarta_FLM_home_side_router_2.8.0`, commit `8490bae4742987f26f0e6d806155b4bf98c394e0`  
**Investigation date:** 2026-09-06  
**Scope:** source-level diagnosis and architecture design only; no implementation or simulation was performed

## 1. Executive verdict

### Verdict: C — not suitable for the current PhD resubmission

True same-motorcycle continuity is technically implementable in this dependency set, but it is **not** a small extension of the 2.8.0 home-side filter. A defensible implementation would need to replace or substantially wrap two independent trip-routing orchestrators:

1. DMC's `CumulativeTourEstimator`, so that the selected parking link from an outbound routed PT trip is known **before** the return PT trip is sent to RAPTOR; and
2. startup/general `PlanRouter`, or the surrounding prepare-for-simulation services, so the same tour state is enforced before iteration 0 and whenever generic routing is invoked.

It would also need to integrate ordinary main-mode motorcycle legs, make the paired outbound/return parking location part of the route-choice problem, prevent a mandatory retrieval route from being replaced by SwissRailRaptor's direct-walk fallback, make caches state-aware, and retain a whole-tour post-routing invariant. Those are meaningful changes to routing and alternative generation, followed by substantial model revalidation and probably sensitivity/calibration work.

The source **does** provide useful building blocks:

- `RoutingRequest` attributes can carry immutable request-local state;
- `RaptorStopFinder` can filter access and egress candidates before RAPTOR optimisation;
- routed feeder `NetworkRoute` objects expose start/end link IDs and sometimes a vehicle ID;
- `TourEstimator` and `PersonPrepareForSimAlgorithm` are extension interfaces.

The missing piece is an existing coordinator that owns a motorcycle state across trips and jointly exposes feasible paired feeder alternatives. Neither MATSim, DMC, SwissRailRaptor, eqasim, nor Jakarta 2.8.0 supplies that coordinator in the pinned versions.

**Recommendation for the resubmission:** retain validated 2.8.0, describe its result accurately as a *home-side private-motorcycle feeder availability restriction*, and do not make a same-vehicle parking/retrieval claim. Treat a stateful 2.9.0 as a separate post-resubmission model-development project. A post-estimation continuity constraint may be added later as a defensive test, but it is not an adequate primary behavioural implementation.

## 2. Precise invariant: physical identity versus logical continuity

The proposed claim requires a state machine for each person and each logical private motorcycle. Under the present modelling assumption of one motorcycle per eligible person, a state is at least:

```text
MotorcycleState = (logicalVehicleId, currentParkingLinkId, status)
status in {PARKED, IN_USE}
```

For every complete home-based tour:

```text
initial:  currentParkingLinkId = designatedHomeMotorcycleLink

motorcycle movement L0 -> L1:
    require status = PARKED
    require L0 = currentParkingLinkId
    status = IN_USE while moving
    currentParkingLinkId = L1
    status = PARKED after arrival

PT access with motorcycle:
    apply the movement rule to every motorcycle segment
    record the final motorcycle route end link as parking link P

later PT egress retrieval:
    require first motorcycle route start link = P
    update state along the motorcycle route, normally ending at the home link

end of complete home-based tour:
    require currentParkingLinkId = designatedHomeMotorcycleLink
```

The following are not sufficient definitions of continuity:

- access count equals egress count;
- access and egress use the same transit stop;
- both feeder legs have mode `motorcycle`;
- both routes contain the same person ID;
- QSim happens to use the same vehicle ID after teleporting it.

There are two different claims to distinguish:

1. **MATSim physical-vehicle identity:** the network route/QSim vehicle has one `Id<Vehicle>` and the same `QVehicle` remains parked on a network link until removed there.
2. **Behavioural logical continuity:** the route-choice model permits a person to use the one private motorcycle only at its current link and conditions future routing on that link.

A robust 2.9.0 would need both. The current model has partial physical-vehicle machinery at mobsim time, but no behavioural tour-level state in routing.

## 3. Exact source and version provenance

The inspected checkout was not inferred from class names:

| Item | Exact evidence |
|---|---|
| Branch | `jakarta_FLM_home_side_router_2.8.0` |
| HEAD | `8490bae4742987f26f0e6d806155b4bf98c394e0` (`2.8.0: Add routing-level PT motorcycle home-side restriction`) |
| Jakarta | `jakarta/pom.xml:17-19`, version `2.8.0` |
| MATSim BOM/core | `jakarta/pom.xml:9-15,74-79`, `2026.0-2025w19` |
| DMC | `jakarta/pom.xml:81-86`, `2026.0-2025w19` |
| eqasim core | `jakarta/pom.xml:52-58`, `2.0.0` |
| Java | `jakarta/pom.xml:199-205`, source/target 21 |

The exact locally cached source archives inspected were:

| Source archive | SHA-256 |
|---|---|
| `~/.m2/repository/org/matsim/matsim/2026.0-2025w19/matsim-2026.0-2025w19-sources.jar` | `1a7c8cd3a1e7c5ce4c45387a28ac8fbdc4788489488b48ae3e0078d8f27bf2ff` |
| `~/.m2/repository/org/matsim/contrib/discrete_mode_choice/2026.0-2025w19/discrete_mode_choice-2026.0-2025w19-sources.jar` | `9fbc93c0141d485c8f06ac264eee09a9b03e998529a7a14f6676862211a7da00` |
| `~/.m2/repository/org/eqasim/core/2.0.0/core-2.0.0-sources.jar` | `a7fb5142b63d062308178722f8ed4889dc9881ee2e017cc22b15556665bc4d72` |

`RunSimulation` requires an external `--config-path` and loads that scenario (`src/main/java/org/eqasim/jakarta/RunSimulation.java:37-53`). No Jakarta runtime XML is committed in this module. Consequently, the stated runtime choices (`modelType=Tour`, `tourEstimator=Cumulative`, `tourFinder=ActivityBased`, `FromTripBased`, `VehicleContinuity`, and `performReroute=false`) are treated as audited run evidence. Where vehicle behaviour depends on an external setting, this report gives both the exact pinned default and the source branch selected by that setting instead of inventing an XML value.

No build, package, MATSim run, or simulation was executed for this investigation.

## 4. Current Jakarta 2.8.0 architecture

### 4.1 Home-side routing restriction

`JakartaConfigurator` registers `JakartaHomeSideRoutingModule` after the eqasim/SRR modules (`src/main/java/org/eqasim/jakarta/JakartaConfigurator.java:8-15`). The module:

- registers `AnnotateHomeSideTripAttributes` as a `PersonPrepareForSimAlgorithm`;
- binds the thread-safe aggregate diagnostics; and
- replaces `RaptorStopFinder` with `JakartaHomeSideRaptorStopFinder` while preserving `DefaultRaptorStopFinder` as its delegate (`src/main/java/org/eqasim/jakarta/routing/JakartaHomeSideRoutingModule.java:10-23`).

The annotation traverses every plan and every substantive `TripStructureUtils.Trip`, placing origin and destination activity types into the origin activity's trip-attribute map (`src/main/java/org/eqasim/jakarta/routing/AnnotateHomeSideTripAttributes.java:17-28`). This matches the pinned `Trip.getTripAttributes()` implementation, which returns the preceding/origin activity attributes (`matsim...sources.jar!/org/matsim/core/router/TripStructureUtils.java:382-430`).

The stop finder delegates normal candidate production, scans all feeder `Leg` elements for motorcycle, and removes directionally invalid candidates before RAPTOR chooses a path (`src/main/java/ch/sbb/matsim/routing/pt/raptor/JakartaHomeSideRaptorStopFinder.java:46-100,103-129`). It considers only endpoint type and direction. It does **not**:

- read a previous trip's selected parking link;
- require a future retrieval;
- compare access and egress link IDs;
- reconcile embedded feeders with ordinary motorcycle trips; or
- carry any mutable tour state.

This is intentional and consistent with the 2.8.0 claim.

### 4.2 Defensive DMC layers

`NoMotorcycleEgressExceptHome` runs after a DMC trip candidate is routed. It finds the first and last actual `pt` leg, scans all legs on each side, and applies the home-side rule (`src/main/java/org/eqasim/jakarta/mode_choice/constraints/NoMotorcycleEgressExceptHome.java:61-175`). It validates one trip at a time and ignores the supplied previous-candidate list. It does not match a retrieval to an earlier parking event.

`JakartaPTUtilityEstimator` retains an older adjacency-based negative-infinity guard (`src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaPTUtilityEstimator.java:53-110`). It is explicitly defensive, incomplete for intervening feeder legs, and not involved in startup routing.

### 4.3 Candidate production inside SwissRailRaptor

For `CalcLeastCostModePerStop`, `DefaultRaptorStopFinder` loops over every configured intermodal parameter set and produces candidates (`matsim...sources.jar!/ch/sbb/matsim/routing/pt/raptor/DefaultRaptorStopFinder.java:136-162`). For each candidate stop it calls the configured feeder `RoutingModule`:

- access: substantive origin facility to the stop-side facility;
- egress: stop-side facility to substantive destination facility

(`DefaultRaptorStopFinder.java:165-228`). It may add a walk leg between a configured changed-link facility and the actual transit stop (`DefaultRaptorStopFinder.java:229-257`), then stores the complete feeder route parts in `InitialStop` (`DefaultRaptorStopFinder.java:258-270`).

`SwissRailRaptor.calcRoute` asks for all access and egress candidates before calling the core least-cost router (`matsim...sources.jar!/ch/sbb/matsim/routing/pt/raptor/SwissRailRaptor.java:81-97`). Thus access and egress of one request are generated from the same static request attributes; neither side is selected and fed back into generation of the other.

## 5. Current built-in vehicle-continuity findings

### 5.1 `tourConstraint:VehicleContinuity`

The DMC `VehicleTourConstraint` is a **main-candidate-mode** constraint. For each configured restricted mode, it finds the first and last index in `List<String> modes`, compares substantive activity locations to the vehicle base, and advances a local `currentLocationId` only when a later trip's candidate mode equals that restricted mode (`discrete_mode_choice...sources.jar!/org/matsim/contribs/discrete_mode_choice/components/constraints/VehicleTourConstraint.java:36-130`). Its `validateAfterEstimation` returns `true` without inspecting `TourCandidate` routes (`VehicleTourConstraint.java:133-137`).

For a PT candidate containing an embedded motorcycle leg, the candidate mode is `pt`, not `motorcycle`. The constraint therefore does not see or move a motorcycle state.

### 5.2 `tripConstraint:VehicleContinuity`

`VehicleTripConstraint` reconstructs a restricted mode's location from the last occurrence of that **main mode** in `previousModes`; it compares substantive activity/facility/link locations before estimation (`.../components/constraints/VehicleTripConstraint.java:41-121`). Its own documentation says it is untested and potentially faulty (`VehicleTripConstraint.java:18-35`), and `validateAfterEstimation` also returns `true` (`VehicleTripConstraint.java:123-127`). It cannot see embedded feeder legs.

In a tour model, trip constraints run only through `FromTripBased`: `TourFromTripConstraint` flattens previous mode lists for before-estimation checks and previous `TripCandidate` lists for after-estimation checks (`.../model/constraints/TourFromTripConstraint.java:30-65`). This makes routed elements available to a custom trip constraint after estimation, but it does not make them available to the built-in vehicle constraint because that method is a no-op.

### 5.3 eqasim and Jakarta variants

`EqasimVehicleTourConstraint` repeats the same mode-index/activity-location design and has a no-op post-estimation method (`core-2.0.0-sources.jar!/org/eqasim/core/simulation/mode_choice/constraints/EqasimVehicleTourConstraint.java:41-133`).

Jakarta's `VehicleTourConstraintWithCarPassenger` likewise operates on the main mode strings and activity locations; if the mode chain contains `car_passenger`, it returns true immediately (`src/main/java/org/eqasim/jakarta/mode_choice/constraints/VehicleTourConstraintWithCarPassenger.java:43-124`). It does not inspect routed PT feeders.

### 5.4 Binding names

DMC maps both trip and tour component name `VehicleContinuity` to the two built-in factories, and maps `FromTripBased` to `TourFromTripConstraintFactory` (`.../modules/ConstraintModule.java:40-65`). The composite invokes all children as an AND operation (`.../model/constraints/CompositeTourConstraint.java:29-50`). These bindings provide no hidden state beyond the implementations above.

### 5.5 Can an existing constraint be extended?

A new `validateAfterEstimation` constraint can safely reuse the DMC extension framework as a **defensive invariant**, because `RoutedTripCandidate.getRoutedPlanElements()` exposes every selected feeder leg (`.../model/trip_based/candidates/RoutedTripCandidate.java:12-14`) and `TourCandidate` exposes the trip candidates. It cannot be the primary implementation because constraints do not reroute and do not create a missing paired route. Extending the existing before-estimation vehicle constraints cannot solve the feeder case: before estimation they receive only main mode strings, not the yet-unknown RAPTOR parking link.

## 6. DMC tour-routing call flow

### 6.1 Exact current flow

```text
DiscreteModeChoiceStrategyProvider.get()
  -> DiscreteModeChoiceReplanningModule
  -> DiscreteModeChoiceAlgorithm.run(plan)
       -> TripListConverter.convert(plan)
       -> TourBasedModel.chooseModes(person, trips, random)
            -> ActivityTourFinder.findTours(trips)
            -> for each tour, enumerate main-mode chains
                 -> TourConstraint.validateBeforeEstimation(... main modes ...)
                 -> CumulativeTourEstimator.estimateTour(...)
                      -> new local tripCandidates list
                      -> for each trip in order:
                           update departure time from earlier candidate duration
                           -> CachedTripEstimator (if configured)
                           -> EqasimUtilityEstimator / AbstractTripRouterEstimator
                                -> TripRouter.calcRoute(main mode, OD, time,
                                                        person, tripAttributes)
                                     -> pt RoutingModule
                                     -> SwissRailRaptor
                                          -> RaptorStopFinder ACCESS
                                          -> RaptorStopFinder EGRESS
                                          -> RAPTOR least-cost route
                                          -> possible direct-walk replacement
                                -> DefaultRoutedTripCandidate
                      -> DefaultTourCandidate
                 -> TourConstraint.validateAfterEstimation(... routed candidate ...)
                 -> UtilitySelector.addCandidate(...)
            -> select one tour candidate
       -> insert the selected RoutedTripCandidate elements into the plan
       -> performReroute=false: route-consistency check only
```

Source anchors:

- strategy composition and the `performReroute` branch: `.../replanning/DiscreteModeChoiceStrategyProvider.java:59-71`;
- conversion and insertion of exact routed elements: `.../replanning/DiscreteModeChoiceAlgorithm.java:46-72`;
- tour enumeration, constraints, estimation, and selection: `.../model/tour_based/TourBasedModel.java:62-143`;
- sequential timing/estimation loop: `.../components/estimators/CumulativeTourEstimator.java:36-62`;
- actual `TripRouter` call with `trip.getTripAttributes()`: `.../components/estimators/AbstractTripRouterEstimator.java:57-75`;
- eqasim utility ignores the `previousTrips` argument and scores only the current routed elements: `core-2.0.0-sources.jar!/org/eqasim/core/simulation/mode_choice/utilities/EqasimUtilityEstimator.java:18-45`.

### 6.2 Answer to the central A/B question

The stock implementation is **sequential for departure-time propagation, but independent for motorcycle state**.

`CumulativeTourEstimator` passes the already routed candidates of the current tour to `TripEstimator` (`CumulativeTourEstimator.java:39-58`), so the interface theoretically permits a custom estimator to inspect them. However:

- `AbstractTripRouterEstimator` routes the current trip using only current OD, time, person, and current trip attributes;
- `EqasimUtilityEstimator` does not use `previousTrips`;
- `CumulativeTourEstimator` ignores its `preceedingTours` parameter; and
- no component parses an outbound PT result into a parking link and adds it to the return request.

Therefore, in the current Jakarta model, outbound PT access choosing parking link `P` cannot condition return routing before RAPTOR. The return request is routed as if motorcycle were independently available under the 2.8.0 home-side rule.

### 6.3 Caching consequence

`CachedTripEstimator` keys its cache only by `(mode, DiscreteModeChoiceTrip)` and does not include `previousTrips` or any vehicle state (`.../model/estimation/CachedTripEstimator.java:21-49`). Any future state-dependent `pt` or `motorcycle` estimation must either:

- exclude those modes from the existing cache; or
- replace the cache with a key including logical vehicle ID, current parking link, required retrieval link, and any other feasibility state.

Otherwise a route calculated with the motorcycle at `P` can be reused when it is at `Q`.

## 7. Startup/general-routing call flow

### 7.1 Exact current flow

```text
controller initialization
  -> PrepareForSimImpl.run()
       -> create one vehicle per person/network-or-main mode (subject to config)
       -> adapt legacy routing modes
       -> all PersonPrepareForSimAlgorithm hooks
            -> AnnotateHomeSideTripAttributes (all plans/trips)
       -> parallel PersonPrepareForSim
            -> inspect every plan
            -> if any activity location or route requires recomputation:
                 -> PlanRouter.run(plan)
                      -> snapshot substantive trips
                      -> for each trip in plan order:
                           identify current routing mode
                           advance TimeTracker
                           -> TripRouter.calcRoute(... oldTrip.getTripAttributes())
                                -> PT RoutingModule -> SwissRailRaptor
                                -> 2.8.0 home-side RaptorStopFinder
                           -> insert routed elements
                           -> advance TimeTracker
  -> selected plan reaches iteration-0 mobsim

before each mobsim
  -> PrepareForMobsimImpl.run()
       -> PersonPrepareForSim
            -> if a route is missing/invalid, PlanRouter reroutes the whole plan
```

Source anchors:

- initial vehicle creation, custom prepare hooks, and generic plan routing: `matsim...sources.jar!/org/matsim/core/controler/PrepareForSimImpl.java:167-196,221-310`;
- all-plan validation and the whole-plan reroute trigger: `.../org/matsim/core/population/algorithms/PersonPrepareForSim.java:104-171`;
- plan-order loop and trip insertion: `.../org/matsim/core/router/PlanRouter.java:74-98`;
- per-mobsim repetition: `.../org/matsim/core/controler/PrepareForMobsimImpl.java:75-111`.

### 7.2 Is `PlanRouter` stateful?

`PlanRouter` is sequential only in iteration order and its `TimeTracker`. Its call at lines 83-90 receives no earlier route, tour object, or vehicle-location state. After the call it inserts the new elements and advances time, but there is no callback that converts those elements into attributes for the next trip. It also snapshots all `Trip` objects before mutation (`PlanRouter.java:75-79`).

The `Person` parameter technically lets a routing module inspect a plan, but that is not a reliable state channel:

- `PlanRouter.run(Person)` routes **every plan**, while `person.getSelectedPlan()` identifies only one;
- repeated OD pairs and departure times are ambiguous;
- DMC alternatives are not yet inserted when they are routed;
- scanning a mutating plan would couple a routing module to call order and plan identity; and
- concurrent replanning would make person-attribute or thread-local state fragile.

### 7.3 Available startup extension points

`PersonPrepareForSimAlgorithm` is an official hook, but all such hooks run **before** the standard `PersonPrepareForSim/PlanRouter` pass (`PrepareForSimImpl.java:178-196`). A custom hook could pre-route an already well-located plan and thereby make the later validator a no-op. It cannot robustly guarantee that result for arbitrary plans: if any activity needs link assignment or any route remains invalid, `PersonPrepareForSim` invokes generic `PlanRouter` for the entire plan.

`PlanRouter` itself is `final` (`PlanRouter.java:48`), as are `PrepareForSimImpl` and `PrepareForMobsimImpl` (`PrepareForSimImpl.java:63`; `PrepareForMobsimImpl.java:45`). MATSim binds the two service interfaces to those implementations (`matsim...sources.jar!/org/matsim/core/controler/NewControlerModule.java:43-47`). Jakarta can override the interfaces, but a fully robust replacement must reproduce or delegate carefully around activity-link assignment, vehicle creation, legacy routing-mode adaptation, locking, parallel execution, and per-mobsim validation. This is not a narrow feeder filter.

### 7.4 Could startup disable motorcycle feeders?

It is technically easy to tag startup requests and remove all motorcycle `InitialStop` candidates. That makes startup continuity vacuously true, while DMC later introduces paired motorcycle feeders. It is not recommended as the authoritative 2.9.0 model because:

- persons not selected for DMC retain an asymmetrically restricted initial plan;
- iteration-0 congestion, scores, and plan competition start from a different choice set;
- DMC weight and finite iteration count affect whether motorcycle feeders ever enter;
- generic later rerouting would need the same special handling; and
- it no longer represents one shared routing rule across startup and DMC.

It could be a controlled experimental initialization only if its bias were quantified against a tour-aware startup router.

## 8. Can parking state be carried before return-trip RAPTOR optimisation?

### 8.1 What is possible

The request plumbing can carry state cleanly. `TripRouter.calcRoute` creates a `RoutingRequest` with the supplied `Attributes` and forwards it to the modal routing module (`matsim...sources.jar!/org/matsim/core/router/TripRouter.java:146-179`). `DefaultRoutingRequest` keeps that exact attribute object (`.../org/matsim/core/router/DefaultRoutingRequest.java:8-28`), and `RaptorStopFinder.findStops` receives it together with `Direction.ACCESS` or `EGRESS` (`.../ch/sbb/matsim/routing/pt/raptor/RaptorStopFinder.java:33-38`). Jakarta 2.8.0 already proves this channel works for immutable endpoint metadata.

A custom tour coordinator could therefore:

1. route outbound PT;
2. inspect the selected routed elements and derive parking link `P`;
3. create a fresh request-attribute object containing `P` and retrieval requirements;
4. route the later return PT trip; and
5. let an extended stop finder remove motorcycle egress candidates whose first motorcycle route begins anywhere other than `P`.

This is the cleanest low-level feasibility boundary.

### 8.2 What is not currently possible

No current component performs steps 2-4. `Trip.getTripAttributes()` is the mutable origin activity map, not a per-alternative state object. Mutating it temporarily for each DMC alternative would risk leakage across alternatives and copied plans. A robust design must construct a request-local copy rather than use global/person/static/thread-local state.

### 8.3 Restricting to `P` is necessary but not sufficient

When retrieval is required, merely *allowing* a motorcycle egress candidate at `P` still permits RAPTOR to choose walk or `mcodt`, leaving the motorcycle stranded. Filtering all non-motorcycle egress candidates is also insufficient because `SwissRailRaptor.calcRoute` constructs a direct walk independently and replaces the found PT route if its adjusted cost is lower (`SwissRailRaptor.java:93-105`). The walk-only result bypasses the `InitialStop` feasibility set.

A robust mandatory-retrieval request therefore needs a PT routing wrapper/variant that:

- makes motorcycle retrieval at `P` a hard route feasibility condition;
- does not permit direct walk to replace that route for this request; and
- returns a clear infeasible result if no PT-plus-retrieval route exists.

Rejecting a walk-only result after it has replaced a feasible PT route loses the feasible route and is behaviourally wrong.

### 8.4 The parking link is endogenous

The cheapest outbound PT access route may park at `P`, while a slightly more expensive outbound route parking at `Q` gives a much better or the only feasible return. Standard RAPTOR exposes one least-cost route for a normal request, and the DMC mode-chain generator creates one `pt` alternative per trip in a main-mode chain. A sequential greedy rule—choose outbound `P`, then constrain return to `P`—enforces continuity but does not necessarily choose the best feasible **paired** tour.

For behavioural correctness, the tour estimator must either:

- enumerate relevant outbound parking-link alternatives and evaluate their conditioned return routes; or
- implement an equivalent dynamic programme/joint search over `(trip index, motorcycle location)`.

That paired search, not the state variable itself, is the main reason the work is not low risk.

## 9. Exact parking-location representation

### 9.1 Recommended authoritative key: motorcycle network-route link ID

For PT access, define parking location as:

```text
P = endLinkId of the last actual motorcycle NetworkRoute
    before the first actual pt leg
```

For PT egress retrieval, require:

```text
startLinkId of the first actual motorcycle NetworkRoute
after the last actual pt leg == P
```

Scan all feeder plan elements, not only the leg adjacent to PT. This correctly handles:

```text
home -> motorcycle -> walk -> pt
pt -> walk -> motorcycle -> home
```

`Leg` exposes its `Route`, and all `Route` implementations expose start/end link IDs (`matsim...sources.jar!/org/matsim/api/core/v01/population/Leg.java:25-41`; `.../org/matsim/api/core/v01/population/Route.java:30-48`). `NetworkRoute` additionally exposes its network path and vehicle ID (`.../org/matsim/core/population/routes/NetworkRoute.java:37-87`).

The stop finder source demonstrates why a transit stop is not the parking key. A stop may be represented by a `ChangedLinkFacility` whose link comes from a configured stop attribute (`DefaultRaptorStopFinder.java:202-209`); SwissRailRaptor then appends/prepends a walk transfer between that link and the actual stop link (`DefaultRaptorStopFinder.java:229-255`). Thus motorcycle parking can occur before a walk to the stop.

### 9.2 Why alternatives are weaker

| Candidate key | Finding |
|---|---|
| Transit stop ID | Incorrect as the authority when motorcycle is parked before a walk or when a changed stop-side link is used. Useful only as auxiliary QC. |
| Facility ID | Substantive and stop facilities do not necessarily identify the actual network link on which QSim parks the vehicle. `LocationUtils` preferring facility IDs is adequate for main-mode tour continuity but not exact feeder parking. |
| Coordinate | Floating-point/tolerance ambiguity; two sides or links can share/approximate a coordinate; not the object used by network vehicle departure. |
| Start/end link pair | Valuable route-transition provenance, but state after a movement is the end link. Store the pair for QC; key continuity on prior end versus next start. |
| Motorcycle route end/start link | Best available and operationally aligned representation in this pinned architecture. |

### 9.3 Relation to QSim parking

`BasicPlanAgentImpl.getDestinationLinkId()` returns the current leg route's end link (`matsim...sources.jar!/org/matsim/core/mobsim/qsim/agents/BasicPlanAgentImpl.java:352-362`). At vehicle arrival, `AbstractQLink` adds the `QVehicle` to that link's parked-vehicle map (`.../qsim/qnetsimengine/AbstractQLink.java:167-200`). On a later network departure, QSim removes the planned vehicle ID from the departure link (`.../qsim/qnetsimengine/NetworkModeDepartureHandlerDefaultImpl.java:66-73`). Link-ID equality is therefore the exact physical lookup condition.

### 9.4 Required fail-closed details

A future inspector should fail closed when a private-motorcycle feeder has:

- no `Route`;
- a route with null start or end link;
- a non-`NetworkRoute` despite motorcycle being a QSim/network mode;
- multiple non-contiguous motorcycle blocks that cannot be interpreted unambiguously; or
- a route vehicle ID inconsistent with the person's canonical motorcycle vehicle ID.

It should retain every start/end transition for diagnostics, even though the final access end link and first egress start link are the pairing key.

## 10. Actual versus logical motorcycle identity

### 10.1 What exists in MATSim

For `vehiclesSource=defaultVehicle` or `modeVehicleTypesFromVehiclesData`, `PrepareForSimImpl` creates a vehicle for every network/main mode for every person and stores a mode-to-vehicle map on the person (`PrepareForSimImpl.java:171-174,221-258`). The standard generated ID is `personId + "_" + mode` (`matsim...sources.jar!/org/matsim/vehicles/VehicleUtils.java:91-101`). Jakarta's config adapter adds `motorcycle` to both QSim main modes and routing network modes (`src/main/java/org/eqasim/jakarta/scenario/RunAdaptConfig.java:50-65,88-93`).

The modern `NetworkRoutingInclAccessEgressModule` obtains `VehicleUtils.getVehicleId(person, leg.getMode())`, uses the `Vehicle` in least-cost routing, and writes that ID into the `NetworkRoute` (`matsim...sources.jar!/org/matsim/core/router/NetworkRoutingInclAccessEgressModule.java:388-440`). The deprecated pure `NetworkRoutingModule`, selected when `routing.accessEgressType=none`, deliberately passes null vehicle and does not set a route vehicle ID (`.../org/matsim/core/router/NetworkRoutingModule.java:98-127`). The pinned `RoutingConfigGroup` default is `accessEgressType=none` (`.../org/matsim/core/config/groups/RoutingConfigGroup.java:73-95`), and Jakarta's committed adapter does not change it. Because the production XML is external, candidate-time vehicle-ID presence cannot be asserted from this checkout alone.

At mobsim preparation, `PopulationAgentSource` fills a missing network-route vehicle ID from the person's mode mapping, inserts the first such vehicle once, and places it at an initial link (`matsim...sources.jar!/org/matsim/core/mobsim/qsim/agents/PopulationAgentSource.java:87-195`). Therefore the selected plan will normally use one actual QSim motorcycle ID per person/mode even when the feeder candidate lacked that ID during routing.

### 10.2 Why this still does not prove continuity

The `InitialStop` API has only package-visible stop, cost, time, distance, mode, and plan-elements fields; it has no vehicle field (`matsim...sources.jar!/ch/sbb/matsim/routing/pt/raptor/InitialStop.java:33-58`). A vehicle ID can only be recovered by inspecting a feeder `NetworkRoute`, and may be null at candidate time as above.

More importantly, QSim's pinned default `vehicleBehavior` is `teleport` (`matsim...sources.jar!/org/matsim/core/config/groups/QSimConfigGroup.java:123-129`). If an agent requests the same vehicle on the wrong link, `NetworkModeDepartureHandlerDefaultImpl` looks it up globally and teleports it to the requested link (`.../qsim/qnetsimengine/NetworkModeDepartureHandlerDefaultImpl.java:70-90,104-123`). This prevents duplication of the Java `QVehicle` object but explicitly permits physical teleportation. `wait` or `exception` detect wrong-location use later; they do not create a feasible route-choice alternative.

Accordingly:

- **actual ID:** usually available by mobsim and sometimes during route generation;
- **same parked physical object:** QSim can maintain it if routes are consistent and teleport behaviour is not invoked;
- **tour-level behavioural availability:** absent;
- **defensible modelling identity for routing:** use an explicit logical one-motorcycle-per-person state, verify any route vehicle ID against it, and make QSim `exception` a future defensive configuration/QC condition rather than the primary solver.

## 11. Post-estimation constraint option

A custom `TourConstraint.validateAfterEstimation` has enough routed information to parse every current tour trip and the previously selected tour candidates (`.../model/tour_based/TourConstraint.java:14-37`; `TourFromTripConstraint.java:48-65`). It can enforce:

- parking creates state `P`;
- retrieval starts at `P`;
- no use from another link;
- no duplicate/unmatched transition;
- ordinary main-mode motorcycle uses the same state; and
- a complete tour ends at home.

But `TourBasedModel` calls that method only **after** `TourEstimator` has already produced a single routed candidate for the main-mode chain (`TourBasedModel.java:86-105`). If return RAPTOR independently chooses motorcycle at `Q`, the constraint rejects the whole tour candidate even if a feasible route via `P` exists. Since that route via `P` was never generated, the selector cannot choose it.

Consequences include:

- feasible PT mode chains classified as unavailable;
- systematic penalty against PT-plus-motorcycle tours;
- utility distortion because route choice is conditioned by rejection rather than evaluated with the feasible paired route;
- sensitivity to which independent route happened to be cheapest; and
- possible fallback behaviour.

There is an additional source-level caveat: with fallback `INITIAL_CHOICE`, `TourBasedModel` calls `createFallbackCandidate` after no candidate is selectable and does not rerun the constraint on that fallback (`TourBasedModel.java:107-125,146-150`). `IGNORE_AGENT` similarly estimates initial modes directly (`TourBasedModel.java:158-168`). A post-estimation constraint alone is therefore not even an unconditional final invariant under all fallback settings.

**Assessment:** appropriate as a defensive invariant after a stateful router, never as the primary behavioural mechanism.

## 12. Ranked stateful-routing options

| Rank | Option | State known before return RAPTOR? | Behavioural correctness | Startup coverage | Risk/suitability |
|---:|---|---|---|---|---|
| 1 | Shared tour-state engine with paired parking-link enumeration; DMC `TourEstimator` adapter plus startup tour-router adapter | Yes | Highest. Can evaluate outbound/return pairs and unify main-mode MC | Yes, with a separate prepare adapter using the same core | Technically strongest, but high code/validation burden; post-resubmission project |
| 2 | Custom/wrapped cumulative estimator: route greedily in sequence, pass selected `P` via request attributes, constrain later egress | Yes | Enforces continuity for the chosen `P`, but can miss a better/only feasible pair at `Q`; must handle mandatory retrieval/direct walk | No without a second startup adapter | Moderate/high behavioural risk; insufficient for a strong optimality claim unless paired-choice sensitivity is negligible and proven |
| 3 | Pre-enumerate paired outbound/return feeder alternatives outside the normal DMC mode-chain enumeration | Yes | Potentially correct; lets tour utility choose among `P` values | Requires analogous startup selection | Very high custom-RAPTOR/API bridge and computational cost risk |
| 4 | Custom PT `RoutingModule` plus extended 2.8 stop finder, driven by request-local state | Yes, if a coordinator supplies state | Correct low-level filter; cannot by itself own tour state or choose among parking pairs | Shared low-level component only | Necessary supporting layer, not a complete solution |
| 5 | Whole-tour post-estimation `TourConstraint` | No | Detects inconsistency but rejects feasible alternatives routed via the wrong link | No | Good defensive invariant only |
| 6 | Custom constraint that reroutes rejected trips | Partly | Constraint API has no clean replacement/selection protocol; risks utility/time inconsistency | No | Fragile; not recommended |
| 7 | Person attributes, static maps, or thread-local “current motorcycle link” inside the PT router | Accidentally | Alternative-order dependent, leaks across plans/tours/threads and cannot represent simultaneous candidates | Superficially | Unacceptable production design |
| 8 | Disable startup motorcycle feeders and introduce them only via DMC | DMC only | Continuity can be made vacuous at startup but initial choice set is biased | Technically yes, behaviourally asymmetric | Experimental fallback, not authoritative architecture |
| 9 | Rely on QSim vehicle ID plus `wait`/`exception` | No | Detects failure at departure; does not route via the parked motorcycle | Applies only in mobsim | QC backstop only |

### Interaction with walk and `mcodt`

Every viable routing-level design should leave walk and `mcodt` candidate generation and costs untouched except when a retrieval is **mandatory** to close an already parked private-motorcycle state. At that point, a walk/`mcodt` egress may still be a feasible intermediate trip if a later retrieval remains possible, but it cannot be accepted as the final home return of a complete tour while the private motorcycle remains at `P`.

## 13. Minimum defensible architecture if work resumes

This is the smallest architecture this investigation considers robust enough for a same-vehicle claim; it is not a recommendation to implement during the current resubmission.

### 13.1 Shared pure domain layer

Add immutable, request-independent types:

- `MotorcycleLocationState`: logical vehicle ID, designated home link, current parking link, status;
- `MotorcycleRouteTransition`: start link, end link, vehicle ID, trip/tour indices, feeder side or ordinary-main-mode role;
- `MotorcycleRouteInspector`: parses all routed elements, actual PT boundaries, all motorcycle blocks, and fail-closed diagnostics;
- `MotorcycleContinuityStateMachine`: pure transition/validation functions; no static, person-attribute, or thread-local state;
- `MotorcycleParkingRoutingAttributes`: namespaced immutable request keys such as current link, required retrieval link, and request role.

### 13.2 RAPTOR boundary layer

Extend or compose `JakartaHomeSideRaptorStopFinder` so the two restrictions are conjunctive:

```text
2.8 home-side eligibility
AND
2.9 current-link / required-retrieval-link eligibility
```

At access, a motorcycle route may start only at the logical current link. At egress retrieval, its first motorcycle network leg must start at the stored `P`. Valid candidate objects and costs should remain unchanged.

Because `InitialStop` plan elements are package-visible with no accessor (`InitialStop.java:33-58`), the current small compatibility technique—placing the Jakarta bridge in `ch.sbb.matsim.routing.pt.raptor`—would still be required. Do not use reflection.

Add a narrowly scoped PT routing wrapper/bridge that can enforce “retrieval required” against the direct-walk branch and expose or enumerate feasible parking-link-specific routes. This is the point at which exact API prototyping is required; `RaptorRoute.getParts()` is public, but a route part's feeder `planElements` remains package-visible (`matsim...sources.jar!/ch/sbb/matsim/routing/pt/raptor/RaptorRoute.java:36-45,107-142`).

### 13.3 DMC adapter

Register a custom `TourEstimator`, not merely a constraint. It must:

1. begin each complete home-based tour with the selected prior state (normally HOME);
2. route trips in time order;
3. pass a fresh attributes copy to every state-dependent route request;
4. parse each returned candidate and advance state;
5. enumerate or otherwise optimise over parking-link states where alternatives exist;
6. score the exact conditioned routed elements with the existing Jakarta/eqasim utility definitions;
7. include ordinary main-mode motorcycle transitions; and
8. return only candidates whose terminal state is valid.

The existing `CumulativeTourEstimator` is short but cannot simply be subclassed into correctness: its state is only a candidate list and it ignores previous tours. Its `TripEstimator` delegate may also be wrapped by `CachedTripEstimator`. A custom implementation should reuse the current `UtilityEstimator`, epsilon, penalty, time-tracking, and cost components through composition, not duplicate calibration constants.

### 13.4 Startup and prepare-for-mobsim adapter

Use the same pure state machine and route-request layer to route each fixed-mode input plan tour-aware. Two implementation paths need a prototype comparison:

- **narrow hook path:** a `PersonPrepareForSimAlgorithm` pre-routes every validly located plan so standard `PersonPrepareForSim` finds no missing route; then a strict verifier proves the generic pass did not alter continuity;
- **robust service path:** override `PrepareForSim` and `PrepareForMobsim` with Jakarta wrappers that retain MATSim normalization/vehicle setup but substitute the stateful plan router.

The hook path has less code but depends on activity locations already being valid. The service path covers all cases but approaches a fork of `PrepareForSimImpl` because its useful steps are private and the class is final. Neither is an A-level small extension.

### 13.5 Final invariant and QSim backstop

Retain/add:

- a post-estimation whole-tour continuity constraint;
- `NoMotorcycleEgressExceptHome` as the 2.8 home-side defensive invariant;
- `performReroute=false`;
- a final selected-plan verifier before mobsim;
- QSim `vehicleBehavior=exception` for validation runs; and
- event/plan QC proving no teleport, stranding, or duplicate use.

## 14. Exact classes/files a future implementation would affect

No files were changed during this investigation other than this report. A future implementation would likely require the following.

### Existing Jakarta files to modify

| File | Prospective responsibility |
|---|---|
| `src/main/java/ch/sbb/matsim/routing/pt/raptor/JakartaHomeSideRaptorStopFinder.java` | Compose home-side and state/location candidate feasibility; preserve the pinned package bridge. |
| `src/main/java/org/eqasim/jakarta/routing/JakartaHomeSideRoutingModule.java` | Bind state-aware stop finder, PT wrapper, startup adapter, verifier, and diagnostics with correct override order. |
| `src/main/java/org/eqasim/jakarta/mode_choice/JakartaModeChoiceModule.java` | Register the custom tour estimator and defensive tour constraint. |
| `src/main/java/org/eqasim/jakarta/JakartaConfigurator.java` | Register any separate continuity module after SRR/eqasim bindings if it is not folded into the existing routing module. |
| `src/main/java/org/eqasim/jakarta/routing/HomeSideTripAttributes.java` | Prefer leaving 2.8 keys stable; only share canonical home semantics. Put state keys in a separate helper. |
| `pom.xml` | Future 2.9.0 version/comment only after implementation approval; no dependency change needed. |

### Likely new production classes

Suggested names are architectural, not an implementation prescription:

- `org.eqasim.jakarta.routing.motorcycle.MotorcycleLocationState`
- `org.eqasim.jakarta.routing.motorcycle.MotorcycleRouteTransition`
- `org.eqasim.jakarta.routing.motorcycle.MotorcycleRouteInspector`
- `org.eqasim.jakarta.routing.motorcycle.MotorcycleContinuityStateMachine`
- `org.eqasim.jakarta.routing.motorcycle.MotorcycleParkingRoutingAttributes`
- `org.eqasim.jakarta.routing.motorcycle.JakartaMotorcycleContinuityTourEstimator`
- `org.eqasim.jakarta.routing.motorcycle.JakartaStatefulPlanRouter` or `JakartaMotorcycleContinuityPrepareForSim`
- `org.eqasim.jakarta.routing.motorcycle.MotorcycleContinuityTourConstraint`
- `org.eqasim.jakarta.routing.motorcycle.MotorcycleContinuityVerifier`
- `ch.sbb.matsim.routing.pt.raptor.JakartaMotorcycleContinuityRaptorBridge` for only the package-private SRR access that is unavoidable in this pinned API

Corresponding focused tests would be needed under both `src/test/java/org/eqasim/jakarta/...` and `src/test/java/ch/sbb/matsim/routing/pt/raptor/...`.

No change to the cached MATSim, DMC, SwissRailRaptor, or eqasim dependency source should be made.

## 15. Interaction with the 2.8.0 safeguards

The 2.8.0 layers remain sensible but do not become continuity layers:

- `JakartaHomeSideRaptorStopFinder`: preserve its rule, then add the current-link condition. Home-side eligibility and physical availability answer different questions.
- `NoMotorcycleEgressExceptHome`: retain as a defensive DMC invariant. Add a separate whole-tour continuity invariant instead of changing its historically scoped class into a state machine.
- `JakartaPTUtilityEstimator`: keep calibrated scoring behaviour; do not use its adjacency guard to carry state.
- aggregate home-side diagnostics: retain and add separate parking/retrieval/state-transition counters.
- post-run structural QC: extend from endpoint feasibility to person/tour/link/vehicle matching.

The current smoke-test observation of zero trips with motorcycle on both sides of the **same PT trip** is neither required nor sufficient for continuity. The relevant pair normally spans different substantive trips in one home-based tour.

## 16. `performReroute` and general rerouting

With `performReroute=false`, DMC inserts the exact routed candidates it estimated and follows them only with a missing-route check (`DiscreteModeChoiceStrategyProvider.java:59-71`; `CheckConsistentRoutingReplanningModule.java:23-40`). This remains a necessary configuration requirement.

With `performReroute=true`, DMC appends MATSim `ReRoute`, whose module creates a generic `PlanRouter` (`matsim...sources.jar!/org/matsim/core/replanning/modules/ReRoute.java:58-65`). That router does not know the selected paired parking state and can independently replace both PT trips. It can therefore destroy a valid pair without rerunning DMC constraints.

The same concern applies to:

- an explicit generic `ReRoute` strategy;
- `TimeAllocationMutatorReRoute` or another strategy containing generic routing; and
- `PrepareForMobsimImpl` if any missing route causes a whole-plan reroute.

A 2.9.0 would need either to prohibit all generic routing paths for affected plans or bind every such path to the stateful tour router. Turning `performReroute=true` is not a route-refresh solution.

## 17. Ordinary main-mode motorcycle interaction

A PT-feeder-only state is invalid. Consider:

```text
home --MC access/PT--> work      motorcycle parked at P
work --ordinary motorcycle--> shop
```

The existing `VehicleContinuity` main-mode constraint sees only the second candidate as `motorcycle`; it does not know that the PT candidate moved the motorcycle to `P`. Conversely, a feeder-only constraint would not see an ordinary motorcycle trip that moved the vehicle.

The unified state machine must parse both:

- every motorcycle `NetworkRoute` embedded before/after actual PT legs; and
- every ordinary main-mode motorcycle candidate, including any access/egress walk inserted around its network leg.

For an ordinary motorcycle trip, the authoritative movement is again the motorcycle network route's start/end link, not automatically the substantive activities' facility IDs. The trip is feasible only when its first motorcycle route starts at the stored current link. After it ends, state moves to its final motorcycle route end link. Existing `VehicleTourConstraint` may remain as an inexpensive main-mode defence, but it cannot own the unified state.

This also exposes a current runtime backstop: because QSim normally uses the same `personId_motorcycle` vehicle, wrong-link use can invoke the pinned default teleport behaviour. A same-vehicle thesis claim requires zero such invocations, not merely stable route IDs.

## 18. Multiple tours and complex activity chains

With `tourFinder=ActivityBased`, DMC binds `ActivityTourFinder` (`.../modules/TourFinderModule.java:25-50`). Its default anchor types are exactly `home` (`.../modules/config/ActivityTourFinderConfigGroup.java:14-49`). It collects matching substantive activities and ends the current tour when a trip's destination is one of those activities; any residual trips become a final tour (`.../components/tour_finder/ActivityTourFinder.java:29-55`; `.../components/tour_finder/AbstractTourFinder.java:15-40`).

Consequences:

- `home -> work -> other -> home` and `home -> education -> shop -> home` are each one tour; state must persist through every intermediate trip/activity.
- Two `home -> ... -> home` chains are two tours; the first must return the motorcycle to its designated home link before the second initializes at home.
- A non-home-to-non-home trip is not a reset boundary.
- A plan that begins or ends outside home produces a partial/residual tour. It cannot silently assume `motorcycleLocation=HOME`; it needs an explicit initial vehicle location or must be classified as incomplete and excluded from a closed-tour same-day claim.
- Copied/replanned plans must recompute state from the candidate/plan being evaluated. Never preserve a mutable state object on the person or copied activity.

The verified population counts—268,095 home-to-non-home, 268,095 non-home-to-home, 8,224 non-home-to-non-home, and no home-to-home substantive trips—make complete outbound/return pairing common, but they do not remove intermediate trips, multiple tours, incomplete plans, or route-level parking-location choice. Absence of home-to-home trips does not simplify the invariant.

## 19. Thread safety and candidate isolation

DMC creates replanning algorithms/models per multithreaded module instance (`.../replanning/DiscreteModeChoiceReplanningModule.java:18-43`), while `TripRouter.calcRoute` is synchronized (`matsim...sources.jar!/org/matsim/core/router/TripRouter.java:146-179`). Neither fact makes shared mutable state safe.

Required design rules:

- state lives on the Java stack inside one tour-candidate evaluation;
- every alternative starts from an immutable snapshot;
- every route request gets a fresh copied `Attributes` object;
- no static map keyed by person;
- no mutable person/activity attributes for candidate-specific state;
- no thread-local communication between estimator and stop finder;
- no mutation of state while merely generating `InitialStop` candidates, because those candidates are alternatives and only one becomes the route;
- advance state only after the returned routed candidate has been inspected;
- cache keys include all state that affects feasibility, or state-dependent modes are not cached;
- selected plans receive only the final route elements, not internal state objects from rejected candidates.

SwissRailRaptor core itself documents that it is not thread-safe because it keeps internal arrays during routing (`matsim...sources.jar!/ch/sbb/matsim/routing/pt/raptor/SwissRailRaptorCore.java:43-69`). Existing synchronized `TripRouter` calls serialize use of a router instance, but a future direct RAPTOR bridge must preserve the provider/instance thread-safety assumptions rather than sharing a core instance arbitrarily.

## 20. Required unit and integration tests for any 2.9.0

### 20.1 Pure state/route inspection

1. `home -> motorcycle -> walk -> pt -> work`, then `work -> pt -> walk -> motorcycle -> home`, with access end link equal to egress start link: **allowed**, terminal state HOME.
2. Same structure with return motorcycle start link `Q != P`: **not selectable**; primary router must produce the route via `P` if one exists, not merely reject the tour.
3. Outbound motorcycle access followed by return home without retrieval: **forbidden** at complete-tour close.
4. Motorcycle egress without prior unmatched parking: **forbidden**.
5. Motorcycle parked at `P`, then ordinary main-mode motorcycle beginning elsewhere: **forbidden before routing/selection**.
6. Walk and `mcodt` feeders only: **allowed and byte/object-equivalent where no state rule applies**.
7. `home -> work -> shop -> home` with state held through the intermediate activity and retrieved at `P`: **allowed**.
8. Two complete home-based tours: first must close at HOME; second starts with an independent HOME state, not the first tour's stale object.
9. Multiple PT transfers and `motorcycle -> walk -> pt`: use the motorcycle route end link, not first PT stop ID.

Also test null routes/links, multiple motorcycle blocks, vehicle-ID mismatch, case-insensitive home semantics, partial tours, copied plans, and final walk-only PT fallbacks.

### 20.2 RAPTOR boundary

- Generate several motorcycle `InitialStop` candidates at `P`, `Q`, plus walk and `mcodt`.
- With current state `P`, prove a retrieval request removes motorcycle-at-`Q` before RAPTOR.
- Prove valid motorcycle-at-`P`, walk, and `mcodt` retain candidate identity/cost when retrieval is not mandatory.
- When retrieval is mandatory, prove direct walk cannot replace an available route via `P`.
- When no route via `P` exists, return explicit infeasibility and do not teleport/relabel/post-convert a route.
- Enumerate two outbound parking links and prove the tour-level choice accounts for both outbound and conditioned return utility.

### 20.3 DMC integration

- Run `TourBasedModel` with deterministic synthetic utilities and a custom stateful estimator.
- Prove the return trip sees the exact `P` selected in the outbound routed candidate before its stop finder runs.
- Prove a rejected mode chain does not leak state to the next candidate.
- Prove two persons evaluated concurrently do not share state.
- Prove cache behaviour differs for current state `P` versus `Q`.
- Exercise `INITIAL_CHOICE`, `IGNORE_AGENT`, and `EXCEPTION` fallback policies; no policy may bypass the final invariant.
- Verify the selected `RoutedTripCandidate` elements inserted by `DiscreteModeChoiceAlgorithm` still satisfy continuity.

### 20.4 Startup/general routing

10. An input plan containing simple direct `pt` legs must be expanded by the startup path into a paired, continuous route.
11. The same plan through DMC tour routing must satisfy the identical state transitions and location key.
12. `PrepareForMobsim` route checks and any enabled generic route consistency step must not alter or invalidate the pair.

Add startup cases with missing activity link IDs, multiple plans, non-selected plans, repeated OD pairs, and two home tours. These decide whether the narrow prepare hook is sufficient or a `PrepareForSim` replacement is necessary.

### 20.5 Mobsim-level smoke test

Use a tiny deterministic network, not Jakarta production:

- one person, one explicit motorcycle vehicle ID, two possible parking links;
- `vehicleBehavior=exception`;
- outbound access and return retrieval at the same link;
- assert vehicle leaves home link, parks at `P`, leaves `P`, and parks home;
- assert zero stuck agents, missing vehicles, vehicle teleports, duplicate vehicle placement, and continuity-verifier failures.

## 21. Future post-run same-vehicle QC

### 21.1 Required exported record

Export one row per motorcycle state transition, with at least:

- iteration and selected-plan identifier/version;
- person ID;
- logical motorcycle ID;
- routed `NetworkRoute.vehicleId` (nullable only as a separately reported defect before mobsim backfill);
- tour index and complete/partial-tour flag;
- substantive trip index;
- origin and destination activity types;
- transition role: `PT_ACCESS_PARK`, `PT_EGRESS_RETRIEVE`, or `ORDINARY_MOTORCYCLE_MOVE`;
- state link before and after;
- every motorcycle route start/end link pair;
- authoritative parking or retrieval link;
- adjacent PT access/egress stop IDs as auxiliary data;
- full routed mode chain;
- PT line/route IDs where relevant;
- routing pathway (`STARTUP`, `DMC`, or other reroute);
- matched parking-event ID for each retrieval; and
- validation outcome/reason.

For an actual-vehicle claim, also reconcile selected-plan transitions with QSim vehicle events by `vehicleId`, link, and time. A log/event flag for any `NetworkModeDepartureHandlerDefaultImpl` vehicle teleport is mandatory.

### 21.2 Audit algorithm

For each person, process substantive trips in plan order and tours from the same tour finder used by DMC:

1. initialize from the explicit/canonical home motorcycle link;
2. verify every motorcycle route starts at the stored link;
3. apply every route transition in order;
4. on PT access, create exactly one unmatched parking event with `P`;
5. while at `P`, reject any motorcycle transition starting elsewhere;
6. on PT egress, require one prior unmatched parking event for the person/tour/logical vehicle and exact link `P`;
7. mark it matched once only;
8. include ordinary main-mode motorcycle movements in the same state;
9. at each complete home-tour end, require the designated home link;
10. report partial tours separately; and
11. reconcile route-level and event-level vehicle identity/location sequences.

Hard acceptance counters should include:

```text
unmatched parking events                         = 0
unmatched retrieval events                       = 0
retrieval link != matched parking link            = 0
motorcycle starts away from stored location       = 0
duplicate use / one parking matched more than once= 0
complete tours ending away from home              = 0
ordinary-MC conflicts with feeder state           = 0
missing/uninspectable motorcycle route endpoints  = 0
route vehicle-ID mismatches                       = 0
QSim motorcycle vehicle teleports                 = 0
stranded motorcycles on complete tours            = 0
```

Only after those person/tour/link checks pass should aggregate parking count equal aggregate retrieval count.

### 21.3 Legitimate exceptions

Exceptions are not silent relaxations:

- **Incomplete day/tour:** a plan ending away from home can retain a motorcycle overnight only under an explicit multi-day boundary model. Otherwise flag/exclude it from a closed-tour claim.
- **Multiple motorcycles:** requires a declared fleet and separate logical/physical IDs; it invalidates the one-per-person state assumption.
- **Shared household motorcycle:** requires a household/time-ordered multi-person state, which is outside this proposed architecture.
- **Failed PT routing:** no selected parking event should be emitted; a selected fallback must be audited under its actual legs.
- **Replanning copies:** unselected plans are not events. Every selected plan must be re-audited after routing.
- **Ordinary motorcycle trips:** not an exception; they must be included in the same state.

## 22. Sankey/thesis claims and final recommendation

### Claims justified by validated 2.8.0

- Motorcycle PT access is restricted to trips whose substantive origin is home.
- Motorcycle PT egress is restricted to trips whose substantive destination is home.
- Walk and `mcodt` feeders remain generally available.
- The routing-level restriction applies to startup/general and DMC PT requests in the tested configuration.

### Claims not justified by 2.8.0

- The outbound and return feeder use the same motorcycle.
- Every motorcycle access parking event has a later retrieval.
- Retrieval occurs at the same station, facility, coordinate, or link.
- The motorcycle remains parked throughout intermediate travel/activities.
- No motorcycle is teleported, stranded, duplicated, or used elsewhere.
- Aggregate access/egress counts represent matched flows.

The observed 2.8.0 counts—8,641 motorcycle PT-access trips versus 6,926 motorcycle PT-egress trips—are themselves incompatible with an unqualified closed-system one-to-one Sankey interpretation. Under the strict simplifying assumption that every event belongs to a complete closed home tour and no other motorcycle movement resolves state, the net difference is at least 1,715 unmatched parking events. The real forensic audit must match person, tour, logical vehicle, and link before drawing any conclusion; aggregate subtraction is context, not proof.

### Conditions for a future Sankey statement

The statement “X motorcycle feeder parking events are matched by X retrieval events” is justified only when:

- every one of the `X` pairs has the same person, complete home-based tour, logical/actual motorcycle ID, and parking/retrieval link;
- pairing is one-to-one and time ordered;
- ordinary motorcycle movements are reconciled;
- every complete tour starts and ends at the designated home motorcycle link;
- partial/multi-day/multi-vehicle cases are reported separately; and
- event-level QSim evidence shows no teleport or duplicate vehicle use.

Global equality is then a consequence of the individual invariant. Equality should also hold by person and by each complete home-based tour. By parking location, arrivals and departures should balance within the analysed closed set, subject only to explicitly modelled boundary carry-over.

### Complexity/risk estimate

The likely implementation is on the order of two routing adapters, a paired-state search/coordinator, an SRR compatibility bridge/wrapper, a shared route/state domain layer, final validators/QC, and a substantial synthetic test harness. The difficult work is not the state record; it is preserving route-choice alternatives, utility consistency, startup parity, and cache/thread isolation. Expect moderate-to-high engineering complexity and high validation risk, plus changed PT stop/feeder availability that may require calibration sensitivity checks.

### Final recommendation

**Retain Jakarta 2.8.0 for the current PhD resubmission. Do not label it a physical motorcycle-continuity model and do not use access/egress aggregate equality as a proxy.**

If a later research phase requires a same-vehicle claim, begin with a small synthetic prototype of the rank-1 shared paired-state architecture. Promotion to 2.9.0 should occur only after both startup and DMC integration tests, state-aware cache tests, QSim `vehicleBehavior=exception` smoke tests, and the person/tour/link/vehicle QC all pass with zero violations.
